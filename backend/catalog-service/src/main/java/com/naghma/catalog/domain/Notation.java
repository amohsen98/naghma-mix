package com.naghma.catalog.domain;

import java.util.Locale;
import java.util.Set;

/** Key, Camelot and maqam notation used when DJs correct tags. */
public final class Notation {

    public static final Set<String> MAQAMAT = Set.of("Rast", "Bayati", "Saba", "Sikah", "Hijaz", "Nahawand", "Kurd", "Ajam");
    private static final String[] NOTES = {"C", "Db", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B"};
    private static final int[] CAMELOT_MAJOR = {8, 3, 10, 5, 12, 7, 2, 9, 4, 11, 6, 1};
    private static final int[] CAMELOT_MINOR = {5, 12, 7, 2, 9, 4, 11, 6, 1, 8, 3, 10};

    private Notation() {}

    public static String camelot(int tonicPc, String mode) {
        int pc = Math.floorMod(tonicPc, 12);
        return "major".equals(mode) ? CAMELOT_MAJOR[pc] + "B" : CAMELOT_MINOR[pc] + "A";
    }

    public static String noteName(int pc) {
        return NOTES[Math.floorMod(pc, 12)];
    }

    /** Parse "C", "F#", "Bb", "E half-flat" into cents (C = 0). */
    public static Integer parseNoteCents(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        String s = note.trim();
        int offset = 0;
        String lower = s.toLowerCase(Locale.ROOT);
        if (lower.endsWith("half-flat")) {
            offset = -50;
            s = s.substring(0, s.length() - "half-flat".length()).trim();
        }
        String[] sharps = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
        for (int i = 0; i < 12; i++) {
            if (NOTES[i].equalsIgnoreCase(s) || sharps[i].equalsIgnoreCase(s)) {
                return Math.floorMod(i * 100 + offset, 1200);
            }
        }
        throw new IllegalArgumentException("Unknown note: " + note);
    }

    /** 24-tone name, e.g. 350 → "E half-flat". */
    public static String quarterToneName(double cents) {
        int q = (int) Math.round(Math.floorMod((long) Math.round(cents), 1200) / 50.0) % 24;
        if (q % 2 == 0) {
            return NOTES[q / 2];
        }
        return NOTES[((q + 1) / 2) % 12] + " half-flat";
    }

    public static String normalizeMaqam(String maqam) {
        if (maqam == null) {
            return null;
        }
        for (String m : MAQAMAT) {
            if (m.equalsIgnoreCase(maqam.trim())) {
                return m;
            }
        }
        throw new IllegalArgumentException("Unknown maqam: " + maqam);
    }
}
