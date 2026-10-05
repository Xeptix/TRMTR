package com.trmtgtnh.erosion;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.trmtgtnh.surface.SurfaceFamily;

/**
 * The arithmetic of a family's run, pinned by figures worked out by hand.
 *
 * <p>
 * The wear editor, the Wear Table and the chain builder all ask these questions, and for a while the
 * editor answered them with its own copy of the sums. The copy drifted and nothing noticed, because it
 * lived in a screen: a typed depth of nought published the depth it would borrow, a change of depth
 * alone republished a reshuffled split, and a staged ceiling ignored the world's. The publish tests
 * below pin each of those in the code that now writes the lines.
 *
 * <p>
 * The families here are described by their numbers rather than looked up, because the lookup wants a
 * loaded Forge configuration and the arithmetic does not. Dirt is the one family whose first
 * gradation is skipped, which is what the offset of one stands for.
 */
class RunShapeTest {

    /** The gradations dirt skips at the start of its run. */
    private static final int DIRT = 1;

    /** The gradations every other family skips, which is none. */
    private static final int OTHER = 0;

    @Test
    @DisplayName("a ceiling stops a run where the chain stops it, and never below one phase of a run that exists")
    void cappedMatchesTheChain() {
        assertEquals(0, RunShape.capped(0, 0.5f), "no run, nothing to reach");
        assertEquals(0, RunShape.capped(-4, 0.5f));
        assertEquals(80, RunShape.capped(80, 1f), "a ceiling of one is no ceiling");
        assertEquals(80, RunShape.capped(80, 1.5f));
        assertEquals(1, RunShape.capped(80, 0f), "capped at nothing still leaves one step");
        assertEquals(1, RunShape.capped(80, -0.25f));
        assertEquals(40, RunShape.capped(80, 0.5f));
        assertEquals(1, RunShape.capped(3, 0.1f), "0.3 rounds to nought and is lifted to one");
        assertEquals(3, RunShape.capped(5, 0.5f), "2.5 rounds up");
        assertEquals(40, RunShape.capped(79, 0.5f), "39.5 rounds up");
    }

    @Test
    @DisplayName("the lower ceiling wins, whichever of the two it is")
    void theLowerCeilingWins() {
        assertEquals(0.5f, RunShape.ceiling(0.5f, 0.8f), 0f, "the world's");
        assertEquals(0.5f, RunShape.ceiling(0.8f, 0.5f), 0f, "the family's");
        assertEquals(1f, RunShape.ceiling(1f, 1f), 0f);
    }

    @Test
    @DisplayName("a depth is clamped exactly as a record's is, and a pixel is worth at least one layer")
    void depthAndLayersAreHeldWhereTheEngineHoldsThem() {
        for (int depth = -3; depth <= 20; depth++) {
            assertEquals(ErosionState.clampSink(depth), RunShape.clampDepth(depth), "depth " + depth);
        }
        assertEquals(0, RunShape.clampDepth(-3));
        assertEquals(0, RunShape.clampDepth(0));
        assertEquals(7, RunShape.clampDepth(7));
        assertEquals(15, RunShape.clampDepth(15));
        assertEquals(15, RunShape.clampDepth(16), "past half a block is a trap");
        assertEquals(15, RunShape.clampDepth(20));

        assertEquals(1, RunShape.layers(0));
        assertEquals(1, RunShape.layers(-5));
        assertEquals(1, RunShape.layers(1));
        assertEquals(8, RunShape.layers(8));
    }

    @Test
    @DisplayName("with no depth and nothing to wear through into, the run is its surface stretch alone")
    void depthNoughtWithNoSuccessor() {
        RunShape.Run run = RunShape.Run.of(10, OTHER, 8, 0, false, 4, 6, 0, 1f, 1f);
        assertFalse(run.borrowed, "nothing to borrow from");
        assertEquals(0, run.depth);
        assertEquals(8, run.layers, "its own layers, unused");
        assertEquals(10, run.built);
        assertEquals(10, run.full);
        assertEquals(10, run.reached);
        assertNull(run.split);

        assertEquals(9, RunShape.Run.of(10, DIRT, 8, 0, false, 4, 6, 0, 1f, 1f).built, "dirt skips one");
        assertEquals(0, RunShape.length(0, DIRT, 8, 0), "never below nothing");
        assertFalse(RunShape.borrowsDepth(0, false));
    }

