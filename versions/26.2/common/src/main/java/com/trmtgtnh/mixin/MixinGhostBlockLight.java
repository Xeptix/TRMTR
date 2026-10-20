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
import net.minecraft.world.level.lighting.BlockLightEngine;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.trmtgtnh.block.BlockGhost;
import com.trmtgtnh.block.BlockGhostStair;

/**
 * Block light through a worn square is asked of that square, as 1.21.1's light engine asked it.
 *
 * <p>
 * <strong>Why this exists from 26.1.</strong> Until 1.21.1 the light engine asked a block how much light it stops,
 * and the face its occlusion shape turns to the light, with the level and the square in hand
 * ({@code getOpacity(state, pos)} reading {@code state.getLightBlock(level, pos)}, and {@code shapeOccludes(fromNode,
 * fromState, toNode, toState, direction)} reading {@code getFaceOcclusionShape(level, pos, direction)}), and a ghost,
 * which keeps no state cache, answered for each square: none once it has sunk, the covered block's figure before. From
 * 1.21.2 both are asked of the state alone and kept in it ({@code BlockStateBase.initCache}), so every ghost would stop
 * all light as unworn earth does - darker ruts, a yard light census that fails. So at the two places the engine asks,
 * this asks the ghost's own per-square rules instead: {@link BlockGhost#getLightBlock} for the light it stops, and
 * {@link BlockGhostStair#getOcclusionShape(BlockState, BlockGetter, BlockPos)} for the worn stair's face, with
 * 1.21.1's arithmetic around each - at least one, and the face of the shape only where the state occludes and lights by
 * its shape, as {@code LightEngine.getOcclusionShape} decides for every other block. Anything that is not a ghost is
 * answered by the game's own call, untouched.
 *
 * <p>
 * The square being lit is the engine's {@code mutablePos}, which it has just set to the square it read the state from
 * (both engines do the same); the square the light comes from is the method's own {@code fromNode}, and the one it goes
 * to is one step from there in the direction the game is asking about. The getter is the engine's own level, as
 * 1.21.1's was. {@link MixinGhostSkyLight} is the same for sky light - a mixin cannot lend another its methods, so the
 * two small rules are written in each - and {@link MixinGhostSkySources} the same for the height the sky falls to.
 * Client side only: a ghost is only ever in the client's copy of the world.
 */
@SuppressWarnings("rawtypes")
@Mixin(BlockLightEngine.class)
public abstract class MixinGhostBlockLight extends LightEngine {

    @Shadow
    @Final
    private BlockPos.MutableBlockPos mutablePos;

    @SuppressWarnings("unchecked")
    private MixinGhostBlockLight() {
        super(null, null);
    }

    @WrapOperation(
        method = "propagateIncrease",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/lighting/BlockLightEngine;getOpacity(Lnet/minecraft/world/level/block/state/BlockState;)I"))
    private int trmt$opacityOfTheSquare(BlockLightEngine engine, BlockState toState, Operation<Integer> original) {
        if (!(toState.getBlock() instanceof BlockGhost ghost)) return original.call(engine, toState);
        return Math.max(1, ghost.getLightBlock(toState, this.chunkSource.getLevel(), this.mutablePos));
    }

    @WrapOperation(
        method = "propagateIncrease",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/lighting/BlockLightEngine;shapeOccludes(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;)Z"))
    private boolean trmt$shapesOfTheSquares(BlockLightEngine engine, BlockState fromState, BlockState toState,
        Direction direction, Operation<Boolean> original, @Local(argsOnly = true, ordinal = 0) long fromNode) {
        if (!(fromState.getBlock() instanceof BlockGhostStair) && !(toState.getBlock() instanceof BlockGhostStair)) {
            return original.call(engine, fromState, toState, direction);
        }
        BlockGetter level = this.chunkSource.getLevel();
        VoxelShape from = trmt$faceShape(fromState, level, BlockPos.of(fromNode), direction);
        VoxelShape to = trmt$faceShape(
            toState,
            level,
            BlockPos.of(BlockPos.offset(fromNode, direction)),
            direction.getOpposite());
        return Shapes.faceShapeOccludes(from, to);
    }

    /**
     * The face a state turns to the light at a square: 1.21.1's {@code LightEngine.getOcclusionShape(state, pos,
     * direction)}, which asked a block that occludes and lights by its shape for that face of its shape at the square,
     * and found nothing in any other.
     */
    private static VoxelShape trmt$faceShape(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        if (state.getBlock() instanceof BlockGhostStair stair && state.canOcclude() && state.useShapeForLightOcclusion()) {
            return stair.getOcclusionShape(state, level, pos)
                .getFaceShape(direction);
        }
        return LightEngine.getOcclusionShape(state, direction);
    }
}
