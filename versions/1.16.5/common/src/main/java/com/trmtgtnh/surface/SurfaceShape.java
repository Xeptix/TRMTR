package com.trmtgtnh.surface;

import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * What shape a surface occupies its block in.
 *
 * <p>
 * Not stored anywhere, and deliberately so. Every other fact about a surface is resolved once into a
 * table because it follows from config; this one follows from what a block is, which cannot change
 * while the game is running. Deriving it on demand costs two type checks, and it means a block named
 * by hand in a family's list is shaped correctly without detection ever having looked at it.
 *
 * <p>
 * Nothing here is persisted or sent, so unlike {@link SurfaceFamily} these may be reordered freely.
 *
 * <p>
 * The three shapes and the two questions about them are exactly the older editions', and that is not
 * a courtesy: {@code SinkProfile} is one of the twenty-eight classes held byte for byte identical
 * across every edition, and it names this enum. It uses {@link #FULL} and {@link #isHalfDepth()} and
 * nothing else, which is what left the rest of this class free to be reshaped.
 *
 * <p>
 * <strong>And it had to be reshaped, because a slab is a different thing here.</strong> 1.7.10 read
 * which half a slab occupies out of metadata bit three and asked {@code renderAsNormalBlock} to tell
 * a double slab from a single one. 1.12.2 had neither, but still registered the single and double
 * forms as two different blocks, so {@code BlockSlab.isDouble()} answered from the block alone.
 * 1.16.5 registers one slab block carrying a {@code SlabType} property that is {@code BOTTOM},
 * {@code TOP} or {@code DOUBLE} - so the block alone can no longer tell you, and the old
 * {@code of(Block)} would have called every double slab a slab and let it sink half a block too far.
 * It is {@link #of(BlockState)} here instead. The signature change is the honest consequence of the
 * game's change, and taking a state is strictly more information than taking a block, so nothing is
 * lost by it.
 */
public enum SurfaceShape {

    /** Fills its block. Everything the mod wore before slabs were let in. */
    FULL,

    /** Half a block, resting on the floor or hung from the ceiling. */
    SLAB,

    /** A full block with a quarter cut out of it, facing one of four ways. */
    STAIR;

    /**
     * What shape this state stands in, judged by what its block extends and what it says of itself.
     *
     * <p>
     * A double slab is a full cube that happens to be a slab, so ancestry alone is not the question
     * and the state is asked as well. The property is asked for by name rather than assumed present,
     * because a modded slab may extend the vanilla class without carrying vanilla's property, and a
     * block that cannot answer is one to treat as ordinary ground rather than one to throw over.
     */
    public static SurfaceShape of(BlockState state) {
        if (state == null) return FULL;
        if (state.getBlock() instanceof StairBlock) return STAIR;
        if (state.getBlock() instanceof SlabBlock) {
            try {
                if (!state.hasProperty(SlabBlock.TYPE)) return SLAB;
                return state.getValue(SlabBlock.TYPE) == SlabType.DOUBLE ? FULL : SLAB;
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
     * setting: half a block of stone cannot lose eight pixels and still be there. Halving the depth is
     * also the whole of what a slab needs done differently, because the run itself is untouched - a
     * slab takes exactly as many crossings to wear through as the block it was cut from, and exactly
     * as long to recover, because it walks the same chain at the same prices and only the picture is
     * drawn shallower.
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
     * carry vanilla's type property is somebody else's slab that has chosen its own way of saying
     * which half it is in; it is treated as resting on the floor, which is where most are.
     */
    public static double bottomOf(SurfaceShape shape, BlockState state) {
        return shape == SLAB && isUpperHalf(state) ? 0.5D : 0.0D;
    }

    /** The top of that same space: half a block for a slab resting on the floor, otherwise one. */
    public static double topOf(SurfaceShape shape, BlockState state) {
        return shape == SLAB && !isUpperHalf(state) ? 0.5D : 1.0D;
    }

    private static boolean isUpperHalf(BlockState state) {
        if (state == null || !state.hasProperty(SlabBlock.TYPE)) return false;
        return state.getValue(SlabBlock.TYPE) == SlabType.TOP;
    }
}
