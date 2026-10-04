package com.trmtgtnh.mixin;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Says whether the game loaded a sprite with the border anisotropic filtering adds.
 *
 * <p>
 * Read off the sprite rather than off the setting, because the two can disagree: a sprite with a loader of its own is
 * never given the border whatever the setting says, unless that loader hands its images to the game's own loadSprite
 * and asks for the border in its third argument. The flag, not the pixels, decides whether a face is cropped, because
 * it is what decides what the game draws: initSprite pulls a marked sprite's texture coordinates in by the border
 * whatever its frames hold, so the middle is the face on screen even where a loader has painted over the wrap.
 *
 * <p>
 * Only loadSprite sets or clears it, and resetSprite leaves it, so a loader that fills the frames itself keeps whatever
 * an earlier load put there. A sprite object a mod keeps across stitches, loaded through the game on one and filled by
 * its own loader on a later one, carries a mark its pixels no longer bear, and the game still draws only its middle.
 * The atlas empties its map every stitch and blocks register new sprites into it, so that takes a mod re-registering
 * one sprite it holds on to, and none is known to. The pixels are checked against the wrap as well, in
 * AnisotropicBorder, but only so the log can count the marked faces that do not carry it.
 *
 * <p>
 * An accessor rather than an injection, for the reason MixinAnimatedSprite gives: nothing is redirected, and a field
 * that has stopped existing fails when the game starts rather than quietly.
 */
@SideOnly(Side.CLIENT)
@Mixin(TextureAtlasSprite.class)
public interface MixinSpriteFiltering {

    @Accessor("useAnisotropicFiltering")
    boolean trmt$usesAnisotropicFiltering();
}
