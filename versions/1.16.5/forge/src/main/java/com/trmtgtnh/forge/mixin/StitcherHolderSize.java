package com.trmtgtnh.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * A sprite's size as the stitcher holds it, for {@link MixinStitcherRegion}. Stitcher.Holder is package-private at
 * this version, so it is named here by its string and read through accessors rather than widened.
 */
@Mixin(targets = "net.minecraft.client.renderer.texture.Stitcher$Holder")
public interface StitcherHolderSize {

    @Accessor("width")
    int trmt$width();

    @Accessor("height")
    int trmt$height();
}
