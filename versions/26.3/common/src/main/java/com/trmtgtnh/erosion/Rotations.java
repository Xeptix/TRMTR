package com.trmtgtnh.erosion;

/**
 * Which way a wear texture faces at a given position.
 *
 * <p>
 * Upstream stores this in block state and spends four blockstate variants on it. Here it is
 * a pure function of the horizontal coordinates, which costs nothing to store, nothing to
 * send, and lets both sides derive the same answer independently. The point is only to stop
 * a path looking tiled: four rotations across neighbouring blocks read as organic wear rather
 * than a repeating pattern.
 *
 * <p>
 * The pick changed in 0.9.229 (Xep, 2026-11-16: "Yes, replace it", docs/goals/0.9.229-rotation-spec.md). Until then
 * it was upstream's hash, {@code (x * 1619) ^ (z * 31337)} folded by shifts and a mask, and because the shift and the
 * mask distribute over an exclusive or, its answer was x's part exclusive-or z's part: every row was another row with
 * its turns relabelled, so a path laid along a row repeated its neighbours' sequence, and one neighbour pair in four
 * shared a turn. Worn ground showed it as banding once the turn became a real quarter turn of the picture. Existing
 * worn blocks may show a different turn once after the update; the wear itself does not change.
 *
 * <p>
 * The pick now has two halves. The high bit is the square's color on a chessboard, {@code (x + z) & 1}, so the four
 * blocks that share an edge with any block always have the other color and never its turn: no fewer same-turn
 * neighbours is possible, since a turn must repeat somewhere among the eight that touch a block. The low bit is a coin
 * thrown by a mixing hash of x, z and depth together, which cannot be split into a part per axis, so no row or column
 * repeats another and nothing bands. Of the eight neighbours, the four across a corner share the color and match on
 * the coin about half the time. The color fixes which pair a block draws from, 0 and 1 or 2 and 3, a quarter turn
 * apart, so the pair the chessboard sets is never one picture and its upside-down copy.
 *
 * <p>
 * Portable and the same file in every edition, so a position turns the same way in all eight (CoreStaysPortableTest).
 */
public final class Rotations {

    private Rotations() {}

    /** Rotation index 0-3, meaning 0, 90, 180 and 270 degrees clockwise. */
    public static int forPosition(int x, int z) {
        return forPosition(x, z, 0);
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
     * Depth moves only the coin, never the square, so neighbours at different depths still never share a turn. Depth
     * zero, and anything below it, is the two-argument form.
     */
    public static int forPosition(int x, int z, int depth) {
        return (square(x, z) << 1) | coin(x, z, Math.max(0, depth));
    }

    /** The chessboard square: 0 or 1, and the other one at every edge neighbour. */
    static int square(int x, int z) {
        return (x + z) & 1;
    }

    /**
     * A fair bit from all three together: their sum under three odd multipliers, then a full avalanche (the finaliser
     * of a well-known 32-bit mixer), whose top bit depends on every bit of the sum, and nonlinearly.
     */
    static int coin(int x, int z, int depth) {
        int h = x * 0x9E3779B1 + z * 0x85EBCA77 + depth * 0xC2B2AE3D;
        h ^= h >>> 16;
        h *= 0x7FEB352D;
        h ^= h >>> 15;
        h *= 0x846CA68B;
        h ^= h >>> 16;
        return h >>> 31;
    }
}
