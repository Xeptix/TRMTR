package com.trmtgtnh.client.texture;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.world.level.block.Block;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.resources.ResourceLocation;

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
        /**
         * Whether these are the block's own pixels rather than a family's stock texture.
         *
         * <p>
         * Both older editions call this {@code fromAtlas} and set it only for a face copied out of
         * the atlas, so a face read from the block's own texture file counted as a stand-in. The
         * tally it feeds says "wearing a family stand-in rather than their own pixels", which is a
         * different question and the one asked here.
         */
        final boolean ownPixels;

        Face(int[] pixels, boolean ownPixels) {
            this.pixels = pixels;
            this.ownPixels = ownPixels;
        }
    }

    /**
     * One face a picture was composed from, for the check against the finished atlas after the bake (WP30, 0.9.224):
     * which block and side it stood for, the name it was read under, the pixels used at their own width, whether they
     * were the block's own or a stand-in, and whether they came out of the atlas already.
     */
    static final class Read {

        final Block block;

        final int side;

        final String name;

        final int[] pixels;

        final boolean own;

        final boolean fromAtlas;

        Read(Block block, int side, String name, int[] pixels, boolean own, boolean fromAtlas) {
            this.block = block;
            this.side = side;
            this.name = name;
            this.pixels = pixels;
            this.own = own;
            this.fromAtlas = fromAtlas;
        }
    }

    /**
     * The widths the atlas drew faces at, by name, where that was wider than the face's file says - a texture made or
     * enlarged after loading. Remembered for the session, so the next plan prices the slot from what is drawn, as
     * 1.7.10 builds its slot from the loaded sprite (WP30's fifth step).
     */
    private static final Map<String, Integer> DRAWN_WIDER = new java.util.concurrent.ConcurrentHashMap<String, Integer>();

    private final ResourceManager manager;

    /** Faces read from files this pass, by the name read, null where the file gave nothing. */
    private final Map<String, int[]> files = new HashMap<String, int[]>();

    /** The faces the harvest in progress has read, taken by the pass after each harvest. */
    private final java.util.List<Read> reading = new java.util.ArrayList<Read>();

    /**
     * Faces the finished atlas showed to differ from what was read, by block and side, which a harvest after the bake
     * is handed in their place. Empty until the check has run.
     */
    private final Map<Block, int[][]> atlasFaces = new java.util.IdentityHashMap<Block, int[][]>();

    private int fileFaces;

    FaceSource(ResourceManager manager) {
        this.manager = manager;
    }

    /**
     * A face's name in the form the atlas files a block sprite under: {@code minecraft:blocks/dirt}. The other
     * edition's names are bare - {@code dirt} - and so are the ones FaceRules still hands out; a bare one is a block
     * texture, and gets the folder added.
     */
    static ResourceLocation spriteLocation(String name) {
        ResourceLocation named = new ResourceLocation(VanillaNames.current(name));
        if (named.getPath()
            .indexOf('/') >= 0) return named;
        // The same move, for the name a sprite is filed under rather than the file it is read from.
        return new ResourceLocation(named.getNamespace(), "block/" + named.getPath());
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
    static String iconName(Block block, int side) {
        return ModelFaces.faceName(block, side);
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
    static String suffixedName(ResourceManager manager, Block block, int side) {
        if (block == null) return null;
        // The registry name, because a block's texture name belongs to its own mod and cannot be asked for, and the two
        // agree by convention anyway: a block registered as modid:thing draws modid:thing.
        String base;
        try {
            Object registered = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block);
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
    static String faceName(ResourceManager manager, Block block, int side, SurfaceFamily made) {
        if (block != null) {
            String icon = iconName(block, side);
            if (icon != null) return icon;
            String suffixed = suffixedName(manager, block, side);
            if (suffixed != null) return suffixed;
        }
        return FaceRules.vanillaName(made, side);
    }

    /**
     * The width of the file a face is read from, from its header, or nought where it cannot be read - or the width the
     * atlas last drew it at, where that was wider (WP30).
     */
    static int pricedWidth(ResourceManager manager, Block block, int side, SurfaceFamily made) {
        String name = faceName(manager, block, side, made);
        int header = WearPatterns.headerWidth(manager, name);
        Integer drawn = name == null ? null : DRAWN_WIDER.get(name);
        return drawn == null ? header : Math.max(header, drawn.intValue());
    }

    /** Remembers that the atlas drew a face wider than its file, for the plans after this one. */
    static void rememberDrawnWider(String name, int width) {
        if (name != null && width > 0) DRAWN_WIDER.put(name, Integer.valueOf(width));
    }

    /** Whether a face has been remembered at that width already, so a rebuild for it is asked once. */
    static boolean drawnWiderKnown(String name, int width) {
        Integer known = name == null ? null : DRAWN_WIDER.get(name);
        return known != null && known.intValue() >= width;
    }

    /** The faces read since this was last asked, in order; the list starts again empty. */
    java.util.List<Read> takeReads() {
        java.util.List<Read> out = new java.util.ArrayList<Read>(reading);
        reading.clear();
        return out;
    }

    /** Hands a later harvest the atlas's face for one block's side in place of the one read before the stitch. */
    void useAtlas(Block block, int side, int[] pixels) {
        if (block == null || side < 0 || side > 5 || pixels == null) return;
        int[][] sides = atlasFaces.get(block);
        if (sides == null) {
            sides = new int[6][];
            atlasFaces.put(block, sides);
        }
        sides[side] = pixels;
    }

    /**
     * The width of the vanilla file a fallback sprite reads for one side: the material it is made of, then, where that
     * cannot be read, the family's own. The same two reads {@link #read} makes for a sprite with no block.
     */
    static int vanillaWidth(ResourceManager manager, SurfaceFamily family, SurfaceFamily appearance, int side) {
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
     * Read from the block's own texture file, under the name its baked model gives - which is what makes this work
     * for a modded block whose texture the naming convention would not have predicted.
     *
     * <p>
     * Both older editions read the atlas first and treat the file as the fallback, because the atlas holds the pixels
     * the game will actually put on screen and so covers a texture assembled at runtime too. That is not available
     * here: the wear sprites arrive through a resource pack, and a pack is read during stitching, before any sprite
     * has pixels. What is lost with it is the texture that has no file behind it - a connected-texture tile cut after
     * stitching, say - which falls through to the family stand-in, exactly as it already did in those editions when
     * the atlas held no frames for it.
     */
    Face read(Block origin, int side, SurfaceFamily originFamily, SurfaceFamily appearance) {
        // After the bake, where the finished atlas drew this side differently, what it drew - the block's own pixels, as
        // 1.7.10 copies them out of its atlas (WP30).
        if (origin != null && side >= 0 && side <= 5) {
            int[][] drawn = atlasFaces.get(origin);
            if (drawn != null && drawn[side] != null) {
                reading.add(new Read(origin, side, null, drawn[side], true, true));
                return new Face(drawn[side], true);
            }
        }
        SurfaceFamily made = FaceRules.madeOf(originFamily, appearance);
        String name = null;
        if (origin != null) {
            name = iconName(origin, side);
            // Nothing came back, so try the convention instead. A block whose faces differ names them after its own
            // texture with a suffix - grass does it, mycelium does it, and so does the grass path, which is why a worn
            // path was coming out as vanilla dirt: the lookup failed and the only remaining answer was the family's
            // stock texture.
            if (name == null) name = suffixedName(manager, origin, side);
        }
        // Everything above names the block itself; everything below is a stand-in. That is the line the tally wants.
        boolean own = name != null;
        if (name == null) name = FaceRules.vanillaName(made, side);
        int[] pixels = fileFace(name);
        if (pixels == null && appearance != originFamily && originFamily != null) {
            // A sprite drawn as a surface this one wears THROUGH into is made of that surface's material, which is why
            // the name above is the appearance's. If the pack cannot produce that material's texture, the honest second
            // choice is the material this block actually is - worn stone put through cobble's pattern is a poor cobble
            // and still an enormous improvement on nothing at all, which is what an unusable sprite leaves on the
            // ground.
            pixels = fileFace(FaceRules.vanillaName(originFamily, side));
            own = false;
        }
        if (origin != null) reading.add(new Read(origin, side, name, pixels, own, false));
        return new Face(pixels, own);
    }

    /** Distinct names read from a file that decoded this pass. */
    int fileFaces() {
        return fileFaces;
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
