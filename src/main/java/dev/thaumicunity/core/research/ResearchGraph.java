package dev.thaumicunity.core.research;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Prerequisite graph of research entries. Validates that parents exist and that there are no cycles, and answers
 * which entries are currently available to a researcher.
 */
public final class ResearchGraph {
    /** Minimal view of a research entry: its id and the ids of the entries that must be completed first. */
    public record Node(String id, List<String> parents, boolean hidden) {
        public Node {
            parents = List.copyOf(parents);
        }
    }

    private final Map<String, Node> nodes;
    private final List<String> order;
    private final List<String> problems;

    private ResearchGraph(Map<String, Node> nodes, List<String> order, List<String> problems) {
        this.nodes = nodes;
        this.order = order;
        this.problems = problems;
    }

    /** Builds the graph. Entries with unknown parents or inside a cycle are dropped and reported. */
    public static ResearchGraph build(Collection<Node> input) {
        List<String> problems = new ArrayList<>();
        Map<String, Node> nodes = new TreeMap<>();
        for (Node node : input) {
            if (nodes.putIfAbsent(node.id(), node) != null) {
                problems.add("Duplicate research id " + node.id());
            }
        }
        boolean removed = true;
        while (removed) {
            removed = false;
            for (Node node : List.copyOf(nodes.values())) {
                for (String parent : node.parents()) {
                    if (!nodes.containsKey(parent)) {
                        problems.add("Research " + node.id() + " has unknown parent " + parent);
                        nodes.remove(node.id());
                        removed = true;
                        break;
                    }
                }
            }
        }

        // Kahn's algorithm; whatever is left over is part of a cycle.
        Map<String, Integer> indegree = new HashMap<>();
        Map<String, List<String>> children = new HashMap<>();
        for (Node node : nodes.values()) {
            indegree.putIfAbsent(node.id(), 0);
            for (String parent : node.parents()) {
                indegree.merge(node.id(), 1, Integer::sum);
                children.computeIfAbsent(parent, k -> new ArrayList<>()).add(node.id());
            }
        }
        List<String> order = new ArrayList<>();
        java.util.TreeSet<String> ready = new java.util.TreeSet<>();
        indegree.forEach((id, deg) -> {
            if (deg == 0) {
                ready.add(id);
            }
        });
        while (!ready.isEmpty()) {
            String id = ready.pollFirst();
            order.add(id);
            for (String child : children.getOrDefault(id, List.of())) {
                if (indegree.merge(child, -1, Integer::sum) == 0) {
                    ready.add(child);
                }
            }
        }
        if (order.size() != nodes.size()) {
            Set<String> cyclic = new HashSet<>(nodes.keySet());
            order.forEach(cyclic::remove);
            for (String id : new java.util.TreeSet<>(cyclic)) {
                problems.add("Research " + id + " is part of a prerequisite cycle");
                nodes.remove(id);
            }
        }
        return new ResearchGraph(Map.copyOf(nodes), List.copyOf(order), List.copyOf(problems));
    }

    public List<String> problems() {
        return problems;
    }

    public boolean contains(String id) {
        return nodes.containsKey(id);
    }

    /** Entries in an order where every parent comes before its children. */
    public List<String> topologicalOrder() {
        return order;
    }

    /** Entries that are not complete yet but whose parents all are. */
    public List<String> available(Set<String> completed) {
        List<String> result = new ArrayList<>();
        for (String id : order) {
            Node node = nodes.get(id);
            if (!completed.contains(id) && completed.containsAll(node.parents())) {
                result.add(id);
            }
        }
        return result;
    }

    /** All entries that must be completed (transitively) before the given one. */
    public Set<String> ancestors(String id) {
        Set<String> result = new HashSet<>();
        List<String> stack = new ArrayList<>();
        Node start = nodes.get(id);
        if (start != null) {
            stack.addAll(start.parents());
        }
        while (!stack.isEmpty()) {
            String next = stack.removeLast();
            if (result.add(next)) {
                Node node = nodes.get(next);
                if (node != null) {
                    stack.addAll(node.parents());
                }
            }
        }
        return result;
    }
}
