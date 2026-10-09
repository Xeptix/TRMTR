package com.trmtgtnh.client.render;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.WoolCarpetBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.block.BlockGhost;
import com.trmtgtnh.erosion.PhysicalDecay;

/**
 * Snow and carpet drawn down with the ground that has worn away underneath them.
 *
 * <p>
 * The 1.7.10 edition moves the renderer's bounds for the block. This version draws baked models, which have
 * no bounds to move, but every renderer at this version - vanilla's, the Sodium family's, FRAPI's and
 * OptiFine's - adds a block state's own offset to its model, the one vanilla scatters flowers with. So a
 * settling block answers that offset with the drop of the rut under it, and nothing else about it changes.
 * Canvas asks for the offset only of a block whose type says it has one, which snow and carpet do not, so
 * under Canvas they stayed up until 0.9.220; the Fabric module tells Canvas they have one
 * ({@link #offsetTypeFor}), and vanilla's own offset still reads none, so nothing is scattered.
 * The drop is the figure the footing is moved by as well, in {@code PhysicalDecay}, so the picture and the
 * footing cannot part company.
 *
 * <p>
 * Missing until 0.9.219, as it was in the 1.12.2 edition: the footing came down and the picture never did,
 * so snow on a snowed-over road hung in the air over the rut.
 *
 * <p>
 * Read from the view the asker was handed, never from the game's own level: the offset and the face test
 * are asked on a mesher thread, holding a snapshot of the chunks being built.
 */
public final class Settling {

    private Settling() {}

    /** Whether this block rests on the ground and goes down with it - snow and carpet. */
    public static boolean settles(Block block) {
        return PhysicalDecay.isSettling(block);
    }

    /**
     * The offset type a renderer that asks before it offsets is told: one with an offset, for a block that settles,
     * so that it goes on to ask the state where to draw it - Canvas, which otherwise skips every block whose type is
     * none, snow and carpet among them, and so never drew them down. Only that asking is changed. Vanilla's own
     * offset reads the block's real type, so a settling block not on worn ground is still drawn where it stands and
     * never scattered as a flower is.
     */
    public static BlockBehaviour.OffsetType offsetTypeFor(Block block) {
        return settles(block) ? BlockBehaviour.OffsetType.XZ : block.getOffsetType();
    }

    /**
     * How far the ground under a position has dropped: the block it stands in for, less the height it can be stood
     * on at - the 1.7.10 edition's GhostRendering.settledDrop.
     *
     * <p>
     * Read off the ghost below, because what wants matching is what is there: a ghost knows both how far it has
     * sunk and how tall the block it stands in for was, and those are not the same question on a grass path that
     * already stood short. The height stood on is BlockGhost.heightAt's footing, which on anything but a slab is the
     * drawn one - so in visual mode snow and carpet come down into the drawn rut, the other edition's code rather
     * than its comment, Xep's choice on 2026-10-08. Until 0.9.220 this took the collision depth, which kept them up.
     */
    public static double dropFor(BlockGetter level, BlockPos pos) {
        if (level == null || pos == null) return 0.0D;
        BlockPos below = pos.below();
        if (!(level.getBlockState(below)
            .getBlock() instanceof BlockGhost)) return 0.0D;
        int origin = com.trmtgtnh.Client.ghostOriginAt(below.getX(), below.getY(), below.getZ());
        short record = com.trmtgtnh.Client.ghostRecordAt(level, below.getX(), below.getY(), below.getZ());
        int outline = BlockGhost.outlineAt(level, below, origin);
        double drop = BlockGhost.topOf(outline) - BlockGhost.heightAt(record, origin, outline, true);
        return drop > 0.0D ? drop : 0.0D;
    }

    /** The offset a settling block is drawn at - vanilla's, dropped by the rut under it - or null for unchanged. */
    public static Vec3 offset(BlockState state, BlockGetter level, BlockPos pos, Vec3 vanilla) {
        if (state == null || !settles(state.getBlock())) return null;
        double drop = dropFor(level, pos);
        if (drop <= 0.0D) return null;
        noteShiftRan();
        return vanilla == null ? new Vec3(0.0D, -drop, 0.0D) : vanilla.add(0.0D, -drop, 0.0D);
    }

    /**
     * Whether a face of a settling block must be drawn whatever the culling concluded.
     *
     * <p>
     * A snow layer hides the side it shares with a neighbour at least as deep, which is right while the two
     * stand on the same ground; over ground worn by different amounts they stand at different heights, and
     * the face both hid is the step between them. The 1.7.10 edition's rule: kept where the two disagree
     * about the drop and the neighbour does not cover this face.
     */
    public static boolean keepsFace(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        if (state == null || side == null || level == null || !settles(state.getBlock())) return false;
        BlockPos at = pos.relative(side);
        BlockState neighbour = level.getBlockState(at);
        if (!settles(neighbour.getBlock())) return false;
        double ownDrop = dropFor(level, pos);
        // Whatever stands above rests on this one, and this one is snow or carpet rather than a ghost.
        double neighbourDrop = side == Direction.UP ? 0.0D : dropFor(level, at);
        if (neighbourDrop == ownDrop) return false;
        double ownBottom = pos.getY() - ownDrop;
        double ownTop = pos.getY() + restingHeight(state) - ownDrop;
        double neighbourBottom = at.getY() - neighbourDrop;
        double neighbourTop = at.getY() + restingHeight(neighbour) - neighbourDrop;
        if (side == Direction.DOWN) return neighbourTop < ownBottom;
        if (side == Direction.UP) return neighbourBottom > ownTop;
        return ownTop > neighbourTop || ownBottom < neighbourBottom;
    }

    private static double restingHeight(BlockState state) {
        if (state.getBlock() instanceof SnowLayerBlock) {
            return state.getValue(SnowLayerBlock.LAYERS)
                .intValue() * 0.125D;
        }
        if (state.getBlock() instanceof WoolCarpetBlock) return 0.0625D;
        return 1.0D;
    }

    private static volatile boolean shiftSaid;

    /**
     * Said once, the first time a block is drawn down - the only evidence the hook bound at all, since a
     * missed injection here is silent and the picture it leaves is merely the old one.
     */
    private static void noteShiftRan() {
        if (shiftSaid) return;
        shiftSaid = true;
        Trmt.LOG.info("Settling a block onto worn ground; the offset its renderer draws it at is in place");
    }
}
