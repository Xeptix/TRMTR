package com.trmtgtnh.mixin;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.trmtgtnh.block.GhostBlock;
import com.trmtgtnh.block.GhostRendering;
import com.trmtgtnh.client.render.SunkLighting;
import com.trmtgtnh.client.texture.SunkLight;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * A sunk top lit by its height under vanilla's smooth lighting, as Forge's light pipeline lights it (0.9.223; Xep,
 * 2026-10-11: "On the bottom 3 images, I prefer how the middle one looks the most, honestly").
 *
 * <p>
 * Vanilla's smooth lighting takes the light and shade of a top face that is not flush with its block's top from
 * inside that block - {@code if (this.renderMaxY >= 1.0D) ++y} is the only thing that moves it up - so every corner of
 * a sunk square was shaded by the blocks at its own height, the ground beside it, and a shallow rut drew as dark as the
 * deepest. Just before the top face is drawn, this replaces its four corners' brightness and color with what
 * {@link SunkLight} works out for them, which is Forge's lighter's arithmetic, and leaves every other face, and every
 * square that has not sunk, exactly as vanilla lit it. The color keeps vanilla's own multiplier - the top face's,
 * passed in - times the new shade, as vanilla multiplies its own.
 *
 * <p>
 * Without a requirement, as the other renderer details here are, so a renderer that does not draw through these
 * methods loses only this; {@link SunkLighting#litCount} says whether it ran, and the harness logs it.
 */
@SideOnly(Side.CLIENT)
@Mixin(RenderBlocks.class)
public abstract class MixinSunkLight {

    @Inject(
        method = { "renderStandardBlockWithAmbientOcclusion(Lnet/minecraft/block/Block;IIIFFF)Z",
            "renderStandardBlockWithAmbientOcclusionPartial(Lnet/minecraft/block/Block;IIIFFF)Z" },
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/RenderBlocks;renderFaceYPos(Lnet/minecraft/block/Block;DDDLnet/minecraft/util/IIcon;)V"),
        require = 0)
    private void trmt$lightSunkTop(Block block, int x, int y, int z, float red, float green, float blue,
        CallbackInfoReturnable<Boolean> cir) {
        RenderBlocks self = (RenderBlocks) (Object) this;
        if (!(block instanceof GhostBlock) || self.renderMaxY >= 1.0D || self.blockAccess == null) return;
        if (!GhostRendering.litAsSunk((GhostBlock) block, x, y, z)) return;
        float[][] corners = SunkLighting.topCorners(self.blockAccess, block, x, y, z, (float) self.renderMaxY);
        self.brightnessTopLeft = SunkLight.packed(corners[0][0], corners[0][1]);
        self.brightnessBottomLeft = SunkLight.packed(corners[1][0], corners[1][1]);
        self.brightnessBottomRight = SunkLight.packed(corners[2][0], corners[2][1]);
        self.brightnessTopRight = SunkLight.packed(corners[3][0], corners[3][1]);
        self.colorRedTopLeft = red * corners[0][2];
        self.colorGreenTopLeft = green * corners[0][2];
        self.colorBlueTopLeft = blue * corners[0][2];
        self.colorRedBottomLeft = red * corners[1][2];
        self.colorGreenBottomLeft = green * corners[1][2];
        self.colorBlueBottomLeft = blue * corners[1][2];
        self.colorRedBottomRight = red * corners[2][2];
        self.colorGreenBottomRight = green * corners[2][2];
        self.colorBlueBottomRight = blue * corners[2][2];
        self.colorRedTopRight = red * corners[3][2];
        self.colorGreenTopRight = green * corners[3][2];
        self.colorBlueTopRight = blue * corners[3][2];
        SunkLighting.noteLit();
    }
}
