package com.trmtgtnh.fabric.mixin;

import java.util.List;

import net.minecraft.client.renderer.texture.atlas.SpriteResourceLoader;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The sources an atlas's {@code atlases/<name>.json} names, which vanilla keeps private and lists only into sprites.
 * MixinSpriteLoader runs them for their names alone, to measure the block atlas before this mod plans. See
 * {@code WearTextures.beginBlockStitch}.
 */
@Mixin(SpriteResourceLoader.class)
public interface SpriteResourceLoaderSources {

    @Accessor("sources")
    List<SpriteSource> trmt$sources();
}
