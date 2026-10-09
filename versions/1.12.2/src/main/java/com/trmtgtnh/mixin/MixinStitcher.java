package com.trmtgtnh.mixin;

import java.util.Set;

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
 * Times each stitch, so the log can say what the stitcher took and what {@link MixinStitcherSlot} spared it -
 * the one way to tell a shortcut that ran from one that never bound. A stitch that throws, 'Unable to fit', is
 * not timed; Forge reports that one itself.
 */
@Mixin(Stitcher.class)
public class MixinStitcher {

    @Shadow
    @Final
    private Set<Stitcher.Holder> setStitchHolders;

    @Unique
    private long trmt$began;

    @Inject(require = 0, method = "doStitch", at = @At("HEAD"))
    private void trmt$noteTheStart(CallbackInfo callback) {
        trmt$began = System.nanoTime();
    }

    @Inject(require = 0, method = "doStitch", at = @At("RETURN"))
    private void trmt$noteTheEnd(CallbackInfo callback) {
        AtlasPlan.Search.stitched(setStitchHolders.size(), System.nanoTime() - trmt$began);
    }
}
