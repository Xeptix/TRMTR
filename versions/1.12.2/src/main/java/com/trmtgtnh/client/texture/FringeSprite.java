package com.trmtgtnh.client.texture;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

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
 * and it stays grey: the render pass that draws it multiplies in the biome colour, so tinting it
 * here would tint it twice.
 *
 * <p>
 * Built straight from the resource file rather than the atlas, because the overlay is a vanilla
 * texture that is always readable and never one of ours generated during the same stitch - so
 * there is no ordering problem to rebuild around.
 */
@net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
public class FringeSprite extends TextureAtlasSprite {

    /**
     * Mip levels the atlas asks for, plus the base image, taken from the atlas when this is registered.
     *
     * <p>
     * The other edition fixed this at five, which covers vanilla's four levels and nothing past them. The
     * generator walks every level by index and needs a slot for each already there, so the honest figure
     * is the atlas's own - which is what the rendering spike found out when a frame of one slot was an
     * index out of bounds at level one.
     */
    private final int mipmapSlots;

    private final int stage;
    private final int stageCount;
    private final int rotation;

    private volatile boolean usable = true;

    public FringeSprite(String name, int stage, int stageCount, int rotation, int mipmapLevels) {
        super(name);
        this.mipmapSlots = Math.max(0, mipmapLevels) + 1;
        this.stage = stage;
        this.stageCount = stageCount;
        this.rotation = rotation;
    }

    @Override
    public boolean hasCustomLoader(IResourceManager manager, ResourceLocation location) {
        return true;
    }

    /**
     * Composed here, in the sprite's own load: 1.12.2 hands a custom loader the whole job, and a false
     * return is what sends the sprite on to the mipmap generator and the stitcher. The other edition's
     * load was the same shape with one argument fewer. Nothing is declared as a dependency, because the
     * overlay is read from its file, not from the atlas.
     */
    @Override
    public boolean load(IResourceManager manager, ResourceLocation location,
        Function<ResourceLocation, TextureAtlasSprite> textureGetter) {
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
        int[][] frame = new int[mipmapSlots][];
        frame[0] = pixels;
        List<int[][]> frames = new ArrayList<int[][]>();
        frames.add(frame);
        setFramesTextureData(frames);
    }

    private static int[] clear(int size) {
        return new int[size * size];
    }
}
