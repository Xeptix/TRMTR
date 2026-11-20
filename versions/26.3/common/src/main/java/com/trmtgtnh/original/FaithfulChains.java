package com.trmtgtnh.original;

import com.trmtgtnh.erosion.ErosionState;
import com.trmtgtnh.surface.SurfaceFamily;

/**
 * The original mod's wear chains, step for step, as this mod's packed states: what Faithful Mode wears grass, dirt
 * and sand along (docs/goals/0.9.229-w5-spec.md, 2.2).
 *
 * <p>
 * A fixed table rather than something {@code ErosionChain.rebuild} works out from the family settings, because the
 * original wears grass through to level earth - five worn turf stages, then bare earth, three worn earth stages and
 * the coarse end, all at full height - and this mod's own builder only ever reaches the material underneath by
 * sinking into it (an empty depth stops the chain after the surface run). So the one shape the builder cannot make is
 * written here, once, from the original's own step counts in {@link OriginalGradation}:
 *
 * <ul>
 * <li>grass, ten steps: grass layers 0 to 4, then dirt layers 0 to 4, all level. Dirt layer 0 is the original's
 * {@code eroded_dirt} stage 0, which looks like plain dirt, and dirt layer 4 its {@code eroded_coarse_dirt};</li>
 * <li>plain dirt, four steps: dirt layers 1 to 4, level, because the original sends plain dirt straight to its first
 * worn stage, as this mod's own dirt chain skips its layer 0;</li>
 * <li>sand, five steps: sand layers 0 to 4, drawn 0, 2, 2, 4 and 6 pixels down, as the original's sand models are.</li>
 * </ul>
 *
 * <p>
 * Each step's threshold is drawn from the family it shows, as the original drew each eroded block's from its vanilla
 * block's range; so grass takes six draws from grass's range to reach bare earth and four from dirt's to reach the
 * coarse end (original, section 5). Records keep storing states rather than indexes, so a record made on this
 * mod's own chain is placed on these when Faithful Mode is switched on, and back again when it is switched off.
 *
 * <p>
 * Pure, so it is the same file in all eight editions and its table is tried without a game.
 */
public final class FaithfulChains {

    /**
     * Whether Faithful Mode is on, as the edition's own settings last read it. Held here rather than asked of the
     * edition's {@code FaithfulProfile} because {@code ErosionChain} is the same file in all eight and may name only
     * what every edition has: the edition sets it from {@code FaithfulProfile.apply}, and an edition that has not been
     * given Faithful Mode yet never sets it, so its chains stay this mod's own.
     */
    private static volatile boolean on;

    private FaithfulChains() {}

    /** Switches Faithful Mode's chains on or off; called by the edition's settings read, before the chains rebuild. */
    public static void switchTo(boolean faithful) {
        on = faithful;
    }

    /** Whether Faithful Mode's chains are in force, as the edition last switched them. */
    public static boolean on() {
        return on;
    }

    /** Whether this base wears along the original's chain: Faithful Mode is on and the base is grass, dirt or sand. */
    public static boolean wears(SurfaceFamily base) {
        return on && has(base);
    }

    /**
     * The worn turf stages of the original's grass chain: everything before bare earth. Bare earth and what follows it
     * are the plain dirt chain's four steps with the original's {@code eroded_dirt} stage 0 in front of them.
     */
    private static int turfSteps() {
        return OriginalGradation.GRASS_STEPS - OriginalGradation.DIRT_STEPS - 1;
    }

    /** Whether Faithful Mode wears this base along a chain of the original's: grass, dirt and sand. */
    public static boolean has(SurfaceFamily base) {
        return OriginalGradation.steps(base) > 0;
    }

    /**
     * The chain Faithful Mode wears this base along, a fresh copy each call, or null for a base the original never
     * wore.
     */
    public static short[] chain(SurfaceFamily base) {
        int steps = OriginalGradation.steps(base);
        if (steps == 0) return null;
        short[] chain = new short[steps];
        for (int step = 0; step < steps; step++) {
            chain[step] = ErosionState
                .pack(appearance(base, step), layer(base, step), OriginalGradation.drawnSink(base, step));
        }
        return chain;
    }

    /**
     * How many stages the family's own run is drawn in while Faithful Mode is on: five for each of the three, the
     * original's five turf stages, its bare earth and three worn earth stages and the coarse end, and its five sand
     * stages. This is the figure the pictures are cut to (spec 2.2, "Which picture"), and 0 for any other family.
     */
    public static int stages(SurfaceFamily family) {
        if (family == SurfaceFamily.GRASS) return turfSteps();
        if (family == SurfaceFamily.DIRT) return OriginalGradation.DIRT_STEPS + 1;
        if (family == SurfaceFamily.SAND) return OriginalGradation.SAND_STEPS;
        return 0;
    }

    /** How far down the deepest step of this base's chain is drawn, in pixels: six for sand, none for the others. */
    public static int deepest(SurfaceFamily base) {
        int steps = OriginalGradation.steps(base);
        int deepest = 0;
        for (int step = 0; step < steps; step++) {
            deepest = Math.max(deepest, OriginalGradation.drawnSink(base, step));
        }
        return deepest;
    }

    private static SurfaceFamily appearance(SurfaceFamily base, int step) {
        if (base == SurfaceFamily.GRASS && step >= turfSteps()) return SurfaceFamily.DIRT;
        return base;
    }

    private static int layer(SurfaceFamily base, int step) {
        if (base == SurfaceFamily.GRASS) return step < turfSteps() ? step : step - turfSteps();
        if (base == SurfaceFamily.DIRT) return step + 1;
        return step;
    }
}
