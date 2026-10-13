package dev.thaumicunity.core.aura;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuraSimulatorTest {
    final AuraSettings settings = AuraSettings.DEFAULT;

    @Test
    void regeneratesTowardsBase() {
        AuraState state = new AuraState(0, 0, 100);
        for (int i = 0; i < 1000; i++) {
            state = AuraSimulator.step(state, settings);
        }
        assertEquals(100, state.vis(), 0.01);
    }

    @Test
    void excessDecays() {
        AuraState state = new AuraState(300, 0, 100);
        AuraState next = AuraSimulator.step(state, settings);
        assertTrue(next.vis() < 300 && next.vis() > 100);
    }

    @Test
    void fluxSlowsRegenerationAndDissipatesOnlyBelowRiftPressure() {
        AuraState clean = AuraSimulator.step(new AuraState(0, 0, 100), settings);
        AuraState dirty = AuraSimulator.step(new AuraState(0, 50, 100), settings);
        assertTrue(dirty.vis() < clean.vis());
        assertTrue(dirty.flux() < 50);
        AuraState rift = AuraSimulator.step(new AuraState(0, 90, 100), settings);
        assertEquals(90, rift.flux(), 1e-9);
    }

    @Test
    void catchUpMatchesRepeatedSteps() {
        AuraState stepped = new AuraState(10, 20, 100);
        for (int i = 0; i < 200; i++) {
            stepped = AuraSimulator.step(stepped, new AuraSettings(0.02, 0.01, 0.08, 0.004, 0.0, 0.75, 4.0));
        }
        AuraState jumped = AuraSimulator.catchUp(new AuraState(10, 20, 100), 200,
                new AuraSettings(0.02, 0.01, 0.08, 0.004, 0.0, 0.75, 4.0));
        assertEquals(stepped.vis(), jumped.vis(), 1e-6);
        assertEquals(stepped.flux(), jumped.flux(), 1e-6);
    }

    @Test
    void diffusionOnlyFlowsDownhillAndNeverEmptiesTheSource() {
        double[] flows = AuraSimulator.diffusion(100, new double[]{50, 150, Double.NaN, 0}, settings);
        assertEquals(1.0, flows[0], 1e-9);
        assertEquals(0, flows[1]);
        assertEquals(0, flows[2]);
        assertEquals(2.0, flows[3], 1e-9);
        double[] extreme = AuraSimulator.diffusion(10, new double[]{0, 0, 0, 0}, new AuraSettings(0.02, 0.01, 1.0, 0.004, 0.75, 0.75, 4.0));
        double sum = 0;
        for (double f : extreme) {
            sum += f;
        }
        assertTrue(sum <= 5.0 + 1e-9);
    }

    @Test
    void drainNeverGoesNegative() {
        AuraState state = AuraSimulator.drain(new AuraState(5, 0, 10), 8);
        assertEquals(0, state.vis());
        assertThrows(IllegalArgumentException.class, () -> new AuraState(-1, 0, 0));
    }

    @Test
    void attunementCostFactors() {
        PrimalAttunement desert = new PrimalAttunement(new double[]{1, 1.5, 3, 0.1, 0.5, 1});
        int ignis = PrimalAttunement.indexOf("ignis");
        int aqua = PrimalAttunement.indexOf("aqua");
        assertTrue(desert.costFactor(ignis) < 1.0);
        assertEquals(3.0, desert.costFactor(aqua));
        assertEquals(1.0, PrimalAttunement.NEUTRAL.costFactor(0));
        double sum = 0;
        for (double w : desert.weights()) {
            sum += w;
        }
        assertEquals(6.0, sum, 1e-9);
    }
}
