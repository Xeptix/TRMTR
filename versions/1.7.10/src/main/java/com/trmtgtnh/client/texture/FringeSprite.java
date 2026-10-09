package com.trmtgtnh.client.texture;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

import com.trmtgtnh.Trmt;

/**
 * One gradation of a worn grass block's side fringe: the grey {@code grass_side_overlay}, thinned
 * so the hanging green strip has receded upward by a set amount.
 *
 * <p>
 * The overlay is universal - grey and the same for every grass block, tinted per biome at render
 * time - so unlike a {@link WearSprite} there is nothing per-origin to bake and nothing to read
 * from another block's pixels. It is the vanilla texture with a fraction of its fringe cleared,
 * and it stays grey: the render pass that draws it multiplies in the biome color, so tinting it
 * here would tint it twice.
 *
 * <p>
 * Built straight from the resource file rather than the atlas, because the overlay is a vanilla
 * texture that is always readable and never one of ours generated during the same stitch - so
 * there is no ordering problem to rebuild around.
 */
public class FringeSprite extends TextureAtlasSprite {

    /** Mip levels the atlas may ask for, plus the base image. Same reasoning as {@link WearSprite}. */
    private static final int MIPMAP_SLOTS = 5;

    private final int stage;
    private final int stageCount;
    private final int rotation;

    private volatile boolean usable = true;

    public FringeSprite(String name, int stage, int stageCount, int rotation) {
        super(name);
        this.stage = stage;
        this.stageCount = stageCount;
        this.rotation = rotation;
    }

    @Override
    public boolean hasCustomLoader(IResourceManager manager, ResourceLocation location) {
        return true;
    }

    @Override
    public boolean load(IResourceManager manager, ResourceLocation location) {
        try {
            BufferedImage image = WearPatterns.readIcon(manager, "grass_side_overlay");
            if (image == null) {
                usable = false; // nothing to thin; the lookup falls back to the vanilla overlay
                fill(clear(WearPatterns.ART_SIZE), WearPatterns.ART_SIZE);
                return false;
            }

            int size = WearPatterns.squareSize(image);
            int[] overlay = WearPatterns.toPixels(image, size);
            float coverage = WearPatterns.fringeCoverage(stage, stageCount);
            int[] thinned = WearCompositor.applyFringe(overlay, size, coverage, rotation);
            fill(thinned, size);
        } catch (RuntimeException failure) {
            usable = false;
            Trmt.LOG.warn("Could not generate grass side fringe {}", getIconName(), failure);
            fill(clear(WearPatterns.ART_SIZE), WearPatterns.ART_SIZE);
        }
        return false; // handled here; there is no file for vanilla to read
    }

    /** True unless the overlay could not be read, in which case the lookup skips this. */
    public boolean isUsable() {
        return usable;
    }

    private void fill(int[] pixels, int size) {
        setIconWidth(size);
        setIconHeight(size);
        int[][] frame = new int[MIPMAP_SLOTS][];
        frame[0] = pixels;
        List<int[][]> frames = new ArrayList<int[][]>();
        frames.add(frame);
        setFramesTextureData(frames);
    }

    private static int[] clear(int size) {
        return new int[size * size];
    }
}
