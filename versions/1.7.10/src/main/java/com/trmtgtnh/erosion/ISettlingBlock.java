package com.trmtgtnh.erosion;

/**
 * Implemented on every {@link net.minecraft.block.Block} by the collision mixin.
 *
 * <p>
 * Carries one flag: does this block rest on the ground rather than fill its own space, so that it
 * should be drawn down with ground that has worn away underneath it. On the block instance rather
 * than in a lookup table for the same reason {@link ISinkableBlock} is: it is read once per
 * rendered block per chunk rebuild, and a field read is free where a registry lookup is not.
 */
public interface ISettlingBlock {

    boolean trmt$isSettling();

    void trmt$setSettling(boolean settling);
}
