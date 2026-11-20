package com.trmtgtnh.original;

import com.trmtgtnh.erosion.ErosionState;
import com.trmtgtnh.surface.SurfaceFamily;

/**
 * Where on this mod's chain a square the original had worn goes, so a converted path looks as worn as it did.
 *
 * <p>
 * {@link OriginalWorld} lists each worn square with the step it had reached on the original's own chain (spec 2.2):
 * grass ten steps, from the first worn turf through bare earth to the coarse end; plain dirt four; sand five. This
 * turns that step into an index on the chain the square's base has here, once the chunk is in its level and the chain
 * is known (spec 2.9, "The record").
 *
 * <p>
 * With Faithful Mode on the chain is the original's, step for step, so the index is the step. With it off the chain is
 * this mod's own, longer and sinking, and the square is put the same fraction of the way along it - a square worn
 * three times out of ten is three tenths along - but never deeper than the original drew it (spec question 9): grass
 * and dirt stayed level in the original, so they stay level here, and sand sank 0, 2, 2, 4 and 6 pixels, so it sinks no
 * further than that. A converted path should look as it did, not be dug into the ground by the conversion.
 *
 * <p>
 * Pure, over the chain handed to it as this mod's packed states, so it is the same file in all eight editions.
 */
public final class OriginalGradation {

    /** The original's grass chain: five worn turf stages, bare earth, three worn earth stages, the coarse end. */
    public static final int GRASS_STEPS = 10;

    /** Plain dirt starts at the original's first worn earth stage: three of them, then the coarse end. */
    public static final int DIRT_STEPS = 4;

    /** Five worn sand stages. */
    public static final int SAND_STEPS = 5;

    /** How far down the original drew each sand stage, in pixels (its models' tops 16, 14, 14, 12, 10). */
    private static final int[] SAND_SINK = { 0, 2, 2, 4, 6 };

    private OriginalGradation() {}

    /** How many steps the original's chain has for this base, or 0 for a base it never wore. */
    public static int steps(SurfaceFamily base) {
        if (base == SurfaceFamily.GRASS) return GRASS_STEPS;
        if (base == SurfaceFamily.DIRT) return DIRT_STEPS;
        if (base == SurfaceFamily.SAND) return SAND_STEPS;
        return 0;
    }

    /** How many pixels down the original drew this step: sand's own depths, and level ground for everything else. */
    public static int drawnSink(SurfaceFamily base, int step) {
        if (base != SurfaceFamily.SAND) return 0;
        return SAND_SINK[clamp(step, SAND_STEPS)];
    }

    /**
     * The index on this base's live chain for a square at this step of the original's chain, or -1 when the live chain
     * is empty (the base wears nothing here, so there is no record to give it).
     *
     * @param faithful whether Faithful Mode is on, so that the live chain is the original's own
     * @param chain    the base's live chain as packed states, as {@code ErosionChain.forBase} holds it
     */
    public static int index(SurfaceFamily base, int step, boolean faithful, short[] chain) {
        int length = chain == null ? 0 : chain.length;
        int original = steps(base);
        if (length == 0 || original == 0) return -1;
        int at = clamp(step, original);
        if (faithful) return at < length ? at : length - 1;

        // The same fraction along: step s of n is (s + 1) / n of the run, rounded to the nearest index.
        int index = ((at + 1) * 2 * length + original) / (2 * original) - 1;
        if (index < 0) index = 0;
        if (index >= length) index = length - 1;

        int deepest = drawnSink(base, at);
        while (index > 0 && ErosionState.sinkOf(chain[index]) > deepest) index--;
        return index;
    }

    private static int clamp(int step, int steps) {
        if (step < 0) return 0;
        return step >= steps ? steps - 1 : step;
    }
}
