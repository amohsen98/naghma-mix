package com.naghma.mix.api;

import com.naghma.mix.domain.Reason;
import com.naghma.mix.domain.Scales;
import com.naghma.mix.domain.TrackFeatures;
import com.naghma.mix.domain.TransitionScorer;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** JSON shapes returned by the mix API. */
public final class Views {

    private Views() {}

    public record TrackSummary(UUID id, String title, String artist, Double bpm, Double energy, String tonality,
                               String camelot, String maqam, String harmonicLabel) {
        public static TrackSummary of(TrackFeatures t) {
            return new TrackSummary(t.id(), t.title(), t.artist(), t.bpm(), t.energy(), t.tonality(), t.camelot(),
                    t.maqam(), Scales.describe(t));
        }
    }

    public record TransitionView(double score, Map<String, Double> components, List<Reason> reasons,
                                 int pitchShiftSemitones) {
        public static TransitionView of(TransitionScorer.Transition t) {
            return new TransitionView(t.score(), t.components(), t.reasons(), t.pitchShiftSemitones());
        }
    }

    public record SuggestionView(TrackSummary track, TransitionView transition) {}

    public record SetEntryView(int position, boolean locked, double targetEnergy, TrackSummary track,
                               TransitionView transitionToNext) {}

    public record SetView(UUID id, String name, String curve, Map<String, Double> weights, double objective,
                          double averageTransition, List<SetEntryView> entries, String createdAt, String updatedAt) {}

    public record SetListItem(UUID id, String name, String curve, int trackCount, String updatedAt) {}
}
