package com.trmtgtnh.original;

import com.trmtgtnh.surface.SurfaceFamily;

/**
 * The original mod's healing, step for step, on Faithful Mode's chains (docs/goals/0.9.229-w5-spec.md, 2.4; original,
 * section 6): how many days each stage waits, what counts as isolated, which step back turns plain dirt into grass and
 * which may grow short grass.
 *
 * <p>
 * The original heals each eroded block in its random tick: covered ground at once, otherwise one stage back once the
 * stage has waited its days untouched, half that when no worn ground is beside, above or below it. Its days are read by
 * stage, so they are read here by the index of {@link FaithfulChains}' chains:
 *
 * <ul>
 * <li>grass 0 to 4, the worn turf: the five {@code grass.stage1} to {@code stage5} figures, one each;</li>
 * <li>grass 5 to 8 and plain dirt 0 to 2, bare and worn earth: the one {@code dirt.erodedDirt} figure;</li>
 * <li>grass 9 and plain dirt 3, the coarse end: {@code dirt.erodedCoarseDirt};</li>
 * <li>sand 0 to 4: the five {@code sand.stage1} to {@code stage5} figures.</li>
 * </ul>
 *
 * <p>
 * The original's worn earth always heals into grass, even where it began as plain dirt: its {@code eroded_dirt} stage 0
 * becomes {@code eroded_grass_block} stage 4. Plain dirt's chain starts at worn earth 1, so the step back from its
 * first
 * index is the one that becomes grass, at the grass chain's bare earth. And the original's coarse end, which writes no
 * record of its own, heals at its first random tick, about a minute after it forms; that is a side effect, not a
 * figure, and is offered as one (spec question 4).
 *
 * <p>
 * Pure, so it is the same file in all eight editions and its rules are tried without a game.
 */
public final class FaithfulHeal {

    /**
     * The twelve squares whose wear keeps a square from being isolated, as offsets: one block away to each of the four
     * sides, at the same height, one above and one below (the original's {@code isIsolated}).
     */
    private static final int[][] AROUND = new int[12][];

    static {
        int[][] sides = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        int at = 0;
        for (int dy = -1; dy <= 1; dy++) {
            for (int[] side : sides) AROUND[at++] = new int[] { side[0], dy, side[1] };
        }
    }

    /** The chance a step back from worn turf grows short grass on the square: the original's 5%. */
    public static final double SHORT_GRASS_CHANCE = 0.05d;

    /**
     * How long the original's coarse end lasts, in days, with no record of its own: one vanilla random tick of a block
     * at the shipped tick speed, 4096 game ticks over 3, of a day's 24000.
     */
    public static final double RANDOM_TICK_DAYS = 4096d / 3d / 24000d;

    /** Whether the square at an offset holds worn ground: a record that is drawn. */
    public interface Worn {

        boolean at(int dx, int dy, int dz);
    }

    private FaithfulHeal() {}

    /** The twelve offsets isolation looks at, each a fresh {dx, dy, dz}. */
    public static int[][] around() {
        int[][] out = new int[AROUND.length][];
        for (int i = 0; i < AROUND.length; i++) out[i] = AROUND[i].clone();
        return out;
    }

    /** Whether none of the twelve squares around holds worn ground, so the square heals in half the days. */
    public static boolean isolated(Worn worn) {
        for (int[] offset : AROUND) {
            if (worn.at(offset[0], offset[1], offset[2])) return false;
        }
        return true;
    }

    /** The days an isolated square waits: half, as the original divides its timeout by two. */
    public static double isolatedDays(double days) {
        return days / 2d;
    }

    /**
     * The days a stage at this index of the base's Faithful chain waits untouched before it steps back one, before
     * isolation and this mod's speed dials; NaN for a base or an index the original has no figure for.
     *
     * @param grass  the five worn turf figures, the first stage first
     * @param dirt   the worn earth figure
     * @param coarse the coarse end's figure
     * @param sand   the five sand figures, the first stage first
     */
    public static double days(SurfaceFamily base, int index, double[] grass, double dirt, double coarse,
        double[] sand) {
        int steps = OriginalGradation.steps(base);
        if (steps == 0 || index < 0 || index >= steps) return Double.NaN;
        if (coarseEnd(base, index)) return coarse;
        if (base == SurfaceFamily.GRASS) {
            if (index >= turfSteps()) return dirt;
            return grass != null && index < grass.length ? grass[index] : Double.NaN;
        }
        if (base == SurfaceFamily.DIRT) return dirt;
        return sand != null && index < sand.length ? sand[index] : Double.NaN;
    }

    /** Whether this index is the coarse end: the last of grass's chain and of plain dirt's. */
    public static boolean coarseEnd(SurfaceFamily base, int index) {
        if (base == SurfaceFamily.GRASS) return index == OriginalGradation.GRASS_STEPS - 1;
        if (base == SurfaceFamily.DIRT) return index == OriginalGradation.DIRT_STEPS - 1;
        return false;
    }

    /**
     * Whether the step back from this index is the one that turns plain dirt into grass: plain dirt's first index, its
     * first worn earth stage, which the original heals to bare earth and on into turf.
     */
    public static boolean healsIntoGrass(SurfaceFamily base, int index) {
        return base == SurfaceFamily.DIRT && index == 0;
    }

    /** The index of grass's chain that plain dirt healed into grass goes on from: bare earth. */
    public static int bareEarth() {
        return turfSteps();
    }

    /**
     * Whether a step back from this index may grow short grass: a grass base going back from worn turf above its first
     * stage, as the original's eroded grass does from any stage above 0. Bare earth going back to turf is worn earth
     * healing, not turf, and the first stage going back to grass is not a step between worn stages.
     */
    public static boolean sprouts(SurfaceFamily base, int index) {
        return base == SurfaceFamily.GRASS && index > 0 && index < turfSteps();
    }

    /** Whether this step back grows short grass, for a roll between 0 and 1. */
    public static boolean sprouts(SurfaceFamily base, int index, double roll) {
        return sprouts(base, index) && roll < SHORT_GRASS_CHANCE;
    }

    /** The worn turf stages of grass's chain, everything before bare earth: five. */
    private static int turfSteps() {
        return OriginalGradation.GRASS_STEPS - OriginalGradation.DIRT_STEPS - 1;
    }
}
