package com.trmtgtnh.mixin;

import net.minecraft.client.renderer.texture.Stitcher;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.trmtgtnh.client.texture.AtlasPlan;

/**
 * Spares the stitcher's slot search the parts of the atlas it already knows are too full, without moving a sprite.
 *
 * <p>
 * Each slot keeps the last sprite it refused, and a search that reaches it with one at least as wide and at least
 * as tall turns back at once, which is what vanilla's search would have found by walking the whole of it. Why that
 * puts every sprite where vanilla's search does - short of the one case where vanilla lays a sprite over others -
 * and what it cost without it, is {@link AtlasPlan.Search}; the test that runs vanilla's search beside this one is
 * AtlasPlanTest. The figures it saves are said once a stitch, from the block atlas's stitch event.
 *
 * <p>
 * {@code require = 0}: a mod that has replaced this search should cost speed, not the game. The stitch report says
 * when a large stitch turned no search back, which is how a shortcut that never bound is heard.
 */
@Mixin(Stitcher.Slot.class)
public class MixinStitcherSlot {

    @Unique
    private int trmt$refusedWidth = AtlasPlan.Search.NOTHING_REFUSED;

    @Unique
    private int trmt$refusedHeight = AtlasPlan.Search.NOTHING_REFUSED;

    @Inject(require = 0, method = "addSlot", at = @At("HEAD"), cancellable = true)
    private void trmt$turnBackWhatCannotFit(Stitcher.Holder holder, CallbackInfoReturnable<Boolean> callback) {
        if (AtlasPlan.Search
            .stillRefuses(trmt$refusedWidth, trmt$refusedHeight, holder.getWidth(), holder.getHeight())) {
            AtlasPlan.Search.spare();
            callback.setReturnValue(Boolean.FALSE);
        }
    }

    @Inject(require = 0, method = "addSlot", at = @At("RETURN"))
    private void trmt$keepTheRefusal(Stitcher.Holder holder, CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValueZ()) return;
        trmt$refusedWidth = holder.getWidth();
        trmt$refusedHeight = holder.getHeight();
    }
}
