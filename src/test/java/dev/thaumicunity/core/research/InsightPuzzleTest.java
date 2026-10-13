package dev.thaumicunity.core.research;

import dev.thaumicunity.core.aspect.AspectGraph;
import dev.thaumicunity.core.aspect.AspectGraphTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InsightPuzzleTest {
    final AspectGraph graph = AspectGraph.build(AspectGraphTest.SMALL);

    @Test
    void boardSizes() {
        assertEquals(7, HexCoord.board(1).size());
        assertEquals(19, HexCoord.board(2).size());
        assertEquals(37, HexCoord.board(3).size());
        for (HexCoord corner : HexCoord.corners(3)) {
            assertEquals(3, HexCoord.ORIGIN.distance(corner));
        }
    }

    @Test
    void solvedWhenAnchorsConnectThroughRelatedAspects() {
        // ignis at (-2,0) and perditio at (2,0): chain ignis-lux-aer-vacuos-perditio along the q axis.
        InsightPuzzle puzzle = new InsightPuzzle(2,
                Map.of(new HexCoord(-2, 0), "ignis", new HexCoord(2, 0), "perditio"), Map.of(), Set.of());
        assertTrue(puzzle.isSolvable(graph));
        assertFalse(puzzle.isSolved(graph));
        puzzle = puzzle.place(new HexCoord(-1, 0), "lux").place(new HexCoord(0, 0), "aer");
        assertFalse(puzzle.isSolved(graph));
        puzzle = puzzle.place(new HexCoord(1, 0), "vacuos");
        assertTrue(puzzle.isSolved(graph));
        assertEquals(3, puzzle.placedCount());
        assertFalse(puzzle.clear(new HexCoord(0, 0)).isSolved(graph));
    }

    @Test
    void unrelatedNeighboursDoNotConnect() {
        InsightPuzzle puzzle = new InsightPuzzle(1,
                Map.of(new HexCoord(-1, 0), "ignis", new HexCoord(1, 0), "ordo"), Map.of(), Set.of())
                .place(HexCoord.ORIGIN, "aer");
        assertFalse(puzzle.isSolved(graph));
    }

    @Test
    void cannotPlaceOnAnchorsBlockedOrOutside() {
        InsightPuzzle puzzle = new InsightPuzzle(1, Map.of(HexCoord.ORIGIN, "aer"), Map.of(), Set.of(new HexCoord(1, 0)));
        assertThrows(IllegalArgumentException.class, () -> puzzle.place(HexCoord.ORIGIN, "lux"));
        assertThrows(IllegalArgumentException.class, () -> puzzle.place(new HexCoord(1, 0), "lux"));
        assertThrows(IllegalArgumentException.class, () -> puzzle.place(new HexCoord(5, 0), "lux"));
    }

    @Test
    void generationIsDeterministicAndKeepsAnchorExitsOpen() {
        InsightPuzzle a = InsightPuzzle.generate(List.of("ignis", "perditio", "ordo"), 3, 4, 42L);
        InsightPuzzle b = InsightPuzzle.generate(List.of("ignis", "perditio", "ordo"), 3, 4, 42L);
        assertEquals(a.anchors(), b.anchors());
        assertEquals(a.blocked(), b.blocked());
        assertEquals(3, a.anchors().size());
        assertEquals(4, a.blocked().size());
        for (HexCoord anchor : a.anchors().keySet()) {
            for (HexCoord blocked : a.blocked()) {
                assertTrue(anchor.distance(blocked) > 1);
            }
        }
        assertTrue(a.isSolvable(graph));
    }
}
