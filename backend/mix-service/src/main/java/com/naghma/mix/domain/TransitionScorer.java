package com.naghma.mix.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Scores playing track B right after track A:
 *
 *   S(A,B) = w_m·M + w_t·T + w_b·B + w_e·E   (normalized by the weight sum)
 *
 * M  harmonic compatibility: Camelot rules for two Western tracks; for any pair involving
 *    a maqam, the share of scale notes the two tracks have in common at their actual
 *    pitches (quarter tones included), optionally after a ±1 semitone key-lock shift.
 * T  tonic closeness: 1 at the same tonic, 0 a tritone apart.
 * B  tempo: BPM distance in percent, half/double time allowed.
 * E  energy: how close the change in energy is to the change the DJ wants here.
 */
public final class TransitionScorer {

    /** Notes closer than this are treated as the same pitch. */
    static final double SAME_PITCH_CENTS = 30.0;
    /** BPM gap (percent) at which the tempo score reaches zero. */
    static final double MAX_TEMPO_GAP_PCT = 8.0;

    public record Transition(double score, Map<String, Double> components, List<Reason> reasons, int pitchShiftSemitones) {}

    private TransitionScorer() {}

    public static Transition score(TrackFeatures a, TrackFeatures b, double desiredEnergyDelta, Weights w) {
        Map<String, Double> c = new LinkedHashMap<>();
        List<Reason> reasons = new ArrayList<>();

        Harmony h = harmony(a, b);
        c.put("harmony", h.score);
        reasons.addAll(h.reasons);

        double t = tonicCloseness(a, b);
        c.put("tonic", t);

        Tempo tempo = tempo(a, b);
        c.put("tempo", tempo.score);
        reasons.add(tempo.reason);

        double delta = b.energyIn() - a.energyOut();
        double e = clamp(1.0 - Math.abs(delta - desiredEnergyDelta) * 1.5);
        c.put("energy", e);
        reasons.add(energyReason(delta));

        double total = (w.harmony() * h.score + w.tonic() * t + w.tempo() * tempo.score + w.energy() * e) / w.sum();
        return new Transition(round(total), roundAll(c), reasons, h.shift);
    }

    record Harmony(double score, List<Reason> reasons, int shift) {
        Harmony(double score, Reason reason, int shift) {
            this(score, List.of(reason), shift);
        }
    }

    record Tempo(double score, Reason reason) {}

    static Harmony harmony(TrackFeatures a, TrackFeatures b) {
        if (!a.isArabic() && !b.isArabic() && a.hasWesternKey() && b.hasWesternKey()) {
            Camelot ca = Camelot.of(a.keyTonicPc(), a.keyMode());
            Camelot cb = Camelot.of(b.keyTonicPc(), b.keyMode());
            Camelot.Match m = ca.compatibility(cb);
            return new Harmony(m.score(), m.reason(), 0);
        }
        double[] pa = Scales.pitchClasses(a);
        double[] pb = Scales.pitchClasses(b);
        if (pa == null || pb == null) {
            return new Harmony(0.5, Reason.of("keyUnknown", "key unknown", Map.of()), 0);
        }
        int bestShared = -1;
        int bestShift = 0;
        for (int shift : new int[] {0, -1, 1}) { // 0 first: prefer no shift on ties
            int shared = sharedNotes(pa, pb, shift * 100.0);
            if (shared > bestShared) {
                bestShared = shared;
                bestShift = shift;
            }
        }
        int n = Math.min(pa.length, pb.length);
        double score = (double) bestShared / n;
        String from = Scales.describe(a);
        String to = Scales.describe(b);
        List<Reason> reasons = new ArrayList<>();
        if (a.isArabic() && b.isArabic() && a.maqam().equals(b.maqam())
                && Scales.circularDistance(a.tonicCents(), b.tonicCents()) < SAME_PITCH_CENTS) {
            reasons.add(Reason.of("sameMaqam", "same maqam (" + from + ")", Map.of("label", from)));
            score = 1.0;
        } else {
            reasons.add(Reason.of("sharedNotes", from + " → " + to + ": shares " + bestShared + " of " + n + " notes",
                    Map.of("from", from, "to", to, "shared", bestShared, "of", n)));
        }
        if (bestShift != 0) {
            score *= 0.9; // a key-lock shift is audible on vocals
            reasons.add(Reason.of("pitchShift", String.format("pitch %+d semitone", bestShift), Map.of("n", bestShift)));
        }
        if (a.maqamUncertain() || b.maqamUncertain()) {
            reasons.add(Reason.of("maqamUncertain", "maqam uncertain", Map.of()));
        }
        return new Harmony(score, reasons, bestShift);
    }

