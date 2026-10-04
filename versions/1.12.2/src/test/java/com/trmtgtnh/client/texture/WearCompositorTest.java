package com.trmtgtnh.client.texture;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The pixel arithmetic that decides what a worn block looks like.
 *
 * <p>
 * The properties worth pinning down are the ones that would go wrong quietly. A coverage cut
 * that removes the wrong fraction, or a ratio that does not transfer cleanly to a differently
 * coloured texture, would still produce a picture — just a subtly bad one, on every worn block
 * in the world.
 */
class WearCompositorTest {

    private static final int SIZE = 16;

    @Test
    @DisplayName("a coverage cut leaves the requested fraction of the block intact")
    void coverageCutsTheRightFraction() {
        float[] order = new float[SIZE * SIZE];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }

        for (float wanted : new float[] { 0.91f, 0.76f, 0.63f, 0.41f, 0.23f, 0.5f }) {
            float cut = WearCompositor.coverageThreshold(order, wanted);
            int survivors = 0;
            for (float value : order) {
                if (value >= cut) survivors++;
            }
            int expected = Math.round(wanted * order.length);
            assertEquals(expected, survivors, "survivors at coverage " + wanted);
        }
    }

    @Test
    @DisplayName("cutting as many gradations as were drawn reproduces the drawn masks exactly")
    void fiveStagesReproduceTheAuthoredMasks() {
        // Five nested masks: each stage removes a further quarter-ish of the block, which is
        // the shape of the real art.
        int stages = 5;
        int[][] masks = new int[stages][];
        float[] coverage = { 0.90f, 0.75f, 0.60f, 0.40f, 0.25f };
        for (int stage = 0; stage < stages; stage++) {
            int[] mask = new int[SIZE * SIZE];
            int keep = Math.round(coverage[stage] * mask.length);
            for (int i = 0; i < mask.length; i++) {
                mask[i] = i < keep ? 255 : 0;
            }
            masks[stage] = mask;
        }

        float[] order = WearCompositor.wearOrder(masks, SIZE, 0);

        for (int stage = 0; stage < stages; stage++) {
            float cut = WearCompositor.coverageThreshold(order, coverage[stage]);
            for (int i = 0; i < order.length; i++) {
                boolean survivesHere = order[i] >= cut;
                boolean survivesInArt = masks[stage][i] >= 128;
                assertEquals(survivesInArt, survivesHere, "pixel " + i + " at stage " + stage);
            }
        }
    }

    @Test
    @DisplayName("wear order runs from first-removed to never-removed")
    void wearOrderIsMonotonic() {
        int[][] masks = new int[3][];
        for (int stage = 0; stage < 3; stage++) {
            int[] mask = new int[SIZE * SIZE];
            for (int i = 0; i < mask.length; i++) {
                // Pixel i disappears at stage i % 4; stage 3 means it never does.
                mask[i] = (i % 4) > stage ? 255 : 0;
            }
            masks[stage] = mask;
        }

        float[] order = WearCompositor.wearOrder(masks, SIZE, 0);
        for (int i = 0; i < order.length; i++) {
            int expected = Math.min(i % 4, 3);
            assertEquals(expected, (int) Math.floor(order[i]), "removal stage of pixel " + i);
            float fraction = order[i] - (float) Math.floor(order[i]);
            assertTrue(fraction >= 0f && fraction < 1f, "tiebreak stays inside its stage");
        }
    }

    @Test
    @DisplayName("a stage divided by the texture it was drawn on is the identity ratio")
    void ratioAgainstItselfIsIdentity() {
        Random random = new Random(7L);
        int[] texture = new int[SIZE * SIZE];
        for (int i = 0; i < texture.length; i++) {
            // Kept off zero: a black reference pixel carries no ratio information.
            texture[i] = 0xFF000000 | (randomChannel(random) << 16)
                | (randomChannel(random) << 8)
                | randomChannel(random);
        }

        float[] ratio = WearCompositor.modulationRatio(texture, texture, SIZE);
        for (float value : ratio) {
            assertEquals(1f, value, 0.0001f, "identity ratio");
        }

        int[] applied = WearCompositor.applyModulation(texture, SIZE, ratio, SIZE, 1f);
        assertArrayEquals(texture, applied, "identity ratio must leave the texture untouched");
    }

    private static int randomChannel(Random random) {
        return 1 + random.nextInt(255);
    }

    @Test
    @DisplayName("wear transfers to a differently coloured texture without dragging its hue")
    void ratioTransfersAcrossPalettes() {
        // A reference that is plain grey, and art that halves its brightness in one half.
        int[] reference = new int[SIZE * SIZE];
        int[] art = new int[SIZE * SIZE];
        for (int i = 0; i < reference.length; i++) {
            reference[i] = 0xFF808080;
            art[i] = i < reference.length / 2 ? 0xFF404040 : 0xFF808080;
        }
        float[] ratio = WearCompositor.modulationRatio(art, reference, SIZE);

        // Applied to a strongly red texture, the worn half must be a darker red — not grey.
        int[] red = new int[SIZE * SIZE];
        for (int i = 0; i < red.length; i++) {
            red[i] = 0xFFC03010;
        }
        int[] worn = WearCompositor.applyModulation(red, SIZE, ratio, SIZE, 1f);

        int wornPixel = worn[0];
        int intactPixel = worn[red.length - 1];
        assertEquals(0xFFC03010, intactPixel, "the untouched half keeps its colour");
        assertEquals(0x60, (wornPixel >>> 16) & 0xFF, 1, "red halved");
        assertEquals(0x18, (wornPixel >>> 8) & 0xFF, 1, "green halved");
        assertEquals(0x08, wornPixel & 0xFF, 1, "blue halved");
    }

    @Test
    @DisplayName("wear strength scales how far the pattern is applied")
    void strengthScalesTheEffect() {
        int[] base = new int[SIZE * SIZE];
        for (int i = 0; i < base.length; i++) {
            base[i] = 0xFF808080;
        }
        float[] halve = new float[SIZE * SIZE * 3];
        for (int i = 0; i < halve.length; i++) {
            halve[i] = 0.5f;
        }

        assertArrayEquals(base, WearCompositor.applyModulation(base, SIZE, halve, SIZE, 0f), "strength 0 is a no-op");

        int half = WearCompositor.applyModulation(base, SIZE, halve, SIZE, 1f)[0] & 0xFF;
        int quarter = WearCompositor.applyModulation(base, SIZE, halve, SIZE, 0.5f)[0] & 0xFF;
        assertEquals(0x40, half, 1, "full strength halves");
        assertEquals(0x60, quarter, 1, "half strength goes a quarter of the way");
    }

    @Test
    @DisplayName("cancelling a tint at full strength restores the original colour")
    void fullCompensationRoundTrips() {
        int tintColour = 0x91BD59; // vanilla plains grass
        int[] earth = new int[SIZE * SIZE];
        for (int i = 0; i < earth.length; i++) {
            earth[i] = 0xFF866043; // vanilla dirt
        }

        int[] corrected = WearCompositor.precompensate(earth, tintColour, 1f);
        int[] rendered = WearCompositor.tint(corrected, tintColour);

        for (int i = 0; i < rendered.length; i++) {
            for (int channel = 0; channel < 3; channel++) {
                int shift = 16 - channel * 8;
                int expected = (earth[i] >>> shift) & 0xFF;
                int actual = (rendered[i] >>> shift) & 0xFF;
                assertEquals(expected, actual, 2, "channel " + channel + " survives the round trip");
            }
        }
    }

    @Test
    @DisplayName("partial compensation lands between doing nothing and correcting fully")
    void partialCompensationIsBetweenTheExtremes() {
        int tintColour = 0x91BD59;
        int[] earth = { 0xFF866043 };

        int none = WearCompositor.tint(WearCompositor.precompensate(earth, tintColour, 0f), tintColour)[0] & 0xFF;
        int part = WearCompositor.tint(WearCompositor.precompensate(earth, tintColour, 0.7f), tintColour)[0] & 0xFF;
        int full = WearCompositor.tint(WearCompositor.precompensate(earth, tintColour, 1f), tintColour)[0] & 0xFF;

        assertTrue(none < part && part < full, "0 < 0.7 < 1 in effect: " + none + " " + part + " " + full);
    }

    @Test
    @DisplayName("compensation never overflows, however dark the tint")
    void compensationClampsInsteadOfOverflowing() {
        int[] bright = new int[SIZE * SIZE];
        for (int i = 0; i < bright.length; i++) {
            bright[i] = 0xFFF0E8E0;
        }
        int[] corrected = WearCompositor.precompensate(bright, 0x203010, 1f);
        for (int pixel : corrected) {
            for (int channel = 0; channel < 3; channel++) {
                int value = (pixel >>> (16 - channel * 8)) & 0xFF;
                assertTrue(value >= 0 && value <= 255, "channel stayed in range");
            }
        }
    }

    @Test
    @DisplayName("polish flattens a surface toward its own average and darkens it")
    void polishFlattensAndDarkens() {
        // A checkerboard of light and dark, so flattening is measurable.
        int[] base = new int[SIZE * SIZE];
        for (int i = 0; i < base.length; i++) {
            base[i] = (i % 2 == 0) ? 0xFFC0C0C0 : 0xFF404040;
        }

        int[] worn = WearCompositor.applyPolish(base, SIZE, 0.5f, 0.8f, 1f);

        int light = worn[0] & 0xFF;
        int dark = worn[1] & 0xFF;
        assertTrue(light - dark < 0x80 - 0x00, "contrast is reduced");
        assertTrue(light < 0xC0 && dark < 0x40 + 0x30, "the surface is darker overall");

        // And the result keeps the surface's own hue: a red checkerboard stays red.
        int[] red = new int[SIZE * SIZE];
        for (int i = 0; i < red.length; i++) {
            red[i] = (i % 2 == 0) ? 0xFFC03010 : 0xFF401004;
        }
        int wornRed = WearCompositor.applyPolish(red, SIZE, 0.5f, 0.8f, 1f)[0];
        int r = (wornRed >>> 16) & 0xFF;
        int g = (wornRed >>> 8) & 0xFF;
        int b = wornRed & 0xFF;
        assertTrue(r > g && g >= b, "still reads as red: " + r + "," + g + "," + b);
    }

    @Test
    @DisplayName("polish at zero strength leaves the surface alone")
    void polishAtZeroStrengthIsANoOp() {
        int[] base = new int[SIZE * SIZE];
        for (int i = 0; i < base.length; i++) {
            base[i] = 0xFF7A6A55;
        }
        assertArrayEquals(base, WearCompositor.applyPolish(base, SIZE, 0.2f, 0.5f, 0f));
    }

    @Test
    @DisplayName("a curve sampled at its own length returns the authored values")
    void curveIsExactAtAuthoredLength() {
        float[] curve = { 0.91f, 0.76f, 0.63f, 0.41f, 0.23f };
        for (int i = 0; i < curve.length; i++) {
            assertEquals(curve[i], WearCompositor.alongCurve(curve, i, curve.length), 0.0001f, "stage " + i);
        }
        // And more gradations stay inside the authored range, in order.
        float previous = Float.MAX_VALUE;
        for (int i = 0; i < 12; i++) {
            float value = WearCompositor.alongCurve(curve, i, 12);
            assertTrue(value <= previous + 0.0001f, "coverage never increases as wear deepens");
            assertTrue(value >= curve[curve.length - 1] - 0.0001f && value <= curve[0] + 0.0001f, "inside range");
            previous = value;
        }
    }

    // ------------------------------------------------------------------
    // The five later looks
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a lighter crack really is lighter than the crack it is lighter than")
    void theLiteCrackIsLighter() {
        for (int[] source : new int[][] { speckled(0x8A8A8A, 34, 71L), speckled(0x6E6A66, 6, 72L) }) {
            int[] full = WearCompositor.applyCrack(source, SIZE, 0.9f, 1.0f, 0);
            int[] lite = WearCompositor.applyCrack(source, SIZE, 0.495f, 0.55f, 0);
            assertTrue(meanOfLuma(lite) > meanOfLuma(full), "a lite crack should leave the face brighter overall");
            assertTrue(darkest(lite) > darkest(full), "and its deepest fissure should be shallower");
        }
    }

    @Test
    @DisplayName("a lighter rub touches less of the face and cuts less deeply into what it touches")
    void theLiteRubIsLighter() {
        for (int[] source : new int[][] { speckled(0x8A7A5A, 24, 73L), speckled(0x6E6A66, 6, 74L) }) {
            float[] order = WearCompositor.sourceWearOrder(source, SIZE, 0);
            int[] full = WearCompositor.applyOverlay(source, SIZE, order, SIZE, 0.72f, 1.0f);
            int[] lite = WearCompositor.applyOverlay(source, SIZE, order, SIZE, 0.396f, 0.55f);
            assertTrue(untouched(source, lite) > untouched(source, full), "a lite rub should leave more of it alone");
            assertTrue(meanOfLuma(lite) > meanOfLuma(full), "and take less light out of the rest");
        }
    }

    @Test
    @DisplayName("the heavy smoothing flattens by the amount its two constants promise")
    void theHeavySmoothingFlattens() {
        for (int[] source : new int[][] { speckled(0x8A8A8A, 34, 75L), speckled(0x6E6A66, 6, 76L) }) {
            double was = spreadOfLuma(source);
            double plain = spreadOfLuma(
                WearCompositor
                    .applyPolish(source, SIZE, WearCompositor.POLISH_CONTRAST, WearCompositor.POLISH_LUMA, 1f));
            double heavy = spreadOfLuma(
                WearCompositor.applyPolish(source, SIZE, WearCompositor.HEAVY_CONTRAST, WearCompositor.HEAVY_LUMA, 1f));
            // applyPolish is a linear remap of luma, so the surviving spread is the two constants
            // multiplied together, to within per-channel rounding. Pinning both at once is the
            // point: anybody tuning either without meaning to fails here rather than in a screenshot.
            assertEquals(WearCompositor.POLISH_CONTRAST * WearCompositor.POLISH_LUMA, plain / was, 0.03d);
            assertEquals(WearCompositor.HEAVY_CONTRAST * WearCompositor.HEAVY_LUMA, heavy / was, 0.03d);
            assertTrue(heavy > was * 0.15d, "a buffed cobble is still a cobble, not a grey square");
        }
    }

    @Test
    @DisplayName("buffing before cracking keeps the fissures that buffing afterwards would rub out")
    void theCompoundOrderIsTheOneThatWorks() {
        for (int[] source : new int[][] { speckled(0x8A8A8A, 34, 77L), speckled(0x6E6A66, 6, 78L) }) {
            int[] forward = WearCompositor.applySmoothedCrack(source, SIZE, 0.72f, 0.8f, 1f, 0);
            int[] backward = WearCompositor.applyPolish(
                WearCompositor.applyCrack(source, SIZE, 0.72f, 0.8f, 0),
                SIZE,
                WearCompositor.POLISH_CONTRAST,
                WearCompositor.POLISH_LUMA,
                1f);
            assertTrue(
                spreadOfLuma(forward) > spreadOfLuma(backward),
                "polishing a cracked face closes the cracks it was given");
            assertTrue(darkest(forward) < darkest(backward), "and lifts the fissure floor with everything else");
        }
    }

    @Test
    @DisplayName("each compound with no buffing in it is exactly the operator it composes")
    void aCompoundWithoutItsBuffingIsItsParent() {
        int[] source = speckled(0x8A8A8A, 34, 79L);
        assertArrayEquals(
            WearCompositor.applyCrack(source, SIZE, 0.72f, 0.8f, 2),
            WearCompositor.applySmoothedCrack(source, SIZE, 0.72f, 0.8f, 0f, 2),
            "a smoothed crack with nothing smoothed is a crack");
        float[] order = WearCompositor.sourceWearOrder(source, SIZE, 2);
        assertArrayEquals(
            WearCompositor.applyOverlay(source, SIZE, order, SIZE, 0.6f, 0.9f),
            WearCompositor.applySmoothedRub(source, SIZE, 0.6f, 0.9f, 0f, 2),
            "and a smoothed rub with nothing smoothed is a rub");
    }

    @Test
    @DisplayName("cracking before rubbing keeps the pixels a rub exists to leave alone")
    void theCrackedRubOrderIsTheOneThatWorks() {
        for (int[] source : new int[][] { speckled(0x8A8A8A, 34, 81L), speckled(0x6E6A66, 6, 82L) }) {
            int[] forward = WearCompositor.applyCrackedRub(source, SIZE, 0.61f, 1f, 0.9f, 1f, 0);
            // The reverse written out longhand, which is what a later tidy-up would reach for.
            int[] cracked = WearCompositor.applyCrack(source, SIZE, 0.9f, 1f, 0);
            float[] order = WearCompositor.sourceWearOrder(source, SIZE, 0);
            int[] rubbedFirst = WearCompositor
                .applyCrack(WearCompositor.applyOverlay(source, SIZE, order, SIZE, 0.61f, 1f), SIZE, 0.9f, 1f, 0);

            assertTrue(
                untouched(cracked, forward) > 0,
                "cracking first must leave the pixels the track does not claim exactly as the crack drew them");
            assertEquals(
                0,
                untouched(cracked, rubbedFirst),
                "cracking last reaches every pixel, whatever its coverage says");
            assertTrue(
                spreadOfLuma(forward) > spreadOfLuma(rubbedFirst),
                "and flattens the face it was handed into the bargain");
        }
    }

    /** The mean luma of a face, which is how much light is left in it. */
    private static double meanOfLuma(int[] pixels) {
        double total = 0;
        for (int pixel : pixels) {
            total += (((pixel >>> 16) & 0xFF) + ((pixel >>> 8) & 0xFF) + (pixel & 0xFF)) / 3.0;
        }
        return total / pixels.length;
    }

    /** The darkest pixel on it, which on a cracked face is the bottom of a fissure. */
    private static double darkest(int[] pixels) {
        double lowest = 255;
        for (int pixel : pixels) {
            double luma = (((pixel >>> 16) & 0xFF) + ((pixel >>> 8) & 0xFF) + (pixel & 0xFF)) / 3.0;
            if (luma < lowest) lowest = luma;
        }
        return lowest;
    }

    /** How many pixels came back exactly as they went in, which is what the rub is measured by. */
    private static int untouched(int[] before, int[] after) {
        int same = 0;
        for (int i = 0; i < before.length && i < after.length; i++) {
            if ((before[i] & 0xFFFFFF) == (after[i] & 0xFFFFFF)) same++;
        }
        return same;
    }

    // ------------------------------------------------------------------
    // Overlay wear
    // ------------------------------------------------------------------

    /** A speckled brown, standing in for a real dirt-like texture. */
    private static int[] speckled(int base, int spread, long seed) {
        Random random = new Random(seed);
        int[] pixels = new int[SIZE * SIZE];
        for (int i = 0; i < pixels.length; i++) {
            int r = clampByte(((base >>> 16) & 0xFF) + random.nextInt(spread * 2 + 1) - spread);
            int g = clampByte(((base >>> 8) & 0xFF) + random.nextInt(spread * 2 + 1) - spread);
            int b = clampByte((base & 0xFF) + random.nextInt(spread * 2 + 1) - spread);
            pixels[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
        }
        return pixels;
    }

    private static int clampByte(int value) {
        return value < 0 ? 0 : value > 255 ? 255 : value;
    }

    private static double spreadOfLuma(int[] pixels) {
        double mean = 0;
        for (int pixel : pixels) {
            mean += (((pixel >>> 16) & 0xFF) + ((pixel >>> 8) & 0xFF) + (pixel & 0xFF)) / 3.0;
        }
        mean /= pixels.length;
        double variance = 0;
        for (int pixel : pixels) {
            double luma = (((pixel >>> 16) & 0xFF) + ((pixel >>> 8) & 0xFF) + (pixel & 0xFF)) / 3.0;
            variance += (luma - mean) * (luma - mean);
        }
        return Math.sqrt(variance / pixels.length);
    }

    @Test
    @DisplayName("wearing nothing leaves the block exactly as it was")
    void zeroCoverageIsIdentity() {
        int[] source = speckled(0x8B6547, 24, 11L);
        float[] order = WearCompositor.sourceWearOrder(source, SIZE, 0);
        int[] worn = WearCompositor.applyOverlay(source, SIZE, order, SIZE, 0f, 0f);
        assertArrayEquals(source, worn, "an unworn block must be its own texture");
    }

    @Test
    @DisplayName("pixels outside the worn patch are left bit-identical")
    void untouchedPixelsSurvive() {
        int[] source = speckled(0x8B6547, 24, 12L);
        float[] order = WearCompositor.sourceWearOrder(source, SIZE, 0);
        int[] worn = WearCompositor.applyOverlay(source, SIZE, order, SIZE, 0.5f, 1f);

        int untouched = 0;
        for (int i = 0; i < source.length; i++) {
            if (source[i] == worn[i]) untouched++;
        }
        // Half the face was asked for, so about half must come back exactly as it went in. This
        // is the property that separates wear from a darkening filter: a filter touches all 256.
        assertTrue(untouched > 90, "expected most of the unworn half untouched, got " + untouched);
        assertTrue(untouched < 200, "expected the worn half to have actually changed, got " + untouched);
    }

    @Test
    @DisplayName("wear never shifts a pixel's hue, whatever colour the block is")
    void wearKeepsTheSourceColour() {
        int[][] sources = { speckled(0x8B6547, 24, 1L), // dirt-ish brown
            speckled(0xDBD3A0, 18, 2L), // sand-ish cream
            speckled(0xA33A2A, 22, 3L), // red sand
            speckled(0x7D7D7D, 30, 4L), // stone grey
        };

        for (int[] source : sources) {
            float[] order = WearCompositor.sourceWearOrder(source, SIZE, 0);
            int[] worn = WearCompositor.applyOverlay(source, SIZE, order, SIZE, 0.7f, 1f);
            for (int i = 0; i < source.length; i++) {
                if (source[i] == worn[i]) continue;
                // Hue and saturation are ratios between channels. Scaling all three by one
                // number cannot move either, so the ratios must survive to within rounding.
                double[] from = { (source[i] >>> 16) & 0xFF, (source[i] >>> 8) & 0xFF, source[i] & 0xFF };
                double[] to = { (worn[i] >>> 16) & 0xFF, (worn[i] >>> 8) & 0xFF, worn[i] & 0xFF };
                double scale = to[0] / Math.max(from[0], 1);
                for (int channel = 1; channel < 3; channel++) {
                    double expected = from[channel] * scale;
                    assertTrue(
                        Math.abs(to[channel] - expected) <= 1.5d,
                        "channel " + channel + " drifted: " + to[channel] + " vs " + expected);
                }
            }
        }
    }

    @Test
    @DisplayName("wear never brightens a pixel")
    void wearOnlyDarkens() {
        int[] source = speckled(0x7D7D7D, 30, 5L);
        float[] order = WearCompositor.sourceWearOrder(source, SIZE, 0);
        int[] worn = WearCompositor.applyOverlay(source, SIZE, order, SIZE, 0.7f, 1f);
        for (int i = 0; i < source.length; i++) {
            for (int channel = 0; channel < 3; channel++) {
                int shift = 16 - channel * 8;
                assertTrue(
                    ((worn[i] >>> shift) & 0xFF) <= ((source[i] >>> shift) & 0xFF),
                    "a worn pixel got brighter, which is not how wear works");
            }
        }
    }

    @Test
    @DisplayName("a worn block keeps most of its relief, where a uniform darkening destroys it")
    void wearKeepsTheBlocksTexture() {
        int[] source = speckled(0x7D7D7D, 30, 6L);
        double before = spreadOfLuma(source);

        float[] order = WearCompositor.sourceWearOrder(source, SIZE, 0);
        int[] worn = WearCompositor.applyOverlay(source, SIZE, order, SIZE, 0.7f, 1f);
        double after = spreadOfLuma(worn);

        // The old operator flattened every pixel toward one mean and left cobbles looking like a
        // grey smear. Leaving a third of the face alone is what stops that.
        assertTrue(after > before * 0.6d, "relief collapsed: " + before + " -> " + after);
    }

    @Test
    @DisplayName("wear grows as the block wears, and never goes backwards")
    void wearIsMonotone() {
        int[] source = speckled(0x8B6547, 24, 7L);
        float[] order = WearCompositor.sourceWearOrder(source, SIZE, 0);

        int previous = -1;
        for (int step = 0; step <= 8; step++) {
            float progress = step / 8f;
            int[] worn = WearCompositor.applyOverlay(source, SIZE, order, SIZE, progress * 0.7f, progress);
            int changed = 0;
            for (int i = 0; i < source.length; i++) {
                if (source[i] != worn[i]) changed++;
            }
            assertTrue(changed >= previous, "wear went backwards at step " + step);
            previous = changed;
        }
    }

    @Test
    @DisplayName("wear arrives in patches rather than as scattered single pixels")
    void wearFormsPatches() {
        // A texture with almost no relief is the hard case: rank its pixels by brightness alone
        // and the worn ones land at random, which reads as static rather than as a worn path.
        int[] flat = speckled(0x8A8A8A, 2, 8L);
        float[] order = WearCompositor.sourceWearOrder(flat, SIZE, 0);
        int[] worn = WearCompositor.applyOverlay(flat, SIZE, order, SIZE, 0.4f, 1f);

        boolean[] isWorn = new boolean[SIZE * SIZE];
        int total = 0;
        for (int i = 0; i < flat.length; i++) {
            isWorn[i] = flat[i] != worn[i];
            if (isWorn[i]) total++;
        }

        int neighbours = 0;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (!isWorn[y * SIZE + x]) continue;
                if (isWorn[y * SIZE + ((x + 1) % SIZE)]) neighbours++;
                if (isWorn[((y + 1) % SIZE) * SIZE + x]) neighbours++;
            }
        }
        // Scattered single pixels would give roughly coverage-squared adjacency. Patches give far
        // more. This is deliberately a loose floor: it fails on static and passes on blobs.
        assertTrue(
            neighbours > total * 0.8d,
            "worn pixels look scattered rather than patchy: " + neighbours + " joins over " + total + " pixels");
    }

    // ------------------------------------------------------------------
    // Cracking
    // ------------------------------------------------------------------

    @Test
    @DisplayName("an uncracked surface is left exactly as it was")
    void crackAtZeroIsIdentity() {
        int[] source = speckled(0x8A8A8A, 20, 21L);
        assertArrayEquals(source, WearCompositor.applyCrack(source, SIZE, 0.7f, 0f));
    }

    @Test
    @DisplayName("a perfectly smooth surface still visibly cracks")
    void crackWorksOnASurfaceWithNoRelief() {
        // The case the old operator could not do anything with: a dressed stone with no crevices
        // to deepen came out looking freshly laid however worn it was.
        int[] flat = new int[SIZE * SIZE];
        for (int i = 0; i < flat.length; i++) {
            flat[i] = 0xFF9A9A9A;
        }

        int[] worn = WearCompositor.applyCrack(flat, SIZE, 0.8f, 1f);
        double spread = spreadOfLuma(worn);
        assertTrue(spread > 8.0d, "a cracked surface must have visible relief, got spread " + spread);

        int darkest = 255;
        for (int pixel : worn) {
            darkest = Math.min(darkest, pixel & 0xFF);
        }
        assertTrue(darkest < 0x70, "the cracks must actually read as dark, got " + darkest);
    }

    @Test
    @DisplayName("each stage of cracking is visibly further along than the last")
    void crackDeepensWithEveryStage() {
        int[] source = speckled(0x8A8A8A, 12, 22L);
        double previousMean = 256d;
        int previousChanged = -1;
        for (int step = 0; step <= 4; step++) {
            float wear = step / 4f;
            int[] worn = WearCompositor.applyCrack(source, SIZE, 0.8f, wear);

            double mean = 0;
            int changed = 0;
            for (int i = 0; i < worn.length; i++) {
                mean += worn[i] & 0xFF;
                if (worn[i] != source[i]) changed++;
            }
            mean /= worn.length;

            assertTrue(mean <= previousMean + 0.5d, "stage " + step + " got brighter, not darker");
            assertTrue(changed >= previousChanged, "stage " + step + " touched fewer pixels than the one before");
            previousMean = mean;
            previousChanged = changed;
        }
    }

    @Test
    @DisplayName("a crack is a shadow in the surface's own colour, never a grey line")
    void crackKeepsTheSurfaceColour() {
        int[] red = new int[SIZE * SIZE];
        for (int i = 0; i < red.length; i++) {
            red[i] = 0xFFC03010;
        }
        int[] worn = WearCompositor.applyCrack(red, SIZE, 0.8f, 1f);
        for (int pixel : worn) {
            int r = (pixel >>> 16) & 0xFF;
            int g = (pixel >>> 8) & 0xFF;
            int b = pixel & 0xFF;
            assertTrue(r >= g && g >= b, "channel order drifted: " + r + "," + g + "," + b);
        }
    }

    // ------------------------------------------------------------------
    // Grass side wear
    // ------------------------------------------------------------------

    /** A grey overlay: transparent everywhere but an opaque fringe hanging from the top rows. */
    private static int[] fringe(int bandRows) {
        int[] overlay = new int[SIZE * SIZE];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                // A ragged foot: the band is a row shallower on every third column.
                int depth = bandRows - ((x % 3 == 0) ? 1 : 0);
                overlay[y * SIZE + x] = y < depth ? 0xFF9AA07F : 0x00000000;
            }
        }
        return overlay;
    }

    private static int opaqueCount(int[] pixels) {
        int count = 0;
        for (int pixel : pixels) {
            if (((pixel >>> 24) & 0xFF) >= 16) count++;
        }
        return count;
    }

    @Test
    @DisplayName("keeping the whole fringe returns the overlay unchanged")
    void fringeAtFullCoverageIsIdentity() {
        int[] overlay = fringe(4);
        assertArrayEquals(overlay, WearCompositor.applyFringe(overlay, SIZE, 1f, 0));
    }

    @Test
    @DisplayName("clearing the fringe leaves nothing opaque")
    void fringeAtZeroCoverageIsEmpty() {
        int[] overlay = fringe(4);
        int[] worn = WearCompositor.applyFringe(overlay, SIZE, 0f, 0);
        assertEquals(0, opaqueCount(worn), "no fringe should survive at zero coverage");
    }

    @Test
    @DisplayName("fringe coverage counts against the band, not the whole face")
    void fringeCoverageIsAFractionOfTheBand() {
        int[] overlay = fringe(4);
        int band = opaqueCount(overlay);
        int half = opaqueCount(WearCompositor.applyFringe(overlay, SIZE, 0.5f, 0));
        // Half the band, give or take the ragged edge and rounding - nowhere near half the face.
        assertTrue(Math.abs(half - band / 2) <= SIZE, "expected ~half the band, got " + half + " of " + band);
    }

    @Test
    @DisplayName("the fringe recedes from the bottom up, the top row surviving longest")
    void fringeRecedesUpward() {
        int[] overlay = fringe(4);
        int[] worn = WearCompositor.applyFringe(overlay, SIZE, 0.5f, 0);
        int survivorsTopRow = 0;
        int survivorsBottomOfBand = 0;
        for (int x = 0; x < SIZE; x++) {
            if (((worn[0 * SIZE + x] >>> 24) & 0xFF) >= 16) survivorsTopRow++;
            if (((worn[3 * SIZE + x] >>> 24) & 0xFF) >= 16) survivorsBottomOfBand++;
        }
        assertTrue(
            survivorsTopRow > survivorsBottomOfBand,
            "top row should outlast the foot: " + survivorsTopRow + " vs " + survivorsBottomOfBand);
    }

    @Test
    @DisplayName("less coverage never keeps a pixel that more coverage dropped")
    void fringeRecessionIsMonotone() {
        int[] overlay = fringe(4);
        int previous = Integer.MAX_VALUE;
        for (int step = 6; step >= 0; step--) {
            int[] worn = WearCompositor.applyFringe(overlay, SIZE, step / 6f, 0);
            int survivors = opaqueCount(worn);
            assertTrue(survivors <= previous, "the fringe grew back at coverage " + (step / 6f));
            previous = survivors;
        }
    }

    @Test
    @DisplayName("de-greening a grass side removes the green top edge and fills the holes")
    void degreenReplacesTopEdgeWithEarth() {
        int[] side = new int[SIZE * SIZE];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int pixel;
                if (y == 0) {
                    pixel = 0x00000000; // a cut-away hole, like loamy grass
                } else if (y <= 2) {
                    pixel = 0xFF6E8E43; // dull green fringe baked into the base
                } else {
                    pixel = 0xFF7A5A3C; // honest dirt
                }
                side[y * SIZE + x] = pixel;
            }
        }

        int[] wall = WearCompositor.degreenTopEdge(side, SIZE);
        for (int i = 0; i < wall.length; i++) {
            int pixel = wall[i];
            assertTrue(((pixel >>> 24) & 0xFF) >= 250, "every pixel of the wall is opaque");
            int r = (pixel >>> 16) & 0xFF;
            int g = (pixel >>> 8) & 0xFF;
            int b = pixel & 0xFF;
            assertTrue(!(g > r + 12 && g > b + 12), "no green survives in the wall at pixel " + i);
        }
        // The dirt below is left exactly as it was.
        for (int y = 3; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                assertEquals(side[y * SIZE + x], wall[y * SIZE + x], "dirt below the edge is untouched");
            }
        }
    }

    @Test
    @DisplayName("laying one texture over another keeps the top where solid, the bottom where clear, and blends between")
    void layingOneTextureOverAnother() {
        int[] under = { 0xFF0000FF, 0xFF0000FF, 0x40112233, 0xFF00FF00 };
        int[] top = { 0xFFABCDEF, 0x80FF0000, 0x00FFFFFF, 0xFCABCDEF };
        int[] underBefore = under.clone();
        int[] topBefore = top.clone();
        assertArrayEquals(
            new int[] { 0xFFABCDEF, 0xFF80007F, 0x40112233, 0xFCABCDEF },
            WearCompositor.over(under, top, 2));
        assertArrayEquals(
            new int[] { 0xFFABCDEF, 0xFF80007F, 0x40112233, 0xFFABCDEF },
            WearCompositor.overThroughHoles(under, top, 2),
            "through holes, a shell pixel is forced solid");
        assertArrayEquals(underBefore, under, "nothing handed in is written to");
        assertArrayEquals(topBefore, top, "nothing handed in is written to");
    }

    /** Opaque, clear, just short of solid and half, mixed, over random colours. */
    private static int[] mixedAlphas(Random random) {
        int[] alphas = { 0x00, 0x80, 0xFB, 0xFF };
        int[] pixels = new int[SIZE * SIZE];
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = (alphas[random.nextInt(alphas.length)] << 24) | (random.nextInt() & 0xFFFFFF);
        }
        return pixels;
    }

    @Test
    @DisplayName("drawing into a scratch picture gives the same picture and touches nothing past it")
    void drawingIntoAScratchPictureGivesTheSamePicture() {
        Random random = new Random(212);
        int[] under = mixedAlphas(random);
        int[] top = mixedAlphas(random);
        int[] nextUnder = mixedAlphas(random);
        int[] nextTop = mixedAlphas(random);
        int[] scratch = new int[4096];

        Arrays.fill(scratch, 0xDEADBEEF);
        assertSame(scratch, WearCompositor.overInto(scratch, under, top, SIZE));
        assertArrayEquals(WearCompositor.over(under, top, SIZE), Arrays.copyOf(scratch, SIZE * SIZE));
        assertEquals(0xDEADBEEF, scratch[SIZE * SIZE], "nothing past the picture is written");
        assertSame(scratch, WearCompositor.overInto(scratch, nextUnder, nextTop, SIZE));
        assertArrayEquals(
            WearCompositor.over(nextUnder, nextTop, SIZE),
            Arrays.copyOf(scratch, SIZE * SIZE),
            "the second picture, with nothing of the first left in it");

        Arrays.fill(scratch, 0xDEADBEEF);
        assertSame(scratch, WearCompositor.overThroughHolesInto(scratch, under, top, SIZE));
        assertArrayEquals(WearCompositor.overThroughHoles(under, top, SIZE), Arrays.copyOf(scratch, SIZE * SIZE));
        assertEquals(0xDEADBEEF, scratch[SIZE * SIZE], "nothing past the picture is written");
        assertSame(scratch, WearCompositor.overThroughHolesInto(scratch, nextUnder, nextTop, SIZE));
        assertArrayEquals(
            WearCompositor.overThroughHoles(nextUnder, nextTop, SIZE),
            Arrays.copyOf(scratch, SIZE * SIZE),
            "the second picture, with nothing of the first left in it");
    }

    @Test
    @DisplayName("a missing layer hands back the other, as it always has")
    void aMissingLayerHandsBackTheOther() {
        int[] scratch = new int[SIZE * SIZE];
        int[] under = new int[SIZE * SIZE];
        int[] top = new int[SIZE * SIZE];
        assertSame(top, WearCompositor.over(null, top, SIZE));
        assertSame(under, WearCompositor.over(under, null, SIZE));
        assertSame(top, WearCompositor.overInto(scratch, null, top, SIZE));
        assertSame(under, WearCompositor.overInto(scratch, under, null, SIZE));
        assertSame(top, WearCompositor.overThroughHoles(null, top, SIZE));
        assertSame(under, WearCompositor.overThroughHoles(under, null, SIZE));
        assertSame(top, WearCompositor.overThroughHolesInto(scratch, null, top, SIZE));
        assertSame(under, WearCompositor.overThroughHolesInto(scratch, under, null, SIZE));
    }

}
