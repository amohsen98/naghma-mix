"""Kafka worker: consume analysis jobs, analyze audio from S3, publish results.

Delivery is at-least-once: the offset is committed only after a result (DONE or FAILED)
has been published. The catalog service treats results idempotently by jobId.
"""
from __future__ import annotations

import json
import logging
import os
import signal
import tempfile
import threading
import time
from http.server import BaseHTTPRequestHandler, HTTPServer

import boto3
from botocore.config import Config
from confluent_kafka import Consumer, KafkaException, Producer

from .analyze import analyze_file

log = logging.getLogger("naghma_worker")


class Settings:
    kafka = os.environ.get("KAFKA_BOOTSTRAP_SERVERS", "localhost:29092")
    jobs_topic = os.environ.get("JOBS_TOPIC", "analysis.jobs")
    results_topic = os.environ.get("RESULTS_TOPIC", "analysis.results")
    dlq_topic = os.environ.get("DLQ_TOPIC", "analysis.jobs.dlq")
    group = os.environ.get("CONSUMER_GROUP", "analysis-workers")
    s3_endpoint = os.environ.get("S3_ENDPOINT", "http://localhost:9000")
    s3_key = os.environ.get("S3_ACCESS_KEY", "naghma")
    s3_secret = os.environ.get("S3_SECRET_KEY", "naghma-dev-secret")
    s3_region = os.environ.get("S3_REGION", "us-east-1")
    max_attempts = int(os.environ.get("MAX_ATTEMPTS", "3"))
    health_port = int(os.environ.get("HEALTH_PORT", "8090"))


class Worker:
    def __init__(self, s: Settings) -> None:
        self.s = s
        self.running = True
        self.healthy = False
        self.consumer = Consumer(
            {
                "bootstrap.servers": s.kafka,
                "group.id": s.group,
                "enable.auto.commit": False,
                "auto.offset.reset": "earliest",
                # analysis can take minutes; don't get kicked out of the group
                "max.poll.interval.ms": 900_000,
            }
        )
        self.producer = Producer({"bootstrap.servers": s.kafka, "enable.idempotence": True, "acks": "all"})
        self.s3 = boto3.client(
            "s3",
            endpoint_url=s.s3_endpoint,
            aws_access_key_id=s.s3_key,
            aws_secret_access_key=s.s3_secret,
            region_name=s.s3_region,
            config=Config(s3={"addressing_style": "path"}),
        )

    def run(self) -> None:
        self.consumer.subscribe([self.s.jobs_topic])
        self.healthy = True
        log.info("worker started, topic=%s group=%s", self.s.jobs_topic, self.s.group)
        try:
            while self.running:
                msg = self.consumer.poll(1.0)
                if msg is None:
                    continue
                if msg.error():
                    log.error("kafka error: %s", msg.error())
                    continue
                self.handle(msg.value())
                self.consumer.commit(message=msg, asynchronous=False)
        finally:
            self.healthy = False
            self.consumer.close()
            self.producer.flush(10)

    def handle(self, raw: bytes) -> None:
        try:
            job = json.loads(raw)
            job_id, track_id = job["jobId"], job["trackId"]
        except (ValueError, KeyError) as e:
            log.error("malformed job, sending to DLQ: %s", e)
            self._send(self.s.dlq_topic, None, raw)
            return

        last_error = None
        for attempt in range(1, self.s.max_attempts + 1):
            try:
                result = self.process(job)
                self._send(self.s.results_topic, track_id, json.dumps(result).encode())
                log.info(
                    "done job=%s track=%s bpm=%s key=%s maqam=%s ms=%s",
                    job_id, track_id, result["bpm"], result["key"].get("camelot"),
                    result["maqam"].get("name"), result.get("timingsMs"),
                )
                return
            except Exception as e:  # noqa: BLE001 - every failure is reported
                last_error = e
                log.warning("job=%s attempt %d/%d failed: %s", job_id, attempt, self.s.max_attempts, e)
                time.sleep(min(2**attempt, 30))

        failed = {"jobId": job_id, "trackId": track_id, "status": "FAILED", "error": str(last_error)[:500]}
        self._send(self.s.results_topic, track_id, json.dumps(failed).encode())
        self._send(self.s.dlq_topic, track_id, raw)

    def process(self, job: dict) -> dict:
        bucket, key = job["bucket"], job["objectKey"]
        suffix = os.path.splitext(key)[1] or ".audio"
        with tempfile.NamedTemporaryFile(suffix=suffix) as tmp:
            self.s3.download_file(bucket, key, tmp.name)
            result = analyze_file(tmp.name)
        if job.get("deleteAudio", True):
            self.s3.delete_object(Bucket=bucket, Key=key)
        return {"jobId": job["jobId"], "trackId": job["trackId"], "status": "DONE", **result}

    def _send(self, topic: str, key: str | None, value: bytes) -> None:
        self.producer.produce(topic, key=key, value=value)
        remaining = self.producer.flush(30)
        if remaining:
            raise KafkaException(f"{remaining} message(s) not delivered to {topic}")


def serve_health(worker: Worker, port: int) -> None:
    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):  # noqa: N802
            ok = worker.healthy
            self.send_response(200 if ok else 503)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"status": "UP" if ok else "DOWN"}).encode())

        def log_message(self, *args):
            pass

    server = HTTPServer(("0.0.0.0", port), Handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()


def main() -> None:
    logging.basicConfig(level=os.environ.get("LOG_LEVEL", "INFO"), format="%(asctime)s %(levelname)s %(name)s %(message)s")
    s = Settings()
    worker = Worker(s)
    serve_health(worker, s.health_port)

    def stop(*_):
        worker.running = False

    signal.signal(signal.SIGTERM, stop)
    signal.signal(signal.SIGINT, stop)
    worker.run()


if __name__ == "__main__":
    main()
