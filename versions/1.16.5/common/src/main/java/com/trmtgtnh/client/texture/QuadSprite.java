package com.trmtgtnh.client.texture;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * The sprite a baked quad draws (WP30, 0.9.224): the 1.16 counterpart of the icon 1.7.10 asks a block for. Every
 * BakedQuad answers it, through a mixin in each loader's module (MixinQuadSprite); Forge has a getter of its own,
 * which common code cannot call.
 */
public interface QuadSprite {

    TextureAtlasSprite trmt$quadSprite();
}
