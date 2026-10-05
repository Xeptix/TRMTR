package com.trmtgtnh.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Which blocks the crosshair asks about, which provider speaks, and where a reinforcement's worth is said.
 *
 * <p>
 * All three failures were silent: lines simply absent, or said twice, or a multiplier promised over a leaf.
 */
class InspectionReachTest {

    private static final boolean[] BOTH = { false, true };

    @Test
    @DisplayName("nothing is asked while nothing reads the answer")
    void nothingAskedWithoutAReader() {
        for (boolean ghost : BOTH)
            for (boolean r : BOTH) for (boolean w : BOTH) assertFalse(InspectionReach.asks(false, ghost, r, w));
    }

    @Test
    @DisplayName("worn ground is always asked about while Waila reads")
    void wornGroundIsAlwaysAsked() {
        for (boolean r : BOTH) for (boolean w : BOTH) assertTrue(InspectionReach.asks(true, true, r, w));
    }

    @Test
    @DisplayName("an unworn block is asked about while either reinforcement or wards are on")
    void unwornIsAskedWhileEitherIsOn() {
        assertTrue(InspectionReach.asks(true, false, true, false));
        assertTrue(InspectionReach.asks(true, false, false, true), "wards on their own are the case 0.9.211 missed");
        assertTrue(InspectionReach.asks(true, false, true, true));
        assertFalse(InspectionReach.asks(true, false, false, false));
    }

    /** How many of the two providers write lines for a block, given which of them Waila hands it to. */
    private static int speakers(boolean everyBlockReaches, boolean ghostProviderReaches, boolean ghost) {
        int speaking = 0;
        if (everyBlockReaches && InspectionReach.speaks(true, ghost)) speaking++;
        if (ghostProviderReaches && InspectionReach.speaks(false, ghost)) speaking++;
        return speaking;
    }

    @Test
    @DisplayName("matched on isInstance, every block is spoken for exactly once")
    void isInstanceSpeaksOnce() {
        assertEquals(1, speakers(true, false, false), "a plain block reaches only the every-block provider");
        assertEquals(1, speakers(true, true, true), "a ghost reaches both, and one speaks");
    }

    @Test
    @DisplayName("matched on the exact class, a ghost keeps every line it had")
    void exactClassKeepsTheGhosts() {
        assertEquals(1, speakers(false, true, true));
    }

    @Test
    @DisplayName("a leaf, a plant or a pane is not told it holds out against traffic")
    void onlyWearingGroundIsToldItsWorth() {
        assertFalse(InspectionReach.wears(false, false));
        assertTrue(InspectionReach.wears(false, true));
        assertTrue(InspectionReach.wears(true, false));
    }
}
