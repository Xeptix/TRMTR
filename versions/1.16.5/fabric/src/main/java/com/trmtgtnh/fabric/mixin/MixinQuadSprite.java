package com.trmtgtnh.fabric.mixin;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.trmtgtnh.client.texture.QuadSprite;

/** The sprite a baked quad draws, for the atlas check (WP30, 0.9.224): see QuadSprite. */
@Mixin(BakedQuad.class)
public abstract class MixinQuadSprite implements QuadSprite {

    @Shadow
    @Final
    protected TextureAtlasSprite sprite;

    @Override
    public TextureAtlasSprite trmt$quadSprite() {
        return sprite;
    }
}
