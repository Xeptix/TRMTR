package com.trmtgtnh.client.render;

import net.minecraft.block.Block;
import net.minecraft.block.BlockSnow;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.block.BlockGhost;
import com.trmtgtnh.erosion.PhysicalDecay;

/**
 * Snow and carpet drawn down with the ground that has worn away underneath them.
 *
 * <p>
 * The 1.7.10 edition does it by moving the renderer's bounds for the block between the two lines every
 * render type passes through. This version draws baked models, which have no bounds to move, but it has
 * a seam of its own that every renderer honours: the offset a block's state gives its picture, which
 * vanilla uses to scatter flowers and tall grass and the block renderer adds to every vertex it writes.
 * So a settling block answers that offset with the drop of the rut under it, and nothing else about it
 * changes - its model, its light and its collision stay its own. The drop is the one figure the footing
 * is moved by as well, in {@code PhysicalDecay.settledBoxAt}, so the picture and the footing cannot part
 * company.
 *
 * <p>
 * Missing until 0.9.219. The footing came down on the server alone and the picture never did, so snow on a
 * snowed-over road hung in the air over the rut, and mobs, items and golems standing on it were drawn sunk
 * into it.
 *
 * <p>
 * Read from the view the asker was handed, never from the game's own world: the offset is asked for on a
 * mesher thread, holding a snapshot of the chunks being built.
 */
@SideOnly(Side.CLIENT)
public final class Settling {

    private Settling() {}

    /** Whether this block is one that rests on the ground and goes down with it - snow and carpet. */
    public static boolean settles(Block block) {
        return PhysicalDecay.isSettling(block);
    }

    /**
     * How far the ground under a position has dropped, from the height that can be stood on.
     *
     * <p>
     * The height stood on rather than the one drawn, as the 1.7.10 edition takes it: in visual mode the
     * ground is drawn sunk and still walked on at full height, and snow drawn down into a rut nobody can
     * walk into would sit below the floor.
     */
    public static double dropFor(IBlockAccess world, BlockPos pos) {
        if (world == null || pos == null || pos.getY() <= 0) return 0.0D;
        BlockPos below = pos.down();
        if (!(world.getBlockState(below)
            .getBlock() instanceof BlockGhost)) return 0.0D;
        return BlockGhost.collisionSink(Trmt.proxy.ghostRecordAt(world, below.getX(), below.getY(), below.getZ()))
            / 16.0D;
    }

    /** The offset a settling block is drawn at - vanilla's, dropped by the rut under it - or null for unchanged. */
    public static Vec3d offset(IBlockState state, IBlockAccess world, BlockPos pos, Vec3d vanilla) {
        if (state == null || !settles(state.getBlock())) return null;
        double drop = dropFor(world, pos);
        if (drop <= 0.0D) return null;
        noteShiftRan();
        return vanilla == null ? new Vec3d(0.0D, -drop, 0.0D) : vanilla.add(0.0D, -drop, 0.0D);
    }

    /**
     * Whether a face of a snow layer must be drawn whatever vanilla's culling concluded.
     *
     * <p>
     * Vanilla hides the side a snow layer shares with a neighbour at least as deep, which is right while the
     * two stand on the same ground. Over ground worn by different amounts they stand at different heights,
     * and the face both hid is the step between them - a band left open down the seam of every rut. The
     * same rule as the 1.7.10 edition's, which has it for a client with better face culling: kept where the
     * two disagree about the drop and the neighbour does not cover this face. Carpet needs nothing, because
     * vanilla never hides the side one carpet shares with another.
     */
    public static boolean keepsFace(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        if (state == null || side == null || world == null || !settles(state.getBlock())) return false;
        BlockPos at = pos.offset(side);
        IBlockState neighbour = world.getBlockState(at);
        if (!settles(neighbour.getBlock())) return false;
        double ownDrop = dropFor(world, pos);
        // Whatever stands above rests on this one, and this one is snow or carpet rather than a ghost.
        double neighbourDrop = side == EnumFacing.UP ? 0.0D : dropFor(world, at);
        if (neighbourDrop == ownDrop) return false;
        double ownBottom = pos.getY() - ownDrop;
        double ownTop = pos.getY() + restingHeight(state) - ownDrop;
        double neighbourBottom = at.getY() - neighbourDrop;
        double neighbourTop = at.getY() + restingHeight(neighbour) - neighbourDrop;
        if (side == EnumFacing.DOWN) return neighbourTop < ownBottom;
        if (side == EnumFacing.UP) return neighbourBottom > ownTop;
        return ownTop > neighbourTop || ownBottom < neighbourBottom;
    }

    private static double restingHeight(IBlockState state) {
        if (state.getBlock() instanceof BlockSnow) {
            return state.getValue(BlockSnow.LAYERS)
                .intValue() * 0.125D;
        }
        if (state.getBlock() instanceof net.minecraft.block.BlockCarpet) return 0.0625D;
        return 1.0D;
    }

    private static volatile boolean shiftSaid;

    /**
     * Said once, the first time a block is drawn down. The only evidence that the hook bound at all, since
     * a missed injection here is silent and the picture it leaves is merely the old one.
     */
    private static void noteShiftRan() {
        if (shiftSaid) return;
        shiftSaid = true;
        Trmt.LOG.info("Settling a block onto worn ground; the offset the block renderer draws it at is in place");
    }
}
