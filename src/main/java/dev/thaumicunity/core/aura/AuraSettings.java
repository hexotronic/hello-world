package dev.thaumicunity.core.aura;

/**
 * Tunables of the aura simulation, all per simulation step (one step = one visit of the round-robin ticker).
 *
 * @param regenRate       fraction of the gap to base recovered per step when below base
 * @param overflowDecay   fraction of the excess above base lost per step
 * @param diffusionRate   fraction of the difference to a lower neighbour that flows to it per step (split over 4)
 * @param fluxDecay       fraction of flux that dissipates per step while flux pressure is below {@code riftPressure}
 * @param fluxRegenPenalty how strongly flux pressure slows regeneration (0 = no effect, 1 = stops at pressure 1)
 * @param riftPressure    flux pressure at which flux stops dissipating and rifts may form
 * @param maxVisFactor    vis can never exceed base × this factor
 */
public record AuraSettings(double regenRate, double overflowDecay, double diffusionRate, double fluxDecay,
                           double fluxRegenPenalty, double riftPressure, double maxVisFactor) {
    public static final AuraSettings DEFAULT = new AuraSettings(0.02, 0.01, 0.08, 0.004, 0.75, 0.75, 4.0);

    public AuraSettings {
        check(regenRate, "regenRate");
        check(overflowDecay, "overflowDecay");
        check(diffusionRate, "diffusionRate");
        check(fluxDecay, "fluxDecay");
        check(fluxRegenPenalty, "fluxRegenPenalty");
        if (riftPressure <= 0 || maxVisFactor < 1) {
            throw new IllegalArgumentException("riftPressure must be > 0 and maxVisFactor >= 1");
        }
    }

    private static void check(double value, String name) {
        if (value < 0 || value > 1) {
            throw new IllegalArgumentException(name + " must be within [0, 1], was " + value);
        }
    }
}
