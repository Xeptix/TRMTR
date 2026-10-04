package com.trmtgtnh.surface;

import net.minecraft.block.Block;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.BlockStairs;

/**
 * What shape a surface occupies its block in.
 *
 * <p>
 * Not stored anywhere, and deliberately so. Every other fact about a surface is resolved once into
 * a table because it follows from config; this one follows from what class a block is, which
 * cannot change while the game is running. Deriving it on demand costs two type checks, and it
 * means a block named by hand in a family's list is shaped correctly without detection ever having
 * looked at it.
 *
 * <p>
 * Nothing here is persisted or sent, so unlike {@link SurfaceFamily} these may be reordered
 * freely.
 */
public enum SurfaceShape {

    /** Fills its block. Everything the mod wore before slabs were let in. */
    FULL,

    /** Half a block, resting on the floor or hung from the ceiling. */
    SLAB,

    /** A full block with a quarter cut out of it, facing one of four ways. */
    STAIR;

    /**
     * What shape this block stands in, judged by what it extends.
     *
     * <p>
     * A double slab is a full cube that happens to be a {@code BlockSlab}, so ancestry alone is
     * not the question and its geometry is asked as well. Asked defensively, because
     * {@code renderAsNormalBlock} is somebody else's method in a pack of this size and a block
     * that cannot answer is one to treat as ordinary ground.
     */
    public static SurfaceShape of(Block block) {
        if (block == null) return FULL;
        if (block instanceof BlockStairs) return STAIR;
        if (block instanceof BlockSlab) {
            try {
                return block.renderAsNormalBlock() ? FULL : SLAB;
            } catch (RuntimeException awkwardBlock) {
                return FULL;
            }
        }
        return FULL;
    }

    /** True for a shape that leaves part of its block empty, and so can never be an opaque cube. */
    public boolean isPartial() {
        return this != FULL;
    }

    /**
     * True for a shape whose thinnest load-bearing part is half a block.
     *
     * <p>
     * Which is what decides how far it may sink, and it is a fact about the shape rather than a
     * setting: half a block of stone cannot lose eight pixels and still be there. Halving the
     * depth is also the whole of what a slab needs done differently, because the run itself is
     * untouched - a slab takes exactly as many crossings to wear through as the block it was cut
     * from, and exactly as long to recover, because it walks the same chain at the same prices and
     * only the picture is drawn shallower.
     */
    public boolean isHalfDepth() {
        return this != FULL;
    }

    /**
     * The bottom of the space this block actually occupies, in block units.
     *
     * <p>
     * Worked out from the shape and the metadata rather than read off the block, because a block's
     * bounds fields are shared mutable state that only mean anything immediately after
     * {@code setBlockBoundsBasedOnState} - and the two sides that have to agree about where the
     * ground is do not call that at the same moments. Metadata bit three is vanilla's
     * upper-half flag and every slab in the game honours it, because {@code BlockSlab} reads it
     * itself rather than leaving it to its subclasses.
     */
    public static double bottomOf(SurfaceShape shape, int meta) {
        return shape == SLAB && (meta & 8) != 0 ? 0.5D : 0.0D;
    }

    /** The top of that same space: half a block for a slab resting on the floor, otherwise one. */
    public static double topOf(SurfaceShape shape, int meta) {
        return shape == SLAB && (meta & 8) == 0 ? 0.5D : 1.0D;
    }
}
