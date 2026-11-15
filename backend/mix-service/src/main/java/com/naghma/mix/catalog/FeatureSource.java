package com.naghma.mix.catalog;

import com.naghma.mix.domain.TrackFeatures;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.rest.client.inject.RestClient;

/** Loads features for the current user, with timeout, retry and a circuit breaker. */
@ApplicationScoped
public class FeatureSource {

    @Inject
    @RestClient
    CatalogClient catalog;

    @Inject
    JsonWebToken jwt;

    @Timeout(5000)
    @Retry(maxRetries = 2, delay = 200, abortOn = NotFoundException.class)
    @CircuitBreaker(requestVolumeThreshold = 10, failureRatio = 0.5, delay = 10_000)
    public List<TrackFeatures> library() {
        return fetch(null);
    }

    @Timeout(5000)
    @Retry(maxRetries = 2, delay = 200, abortOn = NotFoundException.class)
    @CircuitBreaker(requestVolumeThreshold = 10, failureRatio = 0.5, delay = 10_000)
    public List<TrackFeatures> byIds(Collection<UUID> ids) {
        List<TrackFeatures> found = existing(ids);
        if (found.size() != ids.size()) {
            throw new NotFoundException("Some tracks were not found or are not analyzed yet");
        }
        return found;
    }

    /** Only the tracks that still exist; a saved set stays viewable after a track is deleted. */
    @Timeout(5000)
    @Retry(maxRetries = 2, delay = 200)
    @CircuitBreaker(requestVolumeThreshold = 10, failureRatio = 0.5, delay = 10_000)
    public List<TrackFeatures> existing(Collection<UUID> ids) {
        return fetch(ids.stream().map(UUID::toString).collect(Collectors.joining(",")));
    }

    private List<TrackFeatures> fetch(String ids) {
        return catalog.features(ids, "Bearer " + jwt.getRawToken()).stream().map(FeatureSource::toDomain).toList();
    }

    static TrackFeatures toDomain(CatalogClient.Features f) {
        List<Double> curve = f.energyCurve() == null ? List.of()
                : f.energyCurve().stream().map(CatalogClient.EnergyPoint::energy).toList();
        return new TrackFeatures(f.id(), f.title(), f.artist(), f.filename(), f.durationSec(), f.bpm(), f.energy(),
                curve, f.tonality(), f.keyTonicPc(), f.keyMode(), f.camelot(), f.maqam(), f.tonicCents(),
                Boolean.TRUE.equals(f.maqamUncertain()));
    }
}
