package com.trmtgtnh.client.render;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.trmtgtnh.block.BlockGhostStair;
import com.trmtgtnh.client.model.GhostWindows;

/**
 * Whether a face is drawn, given what the renderer's own face test answered: the one rule every face hook in every
 * loader asks - vanilla's on NeoForge and Forge, Fabric's, Indigo's and the Sodium family's - so they cannot drift.
 *
 * <p>
 * Three questions, in order:
 * <ol>
 * <li><strong>A face beside a worn stair, or a worn stair's own face, is tested with the stair at its square</strong>
 * (0.9.227). From 1.21.2 a block's occlusion shape is asked once per state with no square, and kept in the state, and
 * the renderers read that kept face. The worn stair's ghost stops light and hides faces with the covered stair's own
 * shape, which only the square knows, so its kept shape is the ghost's at no square - the whole cell - and every face
 * beside a worn stair was hidden, its open half included, where the cell behind showed through. 1.21.1 asked
 * {@code getFaceOcclusionShape(level, pos, face)} of both blocks and was handed {@link BlockGhostStair}'s answer for
 * the square; this asks the same answers and runs vanilla's own comparison on them - the full face hides, the skip test
 * is the block's own, an empty face on either side shows, and otherwise the face shows where it is not covered.
 * <li><strong>Two windows side by side hide the face between them</strong>, the way two panes of glass do - the 1.7.10
 * edition's shouldSideBeRendered on its window twins (0.9.220). See {@link GhostWindows#sharesPane}.
 * <li><strong>The step two settled blocks share is kept</strong> where they stand at different heights. See
 * {@link Settling#keepsFace}.
 * </ol>
 *
 * <p>
 * Read from the view the asker was handed, never from the game's own level, as {@link Settling} says: the face test is
 * asked on a mesher thread holding a snapshot of the chunks being built. OptiFine's own test keeps its own order, in
 * {@link OptiFineFaces}.
 */
public final class FaceRule {

    private FaceRule() {}

    /** Whether a face the renderer answered {@code vanilla} for is drawn. */
    public static boolean decide(boolean vanilla, BlockState state, BlockGetter level, BlockPos pos, Direction face) {
        if (state == null || level == null || pos == null || face == null) return vanilla;
        boolean drawn = besideWornStair(vanilla, state, level, pos, face);
        if (drawn && GhostWindows.sharesPane(state, level, pos, face)) return false;
        if (!drawn && Settling.keepsFace(state, level, pos, face)) return true;
        return drawn;
    }

    /**
     * Vanilla's own face test, {@code Block.shouldRenderFace(state, neighbour, face)} as 26.1.2's and 26.2's jars have it
     * (the same calls in the same order, read from both), asked with
     * a worn stair's shape at its square on whichever side of the face one stands - the neighbour, the block drawn, or
     * both, as two worn stairs side by side are (1.21.1 asked both shapes at their squares); the renderer's answer
     * everywhere else.
     */
    static boolean besideWornStair(boolean vanilla, BlockState state, BlockGetter level, BlockPos pos, Direction face) {
        BlockPos at = pos.relative(face);
        BlockState neighbour;
        try {
            neighbour = level.getBlockState(at);
        } catch (RuntimeException outsideTheView) {
            return vanilla;
        }
        if (neighbour == null) return vanilla;
        boolean mineWorn = state.getBlock() instanceof BlockGhostStair;
        boolean theirsWorn = neighbour.getBlock() instanceof BlockGhostStair;
        if (!mineWorn && !theirsWorn) return vanilla;
        VoxelShape theirs;
        VoxelShape mine;
        try {
            theirs = theirsWorn ? ((BlockGhostStair) neighbour.getBlock()).getOcclusionShape(neighbour, level, at)
                .getFaceShape(face.getOpposite()) : neighbour.getFaceOcclusionShape(face.getOpposite());
            mine = mineWorn ? ((BlockGhostStair) state.getBlock()).getOcclusionShape(state, level, pos)
                .getFaceShape(face) : state.getFaceOcclusionShape(face);
        } catch (RuntimeException awkwardBlock) {
            return vanilla;
        }
        // By identity, as vanilla asks it: a face slice that covers the whole face comes back as Shapes.block() itself.
        if (theirs == Shapes.block()) return false;
        if (state.skipRendering(neighbour, face)) return false;
        if (theirs.isEmpty()) return true;
        if (mine.isEmpty()) return true;
        return Shapes.joinIsNotEmpty(mine, theirs, BooleanOp.ONLY_FIRST);
    }
}
