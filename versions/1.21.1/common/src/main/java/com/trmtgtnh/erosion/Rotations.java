package com.trmtgtnh.erosion;

/**
 * Which way a wear texture faces at a given position.
 *
 * <p>
 * Upstream stores this in block state and spends four blockstate variants on it. Here it is
 * a pure function of the horizontal coordinates, which costs nothing to store, nothing to
 * send, and lets both sides derive the same answer independently. The point is only to stop
 * a path looking tiled: four rotations of the same texture across neighbouring blocks read
 * as organic wear rather than a repeating pattern.
 *
 * <p>
 * The constants are upstream's, so a path rotates the same way here as it does there.
 */
public final class Rotations {

    private Rotations() {}

    /** Rotation index 0-3, meaning 0, 90, 180 and 270 degrees clockwise. */
    public static int forPosition(int x, int z) {
        int h = (x * 1619) ^ (z * 31337);
        return ((h >>> 4) ^ (h >>> 8)) & 3;
    }

    /**
     * The same, with how far the ground has dropped folded in.
     *
     * <p>
     * A rut passes the same run of visual layers once per pixel of depth, so a position eight
     * pixels down was drawing, in the same orientation, the picture it drew one pixel down - the
     * whole sunken half of a family's run replaying eight gradations eight times over. That is not
     * a texture budget problem. All four turns of every gradation are stitched into the atlas
     * whether or not anything asks for them, so this spends variety that has been paid for since
     * the atlas was built.
     *
     * <p>
     * Depth zero returns exactly what the two-argument form does, which is what keeps ground that
     * has not sunk looking as it always has - and keeps the upstream constants meaning what they
     * meant. Because the fold is an exclusive or and the shift-and-mask distributes over it, the
     * answer still separates into the position's own turn and a turn per depth: neighbouring
     * columns keep precisely the disagreement they had, which is the only thing this class exists
     * for, and each column sees several orientations on its way down.
     */
    public static int forPosition(int x, int z, int depth) {
        if (depth <= 0) return forPosition(x, z);
        int h = (x * 1619) ^ (z * 31337) ^ (depth * 2749);
        return ((h >>> 4) ^ (h >>> 8)) & 3;
    }
}
