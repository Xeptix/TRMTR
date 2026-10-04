package com.trmtgtnh.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * How long a golem remembers that its stores could not pay.
 *
 * <p>
 * Both ways this can go wrong are quiet. A refusal that lasts too long leaves a golem standing beside
 * a chest that has just been filled, saying it needs the right block; one that lasts too short a time
 * brings back the walk of every container for every square, which nobody sees except as a server that
 * has become slower. So each lifetime is pinned here by the exact counter that should end it and by the
 * ones that should not.
 *
 * <p>
 * The figures stand in for what the golem passes: a scan is the tick its stores were last looked at,
 * never below one once they have been, and a stock is a counter bumped whenever anything it carries
 * changes. The block ids are only numbers here; nothing is looked up.
 */
class RefusedFetchesTest {

    private static final long KIND = RefusedFetches.kindOf(1, 0);
    private static final long OTHER = RefusedFetches.kindOf(2, 0);

    @Test
    @DisplayName("a kind is its block and its metadata, and neither can pass for the other")
    void kindsPackBlockAndMeta() {
        assertNotEquals(RefusedFetches.kindOf(1, 0), RefusedFetches.kindOf(0, 1));
        assertNotEquals(RefusedFetches.kindOf(5, 15), RefusedFetches.kindOf(5, 14));
        assertEquals(RefusedFetches.kindOf(5, 15), RefusedFetches.kindOf(5, 15));
        // A negative metadata must not spread its sign across the block id and make every block alike.
        assertNotEquals(RefusedFetches.kindOf(1, -1), RefusedFetches.kindOf(2, -1));
    }

    @Test
    @DisplayName("nothing to be had stands until the stores are looked at again, whatever the golem carries")
    void nothingToBeHadHoldsUntilTheNextScan() {
        RefusedFetches memo = new RefusedFetches();
        memo.nothingToBeHad(KIND, 20);
        assertTrue(memo.stillRefused(KIND, 20, 0));
        assertTrue(memo.stillRefused(KIND, 20, 99), "nothing the golem carries can fill a container");
        assertFalse(memo.stillRefused(KIND, 220, 0), "the next look at the stores may find one restocked");
    }

    @Test
    @DisplayName("no room stands until the stores are looked at again or anything the golem carries changes")
    void noRoomHoldsUntilStockOrScanChanges() {
        RefusedFetches memo = new RefusedFetches();
        memo.noRoomFor(KIND, 20, 7);
        assertTrue(memo.stillRefused(KIND, 20, 7));
        assertFalse(memo.stillRefused(KIND, 20, 8), "whatever changed may have made room");
        assertFalse(memo.stillRefused(KIND, 21, 7), "a fresh look at the stores ends it too");
    }

    @Test
    @DisplayName("a kind recorded again takes the new refusal's lifetime and no second place")
    void aLaterRecordReplacesTheEarlier() {
        RefusedFetches memo = new RefusedFetches();
        memo.nothingToBeHad(KIND, 20);
        memo.noRoomFor(KIND, 20, 7);
        assertEquals(1, memo.size());
        assertTrue(memo.stillRefused(KIND, 20, 7));
        assertFalse(memo.stillRefused(KIND, 20, 8), "now a refusal for want of room, which a change of stock ends");

        memo.nothingToBeHad(KIND, 20);
        assertEquals(1, memo.size());
        assertTrue(memo.stillRefused(KIND, 20, 8), "and back to nothing to be had, which a change of stock does not");

        memo.nothingToBeHad(KIND, 220);
        assertEquals(1, memo.size());
        assertTrue(memo.stillRefused(KIND, 220, 0));
        assertFalse(memo.stillRefused(KIND, 20, 0), "the earlier scan is no longer the one it was made against");
    }

    @Test
    @DisplayName("a kind recorded again when every place is taken pushes nothing out")
    void aKindRecordedAgainTakesNoNewPlace() {
        RefusedFetches memo = new RefusedFetches();
        for (int block = 1; block <= RefusedFetches.MOST_KINDS; block++) {
            memo.nothingToBeHad(RefusedFetches.kindOf(block, 0), 20);
        }
        memo.noRoomFor(RefusedFetches.kindOf(RefusedFetches.MOST_KINDS, 0), 20, 7);
        memo.nothingToBeHad(RefusedFetches.kindOf(1, 0), 20);
        assertEquals(RefusedFetches.MOST_KINDS, memo.size());
        for (int block = 1; block < RefusedFetches.MOST_KINDS; block++) {
            assertTrue(memo.stillRefused(RefusedFetches.kindOf(block, 0), 20, 0), "block " + block);
        }
        assertTrue(memo.stillRefused(RefusedFetches.kindOf(RefusedFetches.MOST_KINDS, 0), 20, 7));
    }

