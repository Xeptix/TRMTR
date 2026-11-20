package com.trmtgtnh.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Hides a face against an unsunk worn square, as against the block it stands in for (0.9.229, spec GF1) - see
 * {@code client.render.GhostHide}: on (off with {@code -Dtrmt.gf1.hide=false}), and only while vanilla's mesher draws
 * the faces - under Sodium, Embeddium, Rubidium, Canvas or OptiFine it answers nothing (Xep, 2026-11-09: "Keep,
 * vanilla only").
 *
 * <p>
 * At the head of the positional {@code Block.shouldRenderFace}, the face test vanilla's mesher and Indigo's
 * ({@code BlockRenderInfo.shouldDrawFace}, read from Fabric API 0.92.12's Indigo) both ask: before vanilla's
 * per-state {@code OCCLUSION_CACHE} is reached, so the per-square answer is never stored in it. Fabric has no API for
 * this, so the hook is the only seat here. Indium (Sodium's renderer for Fabric API's models, 1.0.36) and Canvas
 * (20.0.2625) ask this same test from their own meshers (read from their jars, {@code tools/gf1_renderer_calls.py}),
 * so the hide's own gate, {@code GhostHide.active}, is what keeps it off there. Only ever hides a face; where the square
 * does not hide it, vanilla's test runs as it always did. The Forge module has the same class.
 */
@Mixin(Block.class)
public abstract class MixinGhostHidesFaces {

    // Optional, as every cosmetic hook here is: its loss costs the saving and draws what was drawn before.
    @Inject(method = "shouldRenderFace", at = @At("HEAD"), cancellable = true, require = 0)
    private static void trmt$hideFaceAgainstGhost(BlockState state, BlockGetter level, BlockPos pos, Direction face,
        BlockPos neighbour, CallbackInfoReturnable<Boolean> callback) {
        if (com.trmtgtnh.client.render.GhostHide.hidden(state, level, pos, face, neighbour)) {
            callback.setReturnValue(Boolean.FALSE);
        }
    }
}
