package com.trmtgtnh.client.texture;

import com.trmtgtnh.surface.SurfaceFamily;

/**
 * Which face a wear sprite is drawn from, what stands in for it, what it is made of, and the edge it is drawn at.
 *
 * <p>
 * These rules used to be written twice: in WearSprite, which builds the picture, and again in WearTextures, which
 * prices it, with a line in WearTextures saying change one, change both. Nothing enforced that, the WearSprite line
 * numbers it quoted went stale within a version, and the size load gave a sprite was a third reading of them. So they
 * live here once. The planner prices a sprite at the edge this gives, the atlas stitches it at that edge, and the
 * compositor draws at it, so the three cannot come apart.
 *
 * <p>
 * Nothing from the game is in it, which is what lets a test pin every rule; it is on the list CoreStaysPortableTest
 * keeps free of Minecraft. What a face's file is called and how wide it is stay in FaceSource, which hands every
 * figure in, and whether a look takes a cover off is handed in as a yes or no, because the settings that say so are
 * Forge's. GhostLogic.fallbackTextureName keeps a table of its own, and deliberately a different one: it names the
 * top face alone, grass_top for grass whatever the side, because the renderer decides how to tint a block's sides by
 * comparing that exact name.
 */
public final class FaceRules {

    /**
     * The edge of the flat stand-in a sprite read only from vanilla files is given when none of them can be read. It
     * is WearPatterns.ART_SIZE, repeated as a figure because that class is not free of Minecraft.
     */
    public static final int PLACEHOLDER_EDGE = 16;

    private FaceRules() {}

    /**
     * Whether an appearance is drawn as a cover taken off the face under it. Asked of the look rather than of the
     * family, which is what the setting has always said it meant, and only while the sprite is still drawing its own
     * surface, because once it has worn through into a successor the cover is already gone.
     */
    public static boolean drawsCover(SurfaceFamily originFamily, SurfaceFamily appearance, boolean coverLook) {
        return coverLook && appearance == originFamily;
    }

    /**
     * Whether an appearance is grass worn through to the earth it was sitting on, drawn from the block's underside.
     * Everything else wears the face that was already being looked at.
     */
    public static boolean revealsEarth(SurfaceFamily originFamily, SurfaceFamily appearance) {
        return originFamily == SurfaceFamily.GRASS && appearance != SurfaceFamily.GRASS;
    }

    /**
     * Whether a set of a family's worn pictures is made from the block's underside as well as its top: grass worn
     * through
     * to its earth (revealsEarth), and a first run drawn as a cover, which lays the top over the underside - grass's
     * look by default, and any family's whose look is set to it ({@code coverLook}).
     */
    public static boolean readsUnderside(SurfaceFamily family, boolean coverLook) {
        return family == SurfaceFamily.GRASS || coverLook;
    }

    /**
     * What blocks must have in common to share one set of worn pictures: the family, which decides the appearances a
     * set
     * holds; every face the pictures are made of; and the layer drawn behind it. The underside only where the pictures
     * read it (readsUnderside), so a stone stair, slab and block still share the one set "stone" draws.
     *
     * <p>
     * The underside was left out until 0.9.223 and nothing showed it until a pack held two lawns with one top on two
     * earths. Xep, 2026-10-10: worn vanilla grass sank into an earth far darker than modded lawns', in GT New Horizons
     * and back to 0.9.216. Biomes O' Plenty's loamy grass draws vanilla's grass_top and sorts before minecraft:grass,
     * so
     * it made the shared set from its own underside, dirt_loamy, and vanilla grass wore into that.
     */
    public static String setKey(SurfaceFamily family, String top, String underside, String behind, boolean coverLook) {
        return family.ordinal() + " "
            + top
            + (readsUnderside(family, coverLook) ? " over " + underside : "")
            + (behind == null ? "" : " behind " + behind);
    }

    /**
     * Which family's material a sprite is made of, which is not always the one covered.
     *
     * <p>
     * The appearance rather than the origin, because a sprite drawn as a successor is made of that successor. Without
     * it a stone surface showing cobble would be stone put through cobble's wear pattern - and since both default to
     * the same pattern at the same strength, byte for byte the stone sprite, so wearing through would look like nothing
     * happening at all.
     *
     * <p>
     * Grass keeps the origin, because its pair of names is read as a pair: the top face of a worn lawn is turf and the
     * sides are the earth under it, and that is true at every step of its run whether the chain calls it grass or dirt.
     */
    public static SurfaceFamily madeOf(SurfaceFamily originFamily, SurfaceFamily appearance) {
        return originFamily == SurfaceFamily.GRASS || appearance == null ? originFamily : appearance;
    }

