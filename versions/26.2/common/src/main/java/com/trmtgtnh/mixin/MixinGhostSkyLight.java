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
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.level.lighting.SkyLightEngine;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.trmtgtnh.block.BlockGhost;
import com.trmtgtnh.block.BlockGhostStair;

/**
 * Sky light through a worn square is asked of that square, as 1.21.1's light engine asked it - {@link
 * MixinGhostBlockLight} for sky light, which spreads through the same two questions in its own engine; the reasons and
 * the rules are written there. The two small rules are repeated here because a mixin cannot lend another its methods.
 */
@SuppressWarnings("rawtypes")
@Mixin(SkyLightEngine.class)
public abstract class MixinGhostSkyLight extends LightEngine {

    @Shadow
    @Final
    private BlockPos.MutableBlockPos mutablePos;

    @SuppressWarnings("unchecked")
    private MixinGhostSkyLight() {
        super(null, null);
    }

    @WrapOperation(
        method = "propagateIncrease",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/lighting/SkyLightEngine;getOpacity(Lnet/minecraft/world/level/block/state/BlockState;)I"))
    private int trmt$opacityOfTheSquare(SkyLightEngine engine, BlockState toState, Operation<Integer> original) {
        if (!(toState.getBlock() instanceof BlockGhost ghost)) return original.call(engine, toState);
        return Math.max(1, ghost.getLightBlock(toState, this.chunkSource.getLevel(), this.mutablePos));
    }

    @WrapOperation(
        method = "propagateIncrease",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/lighting/SkyLightEngine;shapeOccludes(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;)Z"))
    private boolean trmt$shapesOfTheSquares(SkyLightEngine engine, BlockState fromState, BlockState toState,
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

    /** As {@code MixinGhostBlockLight.trmt$faceShape}: 1.21.1's per-square face of the occlusion shape. */
    private static VoxelShape trmt$faceShape(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        if (state.getBlock() instanceof BlockGhostStair stair && state.canOcclude() && state.useShapeForLightOcclusion()) {
            return stair.getOcclusionShape(state, level, pos)
                .getFaceShape(direction);
        }
        return LightEngine.getOcclusionShape(state, direction);
    }
}
