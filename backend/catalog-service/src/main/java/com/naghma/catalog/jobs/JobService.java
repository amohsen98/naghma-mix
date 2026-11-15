package com.naghma.catalog.jobs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.naghma.catalog.model.AnalysisJob;
import com.naghma.catalog.model.OutboxEvent;
import com.naghma.catalog.model.Track;
import com.naghma.catalog.storage.StorageService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class JobService {

    /** Message contract with the Python worker (naghma_worker/main.py). */
    public record JobMessage(UUID jobId, UUID trackId, String bucket, String objectKey, boolean deleteAudio) {}

    @Inject
    ObjectMapper json;

    @Inject
    StorageService storage;

    @ConfigProperty(name = "naghma.kafka.jobs-topic", defaultValue = "analysis.jobs")
    String jobsTopic;

    /** Queue a track for analysis. Must run inside the caller's transaction. */
    @Transactional(Transactional.TxType.MANDATORY)
    public AnalysisJob enqueue(Track track) {
        AnalysisJob job = new AnalysisJob();
        job.id = UUID.randomUUID();
        job.trackId = track.id;
        job.status = AnalysisJob.Status.PENDING;
        job.createdAt = Instant.now();
        job.persist();

        OutboxEvent e = new OutboxEvent();
        e.topic = jobsTopic;
        e.key = track.id.toString();
        e.payload = write(new JobMessage(job.id, track.id, storage.bucket(), track.objectKey, !track.keepAudio));
        e.createdAt = Instant.now();
        e.persist();

        track.status = Track.Status.QUEUED;
        track.error = null;
        return job;
    }

    private String write(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
