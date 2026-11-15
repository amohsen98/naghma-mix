package com.naghma.catalog.jobs;

import com.naghma.catalog.model.OutboxEvent;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.reactive.messaging.MutinyEmitter;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Message;

/**
 * Relays outbox rows to Kafka. FOR UPDATE SKIP LOCKED lets several catalog instances run
 * the relay without sending a row twice; a row is marked sent only after Kafka acked it.
 * If the relay crashes after the ack but before the commit, the row is sent again, which
 * is why the worker and the result consumer are idempotent by jobId.
 *
 * The query is native SQL (Hibernate's query language has no SKIP LOCKED), so the schema
 * is not applied automatically: {h-schema} expands to the configured default schema.
 */
@ApplicationScoped
public class OutboxRelay {

    private static final int BATCH = 100;

    @Inject
    EntityManager em;

    @Inject
    @Channel("analysis-jobs")
    MutinyEmitter<String> jobs;

    @Scheduled(every = "${naghma.outbox.interval:1s}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    @Transactional
    void relay() {
        @SuppressWarnings("unchecked")
        List<OutboxEvent> batch = em.createNativeQuery(
                        "SELECT * FROM {h-schema}outbox WHERE sent_at IS NULL ORDER BY id LIMIT :n FOR UPDATE SKIP LOCKED",
                        OutboxEvent.class)
                .setParameter("n", BATCH)
                .getResultList();
        for (OutboxEvent e : batch) {
            try {
                Message<String> msg = Message.of(e.payload).addMetadata(
                        OutgoingKafkaRecordMetadata.<String>builder().withKey(e.key).withTopic(e.topic).build());
                jobs.sendMessageAndAwait(msg);
                e.sentAt = Instant.now();
            } catch (RuntimeException ex) {
                e.attempts++;
                Log.warnf("outbox %d not sent (attempt %d): %s", e.id, e.attempts, ex.getMessage());
                break; // keep order; retry on the next tick
            }
        }
    }

    /** Sent rows are only an audit trail; keep a week. */
    @Scheduled(every = "1h")
    @Transactional
    void purge() {
        OutboxEvent.delete("sentAt < ?1", Instant.now().minusSeconds(7 * 24 * 3600));
    }
}