    static int sharedNotes(double[] a, double[] b, double shiftB) {
        int shared = 0;
        for (double x : a) {
            for (double y : b) {
                if (Scales.circularDistance(x, y + shiftB) < SAME_PITCH_CENTS) {
                    shared++;
                    break;
                }
            }
        }
        return shared;
    }

    /**
     * How related the two tonics are, by interval class rather than raw distance: a move to
     * the fourth or fifth (the usual modulation in both traditions) is close even though it
     * is 500-700 cents away, while a quarter-tone or tritone move is distant.
     */
    static double tonicCloseness(TrackFeatures a, TrackFeatures b) {
        boolean ka = a.isArabic() || a.hasWesternKey();
        boolean kb = b.isArabic() || b.hasWesternKey();
        if (!ka || !kb) {
            return 0.5;
        }
        double d = Scales.circularDistance(Scales.tonic(a), Scales.tonic(b)); // 0..600
        for (double[] rule : TONIC_INTERVALS) {
            if (Math.abs(d - rule[0]) < SAME_PITCH_CENTS) {
                return rule[1];
            }
        }
        return 0.2; // quarter-tone offsets and anything else
    }

    /** {interval class in cents (0..600), closeness}. 500 covers both the fourth and the fifth. */
    private static final double[][] TONIC_INTERVALS = {
        {0, 1.0}, {500, 0.8}, {300, 0.6}, {400, 0.5}, {200, 0.5}, {100, 0.3}, {600, 0.1},
    };

    static Tempo tempo(TrackFeatures a, TrackFeatures b) {
        if (a.bpm() == null || b.bpm() == null || a.bpm() <= 0 || b.bpm() <= 0) {
            return new Tempo(0.5, Reason.of("tempoUnknown", "tempo unknown", Map.of()));
        }
        double ba = a.bpm();
        double best = Double.MAX_VALUE;
        double target = b.bpm();
        String mode = "";
        for (double factor : new double[] {1.0, 2.0, 0.5}) {
            double gap = Math.abs(target * factor - ba) / ba * 100.0;
            if (gap < best) {
                best = gap;
                mode = factor == 1.0 ? "" : (factor == 2.0 ? "double" : "half");
            }
        }
        long diff = Math.round(target - ba);
        Reason reason;
        if (best > MAX_TEMPO_GAP_PCT) {
            reason = Reason.of("tempoJump", String.format("tempo jump %.0f → %.0f BPM", ba, target),
                    Map.of("from", Math.round(ba), "to", Math.round(target)));
        } else if (Math.abs(target - ba) < 0.5 && mode.isEmpty()) {
            reason = Reason.of("sameTempo", "same tempo", Map.of());
        } else {
            String text = String.format("%+d BPM", diff) + (mode.isEmpty() ? "" : " (" + mode + " time)");
            reason = Reason.of("bpmChange", text, Map.of("diff", diff, "time", mode));
        }
        return new Tempo(clamp(1.0 - best / MAX_TEMPO_GAP_PCT), reason);
    }

    static Reason energyReason(double delta) {
        if (delta > 0.08) {
            return Reason.of("energyUp", "energy up", Map.of());
        }
        if (delta < -0.08) {
            return Reason.of("energyDown", "energy down", Map.of());
        }
        return Reason.of("energySteady", "energy steady", Map.of());
    }

    static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    private static Map<String, Double> roundAll(Map<String, Double> m) {
        m.replaceAll((k, v) -> round(v));
        return m;
    }
}
