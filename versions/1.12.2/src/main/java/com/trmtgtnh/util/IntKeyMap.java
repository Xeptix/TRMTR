package com.trmtgtnh.util;

import java.util.Arrays;

/**
 * An int-keyed map of objects, open-addressed, with no boxing.
 *
 * <p>
 * One chunk of well-travelled ground holds thousands of wear records, and a world holds thousands of
 * chunks, so the map behind them is asked about more often than anything else in this mod and must not
 * allocate an {@link Integer} per lookup. That is what this is for, and it is the whole of why it
 * exists rather than a {@code HashMap<Integer, ?>}.
 *
 * <p>
 * <strong>It replaces Trove, which modern Minecraft does not ship.</strong> The 1.7.10 and 1.12.2
 * editions get {@code net.sf.trove4j} for free because Minecraft's own library list pulls it in;
 * nothing from 1.16.5 onward does, and the erosion layer is meant to be the part of this mod that
 * ports without caring what it is running on. Three lines of Trove in one file would have become a
 * dependency problem in the middle of a port, so they are gone now instead. Nothing else in the mod
 * ever saw the map's type.
 *
 * <h2>How it works, and why it is written out rather than borrowed</h2>
 *
 * <p>
 * Linear probing, power-of-two capacity, grown at three quarters full. A slot is free when its value
 * is null and a tombstone when its value is {@link #GONE} - which is needed because a probe sequence
 * that walked past a removed entry must keep walking, and a plain null there would end the search
 * early and lose the record behind it.
 *
 * <p>
 * Key 0 is a perfectly ordinary key here - it is chunk-local x 0, z 0, y 0, which is bedrock in the
 * corner of a chunk - so occupancy cannot be read off the key array and is read off the value array
 * instead. That is the one thing most hand-rolled versions of this get wrong.
 *
 * <p>
 * Not general-purpose and not public API beyond this mod: no null values, no iteration order, no
 * concurrent access. The erosion store is single-threaded per world and says so.
 */
public final class IntKeyMap<V> {

    /** A removed entry, which a probe must walk past rather than stop at. */
    private static final Object GONE = new Object();

    private int[] keys;

    private Object[] values;

    /** Live entries, not counting tombstones. */
    private int size;

    /** Live entries plus tombstones, which is what decides when to grow. */
    private int filled;

    public IntKeyMap() {
        this(16);
    }

    public IntKeyMap(int capacity) {
        int room = 4;
        while (room < capacity * 2) room <<= 1;
        keys = new int[room];
        values = new Object[room];
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    @SuppressWarnings("unchecked")
    public V get(int key) {
        int mask = keys.length - 1;
        int at = spread(key) & mask;
        while (true) {
            Object held = values[at];
            if (held == null) return null;
            if (held != GONE && keys[at] == key) return (V) held;
            at = (at + 1) & mask;
        }
    }

    public boolean containsKey(int key) {
        return get(key) != null;
    }

    /**
     * Puts a value, replacing any already under this key.
     *
     * <p>
     * A tombstone is reused where the probe finds one before it finds the key, which is what keeps a
     * map that is written and rewritten in place from growing for ever.
     */
    @SuppressWarnings("unchecked")
    public V put(int key, V value) {
        if (value == null) throw new IllegalArgumentException("no null values: key " + key);
        int mask = keys.length - 1;
        int at = spread(key) & mask;
        int reusable = -1;
        while (true) {
            Object held = values[at];
            if (held == null) break;
            if (held == GONE) {
                if (reusable < 0) reusable = at;
            } else if (keys[at] == key) {
                V was = (V) held;
                values[at] = value;
                return was;
            }
            at = (at + 1) & mask;
        }
        if (reusable >= 0) {
            keys[reusable] = key;
            values[reusable] = value;
            size++;
            return null;
        }
        keys[at] = key;
        values[at] = value;
        size++;
        filled++;
        if (filled * 4 >= keys.length * 3) grow();
        return null;
    }

    @SuppressWarnings("unchecked")
    public V remove(int key) {
        int mask = keys.length - 1;
        int at = spread(key) & mask;
        while (true) {
            Object held = values[at];
            if (held == null) return null;
            if (held != GONE && keys[at] == key) {
                V was = (V) held;
                values[at] = GONE;
                size--;
                return was;
            }
            at = (at + 1) & mask;
        }
    }

    public void clear() {
        Arrays.fill(values, null);
        size = 0;
        filled = 0;
    }

    /**
     * Every live key, in no particular order.
     *
     * <p>
     * A fresh array each time, because every caller either walks it while changing the map - which
     * iterating the table itself would not survive - or hands it to something that keeps it.
     */
    public int[] keys() {
        int[] out = new int[size];
        int found = 0;
        for (int i = 0; i < values.length; i++) {
            Object held = values[i];
            if (held != null && held != GONE) out[found++] = keys[i];
        }
        return out;
    }

    /** Every live value, in the same order {@link #keys()} returns its keys. */
    @SuppressWarnings("unchecked")
    public java.util.List<V> values() {
        java.util.List<V> out = new java.util.ArrayList<V>(size);
        for (Object held : values) {
            if (held != null && held != GONE) out.add((V) held);
        }
        return out;
    }

    /**
     * Rebuilt at twice the size, or at the same size when it is mostly tombstones.
     *
     * <p>
     * The second case matters here: a chunk of ground that heals completely and is walked in again
     * replaces every entry it ever had, and a map that only ever grew would end up enormous for a
     * chunk holding a handful of records.
     */
    private void grow() {
        int room = size * 4 >= keys.length ? keys.length << 1 : keys.length;
        int[] oldKeys = keys;
        Object[] oldValues = values;
        keys = new int[room];
        values = new Object[room];
        int mask = room - 1;
        for (int i = 0; i < oldValues.length; i++) {
            Object held = oldValues[i];
            if (held == null || held == GONE) continue;
            int at = spread(oldKeys[i]) & mask;
            while (values[at] != null) at = (at + 1) & mask;
            keys[at] = oldKeys[i];
            values[at] = held;
        }
        filled = size;
    }

    /**
     * Stirs a key before it is masked.
     *
     * <p>
     * The keys this holds are packed positions, so their low bits are a y coordinate and their high
     * bits a chunk-local x and z. A column of worn ground is therefore a run of keys differing only in
     * the low byte, and a run like that masked straight into a small table lands in consecutive slots
     * and probes through each other. Murmur's finalising mix spreads them for the cost of two shifts
     * and two multiplies.
     */
    private static int spread(int key) {
        int h = key;
        h ^= h >>> 16;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        return h & 0x7fffffff;
    }
}
