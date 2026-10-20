package com.trmtgtnh.util;

import net.minecraft.world.level.BlockGetter;

/**
 * How tall a level is, asked of the level: the lowest block it holds and the highest. The one place this edition says
 * how high its worlds go (0.9.222).
 *
 * <p>
 * In 1.16.5 every level held 0 to 255; from 1.18 a level starts below zero and reaches higher - the overworld holds -64
 * to 319 - and this edition answers from the level's own lowest and highest build heights. Until 0.9.222
 * some thirty checks wrote 0 and 255 out, each one a fault waiting in a port to a taller game: a square below zero
 * could never be worn, a golem could not be sent there, the overlay would not draw it. Every check of a y against a
 * world's bounds asks this instead, so a taller game changes this class and nothing else. Where 1.16.5 packs a y into a
 * byte, for the wire or a golem's list of targets, this edition widens it: four packets send a short, and a golem keeps
 * a target's height relative to its home.
 */
public final class Heights {

    private Heights() {}

    /** The lowest y a level holds a block at. */
    public static int bottom(BlockGetter level) {
        // getMinY from 1.21.2, the same lowest y getMinBuildHeight gave.
        return level == null ? 0 : level.getMinY();
    }

    /**
     * The highest y a level holds a block at. From 1.21.2 the level says it itself, as getMaxY; getMaxBuildHeight, which
     * it replaced, was the first y above, and 1.21.1 took one off. The same y, and 255 where there is no level.
     */
    public static int top(BlockGetter level) {
        return level == null ? 255 : level.getMaxY();
    }

    /** Whether a level can hold a block at this y. */
    public static boolean holds(BlockGetter level, int y) {
        return y >= bottom(level) && y <= top(level);
    }
}