    /**
     * The vanilla texture a sprite made of this family is read from where nothing of a block's own can be, for one
     * side.
     *
     * <p>
     * Written when there were six staged families, this never grew when the nether, the end, snow and ice were
     * appended, so all four answered dirt - and this is the one table that decides what a surface wears when its own
     * pixels cannot be read. Every fallback set for those families was therefore made of vanilla soil, and so was every
     * block that fell back to one. Chisel's connected-texture variants fall back for a reason nothing here can avoid:
     * their icon is a tile of a shared sheet, and Chisel only cuts those tiles on TextureStitchEvent.Post, by which
     * time the atlas has thrown its pixels away. Ground that cannot be read should at least wear as its own kind of
     * ground.
     */
    public static String vanillaName(SurfaceFamily made, int side) {
        if (made == null) return "dirt";
        switch (made) {
            case GRASS:
                return side == 1 ? "grass_top" : "dirt";
            case SAND:
                return "sand";
            case GRAVEL:
                return "gravel";
            case STONE:
                return "stone";
            case COBBLE:
                return "cobblestone";
            case NETHER:
                return "netherrack";
            case END:
                return "end_stone";
            case SNOW:
                return "snow";
            case ICE:
                return "ice";
            default:
                return "dirt";
        }
    }

    /**
     * Whether vanilla's earth is laid in for a lawn's own - which is only ever for a grey mask.
     *
     * <ul>
     * <li><em>Grass worn through to its earth</em> ({@code revealsEarth}, not a cover), when the underside is grey. A
     * turf block whose every face is one greyscale texture is not a green thing on a brown thing; it is a grey mask
     * that
     * only becomes a color once the biome tint runs through it, and revealing it once the tint has been dropped turns
     * the
     * ground grey the moment it starts to sink.</li>
     * <li><em>A lawn's first run</em> ({@code lawnCover}), when the underside is that grey mask and is drawn in the
     * top's
     * own colors (0.9.223) - Natura's topiary grass, grey all round. That run takes the top away in patches to show the
     * underside, and where the underside is the top's own picture nothing changed until the ground sank; it now shows
     * vanilla's earth from the first step, as its sunk run always did.</li>
     * </ul>
     *
     * <p>
     * A lawn whose underside is in its top's own colors and has a color of its own - Et Futurum's nether wart block and
     * target, GalaxySpace's Barnarda C dirt - keeps its own earth, sunk and unsunk: Xep, 2026-10-10, "Before they were
     * wearing as their own block, which is what I want, it's just the top layer wasn't really showing any wear." Its
     * first run is given its own earth worn to {@link #EARTH_HEAD} instead; see {@link #earthHead}.
     */
    public static boolean standsInForEarth(boolean lawnCover, boolean revealsEarth, boolean cover,
        boolean undersideColorless, boolean undersideInTopsColors) {
        if (lawnCover) return undersideInTopsColors && undersideColorless;
        return !cover && revealsEarth && undersideColorless;
    }

    /**
     * How far a lawn with no earth of its own has worn its own earth by the end of its first run (0.9.223). The first
     * run
     * shows its underside through the top, and where the underside is drawn in the top's own colors that shows nothing;
     * so it shows the underside as the earth's own look has worn it this far, and the run under the ground starts from
     * here rather than from untouched (see {@link #earthProgress}), so the picture never steps back lighter as the
     * ground sinks.
     */
    public static final float EARTH_HEAD = 0.4f;

    /**
     * The head a lawn's earth starts from: {@link #EARTH_HEAD} where its underside is drawn in its top's own colors and
     * is no grey mask (which takes vanilla's earth instead), and nought - every lawn on earth of its own - otherwise.
     */
    public static float earthHead(boolean undersideColorless, boolean undersideInTopsColors) {
        return undersideInTopsColors && !undersideColorless ? EARTH_HEAD : 0f;
    }

    /**
     * Where along its own run an earth that starts at {@code head} is, at {@code progress} along the run under ground.
     */
    public static float earthProgress(float progress, float head) {
        return head + (1f - head) * progress;
    }

    /** Whether a sprite is a lawn's first run: grass drawn as grass, its top over its underside. */
    public static boolean lawnCover(SurfaceFamily originFamily, SurfaceFamily appearance, boolean cover) {
        return cover && originFamily == SurfaceFamily.GRASS && appearance == SurfaceFamily.GRASS;
    }

