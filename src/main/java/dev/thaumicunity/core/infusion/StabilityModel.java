package dev.thaumicunity.core.infusion;

import java.util.List;

/**
 * Infusion stability (docs/DESIGN_DECISIONS.md, D9). Stability is a number where positive is safe; every point below
 * zero raises the chance of an instability event during each infusion step.
 */
public final class StabilityModel {
    /** Relative position of a pedestal (or stabiliser) to the runic matrix, on the horizontal plane. */
    public record Offset(int dx, int dz) {
    }

    /**
     * Inputs of one stability evaluation.
     *
     * @param pedestals          offsets of pedestals that hold an item
     * @param emptyPedestals     offsets of empty pedestals (count towards symmetry)
     * @param stabilizerTotal    sum of stabiliser values around the altar (candles, skulls, crystals…)
     * @param pillarBonus        bonus from the matrix pillars (arcane/ancient/eldritch)
     * @param matrixUpgrades     bonus from matrix stability upgrades
     * @param recipeInstability  the recipe's own instability (0–10 in shipped recipes)
     * @param fluxPressure       flux pressure of the chunk ({@code flux / base})
     */
    public record Input(List<Offset> pedestals, List<Offset> emptyPedestals, double stabilizerTotal, double pillarBonus,
                        double matrixUpgrades, double recipeInstability, double fluxPressure) {
        public Input {
            pedestals = List.copyOf(pedestals);
            emptyPedestals = List.copyOf(emptyPedestals);
        }
    }

    /** Result: the stability number and the per-step chance (0–1) that an instability event fires. */
    public record Result(double stability, double eventChance, double asymmetry) {
    }

    /** Stability lost per unit of asymmetry. */
    public static final double ASYMMETRY_WEIGHT = 0.5;
    /** Stability lost per pedestal holding an item (more items = harder infusion). */
    public static final double ITEM_WEIGHT = 0.25;
    /** Stability lost per point of recipe instability. */
    public static final double RECIPE_WEIGHT = 1.0;
    /** Stability lost per unit of flux pressure. */
    public static final double FLUX_WEIGHT = 2.0;
    /** Stabilisers saturate: the first points count fully, later ones less ({@code 8 × (1 − e^{−total/8})}). */
    public static final double STABILIZER_CAP = 8.0;

    private StabilityModel() {
    }

    public static Result evaluate(Input input) {
        double asymmetry = asymmetry(input.pedestals()) + 0.5 * asymmetry(input.emptyPedestals());
        double stabilizers = STABILIZER_CAP * (1.0 - Math.exp(-Math.max(0, input.stabilizerTotal()) / STABILIZER_CAP));
        double stability = stabilizers + input.pillarBonus() + input.matrixUpgrades()
                - asymmetry * ASYMMETRY_WEIGHT
                - input.pedestals().size() * ITEM_WEIGHT
                - input.recipeInstability() * RECIPE_WEIGHT
                - input.fluxPressure() * FLUX_WEIGHT;
        double chance = stability >= 0 ? 0.0 : Math.min(0.95, 1.0 - Math.exp(stability / 6.0));
        return new Result(stability, chance, asymmetry);
    }

    /**
     * Asymmetry of a set of offsets: for every offset, check whether its point mirror through the matrix is also
     * present; each unmatched offset adds 1. A perfectly symmetric layout has asymmetry 0.
     */
    public static double asymmetry(List<Offset> offsets) {
        int unmatched = 0;
        for (Offset o : offsets) {
            boolean mirrored = false;
            for (Offset other : offsets) {
                if (other.dx() == -o.dx() && other.dz() == -o.dz()) {
                    mirrored = true;
                    break;
                }
            }
            if (!mirrored) {
                unmatched++;
            }
        }
        return unmatched;
    }
}
