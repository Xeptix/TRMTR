package com.trmtgtnh.forge.mixin;


import net.minecraft.client.renderer.texture.Stitcher;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.trmtgtnh.client.texture.AtlasPlan;

/**
 * Times each stitch, so the log can say what the stitcher took and what {@link MixinStitcherRegion} spared it - the
 * one way to tell a shortcut that ran from one that never bound. The 1.7.10 edition's MixinStitcher. A stitch that
 * throws is not timed; the game reports that one itself. This version stitches its atlases side by side, and the
 * report keeps the largest, which is the block atlas.
 */
@Mixin(Stitcher.class)
public class MixinStitcher {

    @Shadow
    @Final
    // A list from 1.19.3, where 1.16.5 kept a set.
    private java.util.List<?> texturesToBeStitched;

    @Unique
    private long trmt$began;

    @Inject(require = 0, method = "stitch", at = @At("HEAD"))
    private void trmt$noteTheStart(CallbackInfo callback) {
        trmt$began = System.nanoTime();
    }

    @Inject(require = 0, method = "stitch", at = @At("RETURN"))
    private void trmt$noteTheEnd(CallbackInfo callback) {
        AtlasPlan.Search.stitched(texturesToBeStitched.size(), System.nanoTime() - trmt$began);
    }
}
