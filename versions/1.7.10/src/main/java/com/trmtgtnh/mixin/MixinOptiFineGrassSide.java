package com.trmtgtnh.mixin;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.util.IIcon;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.trmtgtnh.block.BlockGhostGrass;
import com.trmtgtnh.block.GhostRendering;

/**
 * Lets OptiFine draw the fringe over a worn grass block's side.
 *
 * <p>
 * Vanilla draws grass's tinted fringe over any side whose texture is <em>named</em>
 * {@code grass_side}, and a worn grass wall is handed back under that name for exactly that reason.
 * OptiFine for 1.7.10 asks a different question. In its smooth-lighting method, and in the overlay
 * test of its flat-lit one, it compares the side against its own cached copy of vanilla's sprite,
 * {@code TextureUtils.iconGrassSide}, with {@code ==}. A wrapper that answers to the name is not that
 * object, so the fringe was never drawn: every worn grass wall under OptiFine came out bare earth.
 * Plain 1.7.10 and Angelica both draw it.
 *
 * <p>
 * So, as {@link MixinGrassTint} does with the {@code Blocks.grass} that the same methods compare
 * against, this substitutes the value the comparison reads. For a worn grass ghost it hands back the
 * stand-in that ghost's sides are drawn with, which is the same object the face was just drawn
 * with, so the comparison matches and the overlay is drawn - and thinned, by
 * {@link MixinGrassSideOverlay}, exactly as on vanilla. For anything else the original goes through.
 *
 * <p>
 * Found on 2026-10-07 by applying OptiFine's own patches to vanilla's RenderBlocks and reading the
 * result: OptiFine ships its renderer as binary deltas, not as classes. The comparisons it makes are
 * written down in docs/optifine-flank.md.
 */
@Mixin(RenderBlocks.class)
public class MixinOptiFineGrassSide {

    // Not required to apply, and on every renderer but OptiFine it never does: TextureUtils is
    // OptiFine's own class, so without it there is no such field to read and nothing to match.
    @ModifyExpressionValue(
        require = 0,
        // The smooth-lighting method reads the field four times, once per side face, and every one
        // is the overlay test. Better Grass is decided in a method of its own, which is left alone.
        method = "renderStandardBlockWithAmbientOcclusion(Lnet/minecraft/block/Block;IIIFFF)Z",
        at = @At(
            value = "FIELD",
            target = "LTextureUtils;iconGrassSide:Lnet/minecraft/util/IIcon;",
            opcode = Opcodes.GETSTATIC))
    private IIcon trmt$wornWallTakesItsFringe(IIcon original, Block block, int x, int y, int z, float red, float green,
        float blue) {
        return standIn(original, block, x, y, z);
    }

    // The flat-lit method reads it twice per face, and only the second is the overlay test. The
    // first is Better Grass asking whether to paint the side over with the top - and a worn wall
    // must not be told yes, or turning Better Grass on would grass over the wear it is showing.
    // Hence the four odd reads by number: OptiFine for 1.7.10 ended at HD_U_E7, so the order is
    // fixed, and getting it wrong would only cost the fringe in flat lighting.
    @ModifyExpressionValue(
        require = 0,
        method = "renderStandardBlockWithColorMultiplier(Lnet/minecraft/block/Block;IIIFFF)Z",
        at = {
            @At(
                value = "FIELD",
                target = "LTextureUtils;iconGrassSide:Lnet/minecraft/util/IIcon;",
                opcode = Opcodes.GETSTATIC,
                ordinal = 1),
            @At(
                value = "FIELD",
                target = "LTextureUtils;iconGrassSide:Lnet/minecraft/util/IIcon;",
                opcode = Opcodes.GETSTATIC,
                ordinal = 3),
            @At(
                value = "FIELD",
                target = "LTextureUtils;iconGrassSide:Lnet/minecraft/util/IIcon;",
                opcode = Opcodes.GETSTATIC,
                ordinal = 5),
            @At(
                value = "FIELD",
                target = "LTextureUtils;iconGrassSide:Lnet/minecraft/util/IIcon;",
                opcode = Opcodes.GETSTATIC,
                ordinal = 7) })
    private IIcon trmt$wornWallTakesItsFringeFlat(IIcon original, Block block, int x, int y, int z, float red,
        float green, float blue) {
        return standIn(original, block, x, y, z);
    }

    private IIcon standIn(IIcon original, Block block, int x, int y, int z) {
        // The concrete grass class, as MixinGrassTint checks: only grass ghosts are drawn as grass.
        if (!(block instanceof BlockGhostGrass)) return original;
        IIcon standIn = GhostRendering
            .grassSideStandIn((BlockGhostGrass) block, ((RenderBlocks) (Object) this).blockAccess, x, y, z);
        return standIn != null ? standIn : original;
    }
}