    @Test
    @DisplayName("a refusal for one kind says nothing about another")
    void kindsAreSeparate() {
        RefusedFetches memo = new RefusedFetches();
        memo.nothingToBeHad(KIND, 20);
        assertFalse(memo.stillRefused(OTHER, 20, 0));
        memo.noRoomFor(OTHER, 20, 3);
        assertEquals(2, memo.size());
        assertTrue(memo.stillRefused(KIND, 20, 4), "the other kind's lifetime is not this one's");
        assertTrue(memo.stillRefused(OTHER, 20, 3));
        assertFalse(memo.stillRefused(OTHER, 20, 4));
        assertFalse(memo.stillRefused(RefusedFetches.kindOf(1, 1), 20, 0), "the same block at another metadata");
    }

    @Test
    @DisplayName("nothing learned before the stores were first looked at is remembered")
    void aScanOfNoughtIsNeverRemembered() {
        RefusedFetches memo = new RefusedFetches();
        memo.nothingToBeHad(KIND, 0);
        memo.noRoomFor(OTHER, 0, 0);
        memo.nothingToBeHad(RefusedFetches.kindOf(3, 0), -5);
        assertEquals(0, memo.size());
        assertFalse(memo.stillRefused(KIND, 0, 0));
        assertFalse(memo.stillRefused(OTHER, 0, 0));

        // Nor does an ask at nought match a refusal that was made against a real look.
        memo.nothingToBeHad(KIND, 20);
        assertFalse(memo.stillRefused(KIND, 0, 0));
    }

    @Test
    @DisplayName("forgetting clears every kind, and the memo fills from its first place again afterwards")
    void forgetClearsEverything() {
        RefusedFetches memo = new RefusedFetches();
        // Past full, so the kinds past the last place were offered and not taken.
        for (int block = 1; block <= RefusedFetches.MOST_KINDS + 5; block++) {
            memo.nothingToBeHad(RefusedFetches.kindOf(block, 0), 20);
        }
        memo.noRoomFor(RefusedFetches.kindOf(1000, 0), 20, 7);
        memo.forget();
        assertEquals(0, memo.size());
        for (int block = 1; block <= RefusedFetches.MOST_KINDS + 5; block++) {
            assertFalse(memo.stillRefused(RefusedFetches.kindOf(block, 0), 20, 0), "block " + block);
        }
        assertFalse(memo.stillRefused(RefusedFetches.kindOf(1000, 0), 20, 7));

        memo.nothingToBeHad(KIND, 20);
        assertEquals(1, memo.size());
        assertTrue(memo.stillRefused(KIND, 20, 0));

        // Filled again from nothing, every place is free once more: the first sixty-four kinds recorded after
        // forgetting are all held, and only the one past them is turned away.
        memo.forget();
        for (int block = 101; block <= 100 + RefusedFetches.MOST_KINDS + 1; block++) {
            memo.nothingToBeHad(RefusedFetches.kindOf(block, 0), 20);
        }
        assertEquals(RefusedFetches.MOST_KINDS, memo.size());
        assertTrue(memo.stillRefused(RefusedFetches.kindOf(101, 0), 20, 0), "the first recorded after forgetting");
        assertTrue(memo.stillRefused(RefusedFetches.kindOf(100 + RefusedFetches.MOST_KINDS, 0), 20, 0));
        assertFalse(memo.stillRefused(RefusedFetches.kindOf(100 + RefusedFetches.MOST_KINDS + 1, 0), 20, 0));
    }

    @Test
    @DisplayName("a full memo takes nothing new and pushes nothing out")
    void aFullMemoTakesNothingNew() {
        RefusedFetches memo = new RefusedFetches();
        int past = RefusedFetches.MOST_KINDS + 1;
        for (int block = 1; block <= past; block++) {
            memo.nothingToBeHad(RefusedFetches.kindOf(block, 0), 20);
        }
        assertEquals(RefusedFetches.MOST_KINDS, memo.size());
        for (int block = 1; block <= RefusedFetches.MOST_KINDS; block++) {
            assertTrue(memo.stillRefused(RefusedFetches.kindOf(block, 0), 20, 0), "block " + block + " is held");
        }
        assertFalse(memo.stillRefused(RefusedFetches.kindOf(past, 0), 20, 0), "the one past full was not taken");
    }

    @Test
    @DisplayName("a golem asking after seventy kinds in the same order every stroke walks only the six past full")
    void aStrokeInTheSameOrderWalksOnlyWhatDoesNotFit() {
        RefusedFetches memo = new RefusedFetches();
        int kinds = RefusedFetches.MOST_KINDS + 6;
        int firstStroke = walksInOrder(memo, kinds);
        int secondStroke = walksInOrder(memo, kinds);
        assertEquals(kinds, firstStroke, "nothing is known before the first stroke");
        assertEquals(6, secondStroke, "the sixty-four held are not walked again");
        assertEquals(6, walksInOrder(memo, kinds), "and it stays that way");
    }

    /** One stroke's worth of asking after kinds 1 to count in order, walking and recording each not refused. */
    private static int walksInOrder(RefusedFetches memo, int count) {
        int walks = 0;
        for (int block = 1; block <= count; block++) {
            long kind = RefusedFetches.kindOf(block, 0);
            if (memo.stillRefused(kind, 20, 0)) continue;
            walks++;
            memo.nothingToBeHad(kind, 20);
        }
        return walks;
    }
}
