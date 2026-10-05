package com.trmtgtnh.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * The map that holds a world's wear records, tested like something that holds a world's wear records.
 *
 * <p>
 * This replaced three lines of Trove, and the thing being bought with that is not performance - it is
 * that the erosion layer stops depending on a library modern Minecraft no longer ships. The thing
 * being risked is a player's ground, so the interesting tests here are not the happy path but the
 * three ways an open-addressed map loses data: a key of zero read as an empty slot, a removal that
 * breaks the probe sequence behind it, and a table that grows while tombstones are in it.
 */
class IntKeyMapTest {

    /** Key 0 is chunk-local x 0, z 0, y 0 - ordinary bedrock, not an absence. */
    @Test
    void zeroIsAnOrdinaryKey() {
        IntKeyMap<String> map = new IntKeyMap<String>();
        map.put(0, "bedrock corner");

        assertEquals("bedrock corner", map.get(0));
        assertEquals(1, map.size());
        assertTrue(map.containsKey(0));
        assertArrayEquals(new int[] { 0 }, map.keys());
    }

    /**
     * A removal must not hide what was probed past to reach it.
     *
     * <p>
     * Two keys that collide, the first removed, and the second must still be found. Without a
     * tombstone the probe stops at the hole and the second record is gone - silently, which is the
     * worst way for a store to fail.
     */
    @Test
    void aRemovalDoesNotHideWhatIsBehindIt() {
        IntKeyMap<String> map = new IntKeyMap<String>(4);
        List<Integer> colliding = keysSharingASlot(3);

        for (int key : colliding) map.put(key, "at " + key);
        int first = colliding.get(0);
        map.remove(first);

        assertNull(map.get(first));
        for (int i = 1; i < colliding.size(); i++) {
            int key = colliding.get(i);
            assertEquals("at " + key, map.get(key), "lost the record behind a removal");
        }
        assertEquals(colliding.size() - 1, map.size());
    }

    /** Growing while tombstones are present must keep every live entry and drop every dead one. */
    @Test
    void growingPastTombstonesKeepsTheLiveEntries() {
        IntKeyMap<String> map = new IntKeyMap<String>(4);
        for (int i = 0; i < 400; i++) map.put(i, "v" + i);
        for (int i = 0; i < 400; i += 2) map.remove(i);
        for (int i = 400; i < 800; i++) map.put(i, "v" + i);

        assertEquals(200 + 400, map.size());
        for (int i = 1; i < 400; i += 2) assertEquals("v" + i, map.get(i));
        for (int i = 0; i < 400; i += 2) assertNull(map.get(i));
        for (int i = 400; i < 800; i++) assertEquals("v" + i, map.get(i));
    }

    /** A map written and rewritten in place must not grow without bound. */
    @Test
    void rewritingInPlaceDoesNotGrowForEver() {
        IntKeyMap<String> map = new IntKeyMap<String>(16);
        for (int round = 0; round < 500; round++) {
            for (int i = 0; i < 20; i++) map.put(i, "r" + round);
            for (int i = 0; i < 20; i++) map.remove(i);
        }
        map.put(1, "last");

        assertEquals(1, map.size());
        assertEquals("last", map.get(1));
        assertEquals(1, map.keys().length);
    }

    /** Against the JDK's own map, on the key shapes this actually sees. */
    @Test
    void agreesWithHashMapOverAThousandRandomOperations() {
        IntKeyMap<String> mine = new IntKeyMap<String>();
        Map<Integer, String> theirs = new HashMap<Integer, String>();
        // Fixed seed: a failure here has to be reproducible, and a store is not a place for surprises.
        Random random = new Random(20261002L);

        for (int step = 0; step < 20000; step++) {
            // The real key shape: four bits of x, four of z, a signed y in the low sixteen.
            int key = ((random.nextInt(16) & 0xF) << 20) | ((random.nextInt(16) & 0xF) << 16)
                | ((random.nextInt(384) - 64) & 0xFFFF);
            if (random.nextInt(3) == 0) {
                assertEquals(theirs.remove(key), mine.remove(key), "remove disagreed at " + key);
            } else {
                String value = "s" + step;
                assertEquals(theirs.put(key, value), mine.put(key, value), "put disagreed at " + key);
            }
            assertEquals(theirs.size(), mine.size(), "size disagreed after step " + step);
        }

        for (Map.Entry<Integer, String> held : theirs.entrySet()) {
            assertEquals(
                held.getValue(),
                mine.get(
                    held.getKey()
                        .intValue()));
        }
        int[] keys = mine.keys();
        assertEquals(theirs.size(), keys.length);
        for (int key : keys) assertTrue(theirs.containsKey(Integer.valueOf(key)), "keys() invented " + key);
    }

