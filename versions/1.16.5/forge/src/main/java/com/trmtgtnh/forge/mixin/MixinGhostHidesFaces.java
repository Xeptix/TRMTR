package com.trmtgtnh.forge.mixin;

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
 * At the head of {@code Block.shouldRenderFace}, before vanilla's per-state {@code OCCLUSION_CACHE} is reached, so the
 * per-square answer is never stored in it. Forge 36.2.34 has no {@code hidesNeighborFace} (its patched test asks only
 * vanilla's {@code skipRendering}, {@code canOcclude} and the two face shapes), so this is the only seat. At 1.16.5 the
 * test is handed no neighbour's position, so the hide is asked of the square across the face
 * ({@code GhostHide.hiddenToward}). Only ever hides a face; where the square does not hide it, the test runs as it
 * always did. Embeddium asks its own {@code shouldDrawSide} and OptiFine its own {@code BlockUtils}, neither of which
 * reaches this, and under both the hide answers nothing in any case. The Fabric module has the same class.
 */
@Mixin(Block.class)
public abstract class MixinGhostHidesFaces {

    // Optional, as every cosmetic hook here is: its loss costs the saving and draws what was drawn before.
    @Inject(method = "shouldRenderFace", at = @At("HEAD"), cancellable = true, require = 0)
    private static void trmt$hideFaceAgainstGhost(BlockState state, BlockGetter level, BlockPos pos, Direction face,
        CallbackInfoReturnable<Boolean> callback) {
        if (com.trmtgtnh.client.render.GhostHide.hiddenToward(state, level, pos, face)) {
            callback.setReturnValue(Boolean.FALSE);
        }
    }
}
