package dev.thaumicunity.core.infusion;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StabilityModelTest {
    static StabilityModel.Offset o(int x, int z) {
        return new StabilityModel.Offset(x, z);
    }

    @Test
    void symmetricLayoutHasNoAsymmetry() {
        assertEquals(0, StabilityModel.asymmetry(List.of(o(2, 0), o(-2, 0), o(0, 2), o(0, -2))));
        assertEquals(2, StabilityModel.asymmetry(List.of(o(2, 0), o(-2, 0), o(0, 2), o(1, 1))));
    }

    @Test
    void stabilisersHelpAndSaturate() {
        var base = new StabilityModel.Input(List.of(o(2, 0), o(-2, 0)), List.of(), 0, 0, 0, 3, 0);
        var helped = new StabilityModel.Input(List.of(o(2, 0), o(-2, 0)), List.of(), 6, 0, 0, 3, 0);
        var lots = new StabilityModel.Input(List.of(o(2, 0), o(-2, 0)), List.of(), 1000, 0, 0, 3, 0);
        double s0 = StabilityModel.evaluate(base).stability();
        double s1 = StabilityModel.evaluate(helped).stability();
        double s2 = StabilityModel.evaluate(lots).stability();
        assertTrue(s1 > s0);
        assertTrue(s2 - s0 <= StabilityModel.STABILIZER_CAP + 1e-9);
        assertTrue(StabilityModel.evaluate(base).eventChance() > 0);
        assertEquals(0, StabilityModel.evaluate(lots).eventChance());
    }

    @Test
    void fluxMakesThingsWorse() {
        var clean = new StabilityModel.Input(List.of(o(2, 0), o(-2, 0)), List.of(), 4, 1, 0, 2, 0);
        var dirty = new StabilityModel.Input(List.of(o(2, 0), o(-2, 0)), List.of(), 4, 1, 0, 2, 1.5);
        assertTrue(StabilityModel.evaluate(dirty).stability() < StabilityModel.evaluate(clean).stability());
    }
}
