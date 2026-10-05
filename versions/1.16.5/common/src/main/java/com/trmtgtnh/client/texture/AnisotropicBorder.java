package com.trmtgtnh.client.texture;

/**
 * The border anisotropic filtering adds round a texture the game loads, and how to take it off again.
 *
 * <p>
 * With filtering on, the game loads each block texture sixteen pixels wider and taller than its file, fills the extra
 * eight on every side by wrapping the face round, and draws only the middle, pulling the sprite's texture coordinates
 * in by eight pixels. A wear sprite copies a face's pixels out of the atlas and has a loader of its own, so the game
 * neither widens it nor pulls its coordinates in, and a copy taken with the border drew the whole frame in the face's
 * place: the face shrunk in the ratio of its width to the frame's and repeated round across it from eight pixels back,
 * which is half its scale, twice across and half a face in for a sixteen-pixel face, but two thirds of it and one and a
 * half times across for a thirty-two-pixel one. The border is taken off here before anything is made from it.
 *
 * <p>
 * The wrap puts at every pixel of the widened frame the face's pixel eight rows and eight columns back, counted round
 * the face: the widened frame is the face repeated round itself, shifted eight pixels and sixteen wider than the face,
 * for a face of any width, one narrower than the border included. It is two faces across only for a sixteen-pixel
 * face; a thirty-two-pixel face gives one and a half, a four-pixel one five. A frame is counted as bordered only where
 * the wrap holds for every pixel. FaceSource crops a marked frame whether it holds or not, since the game draws only
 * the middle of a marked sprite whatever its frames carry, and asks bordered only to count the marked frames that do
 * not carry the wrap. Art that already repeats in exactly that way cannot be told apart, and is counted as wrapped,
 * which changes nothing drawn.
 *
 * <p>
 * Nothing from the game is in it; it is on the list CoreStaysPortableTest keeps free of Minecraft, and
 * AnisotropicBorderTest holds it to a transcription of the game's own loops.
 */
public final class AnisotropicBorder {

    /** Pixels of border on each side of a face the game loads with anisotropic filtering: half the growth. */
    public static final int WIDTH = AtlasPlan.ANISOTROPIC_GROWTH / 2;

    private AnisotropicBorder() {}

    /**
     * Whether a frame of this edge is a face with the filtering border round it, pixel for pixel as the game wraps it.
     * False for no frame, for an edge with no face inside its border, and for a frame shorter than its edge squared; a
     * longer one is read only to its edge.
     */
    public static boolean bordered(int[] frame, int grownEdge) {
        if (!fits(frame, grownEdge)) return false;
        int face = grownEdge - AtlasPlan.ANISOTROPIC_GROWTH;
        for (int y = 0; y < grownEdge; y++) {
            int row = y * grownEdge;
            int middleRow = (WIDTH + Math.floorMod(y - WIDTH, face)) * grownEdge + WIDTH;
            for (int x = 0; x < grownEdge; x++) {
                if (frame[row + x] != frame[middleRow + Math.floorMod(x - WIDTH, face)]) return false;
            }
        }
        return true;
    }

    /**
     * The face inside a frame of this edge, sixteen pixels narrower and shorter, as a new array; null where bordered
     * would refuse the frame for its size or its length. It does not ask whether the frame is bordered: the game draws
     * the middle of a marked frame whatever it holds, so the crop follows the mark, and bordered is asked apart, to
     * count. It never writes to the frame.
     */
    public static int[] crop(int[] frame, int grownEdge) {
        if (!fits(frame, grownEdge)) return null;
        int face = grownEdge - AtlasPlan.ANISOTROPIC_GROWTH;
        int[] out = new int[face * face];
        for (int y = 0; y < face; y++) {
            System.arraycopy(frame, (y + WIDTH) * grownEdge + WIDTH, out, y * face, face);
        }
        return out;
    }

    /** Whether a frame is long enough for its edge and that edge has a face inside its border. */
    private static boolean fits(int[] frame, int grownEdge) {
        return frame != null && grownEdge > AtlasPlan.ANISOTROPIC_GROWTH
            && frame.length >= (long) grownEdge * grownEdge;
    }
}
