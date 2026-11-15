package com.naghma.mix.domain;

/** How much the DJ cares about each part of a transition (FR-4.3). Normalized on use. */
public record Weights(double harmony, double tonic, double tempo, double energy) {

    public static final Weights DEFAULT = new Weights(0.45, 0.10, 0.30, 0.15);

    public Weights {
        if (harmony < 0 || tonic < 0 || tempo < 0 || energy < 0) {
            throw new IllegalArgumentException("weights must be >= 0");
        }
    }

    public static Weights of(Double harmony, Double tonic, Double tempo, Double energy) {
        return new Weights(
                harmony == null ? DEFAULT.harmony : harmony,
                tonic == null ? DEFAULT.tonic : tonic,
                tempo == null ? DEFAULT.tempo : tempo,
                energy == null ? DEFAULT.energy : energy);
    }

    public double sum() {
        double s = harmony + tonic + tempo + energy;
        return s == 0 ? 1 : s;
    }
}
