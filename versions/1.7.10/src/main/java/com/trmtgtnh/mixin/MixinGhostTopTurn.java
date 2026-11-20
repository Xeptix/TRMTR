package com.trmtgtnh.mixin;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.util.IIcon;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.trmtgtnh.block.GhostBlock;
import com.trmtgtnh.block.GhostRendering;

/**
 * Turns a worn block's top a quarter at a time with its position (0.9.229; Xep, 2026-11-16, "Turn and re-seed",
 * "Build it": docs/goals/0.9.229-rotation-spec.md).
 *
 * <p>
 * The turn is the UV turn vanilla already has for the top face, {@code uvRotateTop}, which renderFaceYPos reads to lay
 * the picture a quarter, a half or three quarters round (the log's top, a piston's face). It is set here for the one
 * face it is wanted on - a ghost's top drawing a worn picture whose set turns - and put back as the face is drawn, so
 * nothing else the renderer draws sees it. The whole face picture turns, the block's own texture and its wear
 * together, which is the only turn that reads right: the wear follows the stones and cannot turn on its own.
 *
 * <p>
 * Every mesher draws the top through this one method: vanilla's chunk builder, and Angelica and Celeritas, which call
 * {@code renderBlockByRenderType} on a {@code RenderBlocks} of their own and keep what it writes (GhostBoxes,
 * MixinSettleOnWornGround). Each builds one renderer per meshing task, so the value held between the two injections
 * never crosses threads. Not required: a renderer that replaced the method would draw worn tops unturned, a blemish,
 * where a required injection would refuse to start the pack.
 */
@Mixin(RenderBlocks.class)
public class MixinGhostTopTurn {

    @Shadow
    public int uvRotateTop;

    /** The uvRotateTop the face found, while it is drawn turned; -1 while nothing is held. */
    @Unique
    private int trmt$heldTop = -1;

    @Inject(method = "renderFaceYPos", at = @At("HEAD"), require = 0)
    private void trmt$turnTop(Block block, double x, double y, double z, IIcon icon, CallbackInfo info) {
        if (!(block instanceof GhostBlock)) return;
        int turn = GhostRendering.uvRotateTop(icon, (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
        if (turn == 0) return;
        trmt$heldTop = uvRotateTop;
        uvRotateTop = turn;
    }

    @Inject(method = "renderFaceYPos", at = @At("RETURN"), require = 0)
    private void trmt$putBack(Block block, double x, double y, double z, IIcon icon, CallbackInfo info) {
        if (trmt$heldTop < 0) return;
        uvRotateTop = trmt$heldTop;
        trmt$heldTop = -1;
    }
}
