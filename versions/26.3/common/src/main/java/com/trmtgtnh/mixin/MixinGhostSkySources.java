package com.trmtgtnh.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.lighting.ChunkSkyLightSources;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.trmtgtnh.block.BlockGhost;
import com.trmtgtnh.block.BlockGhostStair;

/**
 * The height the sky falls straight down to in a column is found through a worn square by asking that square, as
 * 1.21.1's chunk asked it.
 *
 * <p>
 * Each chunk keeps, for each of its columns, the lowest height its sky light falls straight down to, and finds it by
 * asking every edge between two blocks down the column whether it stops the sky. Until 1.21.1 that question,
 * {@code isEdgeOccluded(level, topPos, topState, bottomPos, bottomState)}, was asked with the getter and both squares:
 * the lower block's {@code getLightBlock(level, bottomPos)}, then the faces of both occlusion shapes at their squares.
 * A ghost answered it for its square - a sunk one lets the sky through - asked by the chunk about itself, in the
 * chunk's coordinates, which is why {@link BlockGhost#inWorld} exists. From 1.21.2 the question takes the two states
 * alone, so a sunk square stopped the sky at its top. Here it is asked with the squares again wherever either block is
 * a ghost, by 1.21.1's rule: {@link BlockGhost#getLightBlock} for the lower block, and the worn stair's own occlusion
 * shape ({@link BlockGhostStair#getOcclusionShape(BlockState, BlockGetter, BlockPos)}) for the faces, as {@link
 * MixinGhostBlockLight} asks them. Everything else is the game's own answer.
 *
 * <p>
 * The squares are where the game holds them: in its two walks down a column - from the top of a chunk, and from a
 * changed block downwards - the upper square is always {@code mutablePos1} and the lower {@code mutablePos2}, and the
 * edge test after a single change is handed both as arguments. The getter is the chunk itself where the chunk fills its
 * own columns, and the level it was handed otherwise - as in 1.21.1.
 */
@Mixin(ChunkSkyLightSources.class)
public abstract class MixinGhostSkySources {

    @Shadow
    @Final
    private BlockPos.MutableBlockPos mutablePos1;

    @Shadow
    @Final
    private BlockPos.MutableBlockPos mutablePos2;

    @WrapOperation(
        method = "findLowestSourceY",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/lighting/ChunkSkyLightSources;isEdgeOccluded(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean trmt$edgeDownTheChunk(BlockState topState, BlockState bottomState, Operation<Boolean> original,
        @Local(argsOnly = true) ChunkAccess chunk) {
        if (!trmt$either(topState, bottomState)) return original.call(topState, bottomState);
        return trmt$edgeOccluded(chunk, this.mutablePos1, topState, this.mutablePos2, bottomState);
    }

    @WrapOperation(
        method = "findLowestSourceBelow",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/lighting/ChunkSkyLightSources;isEdgeOccluded(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean trmt$edgeDownFromAChange(BlockState topState, BlockState bottomState, Operation<Boolean> original,
        @Local(argsOnly = true) BlockGetter level) {
        if (!trmt$either(topState, bottomState)) return original.call(topState, bottomState);
        return trmt$edgeOccluded(level, this.mutablePos1, topState, this.mutablePos2, bottomState);
    }

    @WrapOperation(
        method = "updateEdge",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/lighting/ChunkSkyLightSources;isEdgeOccluded(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean trmt$edgeAtAChange(BlockState topState, BlockState bottomState, Operation<Boolean> original,
        @Local(argsOnly = true) BlockGetter level, @Local(argsOnly = true, ordinal = 0) BlockPos topPos,
        @Local(argsOnly = true, ordinal = 1) BlockPos bottomPos) {
        if (!trmt$either(topState, bottomState)) return original.call(topState, bottomState);
        return trmt$edgeOccluded(level, topPos, topState, bottomPos, bottomState);
    }

    private static boolean trmt$either(BlockState topState, BlockState bottomState) {
        return topState.getBlock() instanceof BlockGhost || bottomState.getBlock() instanceof BlockGhost;
    }

    /** 1.21.1's {@code isEdgeOccluded}, with the getter and the squares, for an edge with a ghost on either side. */
    private static boolean trmt$edgeOccluded(BlockGetter level, BlockPos topPos, BlockState topState, BlockPos bottomPos,
        BlockState bottomState) {
        int stops = bottomState.getBlock() instanceof BlockGhost ghost
            ? ghost.getLightBlock(bottomState, level, bottomPos)
            : bottomState.getLightDampening();
        if (stops != 0) return true;
        VoxelShape topShape = trmt$faceShape(topState, level, topPos, Direction.DOWN);
        VoxelShape bottomShape = trmt$faceShape(bottomState, level, bottomPos, Direction.UP);
        return Shapes.faceShapeOccludes(topShape, bottomShape);
    }

    /** As {@code MixinGhostBlockLight.trmt$faceShape}: 1.21.1's per-square face of the occlusion shape. */
    private static VoxelShape trmt$faceShape(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        if (state.getBlock() instanceof BlockGhostStair stair && state.canOcclude() && state.useShapeForLightOcclusion()) {
            return stair.getOcclusionShape(state, level, pos)
                .getFaceShape(direction);
        }
        return LightEngine.getOcclusionShape(state, direction);
    }
}
