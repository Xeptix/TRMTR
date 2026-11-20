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
            case FLESH:
                // No vanilla flesh: soul sand is the Nether floor it is all but a twin of (0.9.229, W4 T17).
                return "soul_sand";
            case TAINT:
                // No vanilla taint: mycelium's top is the purple living crust nearest a crust of it.
                return "mycelium_top";
            case SCULK:
                // Vanilla's own from 1.19, and Et Futurum Requiem's in the 1.7.10 edition, under the same name. Where
                // no file of that name can be read, its second choice is obsidian (secondChoice), so this one table
                // stays the same bytes in every edition (0.9.229, W4).
                return "sculk";
            case CLOUD:
                // No vanilla cloud: white wool is fluffy cloud's twin in all a ghost reports, as soft to dig, as
                // quiet, and as white. Spelt as the older editions spell it; VanillaNames gives the 1.13 name.
                return "wool_colored_white";
            default:
                return "dirt";
        }
    }

    /**
     * The vanilla texture a family's fallback set reads for one appearance and side: what the sprite is made of
     * ({@link #madeOf}, {@link #vanillaName}), except sculk worn into stone, which is the deepslate the deep dark's
     * sculk is spread over, {@code deepslate_top} on top and {@code deepslate} on the sides (0.9.229, W4; Xep,
     * 2026-11-11, "Yes, by default"). Without it sculk worn into stone would show vanilla's smooth stone, which is not
     * the deep dark's rock. Where no deepslate file can be read, {@link #secondChoice} is stone's own.
     *
     * <p>
     * Not grass's route: grass shows the earth on the block's own underside ({@link #revealsEarth}), and sculk's
     * underside is sculk, so the deepslate comes from a named picture instead.
     */
    public static String fallbackName(SurfaceFamily originFamily, SurfaceFamily appearance, int side) {
        SurfaceFamily made = madeOf(originFamily, appearance);
        if (originFamily == SurfaceFamily.SCULK && made == SurfaceFamily.STONE) {
            return side == 1 ? "deepslate_top" : "deepslate";
        }
        return vanillaName(made, side);
    }

    /**
     * What a vanilla texture of this table is read as where its own file cannot be: deepslate falls back to stone,
     * and sculk to obsidian, the nearest vanilla face to it ({@code #0f0a18} against sculk's {@code #0d1b21}) and
     * one every edition has. Deepslate and sculk exist from 1.17 and 1.19, and in the 1.7.10 edition only where Et
     * Futurum Requiem ships them. Null for every other name, which has no second choice.
     */
    public static String secondChoice(String name) {
        if ("deepslate_top".equals(name) || "deepslate".equals(name)) return "stone";
        if ("sculk".equals(name)) return "obsidian";
        return null;
    }

    /**
     * What a lawn shows on a face its own picture says is its top: {@code grass_side} on a flank, {@code dirt}
     * underneath,
     * or null where the face is the block's own to draw (0.9.229, W6 S2).
     *
     * <p>
     * A lawn is a block that draws vanilla's grass top, and everything downstream takes it at its word: its flank is
     * drawn untinted under a tinted fringe, past grass it shows its underside, and the wall that recedes is made from
     * its flank. All of that is right for grass's own side and earth, and wrong for the top, which is stored grey and
     * is
     * green only on a tinted face: a block that draws grass_top all round - Grass Overhaul's sod blocks do, by design,
     * and a resource pack doing OptiFine's Better Grass may - wore a grey wall. So it is read as the lawn it is, and
     * the
     * worn wall shows earth and thinned fringe; its unworn neighbours stay green, which is the look a Better Grass
     * player
     * already has, and the 1.7.10 edition's MixinOptiFineGrassSide keeps the same rule under OptiFine's.
     *
     * <p>
     * The grey top alone, for the reason the fringe is asked the same question: a modded turf's top drawn on its flanks
     * is in its own colors and is that block's business. The name is compared however an edition or a model spells it,
     * because one texture has several spellings ({@code grass_top}, {@code minecraft:blocks/grass_top},
     * {@code block/grass_block_top}).
     *
     * @param top  the name the block's top draws
     * @param face the name this face draws, or null
     * @param side the face, in the mod's numbering: 0 down, 1 up, 2-5 the flanks
     */
    public static String lawnFace(String top, String face, int side) {
        if (side == 1 || top == null || face == null) return null;
        String bare = bareName(top);
        if (!bare.equals("grass_top") && !bare.equals("grass_block_top")) return null;
        if (!bare.equals(bareName(face))) return null;
        return side == 0 ? "dirt" : "grass_side";
    }

    /** A vanilla block texture's name with vanilla's namespace and its folder taken off; any other name as it is. */
    private static String bareName(String name) {
        String bare = name.startsWith("minecraft:") ? name.substring("minecraft:".length()) : name;
        if (bare.startsWith("blocks/")) return bare.substring("blocks/".length());
        return bare.startsWith("block/") ? bare.substring("block/".length()) : bare;
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
