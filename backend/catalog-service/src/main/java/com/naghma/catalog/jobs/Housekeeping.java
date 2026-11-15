package com.naghma.catalog.jobs;

import com.naghma.catalog.model.Track;
import com.naghma.catalog.storage.StorageService;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Removes uploads that were started but never finished, so they stop counting against the quota. */
@ApplicationScoped
public class Housekeeping {

    private static final Duration ABANDONED_AFTER = Duration.ofHours(24);

    @Inject
    StorageService storage;

    @Scheduled(every = "1h", delayed = "1m")
    @Transactional
    void dropAbandonedUploads() {
        List<Track> stale = Track.list("status = ?1 and createdAt < ?2",
                Track.Status.AWAITING_UPLOAD, Instant.now().minus(ABANDONED_AFTER));
        for (Track t : stale) {
            storage.deleteQuietly(t.objectKey);
            t.delete();
        }
        if (!stale.isEmpty()) {
            Log.infof("removed %d abandoned uploads", stale.size());
        }
    }
}
