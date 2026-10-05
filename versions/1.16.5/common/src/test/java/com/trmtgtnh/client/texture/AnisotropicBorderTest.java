package com.trmtgtnh.client.texture;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The border anisotropic filtering adds round a texture the game loads, and taking it off, held to the game's own
 * loops.
 *
 * <p>
 * The game's routine cannot be called from a test: TextureUtil's static initialiser builds a texture, which needs a GL
 * context. So its loops are transcribed below, line for line, and every frame here is made by the transcription rather
 * than by the rule AnisotropicBorder checks. A rule checked against frames made by the same rule would pass whatever
 * the game did; the transcription is pinned to hand-worked pixels first, so a slip in copying it shows as well.
 */
class AnisotropicBorderTest {

    /**
     * TextureUtil.prepareAnisotropicData(data, edge, edge, 8), lines 374-413, over a face copied into the head of a
     * frame sixteen wider and taller, as TextureAtlasSprite.prepareAnisotropicFiltering (380-404) copies it.
     */
    private static int[] vanillaWrap(int[] face, int edge) {
        int border = 8;
        int grown = edge + 2 * border;
        int[] data = new int[grown * grown];
        System.arraycopy(face, 0, data, 0, face.length);
        for (int row = edge - 1; row >= 0; --row) {
            int from = row * edge;
            int to = border + (row + border) * grown;
            for (int step = 0; step < border; step += edge) {
                int run = Math.min(edge, border - step);
                System.arraycopy(data, from + edge - run, data, to - step - run, run);
            }
            System.arraycopy(data, from, data, to, edge);
            for (int step = 0; step < border; step += edge) {
                System.arraycopy(data, from, data, to + edge + step, Math.min(edge, border - step));
            }
        }
        for (int step = 0; step < border; step += edge) {
            int rows = Math.min(edge, border - step);
            System.arraycopy(data, (border + edge - rows) * grown, data, (border - step - rows) * grown, grown * rows);
        }
        for (int step = 0; step < border; step += edge) {
            int rows = Math.min(edge, border - step);
            System.arraycopy(data, border * grown, data, (edge + border + step) * grown, grown * rows);
        }
        return data;
    }

    /** A face of distinct opaque pixels: its row in the green channel and its column in the blue. */
    private static int[] rowsAndColumns(int edge) {
        int[] face = new int[edge * edge];
        for (int y = 0; y < edge; y++) {
            for (int x = 0; x < edge; x++) {
                face[y * edge + x] = 0xFF000000 | (y << 8) | x;
            }
        }
        return face;
    }

    @Test
    @DisplayName("a bordered frame is the face tiled two by two from eight pixels in, as the game fills it")
    void aBorderedFrameIsTheFaceTiledTwoByTwo() {
        int[] face = rowsAndColumns(16);
        int[] grown = vanillaWrap(face, 16);
        assertEquals(0xFF000808, grown[0], "the top left corner is the face's row 8, column 8");
        assertEquals(0xFF000000, grown[8 * 32 + 8], "the face begins eight in");
        assertEquals(0xFF000707, grown[31 * 32 + 31], "the bottom right corner is row 7, column 7");
        assertEquals(0xFF000008, grown[8 * 32 + 0], "the left border of row 0 is its column 8");
        assertEquals(0xFF00080F, grown[0 * 32 + 23], "the top border above column 15 is row 8");
        assertTrue(AnisotropicBorder.bordered(grown, 32));
        assertArrayEquals(face, AnisotropicBorder.crop(grown, 32));
    }

