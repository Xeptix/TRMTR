package com.trmtgtnh.client.texture;

import com.mojang.blaze3d.platform.NativeImage;

/**
 * A loaded sprite's own images, one per mip level with every frame on level nought (WP30, 0.9.224): what the game
 * keeps after upload for its animation, and what the atlas check reads a face from and writes a recomposed picture
 * into. Every SpriteContents answers it - a sprite's images are its contents' from 1.19.3 - through a mixin in each
 * loader's module (MixinSpriteImages): a mixin that
 * names a field writes that name into a refmap, and the common module's refmap is in Fabric's names, which Forge will
 * not read.
 */
public interface SpriteImages {

    NativeImage[] trmt$images();
}
