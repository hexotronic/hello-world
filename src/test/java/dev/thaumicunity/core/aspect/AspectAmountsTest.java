package dev.thaumicunity.core.aspect;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AspectAmountsTest {
    @Test
    void ignoresNonPositiveAmounts() {
        AspectAmounts a = AspectAmounts.of(Map.of("aer", 3, "ignis", 0, "ordo", -2));
        assertEquals(Map.of("aer", 3), a.asMap());
        assertEquals(3, a.total());
    }

    @Test
    void arithmetic() {
        AspectAmounts a = AspectAmounts.of(Map.of("aer", 3, "ignis", 2));
        AspectAmounts b = AspectAmounts.of(Map.of("aer", 1, "terra", 4));
        assertEquals(Map.of("aer", 4, "ignis", 2, "terra", 4), a.add(b).asMap());
        assertEquals(Map.of("aer", 2, "ignis", 2), a.subtract(b).asMap());
        assertTrue(a.containsAll(AspectAmounts.of("aer", 3)));
        assertFalse(a.containsAll(AspectAmounts.of("aer", 4)));
        assertEquals(Map.of("aer", 2, "ignis", 1), a.scale(0.75).asMap());
        assertEquals(AspectAmounts.EMPTY, a.scale(0.1));
        assertEquals(Map.of("aer", 2, "ignis", 2), a.cap(2).asMap());
    }

    @Test
    void expandsToPrimals() {
        AspectGraph graph = AspectGraph.build(AspectGraphTest.SMALL);
        AspectAmounts a = AspectAmounts.of(Map.of("tenebrae", 2, "ordo", 1));
        assertEquals(Map.of("aer", 4, "ignis", 2, "perditio", 2, "ordo", 1), a.toPrimals(graph).asMap());
    }
}
