package dev.thaumicunity.core.aura;

/**
 * Pure functions that advance chunk auras. The Minecraft side calls {@link #step} for a bounded number of loaded
 * chunks per tick and {@link #catchUp} for chunks that were unloaded for a while, so the cost of the aura is
 * independent of world size.
 */
public final class AuraSimulator {
    private AuraSimulator() {
    }

    /** Regeneration/decay of one chunk for one step, ignoring neighbours. */
    public static AuraState step(AuraState state, AuraSettings settings) {
        double vis = state.vis();
        double base = state.base();
        if (vis < base) {
            double penalty = Math.min(1.0, settings.fluxRegenPenalty() * state.fluxPressure());
            vis += (base - vis) * settings.regenRate() * (1.0 - penalty);
        } else if (vis > base) {
            vis -= (vis - base) * settings.overflowDecay();
        }
        vis = Math.min(vis, base * settings.maxVisFactor());
        double flux = state.flux();
        if (state.fluxPressure() < settings.riftPressure()) {
            flux -= flux * settings.fluxDecay();
        }
        return new AuraState(Math.max(0, vis), Math.max(0, flux), base);
    }

    /**
     * Vis that flows from a chunk into each of its four horizontal neighbours this step (only towards lower
     * neighbours; a neighbour that is not loaded should be passed as {@code Double.NaN} and receives nothing).
     */
    public static double[] diffusion(double vis, double[] neighbourVis, AuraSettings settings) {
        double[] flows = new double[neighbourVis.length];
        double share = settings.diffusionRate() / 4.0;
        double total = 0;
        for (int i = 0; i < neighbourVis.length; i++) {
            double n = neighbourVis[i];
            if (!Double.isNaN(n) && vis > n) {
                flows[i] = (vis - n) * share;
                total += flows[i];
            }
        }
        // Never send away more than half of what we have in one step.
        if (total > vis * 0.5 && total > 0) {
            double scale = vis * 0.5 / total;
            for (int i = 0; i < flows.length; i++) {
                flows[i] *= scale;
            }
        }
        return flows;
    }

    /**
     * Closed-form result of {@code steps} regeneration steps, used when a chunk is loaded after being away.
     * Flux pressure is assumed constant during the catch-up for regeneration and dissipates geometrically.
     */
    public static AuraState catchUp(AuraState state, long steps, AuraSettings settings) {
        if (steps <= 0) {
            return state;
        }
        double base = state.base();
        double vis = state.vis();
        double n = Math.min(steps, 1_000_000L);
        if (vis < base) {
            double penalty = Math.min(1.0, settings.fluxRegenPenalty() * state.fluxPressure());
            double k = settings.regenRate() * (1.0 - penalty);
            vis = base - (base - vis) * Math.pow(1.0 - k, n);
        } else if (vis > base) {
            vis = base + (vis - base) * Math.pow(1.0 - settings.overflowDecay(), n);
        }
        double flux = state.flux();
        if (state.fluxPressure() < settings.riftPressure()) {
            flux = flux * Math.pow(1.0 - settings.fluxDecay(), n);
        }
        return new AuraState(Math.max(0, vis), Math.max(0, flux), base);
    }

    /**
     * Drains up to {@code amount} vis. Returns the new state; the amount actually drained is
     * {@code state.vis() - result.vis()}.
     */
    public static AuraState drain(AuraState state, double amount) {
        return state.withVis(state.vis() - Math.min(Math.max(0, amount), state.vis()));
    }
}
