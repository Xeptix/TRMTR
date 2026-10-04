package com.trmtgtnh.erosion;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.trmtgtnh.surface.SurfaceFamily;

/**
 * Which records the engine draws a threshold for on their first step.
 *
 * <p>
 * Both faults it replaces were silent: a stand-in of one made protected ground wear sooner rather than later,
 * and a nought made its first footstep a whole gradation. Neither shows until somebody walks there.
 */
class UndrawnThresholdTest {

    @Test
    @DisplayName("a record made without a draw says so, and a protection on it changes nothing")
    void standInAwaitsADraw() {
        ErosionEntry entry = new ErosionEntry(SurfaceFamily.GRASS, ErosionEntry.UNDRAWN_THRESHOLD, 0);
        assertTrue(entry.awaitsDraw());
        entry.setReinforce(2);
        entry.setWard(1);
        assertTrue(entry.awaitsDraw());
    }

    @Test
    @DisplayName("a record the heal sweep stripped to nought awaits a draw too")
    void strippedToNoughtAwaitsADraw() {
        ErosionEntry entry = new ErosionEntry(SurfaceFamily.DIRT, 9.5f, 0);
        entry.setAppearance(SurfaceFamily.DIRT, -1, 0f);
        entry.setWard(1);
        assertTrue(entry.awaitsDraw());
    }

    @Test
    @DisplayName("a record with a real draw does not")
    void realDrawStands() {
        assertFalse(new ErosionEntry(SurfaceFamily.GRASS, 9.5f, 0).awaitsDraw());
    }

    @Test
    @DisplayName("a record showing a gradation has drawn, whatever its figure")
    void visibleRecordHasDrawn() {
        ErosionEntry entry = new ErosionEntry(SurfaceFamily.GRASS, 9.5f, 0);
        entry.setAppearance(SurfaceFamily.GRASS, 0, ErosionEntry.UNDRAWN_THRESHOLD);
        assertFalse(entry.awaitsDraw());
    }
}
