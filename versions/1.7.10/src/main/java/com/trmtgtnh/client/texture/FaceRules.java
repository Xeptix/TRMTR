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
     * Whether grass worn through to its earth has no earth to show, so vanilla's is laid in its place. A turf block
     * whose every face is one greyscale texture is not a green thing on a brown thing; it is a grey mask that only
     * becomes a color once the biome tint runs through it, and revealing it once the tint has been dropped turns the
     * ground grey the moment it starts to sink.
     */
    public static boolean standsInForEarth(boolean cover, boolean revealsEarth, boolean undersideColorless) {
        return !cover && revealsEarth && undersideColorless;
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
