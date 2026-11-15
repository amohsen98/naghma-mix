package com.naghma.mix.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Orders a selection of tracks into a set (FR-5.1, FR-5.2).
 *
 * Objective, for order x_0..x_{n-1} and target energy curve e*(i):
 *
 *   J = Σ_i S(x_i, x_{i+1}; e*(i+1) - e*(i))  +  λ · Σ_i (1 - |energy(x_i) - e*(i)|)
 *
 * This is a variant of the travelling-salesman problem, so the builder uses a greedy
 * construction followed by local search (pairwise swaps of unlocked positions, evaluated
 * incrementally). The position-independent part of every pair score is computed once, so
 * a 200-track set needs 40k scorer calls and the search itself is cheap arithmetic.
 */
public final class SetBuilder {

    static final double FIT_WEIGHT = 0.5;
    public static final int MAX_TRACKS = 200;
    private static final int MAX_ROUNDS = 30;

    public record Result(List<TrackFeatures> order, List<TransitionScorer.Transition> transitions,
                         double[] targetEnergy, double objective) {}

    private final Weights weights;
    private final EnergyCurve curve;

    public SetBuilder(Weights weights, EnergyCurve curve) {
        this.weights = weights;
        this.curve = curve;
    }

    /** @param locked track id → fixed 0-based position (e.g. the zaffa at 0) */
    public Result build(List<TrackFeatures> tracks, Map<UUID, Integer> locked) {
        int n = tracks.size();
        if (n > MAX_TRACKS) {
            throw new IllegalArgumentException("A set can hold at most " + MAX_TRACKS + " tracks");
        }
        if (n == 0) {
            return new Result(List.of(), List.of(), new double[0], 0);
        }
        TrackFeatures[] all = tracks.toArray(TrackFeatures[]::new);
        Map<UUID, Integer> index = new HashMap<>();
        for (int i = 0; i < n; i++) {
            if (index.put(all[i].id(), i) != null) {
                throw new IllegalArgumentException("Duplicate track " + all[i].id());
            }
        }
        double[] target = new double[n];
        for (int i = 0; i < n; i++) {
            target[i] = curve.at(i, n);
        }
        Pairs pairs = new Pairs(all, weights);

        int[] slot = new int[n];
        java.util.Arrays.fill(slot, -1);
        boolean[] fixed = new boolean[n];
        boolean[] used = new boolean[n];
        for (Map.Entry<UUID, Integer> e : locked.entrySet()) {
            Integer t = index.get(e.getKey());
            int pos = e.getValue();
            if (t == null) {
                throw new IllegalArgumentException("Locked track " + e.getKey() + " is not in the selection");
            }
            if (pos < 0 || pos >= n || fixed[pos]) {
                throw new IllegalArgumentException("Invalid or duplicate locked position " + pos);
            }
            slot[pos] = t;
            fixed[pos] = true;
            used[t] = true;
        }

        // Greedy construction, left to right.
        for (int i = 0; i < n; i++) {
            if (fixed[i]) {
                continue;
            }
            int best = -1;
            double bestScore = Double.NEGATIVE_INFINITY;
            for (int c = 0; c < n; c++) {
                if (used[c]) {
                    continue;
                }
                double s = FIT_WEIGHT * fit(all[c], target[i]);
                if (i > 0) {
                    s += pairs.score(slot[i - 1], c, target[i] - target[i - 1]);
                }
                if (i + 1 < n && fixed[i + 1]) {
                    s += pairs.score(c, slot[i + 1], target[i + 1] - target[i]);
                }
                if (s > bestScore) {
                    bestScore = s;
                    best = c;
                }
            }
            slot[i] = best;
            used[best] = true;
        }

        // Local search with incremental evaluation of each swap.
        Evaluator ev = new Evaluator(all, slot, target, pairs);
        boolean improved = true;
        for (int round = 0; improved && round < MAX_ROUNDS; round++) {
            improved = false;
            for (int i = 0; i < n; i++) {
                if (fixed[i]) {
                    continue;
                }
                for (int j = i + 1; j < n; j++) {
                    if (fixed[j]) {
                        continue;
                    }
                    if (ev.swapGain(i, j) > 1e-9) {
                        ev.swap(i, j);
                        improved = true;
                    }
                }
            }
        }

        List<TrackFeatures> order = new ArrayList<>(n);
        for (int s : slot) {
            order.add(all[s]);
        }
        List<TransitionScorer.Transition> transitions = new ArrayList<>();
        for (int i = 0; i + 1 < n; i++) {
            transitions.add(TransitionScorer.score(order.get(i), order.get(i + 1), target[i + 1] - target[i], weights));
        }
        return new Result(List.copyOf(order), transitions, target, TransitionScorer.round(ev.total()));
    }

    static double fit(TrackFeatures t, double target) {
        return 1.0 - Math.abs(t.energyOr(0.5) - target);
    }

    /** Pair scores split into the position-independent part and the energy part. */
    static final class Pairs {
        private final double[][] base;
        private final double[][] energyDelta;
        private final double energyWeight;

        Pairs(TrackFeatures[] all, Weights w) {
            int n = all.length;
            base = new double[n][n];
            energyDelta = new double[n][n];
            energyWeight = w.energy() / w.sum();
            for (int a = 0; a < n; a++) {
                for (int b = 0; b < n; b++) {
                    if (a == b) {
                        continue;
                    }
                    TransitionScorer.Transition t = TransitionScorer.score(all[a], all[b], 0.0, w);
                    Map<String, Double> c = t.components();
                    base[a][b] = (w.harmony() * c.get("harmony") + w.tonic() * c.get("tonic")
                            + w.tempo() * c.get("tempo")) / w.sum();
                    energyDelta[a][b] = all[b].energyIn() - all[a].energyOut();
                }
            }
        }

        double score(int a, int b, double desiredDelta) {
            double e = TransitionScorer.clamp(1.0 - Math.abs(energyDelta[a][b] - desiredDelta) * 1.5);
            return base[a][b] + energyWeight * e;
        }
    }

    /** Keeps the slot array and evaluates swaps by touching only the affected terms. */
    static final class Evaluator {
        private final TrackFeatures[] all;
        private final int[] slot;
        private final double[] target;
        private final Pairs pairs;

        Evaluator(TrackFeatures[] all, int[] slot, double[] target, Pairs pairs) {
            this.all = all;
            this.slot = slot;
            this.target = target;
            this.pairs = pairs;
        }

        double total() {
            double j = 0;
            for (int i = 0; i < slot.length; i++) {
                j += FIT_WEIGHT * fit(all[slot[i]], target[i]) + edge(i);
            }
            return j;
        }

        /** Score of the transition from position i to i+1 (0 past the end). */
        private double edge(int i) {
            if (i < 0 || i + 1 >= slot.length) {
                return 0;
            }
            return pairs.score(slot[i], slot[i + 1], target[i + 1] - target[i]);
        }

        private double local(int i, int j) {
            double v = FIT_WEIGHT * (fit(all[slot[i]], target[i]) + fit(all[slot[j]], target[j]));
            // edges touching i or j, each counted once
            java.util.TreeSet<Integer> edges = new java.util.TreeSet<>(List.of(i - 1, i, j - 1, j));
            for (int e : edges) {
                v += edge(e);
            }
            return v;
        }

        double swapGain(int i, int j) {
            double before = local(i, j);
            swap(i, j);
            double after = local(i, j);
            swap(i, j);
            return after - before;
        }

        void swap(int i, int j) {
            int t = slot[i];
            slot[i] = slot[j];
            slot[j] = t;
        }
    }
}
