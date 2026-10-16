package dev.thaumicunity.core.aspect;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AspectGraphTest {
    public static final List<AspectDefinition> SMALL = List.of(
            AspectDefinition.primal("aer", 1),
            AspectDefinition.primal("ignis", 2),
            AspectDefinition.primal("perditio", 3),
            AspectDefinition.primal("ordo", 4),
            AspectDefinition.compound("lux", 5, "aer", "ignis"),
            AspectDefinition.compound("vacuos", 6, "aer", "perditio"),
            AspectDefinition.compound("tenebrae", 7, "vacuos", "lux"),
            AspectDefinition.compound("motus", 8, "aer", "ordo"),
            AspectDefinition.compound("volatus", 9, "aer", "motus"));

    @Test
    void tiersAndBreakdown() {
        AspectGraph graph = AspectGraph.build(SMALL);
        assertTrue(graph.problems().isEmpty(), graph.problems().toString());
        assertEquals(0, graph.tier("aer"));
        assertEquals(1, graph.tier("lux"));
        assertEquals(2, graph.tier("tenebrae"));
        assertEquals(-1, graph.tier("missing"));
        assertEquals(Map.of("aer", 2, "ignis", 1, "perditio", 1), graph.primalBreakdown("tenebrae"));
        assertEquals(Map.of("aer", 2, "ordo", 1), graph.primalBreakdown("volatus"));
    }

    @Test
    void combineIsOrderIndependent() {
        AspectGraph graph = AspectGraph.build(SMALL);
        assertEquals("lux", graph.combine("aer", "ignis").orElseThrow());
        assertEquals("lux", graph.combine("ignis", "aer").orElseThrow());
        assertTrue(graph.combine("ignis", "ordo").isEmpty());
    }

    @Test
    void relatedMeansDirectComponent() {
        AspectGraph graph = AspectGraph.build(SMALL);
        assertTrue(graph.related("lux", "aer"));
        assertTrue(graph.related("aer", "lux"));
        assertFalse(graph.related("aer", "ignis"));
        assertFalse(graph.related("tenebrae", "aer"));
    }

    @Test
    void shortestChainWalksThroughRelations() {
        AspectGraph graph = AspectGraph.build(SMALL);
        List<String> chain = graph.shortestChain("ignis", "perditio");
        assertEquals(List.of("ignis", "lux", "aer", "vacuos", "perditio"), chain);
        for (int i = 1; i < chain.size(); i++) {
            assertTrue(graph.related(chain.get(i - 1), chain.get(i)));
        }
        assertEquals(List.of("aer"), graph.shortestChain("aer", "aer"));
    }

    @Test
    void invalidEntriesAreDroppedAndReported() {
        AspectGraph graph = AspectGraph.build(List.of(
                AspectDefinition.primal("aer", 1),
                AspectDefinition.primal("ignis", 1),
                AspectDefinition.compound("lux", 1, "aer", "ignis"),
                AspectDefinition.compound("zz_duplicate_pair", 1, "ignis", "aer"),
                AspectDefinition.compound("unknown", 1, "aer", "nothing"),
                AspectDefinition.compound("cycle_a", 1, "aer", "cycle_b"),
                AspectDefinition.compound("cycle_b", 1, "aer", "cycle_a"),
                new AspectDefinition("three", 1, List.of("aer", "ignis", "lux")),
                AspectDefinition.compound("child_of_rejected", 1, "zz_duplicate_pair", "aer")));
        assertTrue(graph.contains("lux"));
        assertFalse(graph.contains("zz_duplicate_pair"));
        assertFalse(graph.contains("unknown"));
        assertFalse(graph.contains("cycle_a"));
        assertFalse(graph.contains("three"));
        assertFalse(graph.contains("child_of_rejected"));
        assertEquals(6, graph.problems().size(), graph.problems().toString());
    }

    @Test
    void neighboursAreSortedAndComplete() {
        AspectGraph graph = AspectGraph.build(SMALL);
        assertEquals(List.of("lux", "motus", "vacuos", "volatus"), graph.neighbours("aer"));
        assertEquals(List.of("aer", "motus"), graph.neighbours("volatus"));
    }
}
