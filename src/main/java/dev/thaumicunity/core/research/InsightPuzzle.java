package dev.thaumicunity.core.research;

import dev.thaumicunity.core.aspect.AspectGraph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * The insight puzzle (classic research): fixed anchor aspects sit on a hexagonal board; the researcher fills empty
 * cells with aspects from their aspect pool. The puzzle is solved when all anchors belong to one connected group of
 * filled cells in which every adjacent pair of cells holds <em>related</em> aspects (one is a direct component of
 * the other, see {@link AspectGraph#related}).
 *
 * <p>Instances are immutable; {@link #place} and {@link #clear} return new puzzles. Blocked cells cannot be used.</p>
 */
public final class InsightPuzzle {
    private final int radius;
    private final Map<HexCoord, String> anchors;
    private final Map<HexCoord, String> placed;
    private final Set<HexCoord> blocked;

    public InsightPuzzle(int radius, Map<HexCoord, String> anchors, Map<HexCoord, String> placed, Set<HexCoord> blocked) {
        if (radius < 1 || radius > 6) {
            throw new IllegalArgumentException("Puzzle radius must be between 1 and 6, was " + radius);
        }
        this.radius = radius;
        this.anchors = Collections.unmodifiableMap(new LinkedHashMap<>(anchors));
        this.placed = Collections.unmodifiableMap(new LinkedHashMap<>(placed));
        this.blocked = Set.copyOf(blocked);
        for (HexCoord c : this.anchors.keySet()) {
            requireOnBoard(c);
        }
    }

    /**
     * Creates a fresh puzzle: anchors are spread over the board corners (evenly, starting at a seeded random
     * corner) and a few seeded random cells are blocked to vary the layout.
     */
    public static InsightPuzzle generate(List<String> anchorAspects, int radius, int blockedCells, long seed) {
        if (anchorAspects.isEmpty() || anchorAspects.size() > 6) {
            throw new IllegalArgumentException("An insight puzzle needs between 1 and 6 anchors");
        }
        Random random = new Random(seed);
        List<HexCoord> corners = HexCoord.corners(radius);
        int start = random.nextInt(6);
        Map<HexCoord, String> anchors = new LinkedHashMap<>();
        for (int i = 0; i < anchorAspects.size(); i++) {
            int index = (start + (i * 6) / anchorAspects.size()) % 6;
            anchors.put(corners.get(index), anchorAspects.get(i));
        }
        List<HexCoord> free = new ArrayList<>(HexCoord.board(radius));
        free.removeAll(anchors.keySet());
        free.remove(HexCoord.ORIGIN);
        Collections.shuffle(free, random);
        Set<HexCoord> blocked = new HashSet<>();
        for (HexCoord candidate : free) {
            if (blocked.size() >= blockedCells) {
                break;
            }
            // Never block a cell next to an anchor, so every anchor keeps all its exits.
            boolean nearAnchor = anchors.keySet().stream().anyMatch(a -> a.distance(candidate) <= 1);
            if (!nearAnchor) {
                blocked.add(candidate);
            }
        }
        return new InsightPuzzle(radius, anchors, Map.of(), blocked);
    }

    public int radius() {
        return radius;
    }

    public Map<HexCoord, String> anchors() {
        return anchors;
    }

    public Map<HexCoord, String> placed() {
        return placed;
    }

    public Set<HexCoord> blocked() {
        return blocked;
    }

    public boolean isOnBoard(HexCoord c) {
        return HexCoord.ORIGIN.distance(c) <= radius;
    }

    public boolean isFree(HexCoord c) {
        return isOnBoard(c) && !anchors.containsKey(c) && !placed.containsKey(c) && !blocked.contains(c);
    }

    public Optional<String> aspectAt(HexCoord c) {
        String anchor = anchors.get(c);
        return Optional.ofNullable(anchor != null ? anchor : placed.get(c));
    }

    public InsightPuzzle place(HexCoord c, String aspect) {
        if (!isFree(c)) {
            throw new IllegalArgumentException("Cell " + c + " is not free");
        }
        Map<HexCoord, String> copy = new LinkedHashMap<>(placed);
        copy.put(c, aspect);
        return new InsightPuzzle(radius, anchors, copy, blocked);
    }

    /** Removes a placed aspect (anchors cannot be removed). */
    public InsightPuzzle clear(HexCoord c) {
        if (!placed.containsKey(c)) {
            return this;
        }
        Map<HexCoord, String> copy = new LinkedHashMap<>(placed);
        copy.remove(c);
        return new InsightPuzzle(radius, anchors, copy, blocked);
    }

    /** True when every anchor is reachable from the first one through related, filled, adjacent cells. */
    public boolean isSolved(AspectGraph graph) {
        if (anchors.isEmpty()) {
            return true;
        }
        Set<HexCoord> reached = connectedFrom(anchors.keySet().iterator().next(), graph);
        return reached.containsAll(anchors.keySet());
    }

    /** Cells connected to {@code start} through related neighbours. */
    public Set<HexCoord> connectedFrom(HexCoord start, AspectGraph graph) {
        Set<HexCoord> seen = new HashSet<>();
        if (aspectAt(start).isEmpty()) {
            return seen;
        }
        ArrayDeque<HexCoord> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start);
        while (!queue.isEmpty()) {
            HexCoord current = queue.poll();
            String here = aspectAt(current).orElseThrow();
            for (HexCoord next : current.neighbours()) {
                if (seen.contains(next)) {
                    continue;
                }
                Optional<String> there = aspectAt(next);
                if (there.isPresent() && graph.related(here, there.get())) {
                    seen.add(next);
                    queue.add(next);
                }
            }
        }
        return seen;
    }

    /**
     * True when every pair of anchor aspects is connected in the aspect graph. On a board with room to spare this
     * guarantees a solution exists, because a related pair A-B can always be extended as A-B-A to match the path
     * length the board needs.
     */
    public boolean isSolvable(AspectGraph graph) {
        List<String> aspects = new ArrayList<>(anchors.values());
        for (int i = 1; i < aspects.size(); i++) {
            if (graph.shortestChain(aspects.get(0), aspects.get(i)).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Number of aspects the researcher has placed so far (each costs one point of that aspect). */
    public int placedCount() {
        return placed.size();
    }

    private void requireOnBoard(HexCoord c) {
        if (!isOnBoard(c)) {
            throw new IllegalArgumentException("Cell " + c + " is outside a board of radius " + radius);
        }
    }
}
