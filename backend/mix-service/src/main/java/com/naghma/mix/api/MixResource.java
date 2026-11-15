package com.naghma.mix.api;

import com.naghma.mix.catalog.FeatureSource;
import com.naghma.mix.domain.NextTrackRecommender;
import com.naghma.mix.domain.TrackFeatures;
import com.naghma.mix.domain.TransitionScorer;
import com.naghma.mix.domain.Weights;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import java.util.List;
import java.util.UUID;

@Path("/api/mix")
@RolesAllowed("user")
public class MixResource {

    @Inject
    FeatureSource features;

    /** Ranked next-track suggestions with reasons (FR-4.2, FR-4.3). */
    @GET
    @Path("/next/{trackId}")
    public List<Views.SuggestionView> next(@PathParam("trackId") UUID trackId,
                                           @QueryParam("limit") @DefaultValue("10") int limit,
                                           @QueryParam("direction") @DefaultValue("steady") String direction,
                                           @QueryParam("harmony") Double harmony,
                                           @QueryParam("tonic") Double tonic,
                                           @QueryParam("tempo") Double tempo,
                                           @QueryParam("energy") Double energy) {
        List<TrackFeatures> library = features.library();
        TrackFeatures current = library.stream().filter(t -> t.id().equals(trackId)).findFirst()
                .orElseThrow(() -> new NotFoundException("Track not found or not analyzed yet"));
        Weights w = weights(harmony, tonic, tempo, energy);
        return NextTrackRecommender.suggest(current, library, energyDelta(direction), w, limit).stream()
                .map(s -> new Views.SuggestionView(Views.TrackSummary.of(s.track()), Views.TransitionView.of(s.transition())))
                .toList();
    }

    /** Detailed score for one transition. */
    @GET
    @Path("/score")
    public Views.TransitionView score(@QueryParam("from") UUID from, @QueryParam("to") UUID to,
                                      @QueryParam("direction") @DefaultValue("steady") String direction) {
        if (from == null || to == null) {
            throw new BadRequestException("from and to are required");
        }
        List<TrackFeatures> pair = features.byIds(List.of(from, to));
        TrackFeatures a = pair.stream().filter(t -> t.id().equals(from)).findFirst().orElseThrow();
        TrackFeatures b = pair.stream().filter(t -> t.id().equals(to)).findFirst().orElseThrow();
        return Views.TransitionView.of(TransitionScorer.score(a, b, energyDelta(direction), Weights.DEFAULT));
    }

    static double energyDelta(String direction) {
        return switch (direction) {
            case "up" -> 0.15;
            case "down" -> -0.15;
            case "steady" -> 0.0;
            default -> throw new BadRequestException("direction must be up, down or steady");
        };
    }

    static Weights weights(Double harmony, Double tonic, Double tempo, Double energy) {
        try {
            return Weights.of(harmony, tonic, tempo, energy);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
    }
}
