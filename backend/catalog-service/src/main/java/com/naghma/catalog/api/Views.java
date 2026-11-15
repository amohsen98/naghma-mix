package com.naghma.catalog.api;

import com.naghma.catalog.model.Analysis;
import com.naghma.catalog.model.Track;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class Views {

    private Views() {}

    public record TrackView(
            UUID id,
            String title,
            String artist,
            String filename,
            Track.Status status,
            String error,
            String createdAt,
            String analyzedAt,
            Double durationSec,
            Double bpm,
            Double lufs,
            Double energy,
            String tonality,
            Integer keyTonicPc,
            String keyMode,
            String camelot,
            Double keyConfidence,
            String maqam,
            Double maqamConfidence,
            Boolean maqamUncertain,
            String tonicNote,
            Set<String> editedFields) {

        public static TrackView of(Track t, Analysis a) {
            return new TrackView(t.id, t.title, t.artist, t.originalFilename, t.status, t.error,
                    t.createdAt.toString(), t.analyzedAt == null ? null : t.analyzedAt.toString(),
                    a == null ? null : a.durationSec,
                    a == null ? null : a.bpm,
                    a == null ? null : a.lufs,
                    a == null ? null : a.energy,
                    a == null ? null : a.tonality,
                    a == null ? null : a.keyTonicPc,
                    a == null ? null : a.keyMode,
                    a == null ? null : a.camelot,
                    a == null ? null : a.keyConfidence,
                    a == null ? null : a.maqam,
                    a == null ? null : a.maqamConfidence,
                    a == null ? null : a.maqamUncertain,
                    a == null ? null : a.tonicNote,
                    a == null ? Set.of() : a.editedFields);
        }
    }

    public record TrackDetail(TrackView track, List<Analysis.EnergyPoint> energyCurve,
                              List<Analysis.Candidate> maqamCandidates, Double tonicHz, Double tonicCents,
                              Double quarterToneRatio, List<Double> pitchHistogram, String modelVersion) {}

    /** Compact features for the mix service. */
    public record Features(UUID id, String title, String artist, String filename, Double durationSec, Double bpm,
                           Double energy, List<Analysis.EnergyPoint> energyCurve, String tonality, Integer keyTonicPc,
                           String keyMode, String camelot, String maqam, Double tonicCents, Boolean maqamUncertain) {

        public static Features of(Track t, Analysis a) {
            return new Features(t.id, t.title, t.artist, t.originalFilename, a.durationSec, a.bpm, a.energy,
                    a.energyCurve, a.tonality, a.keyTonicPc, a.keyMode, a.camelot, a.maqam, a.tonicCents,
                    a.maqamUncertain);
        }
    }

    public record Page<T>(List<T> items, long total, int page, int size) {}
}
