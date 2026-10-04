package com.trmtgtnh.entity;

import java.util.Arrays;
import java.util.Random;

/**
 * The squares a golem knows want doing, nearest its home first.
 *
 * <p>
 * Built by one sweep of the whole radius and then whittled down as the work is done, because the
 * sweep is the expensive part and doing it once for several hundred squares of work is the whole
 * saving. A golem keeping sixty-four blocks has sixteen thousand columns to look at; asking that
 * question every stroke would cost more than the work.
 *
 * <p>
 * Each square is one int. Distance from home sits in the top bits so that an ordinary ascending
 * sort puts the near work first, and the rest is the offset from home - which fits comfortably,
 * since nothing in the list is further away than the radius the sort is measuring against. No
 * per-square object, so the worst case is sixty-four kilobytes of ints rather than sixteen thousand
 * allocations.
 *
 * <p>
 * Server side, held by the golem, and never saved. A golem coming back from disk sweeps once and
 * has a fresh list; carrying a stale one across a restart would only be carrying a description of
 * ground somebody may have rebuilt in the meantime.
 */
final class GolemTargets {

    /** A slot whose work is done. Sorts to the end and is skipped on the way past. */
    private static final int SPENT = Integer.MAX_VALUE;

    /** How far the offsets are shifted so they survive being packed unsigned. */
    private static final int BIAS = 128;

    private int[] squares = new int[0];

    /** Everything before this is spent, so the search need never look at it again. */
    private int head;

    private int live;

    /** Where the offsets in this list are measured from, so a moved home discards it. */
    private int homeX;

    private int homeY;

    private int homeZ;

    /** The tick the sweep was made on, and the earliest tick another may be made. */
    private long builtAt = Long.MIN_VALUE;

    private long sweepAllowedAt = Long.MIN_VALUE;

    /**
     * One square, packed.
     *
     * <p>
     * Distance first because that is what the sort is for. Seven bits of it, which covers the
     * furthest corner of the widest radius the config will take; eight bits each for the two
     * offsets, biased so they are never negative; and eight for the height, which is all a world
     * this tall has.
     */
    static int pack(int distance, int dx, int dz, int y) {
        return (Math.min(distance, 127) << 24) | ((dx + BIAS) & 0xFF) << 16 | ((dz + BIAS) & 0xFF) << 8 | (y & 0xFF);
    }

    static int distanceOf(int packed) {
        return (packed >>> 24) & 0x7F;
    }

    int xOf(int packed) {
        return homeX + (((packed >>> 16) & 0xFF) - BIAS);
    }

    int zOf(int packed) {
        return homeZ + (((packed >>> 8) & 0xFF) - BIAS);
    }

    static int yOf(int packed) {
        return packed & 0xFF;
    }

    /**
     * Whether the list is worth anything this tick.
     *
     * <p>
     * Three ways for it not to be: it was never built, everything in it is done, or it is older
     * than the age a pack allows. Emptiness comes first in practice - a golem that finishes its
     * round wants to look again rather than sit out the rest of the quarter hour.
     */
    boolean stale(long now, int homeX, int homeY, int homeZ, int ageTicks) {
        if (live <= 0) return true;
        if (this.homeX != homeX || this.homeY != homeY || this.homeZ != homeZ) return true;
        return now - builtAt > ageTicks;
    }

    /** Whether a fresh sweep is allowed yet, which is the only guard on its cost. */
    boolean maySweep(long now) {
        return now >= sweepAllowedAt;
    }

    void holdOff(long now, int cooldownTicks) {
        sweepAllowedAt = now + cooldownTicks;
    }

    /** Takes a finished sweep. The array is sorted here, so callers need not care about order. */
    void accept(int[] found, int count, long now, int homeX, int homeY, int homeZ) {
        this.squares = Arrays.copyOf(found, count);
        Arrays.sort(this.squares);
        this.head = 0;
        this.live = count;
        this.builtAt = now;
        this.homeX = homeX;
        this.homeY = homeY;
        this.homeZ = homeZ;
    }

    int remaining() {
        return live;
    }

    void clear() {
        squares = new int[0];
        head = 0;
        live = 0;
        builtAt = Long.MIN_VALUE;
    }

    /**
     * One square to go and work, from among the nearest one to {@code layers} rings of them.
     *
     * <p>
     * The number of rings is drawn fresh every time, which is the whole trick. Always taking the
     * nearest makes a golem sweep the ground in a visible spiral and always return to the same
     * corner; taking anything at all from the whole radius makes it look like it is not paying
     * attention. One to three rings keeps the work honestly near home and lets it look like a
     * thing choosing where to go next.
     *
     * <p>
     * Rings are counted by distinct rounded distance rather than by count of squares, so a ring
     * with twenty squares in it is one ring and so is a ring with one.
     */
    int pick(Random random, int maxLayers) {
        while (head < squares.length && squares[head] == SPENT) head++;
        if (head >= squares.length) return 0;

        int layers = 1 + random.nextInt(Math.max(1, maxLayers));
        int seen = 0;
        int lastDistance = -1;
        int pool = 0;
        int chosen = 0;

        // Reservoir of one: the pool is walked once and each candidate has an equal chance of
        // being the one kept, so nothing has to be collected into a list to be chosen from.
        for (int at = head; at < squares.length; at++) {
            int square = squares[at];
            if (square == SPENT) continue;
            int distance = distanceOf(square);
            if (distance != lastDistance) {
                if (seen == layers) break;
                seen++;
                lastDistance = distance;
            }
            pool++;
            if (random.nextInt(pool) == 0) chosen = square;
        }
        return chosen;
    }

    /**
     * Marks a square done. Cheap: the list is sorted, so its ring is a short scan.
     *
     * <p>
     * Matched on where it is and not on how high it was. The height was true when the sweep saw
     * it and a square that has since sunk a pixel is the same square - it is the one thing in the
     * packing that can move on its own, so it is the one thing left out of the comparison.
     */
    void spend(int packed) {
        if (packed == 0) return;
        int where = packed & ~0xFF;
        int distance = distanceOf(packed);
        for (int at = head; at < squares.length; at++) {
            int square = squares[at];
            if (square == SPENT) continue;
            if (distanceOf(square) > distance) return;
            if ((square & ~0xFF) != where) continue;
            squares[at] = SPENT;
            live--;
            return;
        }
    }

    /** The packing for a square, so a caller that worked one can say which it was. */
    int locate(int x, int z, int y) {
        int dx = x - homeX;
        int dz = z - homeZ;
        int distance = (int) Math.round(Math.sqrt((double) dx * dx + (double) dz * dz));
        return pack(distance, dx, dz, y);
    }
}