    @Test
    @DisplayName("with no depth but something to wear through into, the depth and its layers are the successor's")
    void depthNoughtWearingThroughBorrows() {
        RunShape.Run run = RunShape.Run.of(16, OTHER, 8, 0, true, 4, 6, 0, 1f, 1f);
        assertTrue(run.borrowed);
        assertEquals(6, run.depth);
        assertEquals(4, run.layers);
        assertEquals(16 + 4 * 6, run.built);

        RunShape.Run shallow = RunShape.Run.of(16, OTHER, 8, 0, true, 4, 0, 0, 1f, 1f);
        assertEquals(0, shallow.depth, "a successor with no depth lends none");
        assertTrue(shallow.borrowed, "though the rule still points at it");
        assertEquals(16, shallow.built);

        assertTrue(RunShape.Run.of(16, OTHER, 8, -1, true, 4, 6, 0, 1f, 1f).borrowed, "below nought is none too");
        RunShape.Run deep = RunShape.Run.of(16, OTHER, 8, 0, true, 4, 20, 0, 1f, 1f);
        assertEquals(15, deep.depth, "a borrowed depth is clamped as well");
        assertEquals(16 + 4 * 15, deep.built);
        RunShape.Run thin = RunShape.Run.of(16, OTHER, 8, 0, true, 0, 6, 0, 1f, 1f);
        assertEquals(1, thin.layers, "and borrowed layers are never below one");
        assertEquals(16 + 6, thin.built);
    }

    @Test
    @DisplayName("a depth of its own is never swapped for a successor's")
    void anOwnDepthNeverBorrows() {
        RunShape.Run run = RunShape.Run.of(16, OTHER, 8, 3, true, 4, 6, 0, 1f, 1f);
        assertFalse(run.borrowed);
        assertEquals(3, run.depth);
        assertEquals(8, run.layers);
        assertEquals(16 + 8 * 3, run.built);
        assertFalse(RunShape.borrowsDepth(3, true));
        assertTrue(RunShape.borrowsDepth(0, true));

        RunShape.Run clamped = RunShape.Run.of(16, OTHER, 8, 20, false, 0, 0, 0, 1f, 1f);
        assertEquals(15, clamped.depth);
        assertEquals(16 + 8 * 15, clamped.built);
    }

    @Test
    @DisplayName("a typed length is the run only where it can be shared out, and the ceiling is the lower of two")
    void aRunTakesItsLengthAndCeiling() {
        // Stone, sixteen gradations and eight layers a pixel for eight pixels: eighty phases.
        RunShape.Run typed = RunShape.Run.of(16, OTHER, 8, 8, false, 0, 0, 40, 1f, 1f);
        assertEquals(80, typed.built, "what the figures build is unmoved");
        assertEquals(40, typed.full);
        assertEquals(40, typed.reached);
        assertArrayEquals(new int[] { 16, 3 }, typed.split);

        // Grass typed at 76 while dirt, whose depth it borrows, is staged fifteen pixels deep: 76 - 8 x 15
        // leaves no surface stretch at all.
        RunShape.Run unreachable = RunShape.Run.of(16, OTHER, 8, 0, true, 8, 15, 76, 1f, 1f);
        assertNull(unreachable.split);
        assertEquals(16 + 8 * 15, unreachable.built);
        assertEquals(136, unreachable.full, "the length that will be built, not the one that cannot");
        assertEquals(136, unreachable.reached);

        RunShape.Run held = RunShape.Run.of(16, OTHER, 8, 8, false, 0, 0, 0, 0.5f, 0.8f);
        assertEquals(0.5f, held.ceiling, 0f);
        assertEquals(0.8f, held.familyCeiling, 0f);
        assertTrue(held.heldByWorld);
        assertEquals(RunShape.capped(80, 0.5f), held.reached);
        assertEquals(40, held.reached);

        RunShape.Run own = RunShape.Run.of(16, OTHER, 8, 8, false, 0, 0, 0, 0.5f, 0.3f);
        assertEquals(0.3f, own.ceiling, 0f);
        assertFalse(own.heldByWorld, "the family's own ceiling is the lower one");
        assertEquals(24, own.reached);
        assertFalse(RunShape.Run.of(16, OTHER, 8, 8, false, 0, 0, 0, 0.5f, 0.5f).heldByWorld, "equal is not held");

        assertEquals(
            20,
            RunShape.Run.of(16, OTHER, 8, 8, false, 0, 0, 40, 0.5f, 1f).reached,
            "a typed run is capped too");
    }

