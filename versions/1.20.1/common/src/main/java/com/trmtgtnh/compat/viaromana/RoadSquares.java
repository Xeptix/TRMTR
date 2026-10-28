package com.trmtgtnh.compat.viaromana;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The squares a charted Via Romana road runs over (0.9.228).
 *
 * <p>
 * Via Romana keeps a road as a graph of nodes, each the block a charting player stood in, four to eight blocks apart
 * at its shipped settings, and keeps nothing of the ground between them (path.PathGraph, path.Node in every one of its
 * 2.2.3 jars). So the road is drawn back here as a walk would cross it: a straight line of squares from each node to
 * each node it is joined to, and beside that line one square either way - the squares a player's step bleeds onto
 * sideways (general.bleedSide), which is how wide a path walked once is. The centre line is what a fast trip wears as
 * a step; the whole band is what heals slowly.
 *
 * <p>
 * Every square is the ground under the line: a node's block less one, which is where a player standing there has their
 * feet. Between two nodes at different heights the ground is taken at the height the line passes, and a record is
 * held to be on the road within {@link #SLACK} blocks of it either way, since a road over a hill is not a straight
 * line in the third dimension.
 */
public final class RoadSquares {

    /** How far above or below the line a square may sit and still be on the road. */
    static final int SLACK = 2;

    /** Column to {ground y}, for the line itself and for the squares beside it, each in the order first reached. */
    private final Map<Long, int[]> centre = new LinkedHashMap<Long, int[]>();

    private final Map<Long, int[]> beside = new LinkedHashMap<Long, int[]>();

    public static long column(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private static int xOf(long column) {
        return (int) (column >> 32);
    }

    private static int zOf(long column) {
        return (int) column;
    }

    /**
     * Adds the road between two nodes, each given as the block a player stood in. The ground is a block lower.
     */
    public void addSegment(int ax, int ay, int az, int bx, int by, int bz) {
        int dx = bx - ax;
        int dy = by - ay;
        int dz = bz - az;
        int steps = Math.max(Math.abs(dx), Math.abs(dz));
        boolean alongX = Math.abs(dx) >= Math.abs(dz);
        for (int i = 0; i <= steps; i++) {
            double t = steps == 0 ? 0d : i / (double) steps;
            int x = ax + (int) Math.round(dx * t);
            int z = az + (int) Math.round(dz * t);
            int ground = ay + (int) Math.round(dy * t) - 1;
            long here = column(x, z);
            if (!centre.containsKey(here)) centre.put(here, new int[] { ground });
            beside.remove(here);
            for (int side = -1; side <= 1; side += 2) {
                long next = alongX ? column(x, z + side) : column(x + side, z);
                if (!centre.containsKey(next) && !beside.containsKey(next)) beside.put(next, new int[] { ground });
            }
        }
    }

    public boolean isEmpty() {
        return centre.isEmpty();
    }

    /** Whether a square at this height is on the road's centre line. */
    public boolean onLine(int x, int y, int z) {
        int[] ground = centre.get(column(x, z));
        return ground != null && Math.abs(y - ground[0]) <= SLACK;
    }

    /** Whether a square at this height is on the road: its line or a square beside it. */
    public boolean onRoad(int x, int y, int z) {
        if (onLine(x, y, z)) return true;
        int[] ground = beside.get(column(x, z));
        return ground != null && Math.abs(y - ground[0]) <= SLACK;
    }

    /**
     * How fast a square here heals: the charted road's speed where it is on the road, and one - the ordinary speed -
     * where it is not.
     */
    public double paceAt(int x, int y, int z, double charted) {
        return onRoad(x, y, z) ? charted : 1d;
    }

    /** The line's squares as {x, ground y, z}, in the order the road reaches them. */
    public List<int[]> line() {
        return squares(centre);
    }

    /** The squares beside the line as {x, ground y, z}. */
    public List<int[]> besideLine() {
        return squares(beside);
    }

    private static List<int[]> squares(Map<Long, int[]> from) {
        List<int[]> out = new ArrayList<int[]>(from.size());
        for (Map.Entry<Long, int[]> each : from.entrySet()) {
            long at = each.getKey()
                .longValue();
            out.add(new int[] { xOf(at), each.getValue()[0], zOf(at) });
        }
        return Collections.unmodifiableList(out);
    }
}