    /** Negative keys work, because a packed y below zero makes the whole key negative. */
    @Test
    void negativeKeysWork() {
        IntKeyMap<String> map = new IntKeyMap<String>();
        int below = ((3 & 0xF) << 20) | ((5 & 0xF) << 16) | (-64 & 0xFFFF);
        map.put(below, "deep");
        map.put(-1, "all ones");

        assertEquals("deep", map.get(below));
        assertEquals("all ones", map.get(-1));
        assertEquals(2, map.size());
    }

    /** Putting over a key returns what was there, and does not change the size. */
    @Test
    void replacingReturnsTheOldValue() {
        IntKeyMap<String> map = new IntKeyMap<String>();
        assertNull(map.put(7, "first"));
        assertEquals("first", map.put(7, "second"));
        assertEquals("second", map.get(7));
        assertEquals(1, map.size());
    }

    /** A null value is a bug in the caller, not a way to remove something. */
    @Test
    void nullValuesAreRefused() {
        IntKeyMap<String> map = new IntKeyMap<String>();
        assertThrows(IllegalArgumentException.class, () -> map.put(1, null));
    }

    @Test
    void clearEmptiesIt() {
        IntKeyMap<String> map = new IntKeyMap<String>();
        for (int i = 0; i < 100; i++) map.put(i, "v" + i);
        map.clear();

        assertEquals(0, map.size());
        assertTrue(map.isEmpty());
        assertEquals(0, map.keys().length);
        assertNull(map.get(5));
        map.put(5, "again");
        assertEquals("again", map.get(5));
    }

    /** values() lines up with keys(), because two callers read them as a pair. */
    @Test
    void valuesLineUpWithKeys() {
        IntKeyMap<String> map = new IntKeyMap<String>();
        for (int i = 0; i < 50; i++) map.put(i * 7, "v" + (i * 7));
        map.remove(21);

        int[] keys = map.keys();
        List<String> values = map.values();
        assertEquals(keys.length, values.size());
        for (int i = 0; i < keys.length; i++) {
            assertEquals("v" + keys[i], values.get(i));
        }
    }

    /** The same instance comes back, not an equal one. */
    @Test
    void theSameInstanceComesBack() {
        IntKeyMap<Object> map = new IntKeyMap<Object>();
        Object value = new Object();
        map.put(42, value);
        assertSame(value, map.get(42));
    }

    /**
     * Keys that land in the same slot, for the probe tests above.
     *
     * <p>
     * The mixing is duplicated here rather than observed, which is the right way round: what the
     * helper needs is a pair that <em>collides</em>, and what the tests then check is that the real
     * map survives the collision. Observing the map to find a collision would mean asking it to
     * misbehave in order to prove it does not. If the mix ever changes so much that no pair is found,
     * this fails loudly rather than quietly testing nothing.
     */
    private static List<Integer> keysSharingASlot(int wanted) {
        List<Integer> found = new ArrayList<Integer>();
        int target = -1;
        for (int key = 0; found.size() < wanted && key < 1_000_000; key++) {
            int slot = slot(key);
            if (target < 0) {
                target = slot;
                found.add(key);
            } else if (slot == target) {
                found.add(key);
            }
        }
        assertTrue(found.size() >= 2, "could not find colliding keys to test with");
        return found;
    }

    /**
     * Which slot of an eight-slot table a key mixes into.
     *
     * <p>
     * Eight because the map rounds a requested capacity of four up to twice that, and the tests above
     * ask for four. Kept in step with {@code IntKeyMap.spread} by hand.
     */
    private static int slot(int key) {
        int h = key;
        h ^= h >>> 16;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        return (h & 0x7fffffff) & 7;
    }

    private static void assertArrayEquals(int[] expected, int[] actual) {
        assertTrue(
            Arrays.equals(expected, actual),
            "expected " + Arrays.toString(expected) + " but was " + Arrays.toString(actual));
    }
}
