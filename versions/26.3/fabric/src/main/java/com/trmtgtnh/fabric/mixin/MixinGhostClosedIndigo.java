package com.trmtgtnh.fabric.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import com.trmtgtnh.client.render.SunkLighting;

/**
 * A ghost that holds no light is closed to light in Indigo's corner average, as it was before 26.3 (0.9.228; Xep,
 * 2026-10-26, of the full pass's blast photographs: "26.3 Fabric / Vanilla dev ... The craters look darker (more
 * shadows) than the other versions").
 *
 * <p>
 * Indigo's {@code AoCalculator.computeFace} asks each cell around a face whether it is clear, and a clear cell's light
 * goes into the corners' average as it is. From Fabric API 0.164 (fabric-renderer-indigo 9.1.24, 26.3) it asks
 * {@code BlockState.isLightPermeable()} - {@code !solidRender || lightDampening == 0} - where 9.1.4 (26.2) asked
 * {@code !isViewBlocking(level, pos) || lightDampening == 0}, which a whole ghost answered no to (read from both jars).
 * Every ghost occludes nothing, so every ghost is permeable by the new question, and a whole one's cell holds no light:
 * each crater wall and each floor beside one averaged a nought in, and drew at about seven tenths of 26.3 Forge's. So
 * each answer is given again through {@link SunkLighting#lightPermeable}, at the cell it was asked about - the search
 * square, or the block's own square for the last question, which asks the block being drawn.
 *
 * <p>
 * Optional, as the face hooks are, and named by its class name because Indigo is Fabric API's implementation and is
 * named nowhere at compile time: a Fabric API that has moved this costs a picture, never the game.
 */
@Mixin(targets = "net.fabricmc.fabric.impl.client.indigo.renderer.aocalc.AoCalculator")
public abstract class MixinGhostClosedIndigo {

    @Shadow
    private BlockAndTintGetter level;

    @Shadow
    private BlockState state;

    @Shadow
    private BlockPos pos;

    @Shadow
    @Final
    private BlockPos.MutableBlockPos searchPos;

    @Redirect(
        method = "computeFace(Lnet/fabricmc/fabric/impl/client/indigo/renderer/aocalc/AoFaceData;Lnet/minecraft/core/Direction;ZLnet/minecraft/core/Direction;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;isLightPermeable()Z"),
        require = 0)
    private boolean trmt$closeDarkGhost(BlockState asked) {
        boolean permeable = asked.isLightPermeable();
        if (!permeable || level == null || searchPos == null
            || !(asked.getBlock() instanceof com.trmtgtnh.block.BlockGhost)) return permeable;
        // Every question but the last asks the state at the search square; the last asks the block being drawn, at
        // its own square, while the search square holds the square in front of it.
        BlockPos at = asked == state && level.getBlockState(searchPos) != asked ? pos : searchPos;
        return SunkLighting.lightPermeable(permeable, asked, level, at);
    }
}
