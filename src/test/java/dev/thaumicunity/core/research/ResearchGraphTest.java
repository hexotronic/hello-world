package dev.thaumicunity.core.research;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResearchGraphTest {
    static ResearchGraph.Node node(String id, String... parents) {
        return new ResearchGraph.Node(id, List.of(parents), false);
    }

    @Test
    void orderAndAvailability() {
        ResearchGraph graph = ResearchGraph.build(List.of(
                node("basics"), node("crucible", "basics"), node("nitor", "crucible"), node("workbench", "basics"),
                node("infusion", "crucible", "workbench")));
        assertTrue(graph.problems().isEmpty());
        List<String> order = graph.topologicalOrder();
        assertTrue(order.indexOf("basics") < order.indexOf("crucible"));
        assertTrue(order.indexOf("workbench") < order.indexOf("infusion"));
        assertEquals(List.of("basics"), graph.available(Set.of()));
        assertEquals(List.of("crucible", "workbench"), graph.available(Set.of("basics")));
        assertEquals(List.of("nitor"), graph.available(Set.of("basics", "crucible")).stream().filter(s -> !s.equals("workbench")).toList());
        assertEquals(Set.of("basics", "crucible", "workbench"), graph.ancestors("infusion"));
    }

    @Test
    void dropsUnknownParentsAndCycles() {
        ResearchGraph graph = ResearchGraph.build(List.of(
                node("a"), node("b", "missing"), node("c", "b"), node("x", "y"), node("y", "x")));
        assertTrue(graph.contains("a"));
        assertFalse(graph.contains("b"));
        assertFalse(graph.contains("c"));
        assertFalse(graph.contains("x"));
        assertFalse(graph.contains("y"));
        assertEquals(4, graph.problems().size(), graph.problems().toString());
    }
}
