package com.trmtgtnh.client.texture;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Function;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

import com.trmtgtnh.config.FamilySettings;
import com.trmtgtnh.surface.SurfaceFamily;

/**
 * Where a wear sprite's faces come from, for the plan that prices them and the pass that builds them.
 *
 * <p>
 * One chain of names, followed the same way by both, to its end. A block's face is the icon its getIcon names; where it
 * names none, the file named after its registry name with _top, _bottom or _side added, or bare; and past those, the
 * vanilla texture of what the sprite is made of. The plan reads the widths of those files from their headers, before
 * anything has loaded. The pass reads pixels, taking them from the sprite the block already draws with wherever the
 * atlas has loaded it, with any filtering border taken off, and from the file otherwise. Until 0.9.212 the plan knew
 * only the icon's name and priced a block that named none at dirt and stone's edge, while the build found the file
 * after its registry name at whatever size that was.
 *
 * <p>
 * Render thread only: the resource manager reads zip files. One is made for each sprite pass and dropped when it ends,
 * and every face read is held until then, so a face a block shares across its appearances, its wall and its mended
 * side is read once. What it hands out is never written to, which is what lets one array stand in several sources.
 *
 * <p>
 * In 1.12.2 the first link of the chain is the block's model rather than its getIcon - see {@link ModelFaces} - and
 * the sprite the block already draws with is reached through the getter the atlas hands a sprite that is loading,
 * which returns it loaded because the wear sprite named it among its dependencies. The filtering border the other
 * edition crops off does not exist here: 1.12.2 dropped anisotropic filtering, and with it the border.
 */
final class FaceSource {

    /** One face as the sprite pass read it. */
    static final class Face {

        /**
         * The face's pixels, square, or null where nothing could be read. Shared between sources, and never written to.
         */
        final int[] pixels;

        /** Whether they were copied out of the sprite the block already draws with, rather than read from a file. */
        final boolean fromAtlas;

        Face(int[] pixels, boolean fromAtlas) {
            this.pixels = pixels;
            this.fromAtlas = fromAtlas;
        }
    }

    private final IResourceManager manager;

    /** Faces copied out of the atlas this pass, by the sprite they came from, null where one had nothing to copy. */
    private final Map<TextureAtlasSprite, int[]> atlas = new IdentityHashMap<TextureAtlasSprite, int[]>();

    /** Faces read from files this pass, by the name read, null where the file gave nothing. */
    private final Map<String, int[]> files = new HashMap<String, int[]>();

    private int atlasFaces;

    private int croppedFaces;

    private int unborderedFaces;

    private int fileFaces;

    /**
     * How this pass reaches the atlas's loaded sprites: the getter the atlas hands each sprite as it loads. The same
     * function every time within a stitch, so the one handed to the first load serves them all.
     */
    private Function<ResourceLocation, TextureAtlasSprite> loaded;

    FaceSource(IResourceManager manager) {
        this.manager = manager;
    }

    /** Hands this pass the atlas's getter, from inside a sprite's load. */
    void use(Function<ResourceLocation, TextureAtlasSprite> getter) {
        if (getter != null) loaded = getter;
    }

    /**
     * A face's name in the form the atlas files a block sprite under: {@code minecraft:blocks/dirt}. The other
     * edition's names are bare - {@code dirt} - and so are the ones FaceRules still hands out; a bare one is a block
     * texture, and gets the folder added.
     */
    static ResourceLocation spriteLocation(String name) {
        ResourceLocation named = new ResourceLocation(name);
        if (named.getPath()
            .indexOf('/') >= 0) return named;
        return new ResourceLocation(named.getNamespace(), "blocks/" + named.getPath());
    }

    /** Whether a family's look is the one that takes a cover off rather than wearing the face. */
    static boolean coverLook(FamilySettings look) {
        return look != null && FamilySettings.PATTERN_GRASS.equals(look.wearPattern);
    }

    /**
     * What a block draws on one side, as a name, or null when it will not say. Asked of the top to tell subtypes apart,
     * and by the plan for every face it prices. Says nothing when a block refuses, because the census and the planner's
     * own walk both ask every block, and one refusal is worth no more than the line the build writes for it.
     */
    static String iconName(Block block, int meta, int side) {
        return ModelFaces.faceName(block, meta, side);
    }