    @Test
    @DisplayName("a face narrower than the border wraps the same way, and so does a wider one")
    void aNarrowFaceWrapsTheSameWay() {
        int[] four = rowsAndColumns(4);
        int[] grownFour = vanillaWrap(four, 4);
        assertEquals(0xFF000000, grownFour[0], "row 0, column 0");
        assertEquals(0xFF000303, grownFour[19 * 20 + 19], "row 3, column 3");
        assertEquals(0xFF000001, grownFour[0 * 20 + 5], "row 0, column 1");
        assertTrue(AnisotropicBorder.bordered(grownFour, 20));
        assertArrayEquals(four, AnisotropicBorder.crop(grownFour, 20));

        int[] three = rowsAndColumns(3);
        int[] grownThree = vanillaWrap(three, 3);
        assertEquals(0xFF000101, grownThree[0], "row 1, column 1");
        assertEquals(0xFF000000, grownThree[8 * 19 + 8], "the face begins eight in");
        assertEquals(0xFF000101, grownThree[18 * 19 + 18], "row 1, column 1 again");
        assertTrue(AnisotropicBorder.bordered(grownThree, 19));
        assertArrayEquals(three, AnisotropicBorder.crop(grownThree, 19));

        int[] one = { 0xFF123456 };
        int[] grownOne = vanillaWrap(one, 1);
        for (int i = 0; i < grownOne.length; i++) {
            assertEquals(0xFF123456, grownOne[i], "pixel " + i);
        }
        assertTrue(AnisotropicBorder.bordered(grownOne, 17));
        assertArrayEquals(one, AnisotropicBorder.crop(grownOne, 17));

        int[] wide = rowsAndColumns(32);
        int[] grownWide = vanillaWrap(wide, 32);
        assertTrue(AnisotropicBorder.bordered(grownWide, 48));
        assertArrayEquals(wide, AnisotropicBorder.crop(grownWide, 48));
    }

    @Test
    @DisplayName("a frame without the border is never taken for one")
    void aFrameWithoutTheBorderIsNeverTakenForOne() {
        Random random = new Random(212);
        int[] noise = new int[32 * 32];
        for (int i = 0; i < noise.length; i++) {
            noise[i] = random.nextInt() | 0xFF000000;
        }
        assertFalse(AnisotropicBorder.bordered(noise, 32));

        int[] corner = vanillaWrap(rowsAndColumns(16), 16);
        corner[0] ^= 1;
        assertFalse(AnisotropicBorder.bordered(corner, 32), "one border pixel out");

        int[] middle = vanillaWrap(rowsAndColumns(16), 16);
        middle[20 * 32 + 20] ^= 1;
        assertFalse(
            AnisotropicBorder.bordered(middle, 32),
            "one face pixel out, which its copies in the border no longer match");
    }

    @Test
    @DisplayName("a frame too small or too short is never cropped")
    void aFrameTooSmallOrTooShortIsNeverCropped() {
        assertFalse(AnisotropicBorder.bordered(null, 32));
        assertFalse(AnisotropicBorder.bordered(new int[256], 16), "no face inside the border");
        assertFalse(AnisotropicBorder.bordered(new int[1000], 32), "shorter than its edge squared");
        assertNull(AnisotropicBorder.crop(new int[1000], 32));
        assertNull(AnisotropicBorder.crop(null, 32));

        int[] face = rowsAndColumns(16);
        int[] longer = Arrays.copyOf(vanillaWrap(face, 16), 32 * 32 + 5);
        assertTrue(AnisotropicBorder.bordered(longer, 32), "a frame longer than its edge squared is read to its edge");
        assertArrayEquals(face, AnisotropicBorder.crop(longer, 32));
    }

    @Test
    @DisplayName("the crop follows the mark: a frame without the border is still cut to its middle, as the game draws it")
    void theCropFollowsTheMark() {
        int[] frame = new int[32 * 32];
        for (int i = 0; i < frame.length; i++) {
            frame[i] = i + 1;
        }
        assertFalse(AnisotropicBorder.bordered(frame, 32), "every pixel differs, so nothing wraps");

        int[] middle = new int[16 * 16];
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                middle[y * 16 + x] = frame[(y + 8) * 32 + x + 8];
            }
        }
        assertArrayEquals(middle, AnisotropicBorder.crop(frame, 32));
        assertEquals(1, frame[0], "the frame is never written to");
    }

    @Test
    @DisplayName("flat colour reads as bordered, and cropping it changes nothing")
    void flatColourReadsAsBordered() {
        int[] flat = new int[32 * 32];
        Arrays.fill(flat, 0xFF7A6A55);
        assertTrue(AnisotropicBorder.bordered(flat, 32), "the documented false positive");
        int[] expected = new int[16 * 16];
        Arrays.fill(expected, 0xFF7A6A55);
        assertArrayEquals(expected, AnisotropicBorder.crop(flat, 32));
    }
}
