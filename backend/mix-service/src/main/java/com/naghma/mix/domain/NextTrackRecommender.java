package com.naghma.mix.domain;

import java.util.Comparator;
import java.util.List;

/** Ranked "what next?" suggestions (FR-4.2). */
public final class NextTrackRecommender {

    public record Suggestion(TrackFeatures track, TransitionScorer.Transition transition) {}

    private NextTrackRecommender() {}

    /**
     * @param desiredEnergyDelta +0.15 to build energy, -0.15 to bring it down, 0 to hold
     */
    public static List<Suggestion> suggest(TrackFeatures current, List<TrackFeatures> library,
                                           double desiredEnergyDelta, Weights weights, int limit) {
        return library.stream()
                .filter(t -> !t.id().equals(current.id()))
                .map(t -> new Suggestion(t, TransitionScorer.score(current, t, desiredEnergyDelta, weights)))
                .sorted(Comparator.comparingDouble((Suggestion s) -> s.transition().score()).reversed()
                        .thenComparing(s -> s.track().title(), Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(Math.max(1, Math.min(limit, 50)))
                .toList();
    }
}
