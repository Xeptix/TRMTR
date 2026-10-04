package com.trmtgtnh.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * When the client asks the server about the block under the crosshair, and whether the answer it holds still
 * stands.
 *
 * <p>
 * Both ways this can go wrong are quiet. An answer believed too long goes on saying "Reinforced" over a block
 * whose reinforcement has been taken off, because the server says nothing about a position with no record; one
 * believed too briefly makes the lines flicker away under ordinary lag. Neither throws or logs, so each is pinned
 * here.
 *
 * <p>
 * The slot is driven tick by tick, as the client drives it: one poll a tick while the crosshair rests on a
 * block, and any reply due that tick taken before the poll, because the client drains its queued packets at
 * the start of the tick and looks at the crosshair after.
 */
class InspectionSlotTest {

    private static int pollFor(InspectionSlot slot, int ticks) {
        int asked = 0;
        for (int i = 0; i < ticks; i++) {
            if (slot.poll(1, 64, 1)) asked++;
        }
        return asked;
    }

    @Test
    @DisplayName("a new position is asked about at once, and again every refresh while it is looked at")
    void asksAtOnceThenEveryRefresh() {
        InspectionSlot slot = new InspectionSlot();
        assertTrue(slot.poll(1, 64, 1));
        assertEquals(0, pollFor(slot, InspectionSlot.REFRESH_TICKS - 1));
        assertTrue(slot.poll(1, 64, 1));
    }

    @Test
    @DisplayName("nothing is believed before a reply arrives")
    void nothingBeforeAReply() {
        InspectionSlot slot = new InspectionSlot();
        assertFalse(slot.has(1, 64, 1));
        slot.poll(1, 64, 1);
        assertFalse(slot.has(1, 64, 1));
    }

    @Test
    @DisplayName("a reply for the position held is believed, and one for anywhere else is refused")
    void onlyTheHeldPositionIsAnswered() {
        InspectionSlot slot = new InspectionSlot();
        slot.poll(1, 64, 1);
        assertFalse(slot.accept(2, 64, 1));
        assertTrue(slot.accept(1, 64, 1));
        assertTrue(slot.has(1, 64, 1));
    }

    @Test
    @DisplayName("looking at another block asks at once and holds nothing about it")
    void anotherBlockStartsAfresh() {
        InspectionSlot slot = new InspectionSlot();
        slot.poll(1, 64, 1);
        slot.accept(1, 64, 1);
        assertTrue(slot.poll(2, 64, 1));
        assertFalse(slot.has(2, 64, 1));
        assertFalse(slot.has(1, 64, 1));
    }

    @Test
    @DisplayName("an answer the server stops giving for two refreshes is dropped, and a reply brings it back")
    void silenceDropsTheAnswer() {
        InspectionSlot slot = new InspectionSlot();
        slot.poll(1, 64, 1);
        slot.accept(1, 64, 1);
        assertEquals(1, pollFor(slot, InspectionSlot.REFRESH_TICKS));
        assertTrue(slot.has(1, 64, 1), "the one outstanding request is the one just sent");
        assertEquals(1, pollFor(slot, InspectionSlot.REFRESH_TICKS));
        assertFalse(slot.has(1, 64, 1), "two refreshes without a reply");
        assertTrue(slot.accept(1, 64, 1));
        assertTrue(slot.has(1, 64, 1));
    }

    @Test
    @DisplayName("a reply that lands after the position was forgotten is refused")
    void clearRefusesALateReply() {
        InspectionSlot slot = new InspectionSlot();
        slot.poll(1, 64, 1);
        slot.clear();
        assertFalse(slot.accept(1, 64, 1));
        assertFalse(slot.has(1, 64, 1));
    }

    @Test
    @DisplayName("under steady lag longer than a refresh, the answer stands once the first reply lands")
    void steadyLagStands() {
        InspectionSlot slot = new InspectionSlot();
        int lag = 45;
        java.util.ArrayDeque<Integer> due = new java.util.ArrayDeque<Integer>();
        boolean landed = false;
        for (int tick = 0; tick < 400; tick++) {
            // The client drains replies before it polls, so a reply due this tick is taken first.
            while (!due.isEmpty() && due.peekFirst() == tick) {
                due.pollFirst();
                assertTrue(slot.accept(1, 64, 1));
                landed = true;
            }
            if (slot.poll(1, 64, 1)) due.addLast(tick + lag);
            if (landed) assertTrue(slot.has(1, 64, 1), "dropped at tick " + tick);
        }
    }

    @Test
    @DisplayName("forgetting the position when the crosshair leaves makes looking back ask again")
    void forgettingMakesLookingBackAskAgain() {
        InspectionSlot slot = new InspectionSlot();
        slot.poll(1, 64, 1);
        slot.accept(1, 64, 1);
        slot.clear();
        assertTrue(slot.poll(1, 64, 1));
        assertFalse(slot.has(1, 64, 1));
    }
}