    /**
     * The file named after a block's registry name, with the suffix its face conventionally carries or bare, where the
     * header of either can be read, or null.
     *
     * <p>
     * Chosen by the header rather than by decoding the image, so that the plan, which reads headers, and the pass
     * choose the same file. A header that reads over an image that will not decode is chosen all the same and yields
     * nothing at build time, where a decode would have gone on to the bare name.
     */
    static String suffixedName(IResourceManager manager, Block block, int side) {
        if (block == null) return null;
        // The registry name, because a block's texture name belongs to its own mod and cannot be asked for, and the two
        // agree by convention anyway: a block registered as modid:thing draws modid:thing.
        String base;
        try {
            Object registered = block.getRegistryName();
            if (registered == null) return null;
            base = registered.toString();
        } catch (RuntimeException unregistrable) {
            // A registry lookup should not throw, but the plan now asks this of every block while the stitch is
            // planned, and a block that makes it throw is better read by the vanilla name than allowed to stop the
            // stitch.
            return null;
        }
        if (base == null || base.isEmpty()) return null;

        String suffix = side == 1 ? "_top" : side == 0 ? "_bottom" : "_side";
        if (WearPatterns.headerWidth(manager, base + suffix) > 0) return base + suffix;
        if (WearPatterns.headerWidth(manager, base) > 0) return base;
        return null;
    }

    /**
     * The name a face is read from, as the plan and the pass both follow it: the icon, the file after the registry
     * name, then the vanilla texture of what the sprite is made of. Asked by the plan, so it says nothing when a block
     * refuses.
     */
    static String faceName(IResourceManager manager, Block block, int meta, int side, SurfaceFamily made) {
        if (block != null) {
            String icon = iconName(block, meta, side);
            if (icon != null) return icon;
            String suffixed = suffixedName(manager, block, side);
            if (suffixed != null) return suffixed;
        }
        return FaceRules.vanillaName(made, side);
    }

    /** The width of the file a face is read from, from its header, or nought where it cannot be read. */
    static int pricedWidth(IResourceManager manager, Block block, int meta, int side, SurfaceFamily made) {
        return WearPatterns.headerWidth(manager, faceName(manager, block, meta, side, made));
    }

    /**
     * The width of the vanilla file a fallback sprite reads for one side: the material it is made of, then, where that
     * cannot be read, the family's own. The same two reads {@link #read} makes for a sprite with no block.
     */
    static int vanillaWidth(IResourceManager manager, SurfaceFamily family, SurfaceFamily appearance, int side) {
        int width = WearPatterns
            .headerWidth(manager, FaceRules.vanillaName(FaceRules.madeOf(family, appearance), side));
        if (width <= 0 && appearance != family) {
            width = WearPatterns.headerWidth(manager, FaceRules.vanillaName(family, side));
        }
        return width;
    }

    /**
     * One face's pixels, for the sprite pass, following the chain faceName follows.
     *
     * <p>
     * Taken from the sprite the block is already drawing with, wherever that is loaded. That is the only source that is
     * always right: it is the pixels the game will actually put on screen, so it works for a block whose texture lives
     * somewhere the naming convention does not predict, for one assembled at runtime, and for a resource pack that
     * replaced it. Modded stone and chiselled blocks were coming out as vanilla stone and vanilla dirt precisely
     * because reading a file by name could not find them. The file is the fallback, for a sprite that holds no frames
     * at the pass: a connected-texture tile Chisel cuts only after stitching, or a loader that keeps none.
     */
    Face read(Block origin, int meta, int side, SurfaceFamily originFamily, SurfaceFamily appearance) {
        SurfaceFamily made = FaceRules.madeOf(originFamily, appearance);
        String name = null;
        if (origin != null) {
            name = iconName(origin, meta, side);
            TextureAtlasSprite sprite = atlasSprite(name);
            if (sprite != null) {
                int[] copied = atlasFace(sprite);
                if (copied != null) return new Face(copied, true);
            }
            // Nothing came back, so try the convention instead. A block whose faces differ names them after its own
            // texture with a suffix - grass does it, mycelium does it, and so does the grass path, which is why a worn
            // path was coming out as vanilla dirt: the lookup failed and the only remaining answer was the family's
            // stock texture.
            if (name == null) name = suffixedName(manager, origin, side);
        }
        if (name == null) name = FaceRules.vanillaName(made, side);
        int[] pixels = fileFace(name);
        if (pixels == null && appearance != originFamily && originFamily != null) {
            // A sprite drawn as a surface this one wears THROUGH into is made of that surface's material, which is why
            // the name above is the appearance's. If the pack cannot produce that material's texture, the honest second
            // choice is the material this block actually is - worn stone put through cobble's pattern is a poor cobble
            // and still an enormous improvement on nothing at all, which is what an unusable sprite leaves on the
            // ground.
            pixels = fileFace(FaceRules.vanillaName(originFamily, side));
        }
        return new Face(pixels, false);
    }

