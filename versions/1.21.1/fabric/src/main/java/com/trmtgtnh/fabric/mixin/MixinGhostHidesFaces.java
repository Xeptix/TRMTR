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
 * ({@code BlockRenderInfo.shouldDrawFace}, read from Fabric API 0.116.17's Indigo) both ask: before vanilla's
 * per-state {@code OCCLUSION_CACHE} is reached, so the per-square answer is never stored in it. Fabric has no API for
 * this, so the hook is the only seat here. Only ever hides a face; where the square does not hide it, vanilla's test
 * runs as it always did. The NeoForge and Forge modules have the same class.
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
