package dev.thaumicunity.core.aspect;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * The aspect combination graph. Built once per data reload from the aspect registry, it answers every question the
 * rest of the mod asks about aspects: which pair makes which compound, an aspect's tier, its primal breakdown,
 * and whether two aspects are "related" (one is a direct component of the other), which is the adjacency rule of
 * the insight puzzle and of essentia-tube filters.
 *
 * <p>Invalid entries are not fatal: {@link #build(Collection)} drops every aspect that cannot be resolved down to
 * primals (unknown components, wrong component count, cycles, duplicate pairs) and reports why in
 * {@link #problems()}, so one bad datapack file never takes the whole system down.</p>
 */
public final class AspectGraph {
    private final Map<String, AspectDefinition> aspects;
    private final Map<String, Integer> tiers;
    private final Map<PairKey, String> byPair;
    private final Map<String, List<String>> usedIn;
    private final Map<String, Map<String, Integer>> primalBreakdown;
    private final List<String> problems;

    private AspectGraph(Map<String, AspectDefinition> aspects, Map<String, Integer> tiers, Map<PairKey, String> byPair,
                        Map<String, List<String>> usedIn, Map<String, Map<String, Integer>> primalBreakdown,
                        List<String> problems) {
        this.aspects = aspects;
        this.tiers = tiers;
        this.byPair = byPair;
        this.usedIn = usedIn;
        this.primalBreakdown = primalBreakdown;
        this.problems = problems;
    }

    public static final AspectGraph EMPTY = build(List.of());

    /** Builds and validates a graph. Entries that fail validation are excluded and described in {@link #problems()}. */
    public static AspectGraph build(Collection<AspectDefinition> definitions) {
        List<String> problems = new ArrayList<>();
        Map<String, AspectDefinition> candidates = new TreeMap<>();
        for (AspectDefinition def : definitions) {
            if (candidates.putIfAbsent(def.id(), def) != null) {
                problems.add("Duplicate aspect id " + def.id());
            }
        }
        for (AspectDefinition def : List.copyOf(candidates.values())) {
            if (!def.isPrimal() && def.components().size() != 2) {
                problems.add("Aspect " + def.id() + " must have 0 or 2 components but has " + def.components().size());
                candidates.remove(def.id());
            } else if (def.components().contains(def.id())) {
                problems.add("Aspect " + def.id() + " lists itself as a component");
                candidates.remove(def.id());
            }
        }

        // Resolve tiers with an iterative fix-point: an aspect is accepted once both components are accepted.
        Map<String, Integer> tiers = new HashMap<>();
        candidates.values().stream().filter(AspectDefinition::isPrimal).forEach(def -> tiers.put(def.id(), 0));
        boolean changed = true;
        while (changed) {
            changed = false;
            for (AspectDefinition def : candidates.values()) {
                if (tiers.containsKey(def.id()) || def.isPrimal()) {
                    continue;
                }
                Integer a = tiers.get(def.components().get(0));
                Integer b = tiers.get(def.components().get(1));
                if (a != null && b != null) {
                    tiers.put(def.id(), Math.max(a, b) + 1);
                    changed = true;
                }
            }
        }
        for (AspectDefinition def : candidates.values()) {
            if (!tiers.containsKey(def.id())) {
                List<String> missing = def.components().stream().filter(c -> !candidates.containsKey(c)).toList();
                problems.add(missing.isEmpty()
                        ? "Aspect " + def.id() + " is part of a component cycle"
                        : "Aspect " + def.id() + " uses unknown components " + missing);
            }
        }

        // Pairs must be unique; on a clash the lower tier (then alphabetically first) aspect keeps the pair.
        List<AspectDefinition> accepted = candidates.values().stream()
                .filter(def -> tiers.containsKey(def.id()))
                .sorted(Comparator.comparingInt((AspectDefinition def) -> tiers.get(def.id())).thenComparing(AspectDefinition::id))
                .toList();
        Map<PairKey, String> byPair = new HashMap<>();
        Map<String, AspectDefinition> aspects = new LinkedHashMap<>();
        for (AspectDefinition def : accepted) {
            if (!def.isPrimal()) {
                if (!aspects.containsKey(def.components().get(0)) || !aspects.containsKey(def.components().get(1))) {
                    problems.add("Aspect " + def.id() + " depends on a rejected aspect");
                    continue;
                }
                PairKey key = PairKey.of(def.components().get(0), def.components().get(1));
                String previous = byPair.putIfAbsent(key, def.id());
                if (previous != null) {
                    problems.add("Aspects " + previous + " and " + def.id() + " use the same component pair " + key);
                    continue;
                }
            }
            aspects.put(def.id(), def);
        }
        tiers.keySet().retainAll(aspects.keySet());

        Map<String, List<String>> usedIn = new HashMap<>();
        for (AspectDefinition def : aspects.values()) {
            for (String component : def.components()) {
                usedIn.computeIfAbsent(component, k -> new ArrayList<>()).add(def.id());
            }
        }
        usedIn.replaceAll((k, v) -> List.copyOf(v));

        Map<String, Map<String, Integer>> breakdown = new HashMap<>();
        for (AspectDefinition def : aspects.values()) {
            breakdown.put(def.id(), computeBreakdown(def.id(), aspects, breakdown));
        }

        return new AspectGraph(Collections.unmodifiableMap(aspects), Map.copyOf(tiers), Map.copyOf(byPair),
                Map.copyOf(usedIn), Map.copyOf(breakdown), List.copyOf(problems));
    }

    private static Map<String, Integer> computeBreakdown(String id, Map<String, AspectDefinition> aspects,
                                                         Map<String, Map<String, Integer>> memo) {
        Map<String, Integer> known = memo.get(id);
        if (known != null) {
            return known;
        }
        AspectDefinition def = aspects.get(id);
        Map<String, Integer> result = new TreeMap<>();
        if (def.isPrimal()) {
            result.put(id, 1);
        } else {
            for (String component : def.components()) {
                computeBreakdown(component, aspects, memo).forEach((k, v) -> result.merge(k, v, Integer::sum));
            }
        }
        Map<String, Integer> frozen = Collections.unmodifiableMap(result);
        memo.put(id, frozen);
        return frozen;
    }

    public List<String> problems() {
        return problems;
    }

    public boolean contains(String id) {
        return aspects.containsKey(id);
    }

    public Optional<AspectDefinition> get(String id) {
        return Optional.ofNullable(aspects.get(id));
    }

    /** All aspects ordered by tier, then id. */
    public Collection<AspectDefinition> all() {
        return aspects.values();
    }

    public int size() {
        return aspects.size();
    }

    public List<String> primals() {
        return aspects.values().stream().filter(AspectDefinition::isPrimal).map(AspectDefinition::id).toList();
    }

    /** Tier 0 for primals, otherwise one more than the highest component tier; -1 for unknown aspects. */
    public int tier(String id) {
        return tiers.getOrDefault(id, -1);
    }

    /** The compound made from the two given aspects, in either order. */
    public Optional<String> combine(String first, String second) {
        return Optional.ofNullable(byPair.get(PairKey.of(first, second)));
    }

    /** Compounds that directly use the given aspect as a component. */
    public List<String> usedIn(String id) {
        return usedIn.getOrDefault(id, List.of());
    }

    /** How many of each primal one unit of the aspect is ultimately made of. */
    public Map<String, Integer> primalBreakdown(String id) {
        return primalBreakdown.getOrDefault(id, Map.of());
    }

    /** True when one aspect is a direct component of the other. */
    public boolean related(String a, String b) {
        AspectDefinition first = aspects.get(a);
        AspectDefinition second = aspects.get(b);
        if (first == null || second == null) {
            return false;
        }
        return first.components().contains(b) || second.components().contains(a);
    }

    /**
     * Shortest chain of related aspects from {@code from} to {@code to}, inclusive of both ends, or an empty list
     * when they are not connected. Used to check that generated insight puzzles are solvable and to show hints.
     */
    public List<String> shortestChain(String from, String to) {
        if (!aspects.containsKey(from) || !aspects.containsKey(to)) {
            return List.of();
        }
        Map<String, String> previous = new HashMap<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        queue.add(from);
        seen.add(from);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (current.equals(to)) {
                List<String> path = new ArrayList<>();
                for (String step = to; step != null; step = previous.get(step)) {
                    path.add(step);
                }
                Collections.reverse(path);
                return List.copyOf(path);
            }
            for (String next : neighbours(current)) {
                if (seen.add(next)) {
                    previous.put(next, current);
                    queue.add(next);
                }
            }
        }
        return List.of();
    }

    /** Aspects related to the given one (its components and the compounds that use it), sorted for determinism. */
    public List<String> neighbours(String id) {
        AspectDefinition def = aspects.get(id);
        if (def == null) {
            return List.of();
        }
        Set<String> result = new java.util.TreeSet<>(def.components());
        result.addAll(usedIn(id));
        return List.copyOf(result);
    }

    private record PairKey(String low, String high) {
        static PairKey of(String a, String b) {
            return a.compareTo(b) <= 0 ? new PairKey(a, b) : new PairKey(b, a);
        }

        @Override
        public String toString() {
            return low + " + " + high;
        }
    }
}
