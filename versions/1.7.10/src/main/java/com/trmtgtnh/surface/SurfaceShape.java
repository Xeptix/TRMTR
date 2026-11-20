package com.trmtgtnh.surface;

import net.minecraft.block.Block;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.BlockStairs;

import com.trmtgtnh.erosion.SinkProfile;

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
 * Since 0.9.229 (W3 T6) a data file may give a block state its shape, which is resolved into the
 * surface table with the families and sent with it, because collision is the server's; the class
 * is then only what a state without one falls back to. The wire carries {@link DataShape}'s own
 * numbers, never these ordinals, so unlike {@link SurfaceFamily} these may still be reordered
 * freely.
 */
public enum SurfaceShape {

    /** Fills its block. Everything the mod wore before slabs were let in. */
    FULL,

    /** Half a block, resting on the floor or hung from the ceiling. */
    SLAB,

    /** A full block with a quarter cut out of it, facing one of four ways. */
    STAIR,

    /**
     * Up to eight boxes a data file gives the block (W3 spec 2.4, task T6), the highest of them walked on and sunk
     * into; the boxes are the file's, in {@link DataShape}, and never decided here.
     */
    BOXES;

    /**
     * What shape this block state stands in: a data file's shape first, then the block's class (W3 spec 2.4). The one
     * to ask for ground, because a file gives shapes by metadata; {@link #of(Block)} is the class alone.
     */
    public static SurfaceShape of(Block block, int meta) {
        DataShape data = SurfaceRegistry.shapeOf(block, meta);
        return data != null ? ofData(data) : of(block);
    }

    /** The shape a data file's shape stands as. */
    public static SurfaceShape ofData(DataShape data) {
        switch (data.kind) {
            case DataShape.SLAB:
                return SLAB;
            case DataShape.STAIR:
                return STAIR;
            case DataShape.BOXES:
                return BOXES;
            default:
                return FULL;
        }
    }

    /**
     * How far worn ground sinks at a block state, in pixels: the family's depth as {@link SinkProfile} gives it for
     * the shape (the collision depth when {@code collision}), then held by the state's data shape - nothing for
     * {@code "sinks": false}, no deeper than its {@code maxSinkPixels}, and always a pixel short of the shape's height
     * (the shortest walked box's for {@code boxes}). Both sides ask this, so the drawn rut, the client's footing and
     * the
     * server's collision come from one number.
     */
    public static int sunk(SurfaceFamily appearance, int storedSink, Block block, int meta, boolean collision) {
        DataShape data = SurfaceRegistry.shapeOf(block, meta);
        SurfaceShape shape = data != null ? ofData(data) : of(block);
        int depth = collision ? SinkProfile.collides(appearance, storedSink, shape)
            : SinkProfile.shown(appearance, storedSink, shape);
        if (data == null) return depth;
        return data.sunk(depth, heightOf(data));
    }

    /**
     * Whether worn ground at this block state can sink at all, by its data shape's own rule ({@link #sunk}'s hold):
     * not for {@code "sinks": false}, a cap of nothing, or a walked box a pixel tall. The record's sink is the chain's
     * step and is kept whatever the shape, so whatever chooses how worn ground is drawn - the sunken stand-in, the
     * sunk top's light, the fringe - asks this rather than the record alone, and a block that collides flat is drawn
     * flat (W3 T6 follow-up, R3's mossy loam, 2026-11-04).
     */
    public static boolean sinks(Block block, int meta) {
        return sinks(SurfaceRegistry.shapeOf(block, meta));
    }

    /** The same for a data shape already in hand; none is the family's own rule, which sinks. */
    public static boolean sinks(DataShape data) {
        if (data == null) return true;
        return data.sunk(SinkProfile.MAX_SINK_PIXELS, heightOf(data)) > 0;
    }

    /** The height a data shape's wear is held a pixel short of: a slab's or a stair's half, else the whole block. */
    private static int heightOf(DataShape data) {
        return ofData(data) == FULL ? DataShape.PIXELS : DataShape.PIXELS / 2;
    }

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
     *
     * <p>
     * Not a data file's boxes: their depth is held by their own walked box instead ({@link DataShape#sunk}).
     */
    public boolean isHalfDepth() {
        return this == SLAB || this == STAIR;
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
