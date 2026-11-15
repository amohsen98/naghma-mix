package com.naghma.mix.domain;

/** Target energy shapes for a set; each is a piecewise-linear curve over set position 0..1. */
public enum EnergyCurve {
    WARM_UP(new double[][] {{0, 0.30}, {1, 0.65}}),
    PEAK_TIME(new double[][] {{0, 0.60}, {0.3, 0.80}, {1, 0.90}}),
    /** Wedding night: lively entrance, a slow-dance dip, a big finish. */
    WEDDING(new double[][] {{0, 0.65}, {0.35, 0.80}, {0.5, 0.40}, {0.6, 0.55}, {1, 0.95}}),
    WAVE(new double[][] {{0, 0.50}, {0.25, 0.80}, {0.5, 0.50}, {0.75, 0.85}, {1, 0.60}}),
    FLAT(new double[][] {{0, 0.60}, {1, 0.60}});

    private final double[][] points;

    EnergyCurve(double[][] points) {
        this.points = points;
    }

    /** Target energy at position i of a set of n tracks. */
    public double at(int i, int n) {
        double x = n <= 1 ? 0 : (double) i / (n - 1);
        for (int k = 1; k < points.length; k++) {
            if (x <= points[k][0]) {
                double[] p = points[k - 1];
                double[] q = points[k];
                double f = (x - p[0]) / (q[0] - p[0]);
                return p[1] + f * (q[1] - p[1]);
            }
        }
        return points[points.length - 1][1];
    }
}
