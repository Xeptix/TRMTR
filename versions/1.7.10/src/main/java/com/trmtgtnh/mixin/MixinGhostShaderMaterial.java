package com.trmtgtnh.mixin;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.trmtgtnh.block.GhostBlock;
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
}
