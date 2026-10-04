package com.trmtgtnh.mixin;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.data.AnimationMetadataSection;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Lets a generated sprite declare itself animated in the way the rest of the game understands.
 *
 * <p>
 * Overriding {@code hasAnimationMetadata} would be enough for vanilla, which is why it is tempting.
 * It is not enough here. The modern chunk builder uploads only sprites that were on screen this
 * frame and advances everything else through a book-keeping pass that begins by reading this very
 * field and returns at once when it is null. A sprite that merely claimed to be animated would stop
 * counting the moment you looked away and resume where it left off - so a worn lavastone would come
 * back a beat behind the unworn one beside it, which is the whole of what this feature is for.
 * Setting the field for real makes both that pass and vanilla's advance the frame counter by exactly
 * the rule they use on the game's own lava, on screen and off.
 *
 * <p>
 * An accessor rather than an injection: no method is redirected and no behaviour replaced, so the
 * only thing that can go wrong is a field that stopped existing - which fails here, at build time,
 * rather than silently at runtime.
 */
@SideOnly(Side.CLIENT)
@Mixin(TextureAtlasSprite.class)
public interface MixinAnimatedSprite {

    @Accessor("animationMetadata")
    void trmt$setAnimationMetadata(AnimationMetadataSection metadata);
}
