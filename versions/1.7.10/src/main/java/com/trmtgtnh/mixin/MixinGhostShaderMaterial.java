package com.trmtgtnh.mixin;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.util.IIcon;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.trmtgtnh.apiload.ApiDraw;
import com.trmtgtnh.block.GhostBlock;
import com.trmtgtnh.client.render.GhostBoxes;
import com.trmtgtnh.client.render.GhostGlow;
import com.trmtgtnh.client.render.ShaderMaterial;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Tells a shader pack what worn ground is made of, one block before it is drawn.
 *
 * <p>
 * The same seam the settling shift uses, and for the same reason: every render type passes through
 * here, and it is the last moment before anything is emitted. What is said here holds for the
 * vertices that follow, which is exactly the span wanted.
 *
 * <p>
 * Nothing is put back at the end of the method. It does not need to be, because the next block
 * through says its own piece first - and a flag remembers whether there is anything to take back
 * at all, so a chunk with no worn ground in it pays one field read per block rather than a
 * reflective call. Anything left standing at the end of a section is corrected by the first block
 * of the next one.
 */
@SideOnly(Side.CLIENT)
@Mixin(RenderBlocks.class)
public abstract class MixinGhostShaderMaterial {

    @Unique
    private boolean trmt$claimed;

    @Inject(
        method = "renderBlockByRenderType(Lnet/minecraft/block/Block;III)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/RenderBlocks;setRenderBoundsFromBlock(Lnet/minecraft/block/Block;)V",
            shift = At.Shift.AFTER),
        require = 0)
    private void trmt$claimShaderMaterial(Block block, int x, int y, int z, CallbackInfoReturnable<Boolean> cir) {
        if (block instanceof GhostBlock) {
            ShaderMaterial.claim((GhostBlock) block, x, y, z);
            trmt$claimed = true;
        } else if (trmt$claimed) {
            ShaderMaterial.claim(null, x, y, z);
            trmt$claimed = false;
        }
    }

    /**
     * The render hook (W3 T8): a ghost over a block a plugin draws is handed to it, and the standard renderer is
     * skipped
     * when it drew. In this class and after the claim above, at the same seam, because injectors of one class run in
     * the order they are written and a cancel stops the ones after it: the shader pack must still be told what this
     * ground is before the hook's faces are emitted. The bounds are already stamped here, which is what a sunk ghost's
     * hook is drawn within.
     */
    @Inject(
        method = "renderBlockByRenderType(Lnet/minecraft/block/Block;III)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/RenderBlocks;setRenderBoundsFromBlock(Lnet/minecraft/block/Block;)V",
            shift = At.Shift.AFTER),
        cancellable = true,
        require = 0)
    private void trmt$wearRenderer(Block block, int x, int y, int z, CallbackInfoReturnable<Boolean> cir) {
        if (block instanceof GhostBlock && ApiDraw.drawn((RenderBlocks) (Object) this, (GhostBlock) block, x, y, z)) {
            cir.setReturnValue(true);
        }
    }

    /**
     * A data file's boxes (W3 T6): a ghost over a block with boxes is drawn box by box, and the standard renderer's one
     * box skipped. After the render hook, so a plugin that draws its own block still draws it, and after the claim, so
     * the shader pack is told first; a cancel above stops this one, which is the order wanted.
     */
    @Inject(
        method = "renderBlockByRenderType(Lnet/minecraft/block/Block;III)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/RenderBlocks;setRenderBoundsFromBlock(Lnet/minecraft/block/Block;)V",
            shift = At.Shift.AFTER),
        cancellable = true,
        require = 0)
    private void trmt$dataBoxes(Block block, int x, int y, int z, CallbackInfoReturnable<Boolean> cir) {
        if (block instanceof GhostBlock
            && GhostBoxes.drawn((RenderBlocks) (Object) this, (GhostBlock) block, x, y, z)) {
            cir.setReturnValue(true);
        }
    }

    // Real glow (Xep, 2026-11-15): each face a ghost is drawn with is followed by its glow, as the face method returns,
    // so the standard renderer, the render hook and a data file's boxes all draw it, and so does any mesher that draws
    // through these methods. Within the claim above, so a shader pack is told the glow is the same ground as its face.
    // Without a requirement, as every renderer detail here is: a renderer that moved these costs the glow and no more.
    // See GhostGlow.

    @Inject(
        method = "renderFaceYNeg(Lnet/minecraft/block/Block;DDDLnet/minecraft/util/IIcon;)V",
        at = @At("RETURN"),
        require = 0)
    private void trmt$glowDown(Block block, double x, double y, double z, IIcon icon, CallbackInfo ci) {
        GhostGlow.afterFace((RenderBlocks) (Object) this, block, 0, x, y, z, icon);
    }

    @Inject(
        method = "renderFaceYPos(Lnet/minecraft/block/Block;DDDLnet/minecraft/util/IIcon;)V",
        at = @At("RETURN"),
        require = 0)
    private void trmt$glowUp(Block block, double x, double y, double z, IIcon icon, CallbackInfo ci) {
        GhostGlow.afterFace((RenderBlocks) (Object) this, block, 1, x, y, z, icon);
    }

    @Inject(
        method = "renderFaceZNeg(Lnet/minecraft/block/Block;DDDLnet/minecraft/util/IIcon;)V",
        at = @At("RETURN"),
        require = 0)
    private void trmt$glowNorth(Block block, double x, double y, double z, IIcon icon, CallbackInfo ci) {
        GhostGlow.afterFace((RenderBlocks) (Object) this, block, 2, x, y, z, icon);
    }

    @Inject(
        method = "renderFaceZPos(Lnet/minecraft/block/Block;DDDLnet/minecraft/util/IIcon;)V",
        at = @At("RETURN"),
        require = 0)
    private void trmt$glowSouth(Block block, double x, double y, double z, IIcon icon, CallbackInfo ci) {
        GhostGlow.afterFace((RenderBlocks) (Object) this, block, 3, x, y, z, icon);
    }

    @Inject(
        method = "renderFaceXNeg(Lnet/minecraft/block/Block;DDDLnet/minecraft/util/IIcon;)V",
        at = @At("RETURN"),
        require = 0)
    private void trmt$glowWest(Block block, double x, double y, double z, IIcon icon, CallbackInfo ci) {
        GhostGlow.afterFace((RenderBlocks) (Object) this, block, 4, x, y, z, icon);
    }

    @Inject(
        method = "renderFaceXPos(Lnet/minecraft/block/Block;DDDLnet/minecraft/util/IIcon;)V",
        at = @At("RETURN"),
        require = 0)
    private void trmt$glowEast(Block block, double x, double y, double z, IIcon icon, CallbackInfo ci) {
        GhostGlow.afterFace((RenderBlocks) (Object) this, block, 5, x, y, z, icon);
    }
}
