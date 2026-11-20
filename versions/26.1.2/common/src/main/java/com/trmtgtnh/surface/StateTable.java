package com.trmtgtnh.surface;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The block states a data file sets apart from the rest of their block: FORMAT.md's {@code ns:name[half=bottom]}, "the
 * states with these properties", from 1.16.5 on (Xep, 2026-11-04).
 *
 * <p>
 * From 1.16.5 the surface tables are kept by block and form (a block, and a slab's doubled form), which is what built-in
 * detection and the player's lists can say, and what every block no file picks apart still asks. A state whose facts
 * from the files differ from its form's takes a {@link Row} of its own, its whole answer, asked ahead of the tables. So
 * a pack whose files name no property pays nothing and sends the same bytes it always did, the save format never meets
 * a row (a record holds a family, and the ghost remembers the whole state it covers), and there is no ceiling on how
 * many states of a block differ, which a wider form key in the codec's four bits would have had.
 *
 * <p>
 * On the wire the rows are laid out by the same two classes as the table, with a row's place in the sorted state ids for
 * its key, so neither {@link SurfaceTableCodec} nor {@link ChainTable}, shared byte for byte with the older editions,
 * changes: {@link #wrap} puts them after the table, behind a mark an older reader of {@link ChainTable#unwrap} refuses
 * rather than misreads, and a table without rows is not touched at all. State ids are the wire's own numbering, the one
 * vanilla's chunk packets use, so they mean the same state on both ends of a connection. Under protocol 3, as the table
 * is.
 *
 * <p>
 * Game-free, so the round trip and the fingerprint are tried without a game. Carried from 1.16.5 to each later edition.
 */
public final class StateTable {

    /** The most rows a table may carry: what the codec's blocks can hold at a row a nibble. */
    public static final int MOST_ROWS = 4096 * 16;

    /** The first four bytes of a table with rows: a length no table has, so an older reader refuses it. */
    private static final int MARK = -1;

    private StateTable() {}

    /** Everything a state set apart answers. A state is asked only here; nothing falls through to its block. */
    public static final class Row {

        /** The family it wears as; null when it does not wear. */
        public final SurfaceFamily family;

        public final boolean cover;

        public final boolean holds;

        /** Its data shape; null where its class decides. */
        public final DataShape shape;

        /** Its own chain; null where it follows its family's, and empty where it sinks as itself. */
        public final SurfaceFamily[] chain;

        /** How much more traffic it takes; the server's alone, so a row off the wire says 1. */
        public final float resistance;

        /** What else pays for mending it; the server's alone, so a row off the wire says none. */
        public final String[] repairWith;

        public Row(SurfaceFamily family, boolean cover, boolean holds, DataShape shape, SurfaceFamily[] chain,
            float resistance, String[] repairWith) {
            this.family = family;
            this.cover = cover;
            this.holds = holds;
            this.shape = shape;
            this.chain = chain;
            this.resistance = resistance;
            this.repairWith = repairWith;
        }
    }

    /** The table's own bytes and the rows that came after them. */
    public static final class Unwrapped {

        /** The bytes for {@link ChainTable#unwrap}. */
        public final byte[] table;

        /** The rows, by state id. Not to be written to. */
        public final Map<Integer, Row> rows;

        Unwrapped(byte[] table, Map<Integer, Row> rows) {
            this.table = table;
            this.rows = Collections.unmodifiableMap(rows);
        }
    }

    /**
     * A fingerprint of a table with the rows folded in. None leaves the table's own exactly as it was; any moves it, by
     * state and by every fact a client draws or stands on.
     */
    public static long fingerprint(long table, Map<Integer, Row> rows) {
        if (rows == null || rows.isEmpty()) return table;
        long hash = mix(table, -5);
        int[] states = sorted(rows);
        hash = mix(hash, states.length);
        for (int state : states) {
            Row row = rows.get(Integer.valueOf(state));
            hash = mix(hash, state);
            hash = mix(hash, row.family == null ? -1 : row.family.ordinal());
            hash = mix(hash, (row.cover ? 1 : 0) | (row.holds ? 2 : 0));
            hash = mix(hash, row.chain == null ? -1 : row.chain.length);
            if (row.chain != null) for (SurfaceFamily each : row.chain) hash = mix(hash, each.ordinal());
            DataShape shape = row.shape;
            if (shape == null) {
                hash = mix(hash, -1);
                continue;
            }
            hash = mix(hash, shape.kind);
            hash = mix(hash, shape.sinks ? 1 : 0);
            hash = mix(hash, shape.maxSinkPixels);
            hash = mix(hash, shape.boxCount());
            for (int b = 0; b < shape.boxCount(); b++) {
                for (int n : shape.box(b)) hash = mix(hash, n);
            }
        }
        return hash == 0L ? 1L : hash;
    }

    /**
     * The table's bytes with the rows after them; the table's bytes themselves where there are none.
     *
     * @throws IllegalArgumentException for more than {@link #MOST_ROWS} rows, or a row the codec cannot pack
     */
    public static byte[] wrap(byte[] table, Map<Integer, Row> rows) {
        if (rows == null || rows.isEmpty()) return table;
        if (rows.size() > MOST_ROWS) throw new IllegalArgumentException(rows.size() + " block states set apart");
        int[] states = sorted(rows);
        Map<Integer, Integer> families = new HashMap<Integer, Integer>();
        Set<Integer> cover = new HashSet<Integer>();
        Set<Integer> holds = new HashSet<Integer>();
        Map<Integer, DataShape> shapes = new HashMap<Integer, DataShape>();
        Map<Integer, int[]> chains = new HashMap<Integer, int[]>();
        for (int at = 0; at < states.length; at++) {
            Row row = rows.get(Integer.valueOf(states[at]));
            Integer place = Integer.valueOf(at);
            if (row.family != null) families.put(place, Integer.valueOf(row.family.ordinal()));
            if (row.cover) cover.add(place);
            if (row.holds) holds.add(place);
            if (row.shape != null) shapes.put(place, row.shape);
            if (row.chain != null) {
                int[] ordinals = new int[row.chain.length];
                for (int i = 0; i < ordinals.length; i++) ordinals[i] = row.chain[i].ordinal();
                chains.put(place, ordinals);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeInt(out, MARK);
        writeInt(out, table.length);
        out.write(table, 0, table.length);
        writeInt(out, states.length);
        // Each id as its step from the last: a block's states are neighbours, so most take one byte.
        int last = 0;
        for (int state : states) {
            if (state < 0) throw new IllegalArgumentException("state id out of range: " + state);
            writeStep(out, state - last);
            last = state;
        }
        byte[] laid = ChainTable.wrap(SurfaceTableCodec.encode(families, cover, holds, shapes), chains);
        out.write(laid, 0, laid.length);
        return out.toByteArray();
    }

    /**
     * The table's bytes and the rows back out of the wire's: a table with no mark is the whole of what was sent. Every
     * row is held to what a file could give, by the two classes that lay it out.
     *
     * @throws IllegalArgumentException for anything that is not a table with its rows
     */
    public static Unwrapped unwrap(byte[] bytes) {
        Map<Integer, Row> rows = new HashMap<Integer, Row>();
        if (bytes == null || bytes.length < 4) return new Unwrapped(bytes, rows);
        int[] at = { 0 };
        if (readInt(bytes, at) != MARK) return new Unwrapped(bytes, rows);
        int length = readInt(bytes, at);
        if (length < 0 || length > bytes.length - at[0]) throw new IllegalArgumentException("a table longer than sent");
        byte[] table = Arrays.copyOfRange(bytes, at[0], at[0] + length);
        at[0] += length;
        int count = readInt(bytes, at);
        if (count <= 0 || count > MOST_ROWS) throw new IllegalArgumentException(count + " block states set apart");
        int[] states = new int[count];
        int last = 0;
        for (int i = 0; i < count; i++) {
            last += readStep(bytes, at);
            if (last < 0 || (i > 0 && last <= states[i - 1])) throw new IllegalArgumentException("state ids out of order");
            states[i] = last;
        }
        ChainTable.Unwrapped laid = ChainTable.unwrap(Arrays.copyOfRange(bytes, at[0], bytes.length));
        SurfaceTableCodec.Table decoded = SurfaceTableCodec.decode(laid.table);
        SurfaceFamily[] all = SurfaceFamily.values();
        for (int i = 0; i < count; i++) {
            Integer place = Integer.valueOf(i);
            Integer ordinal = decoded.families.get(place);
            if (ordinal != null && ordinal.intValue() >= all.length) {
                throw new IllegalArgumentException("a row names family " + ordinal);
            }
            int[] ordinals = laid.chains.get(place);
            SurfaceFamily[] chain = null;
            if (ordinals != null) {
                chain = new SurfaceFamily[ordinals.length];
                for (int n = 0; n < ordinals.length; n++) chain[n] = all[ordinals[n]];
            }
            rows.put(
                Integer.valueOf(states[i]),
                new Row(
                    ordinal == null ? null : all[ordinal.intValue()],
                    decoded.cover.contains(place),
                    decoded.holds.contains(place),
                    decoded.shapes.get(place),
                    chain,
                    1f,
                    new String[0]));
        }
        return new Unwrapped(table, rows);
    }

    private static int[] sorted(Map<Integer, Row> rows) {
        TreeMap<Integer, Row> ordered = new TreeMap<Integer, Row>(rows);
        int[] out = new int[ordered.size()];
        int i = 0;
        for (Integer state : ordered.keySet()) out[i++] = state.intValue();
        return out;
    }

    /** As the codec mixes, so the fold reads as one more part of the same fingerprint. */
    private static long mix(long hash, int value) {
        for (int shift = 0; shift < 32; shift += 8) {
            hash ^= (value >>> shift) & 0xFF;
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    private static void writeInt(ByteArrayOutputStream out, int value) {
        for (int shift = 24; shift >= 0; shift -= 8) out.write((value >>> shift) & 0xFF);
    }

    private static void writeStep(ByteArrayOutputStream out, int value) {
        int left = value;
        while ((left & ~0x7F) != 0) {
            out.write((left & 0x7F) | 0x80);
            left >>>= 7;
        }
        out.write(left);
    }

    private static int readByte(byte[] bytes, int[] at) {
        if (at[0] >= bytes.length) throw new IllegalArgumentException("rows end part way through");
        return bytes[at[0]++] & 0xFF;
    }

    private static int readInt(byte[] bytes, int[] at) {
        return (readByte(bytes, at) << 24) | (readByte(bytes, at) << 16)
            | (readByte(bytes, at) << 8)
            | readByte(bytes, at);
    }

    private static int readStep(byte[] bytes, int[] at) {
        int value = 0;
        for (int shift = 0; shift < 35; shift += 7) {
            int next = readByte(bytes, at);
            value |= (next & 0x7F) << shift;
            if ((next & 0x80) == 0) return value;
        }
        throw new IllegalArgumentException("a state id runs on");
    }
}
