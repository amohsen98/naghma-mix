package com.naghma.mix.domain;

import java.util.List;
import java.util.UUID;

/**
 * What the mix engine needs to know about a track. Built from the catalog service's
 * analysis; null fields mean "unknown" and score neutrally.
 *
 * @param tonality   "WESTERN" or "ARABIC_MODAL"
 * @param keyTonicPc Western tonic pitch class 0-11 (C = 0)
 * @param keyMode    "major" or "minor"
 * @param maqam      maqam name for Arabic tracks, e.g. "Bayati"
 * @param tonicCents maqam tonic as a pitch class in cents (C = 0), quarter tones allowed
 */
public record TrackFeatures(
        UUID id,
        String title,
        String artist,
        String filename,
        Double durationSec,
        Double bpm,
        Double energy,
        List<Double> energyCurve,
        String tonality,
        Integer keyTonicPc,
        String keyMode,
        String camelot,
        String maqam,
        Double tonicCents,
        boolean maqamUncertain) {

    public boolean isArabic() {
        return "ARABIC_MODAL".equals(tonality) && maqam != null && tonicCents != null;
    }

    public boolean hasWesternKey() {
        return keyTonicPc != null && keyMode != null;
    }

    /** Energy at the start / end of the track, falling back to the average. */
    public double energyIn() {
        return energyCurve == null || energyCurve.isEmpty() ? energyOr(0.5) : energyCurve.getFirst();
    }

    public double energyOut() {
        return energyCurve == null || energyCurve.isEmpty() ? energyOr(0.5) : energyCurve.getLast();
    }

    public double energyOr(double fallback) {
        return energy == null ? fallback : energy;
    }

    public String label() {
        if (artist == null || artist.isBlank()) {
            return title;
        }
        return artist + " - " + title;
    }
}
