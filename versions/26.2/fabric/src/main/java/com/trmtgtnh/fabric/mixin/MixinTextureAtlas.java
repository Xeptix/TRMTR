package com.trmtgtnh.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlas;

import com.trmtgtnh.client.texture.WearTextures;

/**
 * Where this mod's wear sprites resolve into the block atlas's own, under Fabric.
 *
 * <p>
 * Forge fires an event as an atlas takes its stitch and needs no mixin for this (ForgeTextures). Fabric offers none at
 * this version, so the moment is taken from vanilla's own method: the end of {@code upload}, which from 1.19.3 is where
 * an atlas takes what its loader stitched - 1.16.5's {@code reload}. The plan and the count are MixinSpriteLoader's.
 *
 * <p>
 * Only the block atlas: the game builds several, and none of the others is this mod's business.
 */
@Mixin(TextureAtlas.class)
public abstract class MixinTextureAtlas {

    @Inject(method = "upload", at = @At("TAIL"))
    private void trmt$resolveWearSprites(SpriteLoader.Preparations prepared, CallbackInfo callback) {
        if (!TextureAtlas.LOCATION_BLOCKS.equals(((TextureAtlas) (Object) this).location())) return;
        WearTextures.endStitch((TextureAtlas) (Object) this);
    }
}
