package com.trmtgtnh.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.trmtgtnh.Client;

/**
 * The ghost painted over a worn stair: the one ghost that occludes, so a worn stair lights as this version's stairs do
 * (Xep, 2026-10-10: "Follow each version"; 0.9.223, spec GF8).
 *
 * <p>
 * A stair at this version lets light into its cell and stops it by its shape: the light engine asks a block that says
 * {@code useShapeForLightOcclusion} for the face of its occlusion shape each way the light would cross, so light comes
 * through the open half and not through the solid back and floor. Whether a block occludes at all is said once for the
 * block, in its properties, and the one ghost says it occludes nothing - which every other worn square needs, a square
 * with a dip in it not hiding its neighbours' faces (spec GF1). So the worn stair is a block of its own: this class
 * says it uses its shape, answers with the covered stair's own occlusion shape at each square, and takes every other
 * answer from the ghost - its light figures, which {@link BlockGhost#getLightBlock} answers as the covered stair's, its
 * model, its sounds and its footing. The server holds the real stair, so the client now lights a worn stair as the
 * server does. The 1.7.10 and 1.12.2 editions stop all light at a worn stair, as their vanilla stairs do.
 *
 * <p>
 * A subclass rather than a flag, because the game asks {@code useShapeForLightOcclusion} while it builds the block's
 * states, inside the constructor, before any field of this class is set - an override answers there, a field does not.
 * Painted only by {@link BlockGhost#litState}, which picks it wherever the covered block is a stair.
 */
public class BlockGhostStair extends BlockGhost {

    public BlockGhostStair() {
        super(ModBlocks.GHOST_STAIR, true);
    }

    @Override
    public boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    /**
     * The covered stair's own occlusion shape at this square - the shape the light engine stops light with, and the one
     * a neighbour's face is hidden behind, as beside the real stair. Anything else answers as the ghost does.
     *
     * <p>
     * From 1.21.2 the game asks a block's occlusion shape once per state, with no square, and keeps it in the state
     * ({@code BlockStateBase.initCache}; {@code dynamicShape} no longer stops that). So this is no longer an override:
     * it is 1.21.1's rule under 1.21.1's signature, for the per-square hooks in each loader's module to ask, and the
     * per-state answer below is this rule asked of no square. A stair's own occlusion shape is a state's answer from
     * 1.21.2 as well, which is what is read here.
     */
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter world, BlockPos pos) {
        // Asked by a chunk about its own column too, in the chunk's coordinates - see BlockGhost.inWorld.
        pos = inWorld(world, pos);
        BlockState stair = coveredStair(Client.ghostOriginAt(pos.getX(), pos.getY(), pos.getZ()));
        if (stair == null) return getOcclusionShape(state);
        try {
            return stair.getOcclusionShape();
        } catch (RuntimeException awkwardBlock) {
            return getOcclusionShape(state);
        }
    }

    /**
     * The shape the game keeps for this state from 1.21.2: the ghost's own, as 1.21.1's rule answers a square where no
     * stair is recorded - vanilla's default, the state's shape at no square.
     */
    @Override
    protected VoxelShape getOcclusionShape(BlockState state) {
        return super.getOcclusionShape(state);
    }
}
