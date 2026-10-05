package com.trmtgtnh.client.texture;

import java.awt.image.BufferedImage;

import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.resources.ResourceLocation;

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
public class FringeSprite {

    /**
     * What this gradation is called, which is also where the atlas will look for it.
     *
     * <p>
     * Both older editions hand this to {@code TextureAtlasSprite}'s constructor, because there the
     * sprite is the thing with a name. Here the name belongs to the file this draws, and the atlas
     * finds it the ordinary way.
     */
    private final ResourceLocation name;

    private final int stage;
    private final int stageCount;
    private final int rotation;

    private volatile boolean usable = true;

    public FringeSprite(ResourceLocation name, int stage, int stageCount, int rotation) {
        this.name = name;
        this.stage = stage;
        this.stageCount = stageCount;
        this.rotation = rotation;
    }

    /** Where the atlas will look for this, and what {@code GeneratedPack} answers to. */
    public ResourceLocation name() {
        return name;
    }

    /**
     * This gradation, drawn.
     *
     * <p>
     * Both older editions do this inside the sprite's own {@code load}, because there a custom
     * loader is handed the whole job and a false return is what sends the result on to the mipmap
     * generator and the stitcher. Here there is no loader to be: the pack is asked for a file, this
     * draws it, and the atlas does its own mipmapping and stitching afterwards exactly as it does
     * for a texture somebody shipped.
     *
     * <p>
     * Read from the resource file rather than from the atlas, as it always was - the overlay is a
     * vanilla texture that is always readable and never one of ours generated during the same
     * stitch, so there is no ordering problem to build around.
     */
    public GeneratedPack.Sheet sheet(ResourceManager manager) {
        try {
            BufferedImage image = WearPatterns.readIcon(manager, "grass_side_overlay");
            if (image == null) {
                usable = false; // nothing to thin; the lookup falls back to the vanilla overlay
                return blank();
            }

            int size = WearPatterns.squareSize(image);
            int[] overlay = WearPatterns.toPixels(image, size);
            float coverage = WearPatterns.fringeCoverage(stage, stageCount);
            int[] thinned = WearCompositor.applyFringe(overlay, size, coverage, rotation);
            return new GeneratedPack.Sheet(thinned, size, size, null);
        } catch (RuntimeException failure) {
            usable = false;
            Trmt.LOG.warn("Could not generate grass side fringe {}", name, failure);
            return blank();
        }
    }

    /** True unless the overlay could not be read, in which case the lookup skips this. */
    public boolean isUsable() {
        return usable;
    }

    /**
     * A square of nothing, for when the overlay could not be read.
     *
     * <p>
     * Something rather than null, because a pack that cannot answer for a name it claimed is an
     * exception inside a resource reload. {@link #isUsable} is how the lookup finds out, and it
     * falls back to the vanilla overlay - so this is never drawn, it only has to exist.
     */
    private GeneratedPack.Sheet blank() {
        return new GeneratedPack.Sheet(
            new int[WearPatterns.ART_SIZE * WearPatterns.ART_SIZE],
            WearPatterns.ART_SIZE,
            WearPatterns.ART_SIZE,
            null);
    }
}
