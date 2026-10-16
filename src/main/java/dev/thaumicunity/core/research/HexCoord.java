package dev.thaumicunity.core.research;

import java.util.ArrayList;
import java.util.List;

/**
 * Axial coordinate on the hexagonal insight-puzzle board. The board is a hexagon of a given radius around (0, 0).
 */
public record HexCoord(int q, int r) {
    private static final int[][] DIRECTIONS = {{1, 0}, {1, -1}, {0, -1}, {-1, 0}, {-1, 1}, {0, 1}};

    public static final HexCoord ORIGIN = new HexCoord(0, 0);

    public int s() {
        return -q - r;
    }

    /** Hex distance to another coordinate. */
    public int distance(HexCoord other) {
        return (Math.abs(q - other.q) + Math.abs(r - other.r) + Math.abs(s() - other.s())) / 2;
    }

    public List<HexCoord> neighbours() {
        List<HexCoord> result = new ArrayList<>(6);
        for (int[] d : DIRECTIONS) {
            result.add(new HexCoord(q + d[0], r + d[1]));
        }
        return result;
    }

    /** All coordinates within {@code radius} steps of the origin, in a stable order. */
    public static List<HexCoord> board(int radius) {
        List<HexCoord> result = new ArrayList<>();
        for (int q = -radius; q <= radius; q++) {
            int rMin = Math.max(-radius, -q - radius);
            int rMax = Math.min(radius, -q + radius);
            for (int r = rMin; r <= rMax; r++) {
                result.add(new HexCoord(q, r));
            }
        }
        return result;
    }

    /** The six corner cells of a board of the given radius, clockwise from the east corner. */
    public static List<HexCoord> corners(int radius) {
        List<HexCoord> result = new ArrayList<>(6);
        for (int[] d : DIRECTIONS) {
            result.add(new HexCoord(d[0] * radius, d[1] * radius));
        }
        return result;
    }
}
