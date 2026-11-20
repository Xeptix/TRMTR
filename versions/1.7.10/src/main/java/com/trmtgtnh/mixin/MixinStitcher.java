package com.trmtgtnh.mixin;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.Stitcher;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.client.texture.AtlasPlan;
import com.trmtgtnh.client.texture.WearTextures;

/**
 * Times each stitch, so the log can say what the stitcher took and what {@link MixinStitcherSlot} spared it -
 * the one way to tell a shortcut that ran from one that never bound. A stitch that throws, 'Unable to fit', is
 * not timed; Forge reports that one itself.
 */
@Mixin(Stitcher.class)
public class MixinStitcher {

    @Shadow
    @Final
    private Set setStitchHolders;

    @Unique
    private long trmt$began;

    @Inject(require = 0, method = "doStitch", at = @At("HEAD"))
    private void trmt$noteTheStart(CallbackInfo callback) {
        trmt$began = System.nanoTime();
        // First, so the stitcher and the rectangle dump below both see this mod's small Angelica gutter and none of
        // the pictures that share another's place (0.9.229, public issue #3).
        WearTextures.beforeStitch(setStitchHolders);
        if (!Boolean.getBoolean("trmt.dumpStitchRects") || setStitchHolders.size() < 100000) return;
        Stitcher.Holder[] holders = (Stitcher.Holder[]) setStitchHolders
            .toArray(new Stitcher.Holder[setStitchHolders.size()]);
        Arrays.sort(holders);
        File output = new File(Minecraft.getMinecraft().mcDataDir, "trmt-stitch-rects.tsv");
        try (BufferedWriter writer = new BufferedWriter(
            new OutputStreamWriter(new FileOutputStream(output), StandardCharsets.UTF_8))) {
            for (Stitcher.Holder holder : holders) {
                writer.write(Integer.toString(holder.getWidth()));
                writer.write('\t');
                writer.write(Integer.toString(holder.getHeight()));
                writer.write('\t');
                writer.write(
                    String.valueOf(
                        holder.getAtlasSprite()
                            .getIconName()));
                writer.newLine();
            }
            Trmt.LOG.warn(
                "Wrote {} sorted block-atlas rectangles to {} for STB replay.",
                Integer.valueOf(holders.length),
                output.getAbsolutePath());
        } catch (IOException error) {
            Trmt.LOG.warn("Could not write block-atlas rectangles for STB replay.", error);
        } catch (RuntimeException error) {
            Trmt.LOG.warn("Could not capture block-atlas rectangles for STB replay.", error);
        }
    }

    @Inject(require = 0, method = "doStitch", at = @At("RETURN"))
    private void trmt$noteTheEnd(CallbackInfo callback) {
        AtlasPlan.Search.stitched(setStitchHolders.size(), System.nanoTime() - trmt$began);
    }
}
