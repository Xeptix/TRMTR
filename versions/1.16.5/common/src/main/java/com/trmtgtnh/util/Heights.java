package com.trmtgtnh.util;

import net.minecraft.world.level.BlockGetter;

/**
 * How tall a level is, asked of the level: the lowest block it holds and the highest. The one place this edition says
 * how high its worlds go (0.9.222).
 *
 * <p>
 * Here every level holds 0 to 255 - {@link BlockGetter#getMaxBuildHeight} answers 256 - and from 1.18 a level starts
 * below zero and reaches higher, which the newer trees answer from the level's own lowest build height. Until 0.9.222
 * some thirty checks wrote 0 and 255 out, each one a fault waiting in a port to a taller game: a square below zero
 * could never be worn, a golem could not be sent there, the overlay would not draw it. Every check of a y against a
 * world's bounds asks this instead, so a taller game changes this class and nothing else. Where a y is packed into a
 * byte for the wire or a golem's list of targets, the packing stays as it is: a released edition's wire format is not
 * changed for a height it does not have, and the newer trees widen it there.
 */
public final class Heights {

    private Heights() {}

    /** The lowest y a level holds a block at. */
    public static int bottom(BlockGetter level) {
        return 0;
    }

    /** The highest y a level holds a block at. */
    public static int top(BlockGetter level) {
        return (level == null ? 256 : level.getMaxBuildHeight()) - 1;
    }

    /** Whether a level can hold a block at this y. */
    public static boolean holds(BlockGetter level, int y) {
        return y >= bottom(level) && y <= top(level);
    }
}
