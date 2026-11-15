package com.naghma.catalog.jobs;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.naghma.catalog.model.Analysis;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Result message from the Python worker (naghma_worker/analyze.py). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AnalysisResult(
        UUID jobId,
        UUID trackId,
        String status,
        String error,
        String analyzerVersion,
        String modelVersion,
        Double durationSec,
        Double bpm,
        List<Double> beatTimes,
        Double lufs,
        Double energy,
        List<Analysis.EnergyPoint> energyCurve,
        String tonality,
        Key key,
        Maqam maqam,
        List<Double> pitchHistogram,
        Map<String, Integer> timingsMs) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Key(String tonic, Integer tonicPc, String mode, String camelot, Double confidence) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Maqam(String name, Double confidence, Boolean uncertain, List<Analysis.Candidate> candidates,
                        Double tonicCents, Double tonicHz, String tonicNote, Double quarterToneRatio) {}

    public boolean done() {
        return "DONE".equals(status);
    }
}
