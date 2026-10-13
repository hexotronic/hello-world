package dev.thaumicunity.core.aura;

import java.util.Arrays;
import java.util.List;

/**
 * How strongly a place resonates with each primal aspect (docs/DESIGN_DECISIONS.md, D2). Weights are normalised to
 * sum to 6, so a perfectly neutral place has weight 1.0 for every primal. Drawing vis for a primal costs
 * {@code amount × costFactor(primal)}.
 */
public final class PrimalAttunement {
    /** Order used everywhere weights are given as arrays. */
    public static final List<String> PRIMAL_ORDER = List.of("aer", "terra", "ignis", "aqua", "ordo", "perditio");
    public static final PrimalAttunement NEUTRAL = new PrimalAttunement(new double[]{1, 1, 1, 1, 1, 1});

    private static final double MIN_FACTOR = 0.5;
    private static final double MAX_FACTOR = 3.0;

    private final double[] weights;

    public PrimalAttunement(double[] raw) {
        if (raw.length != 6) {
            throw new IllegalArgumentException("Attunement needs 6 weights");
        }
        double sum = 0;
        for (double w : raw) {
            if (w < 0 || Double.isNaN(w)) {
                throw new IllegalArgumentException("Attunement weights cannot be negative");
            }
            sum += w;
        }
        weights = new double[6];
        for (int i = 0; i < 6; i++) {
            weights[i] = sum <= 0 ? 1.0 : raw[i] * 6.0 / sum;
        }
    }

    public double weight(int primalIndex) {
        return weights[primalIndex];
    }

    /** Index in {@link #PRIMAL_ORDER} of a primal's path ({@code "ignis"}), or -1. */
    public static int indexOf(String primalPath) {
        return PRIMAL_ORDER.indexOf(primalPath);
    }

    /** Vis multiplier for drawing this primal here: cheap where it is strong, expensive where it is weak. */
    public double costFactor(int primalIndex) {
        double w = weights[primalIndex];
        if (w <= 0) {
            return MAX_FACTOR;
        }
        return Math.max(MIN_FACTOR, Math.min(MAX_FACTOR, 1.0 / w));
    }

    public double[] weights() {
        return weights.clone();
    }

    /** Average of two attunements (used where chunks with different biomes blend). */
    public PrimalAttunement blend(PrimalAttunement other, double otherWeight) {
        double[] mixed = new double[6];
        for (int i = 0; i < 6; i++) {
            mixed[i] = weights[i] * (1 - otherWeight) + other.weights[i] * otherWeight;
        }
        return new PrimalAttunement(mixed);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PrimalAttunement other && Arrays.equals(weights, other.weights);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(weights);
    }

    @Override
    public String toString() {
        return "PrimalAttunement" + Arrays.toString(weights);
    }
}
