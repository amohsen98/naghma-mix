package com.naghma.mix.domain;

/** Camelot wheel notation ("8A" = A minor, "8B" = C major) and the classic mixing rules. */
public record Camelot(int number, char letter) {

    private static final int[] MAJOR = {8, 3, 10, 5, 12, 7, 2, 9, 4, 11, 6, 1}; // by tonic pc
    private static final int[] MINOR = {5, 12, 7, 2, 9, 4, 11, 6, 1, 8, 3, 10};

    public static Camelot of(int tonicPc, String mode) {
        int pc = Math.floorMod(tonicPc, 12);
        return "major".equals(mode) ? new Camelot(MAJOR[pc], 'B') : new Camelot(MINOR[pc], 'A');
    }

    public static Camelot parse(String s) {
        if (s == null || s.length() < 2) {
            return null;
        }
        char letter = Character.toUpperCase(s.charAt(s.length() - 1));
        if (letter != 'A' && letter != 'B') {
            return null;
        }
        try {
            int n = Integer.parseInt(s.substring(0, s.length() - 1));
            return n >= 1 && n <= 12 ? new Camelot(n, letter) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Steps around the wheel, 0..6. */
    public int distance(Camelot o) {
        int d = Math.abs(number - o.number);
        return Math.min(d, 12 - d);
    }

    public record Match(double score, Reason reason) {}

    public Match compatibility(Camelot to) {
        int d = distance(to);
        boolean sameLetter = letter == to.letter;
        java.util.Map<String, Object> args = java.util.Map.of("from", toString(), "to", to.toString());
        if (d == 0 && sameLetter) {
            return new Match(1.0, Reason.of("sameKey", "same key (" + this + ")", args));
        }
        if (d == 0) {
            return new Match(0.9, Reason.of(to.letter == 'A' ? "relativeMinor" : "relativeMajor",
                    "relative " + (to.letter == 'A' ? "minor" : "major") + " (" + this + " → " + to + ")", args));
        }
        if (d == 1 && sameLetter) {
            return new Match(0.9, Reason.of("adjacentKey", "adjacent key (" + this + " → " + to + ")", args));
        }
        if (d == 2 && sameLetter) {
            return new Match(0.6, Reason.of("energyBoostKey", "energy boost, two steps (" + this + " → " + to + ")", args));
        }
        if (d == 1) {
            return new Match(0.55, Reason.of("diagonalKey", "diagonal move (" + this + " → " + to + ")", args));
        }
        return new Match(Math.max(0.0, 0.4 - 0.08 * d), Reason.of("keyClash", "key clash (" + this + " → " + to + ")", args));
    }

    @Override
    public String toString() {
        return number + "" + letter;
    }
}
