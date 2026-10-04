package com.trmtgtnh.erosion;

/**
 * Implemented on every {@link net.minecraft.block.Block} by the collision mixin.
 *
 * <p>
 * Carries one flag: can a surface of this block's family ever end up physically sunk. It lives
 * on the block instance rather than in a lookup table because the collision path reads it for
 * every block a moving entity touches, and a field read is free where a registry lookup is
 * not.
 */
public interface ISinkableBlock {

    boolean trmt$isSinkable();

    void trmt$setSinkable(boolean sinkable);
}