    @Test
    @DisplayName("a typed length is shared out as the chain would build it, or not at all")
    void splitSharesOutALength() {
        assertArrayEquals(new int[] { 11, 8 }, RunShape.split(10, 0, DIRT, false, 8), "no depth: stages alone");
        assertArrayEquals(new int[] { 10, 3 }, RunShape.split(10, 0, OTHER, true, 3));
        assertArrayEquals(new int[] { 16, 8 }, RunShape.split(16, 0, OTHER, false, 8));

        assertArrayEquals(new int[] { 12, 8 }, RunShape.split(76, 8, OTHER, true, 8), "borrowed: the layers stay");
        assertArrayEquals(new int[] { 16, 8 }, RunShape.split(80, 8, OTHER, true, 8));
        assertArrayEquals(new int[] { 1, 8 }, RunShape.split(65, 8, OTHER, true, 8));

        assertNull(RunShape.split(17, 0, OTHER, false, 8), "seventeen gradations");
        assertNull(RunShape.split(16, 0, DIRT, false, 8), "dirt needs one more than it shows");
        assertNull(RunShape.split(0, 0, OTHER, false, 8));
        assertNull(RunShape.split(64, 8, OTHER, true, 8), "a borrowed depth leaving no surface");
        assertNull(RunShape.split(81, 8, OTHER, true, 8));
        assertNull(RunShape.split(5, 8, OTHER, false, 8), "shorter than a single layer a pixel");
        assertNull(RunShape.split(257, 15, OTHER, false, 8), "longer than sixteen of each");
        assertArrayEquals(new int[] { 16, 16 }, RunShape.split(256, 15, OTHER, false, 8));

        assertArrayEquals(new int[] { 9, 4 }, RunShape.split(40, 8, DIRT, false, 8), "dirt typed at 40");

        for (int depth = 1; depth <= 15; depth++) {
            for (int offset = 0; offset <= 1; offset++) {
                for (int target = 1; target <= 16 + 16 * 15; target++) {
                    boolean fits = false;
                    for (int layers = 1; layers <= SurfaceFamily.MAX_STAGES; layers++) {
                        int stages = target + offset - layers * depth;
                        if (stages >= 1 && stages <= SurfaceFamily.MAX_STAGES) fits = true;
                    }
                    int[] split = RunShape.split(target, depth, offset, false, 8);
                    String where = "target " + target + " at depth " + depth + " offset " + offset;
                    assertEquals(fits, split != null, where);
                    if (split == null) continue;
                    assertTrue(split[0] >= 1 && split[0] <= SurfaceFamily.MAX_STAGES, where);
                    assertTrue(split[1] >= 1 && split[1] <= SurfaceFamily.MAX_STAGES, where);
                    assertEquals(target, RunShape.length(split[0], offset, split[1], depth), where);
                    for (int fewer = 1; fewer < split[1]; fewer++) {
                        assertTrue(
                            target + offset - fewer * depth > SurfaceFamily.MAX_STAGES,
                            where + ": the fewest layers a pixel that fit");
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("a staged depth of nought is published as nought, not as the depth it would borrow")
    void publishWritesTheOwnDepthNotTheBorrowedOne() {
        // Stone at nought wearing through into cobble, six pixels deep at four layers.
        RunShape.Run run = RunShape.Run.of(16, OTHER, 8, 0, true, 4, 6, 0, 1f, 1f);
        assertEquals(6, run.depth, "the depth the old screen published");
        RunShape.Published published = RunShape.publish(run, OTHER, 0);
        assertEquals(0, published.maxSinkPixels);
        assertEquals(-1, published.stages);
        assertEquals(-1, published.layersPerDepth);
        assertEquals(1d, published.keepEnd, 0d);

        assertEquals(15, RunShape.publish(run, OTHER, 20).maxSinkPixels, "held where the engine holds it");
        assertEquals(-1, RunShape.publish(run, OTHER, -1).maxSinkPixels, "nothing staged, no line");
    }

    @Test
    @DisplayName("a change of depth alone publishes no split and no compensation")
    void publishADepthAloneSendsNoSplit() {
        // Coarse dirt, 8 gradations and 4 layers, staged four pixels deep: 7 + 4 x 4.
        RunShape.Run run = RunShape.Run.of(8, DIRT, 4, 4, false, 0, 0, 0, 1f, 1f);
        assertEquals(23, run.built);
        RunShape.Published published = RunShape.publish(run, DIRT, 4);
        assertEquals(4, published.maxSinkPixels);
        assertEquals(-1, published.stages);
        assertEquals(-1, published.layersPerDepth);
        assertEquals(1d, published.keepEnd, 0d);

        // What the old screen sent instead: the greedy split of the same length, which moves both.
        assertArrayEquals(new int[] { 16, 2 }, RunShape.split(23, 4, DIRT, false, 4));
    }

    @Test
    @DisplayName("a typed length on a family borrowing its depth publishes stages and never the layers")
    void publishABorrowedLengthKeepsTheLayers() {
        // Grass, sixteen gradations on dirt's eight pixels of eight layers: eighty phases, typed at 76.
        RunShape.Run run = RunShape.Run.of(16, OTHER, 8, 0, true, 8, 8, 76, 1f, 1f);
        RunShape.Published published = RunShape.publish(run, OTHER, -1);
        assertEquals(-1, published.maxSinkPixels);
        assertEquals(12, published.stages);
        assertEquals(-1, published.layersPerDepth, "dirt's setting, not grass's");
        assertEquals(80d / 76d, published.keepEnd, 1e-12);

        RunShape.Run unreachable = RunShape.Run.of(16, OTHER, 8, 0, true, 8, 15, 76, 1f, 1f);
        RunShape.Published none = RunShape.publish(unreachable, OTHER, -1);
        assertEquals(-1, none.stages, "a length that cannot be shared out publishes nothing");
        assertEquals(-1, none.layersPerDepth);
        assertEquals(1d, none.keepEnd, 0d);
    }

    @Test
    @DisplayName("half the run typed costs twice as much a step, so the end stays where it was")
    void publishCompensatesALength() {
        RunShape.Run stone = RunShape.Run.of(16, OTHER, 8, 8, false, 0, 0, 40, 1f, 1f);
        RunShape.Published half = RunShape.publish(stone, OTHER, -1);
        assertEquals(16, half.stages);
        assertEquals(3, half.layersPerDepth);
        assertEquals(2d, half.keepEnd, 0d);
        assertEquals(-1, half.maxSinkPixels);

        // Dirt at 16/8/8 is 79 phases; typed at 40 it splits to nine gradations and four layers.
        RunShape.Run dirt = RunShape.Run.of(16, DIRT, 8, 8, false, 0, 0, 40, 1f, 1f);
        assertEquals(79, dirt.built);
        RunShape.Published published = RunShape.publish(dirt, DIRT, -1);
        assertEquals(9, published.stages);
        assertEquals(4, published.layersPerDepth);
        assertEquals(79d / 40d, published.keepEnd, 1e-12);
        assertEquals(1.975d, published.keepEnd, 1e-12);
    }

    @Test
    @DisplayName("coarse dirt with nothing typed publishes no split")
    void publishNothingTypedSendsNothing() {
        RunShape.Run run = RunShape.Run.of(8, DIRT, 4, 8, false, 0, 0, 0, 1f, 1f);
        assertEquals(39, run.built);
        RunShape.Published published = RunShape.publish(run, DIRT, -1);
        assertEquals(-1, published.maxSinkPixels);
        assertEquals(-1, published.stages);
        assertEquals(-1, published.layersPerDepth);
        assertEquals(1d, published.keepEnd, 0d);
    }

    @Test
    @DisplayName("the layers a successor's typed length publishes reshape the run of a family borrowing them")
    void aSuccessorsStagedLayersReachTheBorrower() {
        RunShape.Run dirt = RunShape.Run.of(16, DIRT, 8, 8, false, 0, 0, 40, 1f, 1f);
        assertArrayEquals(new int[] { 9, 4 }, dirt.split);

        RunShape.Run grass = RunShape.Run.of(16, OTHER, 8, 0, true, dirt.split[1], 8, 0, 1f, 1f);
        assertEquals(16 + 4 * 8, grass.built);
        assertEquals(48, grass.full);
        assertEquals(80, RunShape.Run.of(16, OTHER, 8, 0, true, 8, 8, 0, 1f, 1f).built, "on dirt's file figure");
    }

    @Test
    @DisplayName("keeping the end is the old length over the new, and one where there is no length")
    void keepEndIsARatio() {
        assertEquals(2d, RunShape.keepEnd(80, 40), 0d);
        assertEquals(0.5d, RunShape.keepEnd(40, 80), 0d);
        assertEquals(1d, RunShape.keepEnd(0, 40), 0d);
        assertEquals(1d, RunShape.keepEnd(80, 0), 0d);
        assertEquals(1d, RunShape.keepEnd(-3, 40), 0d);
        assertEquals(1d, RunShape.keepEnd(80, -1), 0d);
    }

    @Test
    @DisplayName("a quarter reaches the phases a row says it reaches")
    void quarterRoundsAsTheRowDoes() {
        float[] fractions = { 0.25f, 0.5f, 0.75f, 1f };
        int[] of39 = { 10, 20, 29, 39 };
        int[] of5 = { 1, 3, 4, 5 };
        int[] of1 = { 0, 1, 1, 1 };
        int[] of80 = { 20, 40, 60, 80 };
        for (int k = 0; k < fractions.length; k++) {
            assertEquals(of39[k], RunShape.quarter(fractions[k], 39));
            assertEquals(of5[k], RunShape.quarter(fractions[k], 5));
            assertEquals(of1[k], RunShape.quarter(fractions[k], 1));
            assertEquals(of80[k], RunShape.quarter(fractions[k], 80));
            assertEquals(0, RunShape.quarter(fractions[k], 0));
        }

        // The expression WearMath.rowFrom rounds its columns by, over every length a run can have.
        for (int phases = 0; phases <= 256; phases++) {
            for (int k = 0; k < fractions.length; k++) {
                int n = Math.round(fractions[k] * phases);
                int expected = n < 0 ? 0 : n > phases ? phases : n;
                assertEquals(expected, RunShape.quarter(fractions[k], phases), phases + " phases");
            }
        }
    }

    @Test
    @DisplayName("a scale solved for a cell reads that cell back, and a cell the family cannot move has no scale")
    void scaleForRoundTrips() {
        assertEquals(2d, RunShape.scaleFor(50d, 0d, 1d, 25d, 1d, 1d), 0d);
        assertEquals(1d, RunShape.scaleFor(50d, 0d, 1d, 25d, 2d, 1d), 0d, "the compensation is already carried");
        assertEquals(6d, RunShape.scaleFor(50d, 0d, 1d, 25d, 1d, 3d), 0d);

        double[] scales = { 0.1d, 0.5d, 1d, 2.5d, 10d };
        double[] figures = { 1d, 7.5d, 300d };
        double[] keeps = { 0.5d, 1d, 1.975d };
        double[] weights = { 0.25d, 1d, 12.5d };
        double[] fixeds = { 0d, 3.25d, 100d };
        double[] speeds = { 0.5d, 1d, 4d };
        for (double k : scales) {
            for (double figure : figures) {
                for (double keep : keeps) {
                    for (double weight : weights) {
                        for (double fixed : fixeds) {
                            for (double speed : speeds) {
                                double target = fixed + weight * figure * k * keep / speed;
                                Double solved = RunShape.scaleFor(target, fixed, weight, figure, keep, speed);
                                assertNotNull(solved);
                                assertEquals(k, solved.doubleValue(), 1e-9);
                            }
                        }
                    }
                }
            }
        }

        assertNull(RunShape.scaleFor(10d, 10d, 1d, 5d, 1d, 1d), "other families already reach it");
        assertNull(RunShape.scaleFor(5d, 10d, 1d, 5d, 1d, 1d), "other families already pass it");
        assertNull(RunShape.scaleFor(10d, 0d, 0d, 5d, 1d, 1d), "owns no phase before it");
        assertNull(RunShape.scaleFor(10d, 0d, 1d, 0d, 1d, 1d), "a figure of nought scales to nothing");
        assertNull(RunShape.scaleFor(10d, 0d, 1d, -5d, 1d, 1d));
        assertNull(RunShape.scaleFor(10d, 0d, 1d, 5d, 0d, 1d));
        assertNull(RunShape.scaleFor(10d, 0d, 1d, 5d, 1d, 0d));
        assertNull(RunShape.scaleFor(Double.NaN, 0d, 1d, 5d, 1d, 1d));
        assertNull(RunShape.scaleFor(10d, 0d, Double.NaN, 5d, 1d, 1d));
        assertNull(RunShape.scaleFor(Double.POSITIVE_INFINITY, 0d, 1d, 5d, 1d, 1d));
    }
}
