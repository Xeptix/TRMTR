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
 * At the head of the positional {@code Block.shouldRenderFace}, before vanilla's per-state {@code OCCLUSION_CACHE}
 * is reached, so the per-square answer is never stored in it. Forge's own patch there asks a block's
 * {@code hidesNeighborFace} only where the face's own block says {@code supportsExternalFaceHiding}, and
 * {@code IForgeBlock}'s default is no for every block in the solid pass ({@code ForgeHooksClient.isBlockInSolidLayer},
 * read from Forge 52.1.19): stone and dirt beside a worn road would never ask. So the answer is asked here, for every
 * face, and the ghost declares no {@code hidesNeighborFace}. Only ever hides a face; where the square does not hide it,
 * the test runs as it always did. OptiFine draws through a face test of its own that asks neither
 * ({@code BlockUtils.shouldSideBeRendered}), and under it the hide answers nothing in any case. The Fabric and NeoForge
 * modules have the same class.
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
