package com.trmtgtnh.fabric.mixin;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import com.mojang.blaze3d.platform.NativeImage;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.trmtgtnh.client.texture.SpriteImages;

/** A loaded sprite's own images, for the atlas check (WP30, 0.9.224): see SpriteImages. */
@Mixin(TextureAtlasSprite.class)
public abstract class MixinSpriteImages implements SpriteImages {

    @Shadow
    @Final
    protected NativeImage[] mainImage;

    @Override
    public NativeImage[] trmt$images() {
        return mainImage;
    }
}
