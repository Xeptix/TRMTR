package com.trmtgtnh.surface;

import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

import com.trmtgtnh.erosion.SinkProfile;

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
 * Since 0.9.229 (W3 T6) a data file may give a block its shape, which is resolved into the surface table with the
 * families and sent with it, because collision is the server's; the class is then only what a state without one falls
 * back to. The wire carries {@link DataShape}'s own numbers, never these ordinals, so unlike {@link SurfaceFamily}
 * these may still be reordered freely.
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
    STAIR,

    /**
     * Up to eight boxes a data file gives the block (W3 spec 2.4, task T6), the highest of them walked on and sunk
     * into; the boxes are the file's, in {@link DataShape}, and never decided here.
     */
    BOXES;

    /**
     * What shape this block state stands in: a data file's shape first, then the block's class (W3 spec 2.4, T6). The
     * one to ask for ground; {@link #ofClass} is the class alone, which only detection asks.
     */
    public static SurfaceShape of(BlockState state) {
        DataShape data = dataOf(state);
        return data != null ? ofData(data) : ofClass(state);
    }

    /** The data file's shape for a state, or null; a registry that cannot answer is no shape. */
    public static DataShape dataOf(BlockState state) {
        if (state == null) return null;
        try {
            return SurfaceRegistry.shapeOf(state);
        } catch (RuntimeException awkward) {
            return null;
        }
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
     * (the shortest walked box's for {@code boxes}). Both sides ask this, so the drawn rut, the client's footing and the
     * server's collision come from one number.
     */
    public static int sunk(SurfaceFamily appearance, int storedSink, BlockState state, boolean collision) {
        DataShape data = dataOf(state);
        SurfaceShape shape = data != null ? ofData(data) : ofClass(state);
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
    public static boolean sinks(BlockState state) {
        return sinks(dataOf(state));
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
     * The stair a data file's {@code stair} over a block that is not one stands as (W3 T6): vanilla's stair at the
     * facing and half the state names, where it has a horizontal {@code facing} and a {@code half} of {@code top}, read
     * by name, and otherwise facing north and resting on the floor; always straight, since the corner a stair turns
     * comes from its neighbours' stair properties, which such a block has none of. Null for a real stair, which is its
     * own, and for anything that is not a data stair.
     */
    public static BlockState stairStandIn(BlockState state) {
        if (state == null || state.getBlock() instanceof StairBlock) return null;
        DataShape data = dataOf(state);
        if (data == null || data.kind != DataShape.STAIR) return null;
        net.minecraft.core.Direction facing = net.minecraft.core.Direction.NORTH;
        net.minecraft.world.level.block.state.properties.Half half = net.minecraft.world.level.block.state.properties.Half.BOTTOM;
        try {
            // A state's values are a stream of pairs from 26.1, where 1.21.1 held a map.
            for (net.minecraft.world.level.block.state.properties.Property.Value<?> each : (Iterable<net.minecraft.world.level.block.state.properties.Property.Value<?>>) state
                .getValues()::iterator) {
                net.minecraft.world.level.block.state.properties.Property<?> property = each.property();
                Object value = each.value();
                if ("facing".equals(property.getName()) && value instanceof net.minecraft.core.Direction
                    && ((net.minecraft.core.Direction) value).getAxis()
                        .isHorizontal()) {
                    facing = (net.minecraft.core.Direction) value;
                } else if ("half".equals(property.getName()) && value != null
                    && "top".equalsIgnoreCase(
                        value instanceof net.minecraft.util.StringRepresentable
                            ? ((net.minecraft.util.StringRepresentable) value).getSerializedName()
                            : value.toString())) {
                    half = net.minecraft.world.level.block.state.properties.Half.TOP;
                }
            }
            return net.minecraft.world.level.block.Blocks.OAK_STAIRS.defaultBlockState()
                .setValue(StairBlock.FACING, facing)
                .setValue(StairBlock.HALF, half);
        } catch (RuntimeException awkwardBlock) {
            return net.minecraft.world.level.block.Blocks.OAK_STAIRS.defaultBlockState();
        }
    }

    /**
     * What shape this state stands in by its class alone, judged by what its block extends and what it says of
     * itself.
     *
     * <p>
     * A double slab is a full cube that happens to be a slab, so ancestry alone is not the question
     * and the state is asked as well. The property is asked for by name rather than assumed present,
     * because a modded slab may extend the vanilla class without carrying vanilla's property, and a
     * block that cannot answer is one to treat as ordinary ground rather than one to throw over.
     */
    public static SurfaceShape ofClass(BlockState state) {
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
