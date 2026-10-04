package com.trmtgtnh.mixin;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.util.IIcon;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.trmtgtnh.block.BlockGhostGrass;
import com.trmtgtnh.block.GhostRendering;

/**
 * Lets a worn grass block's side fringe recede toward dirt as its top wears.
 *
 * <p>
 * A grass block's sides are drawn in two parts: the base, left untinted, and a separately tinted
 * green fringe painted over it from {@link net.minecraft.block.BlockGrass#getIconSideOverlay()}.
 * {@link MixinGrassTint} already keeps the base untinted for a ghost; this handles the fringe,
 * which is the only green left once the base is a de-greened earth wall. Substituting a thinned
 * copy of that overlay is what makes the fringe shrink upward instead of hanging on at full
 * length while the top of the block goes bald.
 *
 * <p>
 * It modifies the value the renderer reads rather than the pixels it draws, so it composes with
 * anything else on the same call and never touches a real grass block or another mod's turf -
 * {@link GhostRendering#grassSideOverlay} hands the original straight back for all of those, and
 * for a ghost once the feature is switched off.
 */
@Mixin(RenderBlocks.class)
public class MixinGrassSideOverlay {

    // Not required to apply, for the same reason MixinGrassTint is not: a renderer update that
    // moved this call would only cost worn grass its receding fringe, which is not worth refusing
    // to start a large pack over.
    @ModifyExpressionValue(
        require = 0,
        // All three, because vanilla draws a block through whichever matches the lighting: with
        // smooth lighting on, which is the default, a grass side goes through the two ambient
        // occlusion methods and never reaches the third. Hooking only that one left worn turf on a
        // plain client with an earth wall and vanilla's full-height fringe laid over it.
        method = { "renderStandardBlockWithAmbientOcclusion(Lnet/minecraft/block/Block;IIIFFF)Z",
            "renderStandardBlockWithAmbientOcclusionPartial(Lnet/minecraft/block/Block;IIIFFF)Z",
            "renderStandardBlockWithColorMultiplier(Lnet/minecraft/block/Block;IIIFFF)Z" },
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/block/BlockGrass;getIconSideOverlay()Lnet/minecraft/util/IIcon;"))
    private IIcon trmt$recedingFringe(IIcon original, Block block, int x, int y, int z, float red, float green,
        float blue) {
        if (!(block instanceof BlockGhostGrass)) return original;
        return GhostRendering.grassSideOverlay((BlockGhostGrass) block, x, y, z, original);
    }
}
