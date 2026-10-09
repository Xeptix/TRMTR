package com.trmtgtnh.forge.mixin;

import net.minecraft.client.renderer.texture.Stitcher;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.trmtgtnh.client.texture.AtlasPlan;

/**
 * Spares the stitcher's slot search the parts of the atlas it already knows are too full, without moving a sprite -
 * the 1.7.10 edition's MixinStitcherSlot, on this version's Stitcher.Region, which is the same search.
 *
 * <p>
 * Each slot keeps the last sprite it refused, and a search that reaches it with one at least as wide and at least
 * as tall turns back at once, which is what vanilla's search would have found by walking the whole of it. Why that
 * puts every sprite where vanilla's search does, and what it cost without it, is {@link AtlasPlan.Search}; this
 * version turns no sprite on its side, so it never meets the one case where vanilla's would lay a sprite over others.
 * The test that runs vanilla's search beside this one is AtlasPlanTest.
 *
 * <p>
 * The sprite arrives as Stitcher.Holder, which is package-private at this version, so it is taken as an Object and
 * read through {@link StitcherHolderSize} - no access widener or transformer, and the same on both loaders.
 *
 * <p>
 * {@code require = 0}: a mod that has replaced this search should cost speed, not the game. The stitch report says
 * when a large stitch turned no search back, which is how a shortcut that never bound is heard.
 */
@Mixin(Stitcher.Region.class)
public class MixinStitcherRegion {

    @Unique
    private int trmt$refusedWidth = AtlasPlan.Search.NOTHING_REFUSED;

    @Unique
    private int trmt$refusedHeight = AtlasPlan.Search.NOTHING_REFUSED;

    @Inject(require = 0, method = "add", at = @At("HEAD"), cancellable = true)
    private void trmt$turnBackWhatCannotFit(@Coerce Object holder, CallbackInfoReturnable<Boolean> callback) {
        StitcherHolderSize size = (StitcherHolderSize) holder;
        if (AtlasPlan.Search
            .stillRefuses(trmt$refusedWidth, trmt$refusedHeight, size.trmt$width(), size.trmt$height())) {
            AtlasPlan.Search.spare();
            callback.setReturnValue(Boolean.FALSE);
        }
    }

    @Inject(require = 0, method = "add", at = @At("RETURN"))
    private void trmt$keepTheRefusal(@Coerce Object holder, CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValueZ()) return;
        StitcherHolderSize size = (StitcherHolderSize) holder;
        trmt$refusedWidth = size.trmt$width();
        trmt$refusedHeight = size.trmt$height();
    }
}
