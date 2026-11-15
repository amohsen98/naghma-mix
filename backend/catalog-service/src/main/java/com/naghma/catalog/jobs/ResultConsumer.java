package com.naghma.catalog.jobs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.naghma.catalog.model.Analysis;
import com.naghma.catalog.model.AnalysisJob;
import com.naghma.catalog.model.Track;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.logging.Log;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.eclipse.microprofile.reactive.messaging.Incoming;

/**
 * Stores worker results. Idempotent: a result is applied only while its job is PENDING,
 * so redelivered or duplicated messages are ignored. Malformed messages go to the
 * dead-letter topic configured on the channel.
 */
@ApplicationScoped
public class ResultConsumer {

    @Inject
    ObjectMapper json;

    @Inject
    MeterRegistry metrics;

    @Incoming("analysis-results")
    @Blocking
    @Transactional
    public void onResult(String payload) throws Exception {
        AnalysisResult r = json.readValue(payload, AnalysisResult.class);
        AnalysisJob job = r.jobId() == null ? null : AnalysisJob.findById(r.jobId());
        if (job == null || job.status != AnalysisJob.Status.PENDING) {
            Log.debugf("ignoring result for job %s (unknown or already finished)", r.jobId());
            return;
        }
        job.finishedAt = Instant.now();
        Track track = Track.findById(job.trackId);
        if (track == null) { // deleted while analyzing
            job.status = AnalysisJob.Status.FAILED;
            return;
        }
        if (!r.done()) {
            job.status = AnalysisJob.Status.FAILED;
            track.status = Track.Status.FAILED;
            track.error = r.error() == null ? "analysis failed" : r.error();
            metrics.counter("naghma.analysis.results", "status", "failed").increment();
            return;
        }
        apply(r, track);
        job.status = AnalysisJob.Status.DONE;
        track.status = Track.Status.DONE;
        track.analyzedAt = Instant.now();
        metrics.counter("naghma.analysis.results", "status", "done", "tonality", String.valueOf(r.tonality())).increment();
    }

    static void apply(AnalysisResult r, Track track) {
        Analysis a = Analysis.findById(track.id);
        if (a == null) {
            a = new Analysis();
            a.trackId = track.id;
            a.editedFields = Set.of();
        }
        Set<String> edited = a.editedFields == null ? Set.of() : a.editedFields;
        a.jobId = r.jobId();
        a.modelVersion = r.modelVersion();
        a.analyzerVersion = r.analyzerVersion();
        a.durationSec = r.durationSec();
        a.lufs = r.lufs();
        a.energy = r.energy();
        a.energyCurve = r.energyCurve();
        a.beatTimes = r.beatTimes();
        a.pitchHistogram = r.pitchHistogram();
        a.maqamTimeline = r.maqamTimeline() == null ? List.of() : r.maqamTimeline();
        a.timings = r.timingsMs();
        if (!edited.contains("bpm")) {
            a.bpm = r.bpm();
        }
        if (!edited.contains("tonality")) {
            a.tonality = r.tonality();
        }
        if (r.key() != null && !edited.contains("key")) {
            a.keyTonicPc = r.key().tonicPc();
            a.keyMode = r.key().mode();
            a.camelot = r.key().camelot();
            a.keyConfidence = r.key().confidence();
        }
        if (r.maqam() != null) {
            if (!edited.contains("maqam")) {
                a.maqam = r.maqam().name() == null || r.maqam().name().isBlank() ? null : r.maqam().name();
                a.maqamConfidence = r.maqam().confidence();
                a.maqamUncertain = r.maqam().uncertain();
                a.maqamCandidates = r.maqam().candidates();
            }
            if (!edited.contains("tonic")) {
                a.tonicCents = r.maqam().tonicCents();
                a.tonicHz = r.maqam().tonicHz();
                a.tonicNote = r.maqam().tonicNote();
            }
            a.quarterToneRatio = r.maqam().quarterToneRatio();
        }
        a.updatedAt = Instant.now();
        a.persist();
    }
}
