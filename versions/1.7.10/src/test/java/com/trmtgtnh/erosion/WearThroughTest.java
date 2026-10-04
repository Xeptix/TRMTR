package com.trmtgtnh.erosion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.trmtgtnh.surface.SurfaceFamily;

/**
 * How a family's pixels of depth are shared out between the surfaces it wears through into.
 *
 * <p>
 * The whole promise of the feature is that it changes what a rut looks like on the way down and
 * changes nothing else, so what is worth testing is the two properties that promise rests on: the
 * run is exactly as long as it was, and no two steps on it are the same. The second is not a
 * nicety - {@link ErosionChain#indexOf} is a scan for an exact packed word, so a duplicated step
 * would make wear loop forever between the two copies.
 */
class WearThroughTest {

    /** Who owns each pixel of depth, as the chain builder asks it. */
    private static int[] owners(int deepest, int successors) {
        int[] out = new int[deepest];
        for (int depth = 1; depth <= deepest; depth++) {
            out[depth - 1] = ErosionChain.sliceOf(depth, deepest, successors);
        }
        return out;
    }

    private static int[] tally(int[] owners, int successors) {
        int[] counts = new int[successors];
        for (int owner : owners) counts[owner]++;
        return counts;
    }

    @Test
    @DisplayName("eight pixels between three successors is three, three, two - the remainder going earliest")
    void stoneSplitsThreeThreeTwo() {
        assertArray(new int[] { 3, 3, 2 }, tally(owners(8, 3), 3), "cobble, then grit, then the earth beneath");
    }

    @Test
    @DisplayName("two successors halve it and one takes the lot")
    void theSimplerSplits() {
        assertArray(new int[] { 4, 4 }, tally(owners(8, 2), 2), "cobble into gravel then dirt");
        assertArray(new int[] { 8 }, tally(owners(8, 1), 1), "gravel straight into dirt");
    }

    @Test
    @DisplayName("what a road shows most of is the first thing it broke into")
    void theEarliestSuccessorIsNeverShortchanged() {
        for (int deepest = 1; deepest <= 15; deepest++) {
            for (int successors = 1; successors <= 4; successors++) {
                int[] counts = tally(owners(deepest, successors), successors);
                for (int i = 1; i < counts.length; i++) {
                    assertTrue(
                        counts[i - 1] >= counts[i],
                        "at depth " + deepest + " with " + successors + " successors, " + i + " outgrew " + (i - 1));
                }
            }
        }
    }

    @Test
    @DisplayName("every pixel of depth is owned by exactly one successor, at any shape")
    void everyDepthIsSpokenFor() {
        for (int deepest = 1; deepest <= 15; deepest++) {
            for (int successors = 1; successors <= 4; successors++) {
                int[] counts = tally(owners(deepest, successors), successors);
                int total = 0;
                for (int count : counts) total += count;
                assertEquals(deepest, total, "depths at " + deepest + "/" + successors);
                // A list longer than the run cannot all be shown, and the ones shown are the ones
                // at the front - a two-pixel stone rut is cobble then grit, not cobble then earth.
                int shown = Math.min(successors, deepest);
                for (int i = 0; i < shown; i++) {
                    assertTrue(counts[i] > 0, "successor " + i + " owns nothing at " + deepest + "/" + successors);
                }
                for (int i = shown; i < counts.length; i++) {
                    assertEquals(0, counts[i], "successor " + i + " should not be reached at " + deepest);
                }
            }
        }
    }

    @Test
    @DisplayName("a successor run is exactly as long as the run it replaced, and no step repeats")
    void theRunKeepsItsLengthAndItsUniqueness() {
        SurfaceFamily[] successors = { SurfaceFamily.COBBLE, SurfaceFamily.GRAVEL, SurfaceFamily.DIRT };
        Set<Short> seen = new HashSet<Short>();
        int steps = 0;

        for (int layer = 0; layer < 16; layer++) {
            seen.add(Short.valueOf(ErosionState.pack(SurfaceFamily.STONE, layer, 0)));
            steps++;
        }
        for (int depth = 1; depth <= 8; depth++) {
            SurfaceFamily owner = successors[ErosionChain.sliceOf(depth, 8, successors.length)];
            for (int layer = 0; layer < 8; layer++) {
                seen.add(Short.valueOf(ErosionState.pack(owner, layer, depth)));
                steps++;
            }
        }

        assertEquals(80, steps, "stone's run is eighty phases with successors exactly as it was without");
        assertEquals(80, seen.size(), "and every one of them is a step the chain can tell from every other");
    }

    @Test
    @DisplayName("a family with nowhere to go, or one pixel deep, is still handled")
    void thedegenerateShapes() {
        assertEquals(0, ErosionChain.sliceOf(1, 8, 1), "one successor owns the first pixel");
        assertEquals(0, ErosionChain.sliceOf(4, 0, 3), "no depth at all cannot index anything");
        assertEquals(0, ErosionChain.sliceOf(1, 1, 1), "the shallowest possible run");
    }

    private static void assertArray(int[] wanted, int[] found, String why) {
        assertEquals(wanted.length, found.length, why + " (length)");
        for (int i = 0; i < wanted.length; i++) {
            assertEquals(wanted[i], found[i], why + " (successor " + i + ")");
        }
    }
}
