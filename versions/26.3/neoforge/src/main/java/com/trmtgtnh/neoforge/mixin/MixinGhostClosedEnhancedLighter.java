package com.trmtgtnh.neoforge.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import com.trmtgtnh.client.render.SunkLighting;

/**
 * A ghost that holds no light is closed to light in NeoForge's enhanced corner average, as it was before 26.3 (0.9.228;
 * Xep, 2026-10-26, of the full pass's blast photographs: "26.3 NeoForge / Vanilla dev ... The craters look darker (more
 * shadows) than the other versions").
 *
 * <p>
 * NeoForge draws blocks with its {@code EnhancedBlockModelLighter}, whose {@code FullFaceCalculator.calculateFaceUncached}
 * asks each cell around a face whether it is clear; with the lightmap blending fix on, as it is unless a switch turns it
 * off, the clear cells' least light lifts the others, so a clear cell with none lifts nothing and is averaged in as
 * nought. From NeoForge 26.3 it asks {@code BlockState.isLightPermeable()} - {@code !solidRender || lightDampening == 0}
 * - where 26.2's asked {@code !isViewBlocking(level, pos) || lightDampening == 0}, which a whole ghost answered no to
 * (read from neoforge 26.2.0.57 and 26.3.0.76). Every ghost occludes nothing, so every ghost is permeable by the new
 * question, and a whole one's cell holds no light: each crater wall and each floor beside one drew at about seven
 * tenths of 26.3 Forge's, which draws with vanilla's lighter. So each answer is given again through
 * {@link SunkLighting#lightPermeable}, at the cell it was asked about - the scratch square for the sides and corners,
 * the sample square for the last question, which asks the cell the face looks out of.
 *
 * <p>
 * {@code require = 0}: a NeoForge that has moved this costs a picture, never the game.
 */
@Mixin(targets = "net.neoforged.neoforge.client.model.ao.FullFaceCalculator")
public abstract class MixinGhostClosedEnhancedLighter {

    @Shadow
    @Final
    BlockPos.MutableBlockPos scratchPos;

    @WrapOperation(
        method = "calculateFaceUncached",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;isLightPermeable()Z"),
        require = 0)
    private boolean trmt$closeDarkGhost(BlockState asked, Operation<Boolean> original,
        @Local(argsOnly = true) BlockAndTintGetter level, @Local(argsOnly = true) BlockPos renderedPos,
        @Local(argsOnly = true, ordinal = 0) Direction direction, @Local(argsOnly = true) boolean sampleOutside) {
        boolean permeable = original.call(asked);
        if (!permeable || level == null || scratchPos == null
            || !(asked.getBlock() instanceof com.trmtgtnh.block.BlockGhost)) return permeable;
        // The sides and corners are asked at the scratch square; the last question asks the sample square, while the
        // scratch square still holds the last corner.
        BlockPos at = level.getBlockState(scratchPos) == asked ? scratchPos
            : sampleOutside ? renderedPos.relative(direction) : renderedPos;
        return SunkLighting.lightPermeable(permeable, asked, level, at);
    }
}