    /**
     * How much of an underside has to be drawn in colors its top uses before it is no earth of its own. Measured on
     * 2026-10-10 against the blocks themselves: the four with no visible first run came to 1.00 (the target's side
     * against its top included), and every lawn on earth of its own measured - vanilla grass, mycelium and podzol on
     * dirt, Botania's dry grass, Biomes O' Plenty's lawns, GalaxySpace's Barnarda C grass on its own purple dirt - to
     * 0.02 or less. Exact colors, so an earth merely near its lawn's colors is never taken for none.
     */
    public static final float TOPS_COLORS = 0.9f;

    /**
     * Whether an underside is drawn in its top's own colors: at least {@link #TOPS_COLORS} of its opaque pixels in a
     * color, exactly, that some opaque pixel of the top has. The top's own picture is, and so is the same set of colors
     * rearranged; earth beneath a lawn is not. Sizes may differ - it is the colors that are asked, not their places.
     */
    public static boolean inTheTopsColors(int[] top, int[] under) {
        if (top == null || under == null) return false;
        java.util.HashSet<Integer> colors = new java.util.HashSet<Integer>();
        for (int argb : top) {
            if (((argb >>> 24) & 0xFF) != 0) colors.add(Integer.valueOf(argb & 0xFFFFFF));
        }
        if (colors.isEmpty()) return false;
        int opaque = 0;
        int shared = 0;
        for (int argb : under) {
            if (((argb >>> 24) & 0xFF) == 0) continue;
            opaque++;
            if (colors.contains(Integer.valueOf(argb & 0xFFFFFF))) shared++;
        }
        return opaque > 0 && shared >= TOPS_COLORS * opaque;
    }

    /**
     * The vanilla texture laid in for earth that has none of its own: the underside of what the sprite is made of.
     */
    public static String standInName(SurfaceFamily originFamily, SurfaceFamily appearance) {
        return vanillaName(madeOf(originFamily, appearance), 0);
    }

    /**
     * True when every pixel is a shade of grey, so the texture carries no color of its own.
     *
     * <p>
     * The signal that a texture is meant to be tinted rather than looked at. Vanilla's grass is drawn this way and so
     * are the mods that copy it, which is exactly the set of blocks whose "earth" is not earth at all. Fully
     * transparent pixels are ignored: an alpha of zero has no color to disagree about.
     */
    public static boolean isColorless(int[] pixels) {
        if (pixels == null || pixels.length == 0) return false;
        boolean sawAnything = false;
        for (int argb : pixels) {
            if (((argb >>> 24) & 0xFF) == 0) continue;
            sawAnything = true;
            int red = (argb >> 16) & 0xFF;
            int green = (argb >> 8) & 0xFF;
            int blue = argb & 0xFF;
            // A little slack rather than exact equality: art is authored by hand and one stray off-grey pixel should
            // not decide that a mask is a picture.
            if (Math.abs(red - green) > 4 || Math.abs(green - blue) > 4 || Math.abs(red - blue) > 4) {
                return false;
            }
        }
        return sawAnything;
    }

    /**
     * The edge a sprite built from one of a block's faces is drawn at: the width of the face's file where it could be
     * read. Where it could not, the larger of dirt and stone's edge and the width of the face paired with it, because
     * a face whose file cannot be read mostly yields nothing at build time either, and a face that yields nothing is
     * replaced by its pair - the bottom by the top, the top by the bottom - and the pair may be the larger. Mostly
     * rather than always: a face drawn by a loader of its own, with no file at its name, can still be copied out of the
     * atlas, and is scaled to this edge whatever size that loader chose, which the end of the sprite pass names.
     * Anisotropic filtering does not grow it, because a wear sprite is made from the face with that border taken off.
     */
    public static int faceEdge(int fileWidth, int pairedWidth, int wearEdge) {
        return fileWidth > 0 ? fileWidth : Math.max(wearEdge, pairedWidth);
    }

    /**
     * The edge a sprite read only from vanilla files is drawn at: its own file, else the file paired with it, else the
     * flat stand-in. Never grown, as no wear sprite is.
     */
    public static int fileEdge(int width, int pairedWidth) {
        return width > 0 ? width : pairedWidth > 0 ? pairedWidth : PLACEHOLDER_EDGE;
    }

    /**
     * The edge one appearance is drawn at, from the face it is drawn from: the larger of the two faces for a cover laid
     * over the face under it, the underside for grass worn through to its earth, and the top otherwise.
     */
    public static int appearanceEdge(boolean cover, boolean revealsEarth, int topEdge, int bottomEdge) {
        return cover ? Math.max(topEdge, bottomEdge) : revealsEarth ? bottomEdge : topEdge;
    }
}