    /** Distinct sprites whose pixels were copied out of the atlas this pass. */
    int atlasFaces() {
        return atlasFaces;
    }

    /** Of those, how many were marked as loaded with the filtering border, carried it, and had it taken off. */
    int croppedFaces() {
        return croppedFaces;
    }

    /**
     * Of those, how many were marked as loaded with the border and did not carry it: cropped to the middle all the
     * same, or used as loaded where no wider than the border's sixteen pixels.
     */
    int unborderedFaces() {
        return unborderedFaces;
    }

    /** Distinct names read from a file that decoded this pass. */
    int fileFaces() {
        return fileFaces;
    }

    /**
     * The atlas's own sprite for a face name, loaded, or null. Only a name the wear sprite declared among its
     * dependencies is sure to be loaded by now; any other comes back unloaded or not at all, and is read from its
     * file instead.
     */
    private TextureAtlasSprite atlasSprite(String name) {
        if (name == null || loaded == null) return null;
        try {
            return loaded.apply(spriteLocation(name));
        } catch (RuntimeException unreachable) {
            return null;
        }
    }

    /** A loaded sprite's face, once per sprite a pass. */
    private int[] atlasFace(TextureAtlasSprite sprite) {
        if (atlas.containsKey(sprite)) return atlas.get(sprite);
        int[] face = copyFace(sprite);
        atlas.put(sprite, face);
        return face;
    }

    /**
     * A loaded sprite's face: the middle the game draws where it marked the sprite as loaded with the filtering border,
     * and the whole frame otherwise.
     *
     * <p>
     * Cropped on the mark rather than on the pixels, because the game draws by the mark: initSprite pulls a marked
     * sprite's texture coordinates in by the border whatever its frames hold. A marked frame that is not the face
     * wrapped as the game wraps it, from a loader that painted over the wrap or a mark left by an earlier load, which
     * only loadSprite clears, is cropped all the same and counted apart for the log; one no wider than the sixteen
     * pixels the border adds has no middle, and is used as it was loaded. A sprite class of a mod's own that draws its
     * whole frame despite the mark would be cropped wrongly, and none is known to. A sprite with no mark is copied as
     * it is, and no sprite the game loads from its file while filtering is off has one.
     */
    private int[] copyFace(TextureAtlasSprite sprite) {
        try {
            if (sprite.getFrameCount() <= 0) return null;
            int[][] frames = sprite.getFrameTextureData(0);
            if (frames == null || frames.length == 0 || frames[0] == null) return null;

            int width = sprite.getIconWidth();
            if (width <= 0 || width != sprite.getIconHeight()) return null;
            if (frames[0].length < (long) width * width) return null;

            atlasFaces++;
            // No filtering border to take off: 1.12.2 has no anisotropic filtering, and so never wraps a sprite in
            // one. The two counts the other edition keeps for it stay, at nought, so the log reads the same.
            return Arrays.copyOf(frames[0], width * width);
        } catch (RuntimeException awkwardSprite) {
            return null;
        }
    }

    /** A face read from its file at the file's own width, once per name a pass. */
    private int[] fileFace(String name) {
        if (name == null) return null;
        if (files.containsKey(name)) return files.get(name);
        BufferedImage image = WearPatterns.readIcon(manager, name);
        int[] pixels = image == null ? null : WearPatterns.toPixels(image, WearPatterns.squareSize(image));
        if (pixels != null) fileFaces++;
        files.put(name, pixels);
        return pixels;
    }
}
