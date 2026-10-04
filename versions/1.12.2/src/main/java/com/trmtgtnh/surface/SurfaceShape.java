package com.trmtgtnh.surface;

import net.minecraft.block.Block;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.state.IBlockState;

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
 *
 * <p>
 * The 1.12.2 edition of this class, and one of the few in this port that is reshaped rather than
 * carried. The three shapes and the two questions about them are exactly the 1.7.10 edition's, which
 * matters because {@code SinkProfile} - a portable class held byte for byte identical across both
 * editions - names this enum. What changed is how a block tells you which one it is. 1.7.10 asked
 * {@code renderAsNormalBlock} to tell a double slab from a single one and read which half a slab
 * occupies out of metadata bit three; 1.12.2 has neither, and asks {@code BlockSlab.isDouble} and the
 * slab's {@code half} property instead.
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
     * not the question and the slab is asked as well. 1.12.2 lets a slab say so directly - vanilla
     * registers the single and double forms as different blocks - and the question is still asked
     * defensively, because it is somebody else's method in a pack of any size and a block that
     * cannot answer is one to treat as ordinary ground.
     */
    public static SurfaceShape of(Block block) {
        if (block == null) return FULL;
        if (block instanceof BlockStairs) return STAIR;
        if (block instanceof BlockSlab) {
            try {
                return ((BlockSlab) block).isDouble() ? FULL : SLAB;
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
     * Worked out from the shape and the state rather than read off the block's bounding box, for the
     * reason the 1.7.10 edition gives and which still holds: the two sides that have to agree about
     * where the ground is must reach the answer by the same arithmetic. A slab whose state does not
     * carry vanilla's {@code half} property is somebody else's slab that has chosen its own way of
     * saying which half it is in; it is treated as resting on the floor, which is where most are.
     */
    public static double bottomOf(SurfaceShape shape, IBlockState state) {
        return shape == SLAB && isUpperHalf(state) ? 0.5D : 0.0D;
    }

    /** The top of that same space: half a block for a slab resting on the floor, otherwise one. */
    public static double topOf(SurfaceShape shape, IBlockState state) {
        return shape == SLAB && !isUpperHalf(state) ? 0.5D : 1.0D;
    }

    private static boolean isUpperHalf(IBlockState state) {
        if (state == null || !state.getPropertyKeys()
            .contains(BlockSlab.HALF)) return false;
        return state.getValue(BlockSlab.HALF) == BlockSlab.EnumBlockHalf.TOP;
    }
}
