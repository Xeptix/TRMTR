package com.trmtgtnh.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

import com.trmtgtnh.block.BlockGhostStair;

/**
 * A worn stair lets the light round a face's corner through, as the real stair does (1.0.0, 26.3).
 *
 * <p>
 * <strong>Why this exists from 26.3.</strong> 26.3's smooth lighting asks each cell round a face's corner
 * {@code isLightPermeable()} - the game's own {@code BlockModelLighter.prepareQuadAmbientOcclusion} four times, and
 * Indigo's {@code AoCalculator.computeFace} (fabric-renderer-indigo 9.1.24) ten, read from their jars - where 26.1.2
 * and 26.2 asked {@code isViewBlocking(level, pos)}, which a worn stair answers at its square. A state answers this
 * one alone, {@code !solidRender || getLightDampening() == 0}, both kept at no square, and the worn stair's ghost keeps
 * the whole cell as its shape and fifteen as the light it stops (BlockGhost's per-state answers), so every worn stair
 * shaded the faces beside it as a solid cube would: the through yard's worn stairs drew about two luma darker on 26.3
 * Fabric and NeoForge without Sodium than on 26.2, where the same real stairs draw alike (the stairlight part,
 * 2026-11-19). The asker holds no square to ask with, and none is needed: the worn stair's ghost is painted only over a
 * stair (BlockGhost.litState), and no stair is a solid cube at its square ({@link BlockGhostStair#solidRenderAt}), so
 * light passes it everywhere, as it passes the real stair. Every other state keeps the game's answer.
 *
 * <p>
 * The harness logs that a worn stair was asked here, and fails a run without Sodium that drew the through yard and
 * never asked one.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class MixinWornStairAoVanilla {

    @Shadow
    public abstract Block getBlock();

    @Inject(method = "isLightPermeable", at = @At("HEAD"), cancellable = true)
    private void trmt$wornStairLetsLightThrough(CallbackInfoReturnable<Boolean> callback) {
        if (!(getBlock() instanceof BlockGhostStair)) return;
        BlockGhostStair.noteLitByVanilla();
        callback.setReturnValue(Boolean.TRUE);
    }
}
