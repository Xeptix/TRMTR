package com.trmtgtnh.forge.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Hides a face against an unsunk worn square, as against the block it stands in for (0.9.229, spec GF1) - see
 * {@code client.render.GhostHide}: on (off with {@code -Dtrmt.gf1.hide=false}), and only while vanilla's mesher draws
 * the faces - under Sodium or OptiFine it answers nothing (Xep, 2026-11-09: "Keep, vanilla only").
 *
 * <p>
 * From 1.21.2 {@code Block.shouldRenderFace} is handed the two states alone, so the hide is asked at the head of the
 * renderer's own private {@code ModelBlockRenderer.shouldRenderFace(level, state, face, neighbourPos)}, which holds
 * the chunk's view and the neighbour's position (read from Forge's jar with {@code tools/mcsource.py}), the same seat
 * {@code MixinSettledFaces} hands to {@code FaceRule}; and only when the renderer culls at all ({@code cull}), as its
 * own first line asks. Before vanilla's per-state {@code OCCLUSION_CACHE} is reached, so the per-square answer is never
 * stored in it. Only ever hides a face; where the square does not hide it, the test runs as it always did. The Fabric
 * module has the same class.
 */
@Mixin(ModelBlockRenderer.class)
public abstract class MixinGhostHidesFaces {

    @Shadow
    @Final
    private boolean cull;

    // Optional, as every cosmetic hook here is: its loss costs the saving and draws what was drawn before.
    @Inject(method = "shouldRenderFace", at = @At("HEAD"), cancellable = true, require = 0)
    private void trmt$hideFaceAgainstGhost(BlockAndTintGetter level, BlockState state, Direction face, BlockPos neighbour,
        CallbackInfoReturnable<Boolean> callback) {
        if (this.cull && face != null && neighbour != null
            && com.trmtgtnh.client.render.GhostHide.hidden(state, level, neighbour.relative(face.getOpposite()), face, neighbour)) {
            callback.setReturnValue(Boolean.FALSE);
        }
    }
}
