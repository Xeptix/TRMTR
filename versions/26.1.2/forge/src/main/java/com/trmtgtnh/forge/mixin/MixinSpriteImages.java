package com.trmtgtnh.forge.mixin;

import com.mojang.blaze3d.platform.NativeImage;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.trmtgtnh.client.texture.SpriteImages;

/** A loaded sprite's own images, for the atlas check (WP30, 0.9.224): see SpriteImages. */
// From 1.19.3 a sprite's images are its contents', as byMipLevel, where 1.16.5 kept them as mainImage.
@Mixin(net.minecraft.client.renderer.texture.SpriteContents.class)
public abstract class MixinSpriteImages implements SpriteImages {

    @Shadow
    NativeImage[] byMipLevel;

    @Override
    public NativeImage[] trmt$images() {
        return byMipLevel;
    }
}
