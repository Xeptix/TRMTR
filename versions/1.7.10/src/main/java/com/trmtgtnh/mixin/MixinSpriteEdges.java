package com.trmtgtnh.mixin;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Lets this mod's own wear pictures set where their edges are drawn (0.9.229, SpriteEdges).
 *
 * <p>
 * An accessor rather than an injection, as MixinAnimatedSprite and MixinSpriteFiltering are: nothing in the game's
 * sprite changes for any picture. Only WearSprite and FringeSprite call these, after the game's own initSprite has
 * placed them, so every other sprite in the atlas keeps exactly the coordinates the game gave it.
 */
@SideOnly(Side.CLIENT)
@Mixin(TextureAtlasSprite.class)
public interface MixinSpriteEdges {

    @Accessor("minU")
    void trmt$setMinU(float u);

    @Accessor("maxU")
    void trmt$setMaxU(float u);

    @Accessor("minV")
    void trmt$setMinV(float v);

    @Accessor("maxV")
    void trmt$setMaxV(float v);
}
