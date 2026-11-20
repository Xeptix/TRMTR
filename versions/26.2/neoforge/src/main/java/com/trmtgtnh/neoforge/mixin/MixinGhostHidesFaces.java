package com.trmtgtnh.neoforge.mixin;

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
 * the faces - under Sodium or OptiFine it answers nothing (Xep, 2026-11-09: "Keep, vanilla only").
 *
 * <p>
 * At the head of NeoForge's positional {@code Block.shouldRenderFace(level, pos, state, neighbour, face)}, which its
 * {@code ModelBlockRenderer} asks once its {@code cull} says faces are culled (read from NeoForge's patched jar with
 * {@code tools/mcsource.py}), the same seat {@code MixinSettledFaces} hands to {@code FaceRule}: before vanilla's
 * per-state {@code OCCLUSION_CACHE} is reached, so the per-square answer is never stored in it. NeoForge's own patch
 * there takes a block's {@code hidesNeighborFace} only together with the face's own block's
 * {@code supportsExternalFaceHiding}, so the answer is asked here, for every face, and the ghost declares no
 * {@code hidesNeighborFace}: Sodium for NeoForge asks that one ({@code NeoForgeBlockAccess}), and the hide is not its.
 * Only ever hides a face; where the square does not hide it, the test runs as it always did. The Forge and Fabric
 * modules hook their own {@code ModelBlockRenderer}.
 */
@Mixin(Block.class)
public abstract class MixinGhostHidesFaces {

    // Optional, as every cosmetic hook here is: its loss costs the saving and draws what was drawn before.
    @Inject(method = "shouldRenderFace(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;"
        + "Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;"
        + "Lnet/minecraft/core/Direction;)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private static void trmt$hideFaceAgainstGhost(BlockGetter level, BlockPos pos, BlockState state, BlockState neighbour,
        Direction face, CallbackInfoReturnable<Boolean> callback) {
        if (com.trmtgtnh.client.render.GhostHide.hiddenToward(state, level, pos, face)) {
            callback.setReturnValue(Boolean.FALSE);
        }
    }
}
