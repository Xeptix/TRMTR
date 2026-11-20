package com.trmtgtnh.fabric.mixin;

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
 * the faces - under Sodium it answers nothing (Xep, 2026-11-09: "Keep, vanilla only").
 *
 * <p>
 * From 1.21.2 {@code Block.shouldRenderFace} is handed the two states alone, so the hide is asked at the head of the
 * renderer's own private {@code ModelBlockRenderer.shouldRenderFace(level, state, face, neighbourPos)}, which holds
 * the chunk's view and the neighbour's position (read from the game's jar with {@code tools/mcsource.py}), the same
 * seat {@code MixinSettledFaces} hands to {@code FaceRule}; and only when the renderer culls at all ({@code cull}), as
 * its own first line asks. Under Fabric API a chunk's blocks are drawn by Indigo, whose own face cull
 * ({@code AltModelBlockRendererImpl.shouldCullFace}) asks the two-state test; the hide is asked there in
 * {@code MixinSettledFacesIndigo}'s redirect, since a second redirect of one call would never apply. Before vanilla's
 * per-state {@code OCCLUSION_CACHE} is reached, so the per-square answer is never stored in it. Only ever hides a
 * face; where the square does not hide it, the test runs as it always did. The Forge module has the same class.
 */
@Mixin(ModelBlockRenderer.class)
public abstract class MixinGhostHidesFaces {

    @Shadow
    @Final
    private boolean cull;

    // Optional, as every cosmetic hook here is: its loss costs the saving and draws what was drawn before.
    @Inject(
        method = "shouldRenderFace(Lnet/minecraft/client/renderer/block/BlockAndTintGetter;"
            + "Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;Lnet/minecraft/core/BlockPos;)Z",
        at = @At("HEAD"),
        cancellable = true,
        require = 0)
    private void trmt$hideFaceAgainstGhost(BlockAndTintGetter level, BlockState state, Direction face, BlockPos neighbour,
        CallbackInfoReturnable<Boolean> callback) {
        if (this.cull && face != null && neighbour != null
            && com.trmtgtnh.client.render.GhostHide.hidden(state, level, neighbour.relative(face.getOpposite()), face, neighbour)) {
            callback.setReturnValue(Boolean.FALSE);
        }
    }
}
