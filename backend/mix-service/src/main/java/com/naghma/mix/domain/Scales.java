package com.naghma.mix.domain;

import java.util.Map;

/**
 * Scales in cents above the tonic. Must stay in sync with the worker's
 * naghma_worker/maqam.py (24-tone approximation, Maqam World conventions).
 */
public final class Scales {

    public record Scale(String name, String arabic, int[] cents, String lowerJins, String upperJins) {}

    public static final Scale MAJOR = new Scale("major", "ماجير", new int[] {0, 200, 400, 500, 700, 900, 1100}, "-", "-");
    public static final Scale MINOR = new Scale("minor", "مينير", new int[] {0, 200, 300, 500, 700, 800, 1000}, "-", "-");

    public static final Map<String, Scale> MAQAMAT = Map.of(
            "Rast", new Scale("Rast", "راست", new int[] {0, 200, 350, 500, 700, 900, 1050}, "Rast", "Rast"),
            "Bayati", new Scale("Bayati", "بياتي", new int[] {0, 150, 300, 500, 700, 800, 1000}, "Bayati", "Nahawand"),
            "Saba", new Scale("Saba", "صبا", new int[] {0, 150, 300, 400, 700, 800, 1000}, "Saba", "Hijaz"),
            "Sikah", new Scale("Sikah", "سيكاه", new int[] {0, 150, 350, 550, 700, 850, 1050}, "Sikah", "Rast"),
            "Hijaz", new Scale("Hijaz", "حجاز", new int[] {0, 100, 400, 500, 700, 800, 1000}, "Hijaz", "Nahawand"),
            "Nahawand", new Scale("Nahawand", "نهاوند", new int[] {0, 200, 300, 500, 700, 800, 1100}, "Nahawand", "Hijaz"),
            "Kurd", new Scale("Kurd", "كرد", new int[] {0, 100, 300, 500, 700, 800, 1000}, "Kurd", "Nahawand"),
            "Ajam", new Scale("Ajam", "عجم", new int[] {0, 200, 400, 500, 700, 900, 1100}, "Ajam", "Ajam"));

    private static final String[] NOTE_NAMES = {"C", "Db", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B"};

    private Scales() {}

    /** Absolute pitch classes (cents, C = 0) of the track's scale, or null if unknown. */
    public static double[] pitchClasses(TrackFeatures t) {
        Scale scale;
        double tonic;
        if (t.isArabic() && MAQAMAT.containsKey(t.maqam())) {
            scale = MAQAMAT.get(t.maqam());
            tonic = t.tonicCents();
        } else if (t.hasWesternKey()) {
            scale = "major".equals(t.keyMode()) ? MAJOR : MINOR;
            tonic = t.keyTonicPc() * 100.0;
        } else {
            return null;
        }
        double[] pcs = new double[scale.cents().length];
        for (int i = 0; i < pcs.length; i++) {
            pcs[i] = mod1200(tonic + scale.cents()[i]);
        }
        return pcs;
    }

    public static double tonic(TrackFeatures t) {
        return t.isArabic() ? t.tonicCents() : t.keyTonicPc() * 100.0;
    }

    public static String describe(TrackFeatures t) {
        if (t.isArabic()) {
            return t.maqam() + " on " + noteName(t.tonicCents());
        }
        if (t.hasWesternKey()) {
            return NOTE_NAMES[Math.floorMod(t.keyTonicPc(), 12)] + ("major".equals(t.keyMode()) ? " major" : " minor");
        }
        return "unknown key";
    }

    /** 24-tone note name: 350 cents -> "E half-flat". */
    public static String noteName(double cents) {
        int q = (int) Math.round(mod1200(cents) / 50.0) % 24;
        if (q % 2 == 0) {
            return NOTE_NAMES[q / 2];
        }
        return NOTE_NAMES[((q + 1) / 2) % 12] + " half-flat";
    }

    public static double mod1200(double c) {
        double m = c % 1200.0;
        return m < 0 ? m + 1200.0 : m;
    }

    /** Shortest distance between two pitch classes, 0..600 cents. */
    public static double circularDistance(double a, double b) {
        double d = Math.abs(mod1200(a) - mod1200(b));
        return Math.min(d, 1200.0 - d);
    }
}
