package com.trmtgtnh.client.texture;

/**
 * How far a worn square's texture coordinates stay inside its picture's edge (0.9.229), the same in every edition.
 *
 * <p>
 * A face drawn with coordinates exactly on its sprite's edge samples the picture beside it in the atlas wherever a
 * pixel's centre falls on the face's edge: the GPU's interpolation carries the coordinate a hair past the edge, and
 * the nearest texel there is the neighbour's. Found on 2026-11-16 in the 1.12.2 rotation photographs: the harness's
 * camera put one block edge exactly on a row of pixel centres, and that one-pixel row carried another picture's texels
 * in every field, grey across worn nether bricks where Xep circled it. Vanilla's own faces from 1.14 on are pulled in
 * by a shrink ratio for this; these quads are this mod's, so they are pulled in here.
 *
 * <p>
 * Clamped rather than scaled: a coordinate already inside the picture by more than the inset is left exactly where it
 * was, so a box covering half a cell still takes the half of the picture it covers, and only the edges move.
 */
public final class EdgeInset {

    private EdgeInset() {}

    /**
     * The inset, in texels of the picture: a thirty-second of one. Far above the GPU's sub-texel precision (a
     * two-hundred-and-fifty-sixth), far below anything the eye can see - the picture is drawn 0.4% smaller across a
     * sixteen-pixel face.
     */
    public static final float TEXELS = 1F / 32F;

    /**
     * A coordinate in sixteenths of a picture, the units every edition's sprites take, held at least {@link #TEXELS}
     * of one of its texels inside 0 and 16. {@code texels} is the picture's width (for u) or height (for v) in pixels.
     */
    public static float clamp(float sixteenths, int texels) {
        float margin = TEXELS * 16F / Math.max(1, texels);
        return Math.max(margin, Math.min(16F - margin, sixteenths));
    }
}
