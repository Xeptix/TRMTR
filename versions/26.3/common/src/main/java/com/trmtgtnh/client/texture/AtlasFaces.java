package com.trmtgtnh.client.texture;

/**
 * The arithmetic of checking a wear picture's faces against the finished atlas (WP30, 0.9.224): what the atlas holds
 * for a face, as this mod's pixels, and whether it is the face the picture was composed from. Nothing here touches the
 * game, so it is held by plain tests on arrays.
 *
 * <p>
 * Why it exists at all: this edition reads every face from its file, because its pictures are served as a pack and a
 * pack is read while the atlas is being built, before any sprite has pixels. Both older editions read the atlas
 * instead - the pixels the game will actually put on screen - which covers a texture that has no file behind it, or
 * one another mod rewrites once it is loaded. So once the models are baked the faces are asked of the atlas after all,
 * and a picture whose face differs is composed again from what is on screen. A face the atlas confirms counts as taken
 * from it, as 1.7.10 counts a face it copied out of the atlas (Xep: "improvised as 1.7.10 means it").
 */
final class AtlasFaces {

    private AtlasFaces() {}

    /**
     * Frame nought of a sprite, as this mod's packed ARGB, from the atlas's own image of it - which the game keeps as
     * ABGR, one int a pixel, every frame stacked under the first. Null when there is nothing square to read.
     *
     * @param abgr  the sprite's level-nought image, row by row, {@code width} pixels a row
     * @param width the sprite's width, which is its frame's edge
     */
    static int[] frameNought(int[] abgr, int width) {
        if (abgr == null || width <= 0 || abgr.length < width * width) return null;
        int[] argb = new int[width * width];
        for (int i = 0; i < argb.length; i++) argb[i] = swapRedBlue(abgr[i]);
        return argb;
    }

    /** ARGB to ABGR and back: the two byte orders differ only in where red and blue sit. */
    static int swapRedBlue(int pixel) {
        return (pixel & 0xFF00FF00) | ((pixel >>> 16) & 0xFF) | ((pixel & 0xFF) << 16);
    }

    /**
     * Whether the face a picture was composed from is the face the atlas draws: the same edge and the same pixels. A
     * fully clear pixel matches any other fully clear one, because what color sits under no alpha is the encoder's
     * choice and not the texture's - a file read through one decoder and the same file loaded by the game can differ
     * there and nowhere else.
     */
    static boolean same(int[] composedFrom, int[] atlas) {
        if (composedFrom == null || atlas == null) return composedFrom == atlas;
        if (composedFrom.length != atlas.length) return false;
        for (int i = 0; i < atlas.length; i++) {
            int a = composedFrom[i];
            int b = atlas[i];
            if (a == b) continue;
            if ((a >>> 24) == 0 && (b >>> 24) == 0) continue;
            return false;
        }
        return true;
    }

    /** The edge of a square face, or nought for one that is not square. */
    static int edgeOf(int[] face) {
        if (face == null) return 0;
        int edge = (int) Math.round(Math.sqrt(face.length));
        return edge * edge == face.length ? edge : 0;
    }
}
