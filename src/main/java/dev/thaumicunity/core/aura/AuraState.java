package dev.thaumicunity.core.aura;

/**
 * Aura of one chunk: current vis, current flux and the base vis level the chunk regenerates towards.
 */
public record AuraState(double vis, double flux, double base) {
    public AuraState {
        if (vis < 0 || flux < 0 || base < 0) {
            throw new IllegalArgumentException("Aura values cannot be negative: " + vis + "/" + flux + "/" + base);
        }
    }

    public AuraState withVis(double value) {
        return new AuraState(Math.max(0, value), flux, base);
    }

    public AuraState withFlux(double value) {
        return new AuraState(vis, Math.max(0, value), base);
    }

    /** Flux relative to the base level; above 1.0 the chunk is badly polluted. */
    public double fluxPressure() {
        return base <= 0 ? (flux > 0 ? Double.POSITIVE_INFINITY : 0) : flux / base;
    }
}
