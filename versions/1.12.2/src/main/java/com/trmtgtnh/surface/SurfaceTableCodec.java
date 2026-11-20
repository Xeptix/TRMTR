package com.trmtgtnh.surface;

import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * A resolved surface table as bytes for the wire, and a fingerprint that says whether two are the same.
 *
 * <p>
 * The server's table is the one that decides what wears, and a client that built its own out of a
 * different file drew and collided differently from the ground it was standing on. So the server
 * sends a fingerprint with its rules and, to a client whose own does not match, the table itself.
 * Kept free of anything from the game, like the rest of the portable core, because the two things it
 * does are exactly the things worth pinning with a test: a table that comes back different from the
 * one sent, and a fingerprint that fails to move when a table does.
 *
 * <p>
 * Keys are the registry's own packing, a block id shifted four and a metadata value in the low four
 * bits. Ids are only meaningful under one id map, and that is safe here for a reason outside this
 * class: Forge hands a joining client the server's id map before any of this is sent.
 *
 * <p>
 * <strong>Format 2</strong> (0.9.229, W3 spec 2.4, task T6) adds the shapes data files give blocks, because a shape
 * decides where a player stands and so is the server's: the distinct shapes once each, then each shaped block's
 * sixteen values as an index into them. It travels only under protocol 3, which is the same step for every edition,
 * so a build that reads format 1 is never sent one: it is told to update by the protocol paths instead.
 */
public final class SurfaceTableCodec {

    /** The layout below. A decoder that meets a different one refuses rather than misreads. */
    public static final int FORMAT = 2;

    /** The most distinct shapes a table may carry, each named by a byte; the byte's last value means "no shape". */
    public static final int MOST_SHAPES = 255;

    /** A metadata value with no shape, in its block's byte of shape indexes. */
    private static final int NO_SHAPE = 0xFF;

    /**
     * The most a table may take once compressed.
     *
     * <p>
     * A server's custom payload is written with a two-byte length, so its real ceiling is 65,535
     * bytes whatever the constructor claims, and the packet needs a few of those for itself. A large
     * pack's table compresses to a few kilobytes, so this is a guard against a pathological one rather
     * than a figure anybody should meet.
     */
    public static final int MAX_COMPRESSED = 60000;

    /**
     * The most raw bytes a well-formed table can inflate to: every id with every value, twice over, and every shape
     * with every box and every id with a shape at each value.
     */
    private static final int MAX_RAW = 1 + 2
        + 4096 * 10
        + 2
        + 4096 * 6
        + 2
        + MOST_SHAPES * (4 + 6 * DataShape.MOST_BOXES)
        + 2
        + 4096 * 18;

    /** A metadata value that belongs to no family, in the four bits a family ordinal is given. */
    private static final int NONE = 0xF;

    private SurfaceTableCodec() {}

    /** A decoded table: family ordinals by packed key, the two ground-cover sets, and the data files' shapes. */
    public static final class Table {

        public final Map<Integer, Integer> families;

        public final Set<Integer> cover;

        public final Set<Integer> holds;

        /** The shapes data files give block states, by packed key (format 2). */
        public final Map<Integer, DataShape> shapes;

        public Table(Map<Integer, Integer> families, Set<Integer> cover, Set<Integer> holds) {
            this(families, cover, holds, Collections.<Integer, DataShape>emptyMap());
        }

        public Table(Map<Integer, Integer> families, Set<Integer> cover, Set<Integer> holds,
            Map<Integer, DataShape> shapes) {
            this.families = Collections.unmodifiableMap(new HashMap<Integer, Integer>(families));
            this.cover = Collections.unmodifiableSet(new HashSet<Integer>(cover));
            this.holds = Collections.unmodifiableSet(new HashSet<Integer>(holds));
            this.shapes = Collections.unmodifiableMap(new HashMap<Integer, DataShape>(shapes));
        }
    }

    /**
     * A fingerprint of a table, the same wherever and in whatever order it was built.
     *
     * <p>
     * Taken over the entries in key order rather than summed, because a sum is blind to two blocks
     * swapping families - which is precisely the edit the family editor makes. Sixty-four bits of
     * FNV-1a, which is not a cryptographic promise and does not need to be: nothing here is defended
     * against a server that wants to lie, only against two honest tables that differ.
     *
     * @param holdsSwitch whether planted ground cover holds its square, which decides as much about
     *                    what a client draws as either set does and so is part of the same answer
     */
    public static long fingerprint(Map<Integer, Integer> families, Set<Integer> cover, Set<Integer> holds,
        boolean holdsSwitch) {
        long hash = 0xcbf29ce484222325L;
        int[] keys = sorted(families.keySet());
        hash = mix(hash, keys.length);
        for (int key : keys) {
            hash = mix(hash, key);
            hash = mix(
                hash,
                families.get(Integer.valueOf(key))
                    .intValue());
        }
        hash = mix(hash, -1);
        int[] covered = sorted(cover);
        hash = mix(hash, covered.length);
        for (int key : covered) hash = mix(hash, key);
        hash = mix(hash, -2);
        int[] held = sorted(holds);
        hash = mix(hash, held.length);
        for (int key : held) hash = mix(hash, key);
        return mix(hash, holdsSwitch ? 1 : 0);
    }

    /**
     * The same, with the data files' shapes. No shapes leaves the fingerprint exactly as the four-part one gives it, so
     * a side without shape data still agrees with one from before format 2; any shape moves it, by state and by every
     * fact of the shape, because each decides where somebody stands.
     */
    public static long fingerprint(Map<Integer, Integer> families, Set<Integer> cover, Set<Integer> holds,
        boolean holdsSwitch, Map<Integer, DataShape> shapes) {
        long hash = fingerprint(families, cover, holds, holdsSwitch);
        if (shapes == null || shapes.isEmpty()) return hash;
        int[] keys = sorted(shapes.keySet());
        hash = mix(hash, -3);
        hash = mix(hash, keys.length);
        for (int key : keys) {
            DataShape shape = shapes.get(Integer.valueOf(key));
            hash = mix(hash, key);
            hash = mix(hash, shape.kind);
            hash = mix(hash, shape.sinks ? 1 : 0);
            hash = mix(hash, shape.maxSinkPixels);
            hash = mix(hash, shape.boxCount());
            for (int b = 0; b < shape.boxCount(); b++) {
                for (int n : shape.box(b)) hash = mix(hash, n);
            }
        }
        return hash;
    }

    /**
     * The table as compressed bytes.
     *
     * <p>
     * A block at a time rather than an entry at a time: detection claims all sixteen metadata values
     * of a block together, so a block's families pack into eight bytes of nibbles beside its id, and
     * its ground-cover membership into two sixteen-bit masks. About ten bytes a block before
     * compression, and the nibbles of a block whose values all share one family compress to almost
     * nothing.
     *
     * @throws IllegalArgumentException for a key or an ordinal that cannot be packed
     */
    public static byte[] encode(Map<Integer, Integer> families, Set<Integer> cover, Set<Integer> holds) {
        return encode(families, cover, holds, Collections.<Integer, DataShape>emptyMap());
    }

    /**
     * The table with the data files' shapes, as compressed bytes.
     *
     * @throws IllegalArgumentException for a key, an ordinal or a shape that cannot be packed, or more distinct shapes
     *                                  than {@link #MOST_SHAPES}
     */
    public static byte[] encode(Map<Integer, Integer> families, Set<Integer> cover, Set<Integer> holds,
        Map<Integer, DataShape> shapes) {
        TreeMap<Integer, int[]> byBlock = new TreeMap<Integer, int[]>();
        for (Map.Entry<Integer, Integer> entry : families.entrySet()) {
            int key = entry.getKey()
                .intValue();
            int ordinal = entry.getValue()
                .intValue();
            if (key < 0 || (key >>> 4) > 0xFFFF) throw new IllegalArgumentException("key out of range: " + key);
            if (ordinal < 0 || ordinal >= NONE) throw new IllegalArgumentException("ordinal out of range: " + ordinal);
            int[] nibbles = byBlock.get(Integer.valueOf(key >>> 4));
            if (nibbles == null) {
                nibbles = new int[16];
                java.util.Arrays.fill(nibbles, NONE);
                byBlock.put(Integer.valueOf(key >>> 4), nibbles);
            }
            nibbles[key & 0xF] = ordinal;
        }

        TreeMap<Integer, int[]> masks = new TreeMap<Integer, int[]>();
        addMasks(masks, cover, 0);
        addMasks(masks, holds, 1);

        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        // The layout first, so a decoder from a build that lays tables out differently refuses one
        // rather than reading its nibbles as something else.
        raw.write(FORMAT);
        writeShort(raw, byBlock.size());
        for (Map.Entry<Integer, int[]> entry : byBlock.entrySet()) {
            writeShort(
                raw,
                entry.getKey()
                    .intValue());
            int[] nibbles = entry.getValue();
            for (int i = 0; i < 16; i += 2) {
                raw.write(nibbles[i] | (nibbles[i + 1] << 4));
            }
        }
        writeShort(raw, masks.size());
        for (Map.Entry<Integer, int[]> entry : masks.entrySet()) {
            writeShort(
                raw,
                entry.getKey()
                    .intValue());
            writeShort(raw, entry.getValue()[0]);
            writeShort(raw, entry.getValue()[1]);
        }
        writeShapes(raw, shapes == null ? Collections.<Integer, DataShape>emptyMap() : shapes);

        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        try {
            deflater.setInput(raw.toByteArray());
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            while (!deflater.finished()) {
                int made = deflater.deflate(buffer);
                out.write(buffer, 0, made);
            }
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }

    /**
     * A table back out of its bytes.
     *
     * @throws IllegalArgumentException for anything that is not a well-formed table, including one
     *                                  that would inflate past the size a real table can reach
     */
    public static Table decode(byte[] compressed) {
        if (compressed == null || compressed.length == 0 || compressed.length > MAX_COMPRESSED) {
            throw new IllegalArgumentException("table bytes missing or too large");
        }
        byte[] raw = inflate(compressed);
        int[] at = { 0 };
        int layout = readByte(raw, at);
        if (layout != FORMAT) {
            throw new IllegalArgumentException("table laid out as " + layout + ", this build reads " + FORMAT);
        }

        Map<Integer, Integer> families = new HashMap<Integer, Integer>();
        int blocks = readShort(raw, at);
        for (int b = 0; b < blocks; b++) {
            int id = readShort(raw, at);
            for (int i = 0; i < 16; i += 2) {
                int packed = readByte(raw, at);
                put(families, id, i, packed & 0xF);
                put(families, id, i + 1, (packed >>> 4) & 0xF);
            }
        }

        Set<Integer> cover = new HashSet<Integer>();
        Set<Integer> holds = new HashSet<Integer>();
        int masked = readShort(raw, at);
        for (int m = 0; m < masked; m++) {
            int id = readShort(raw, at);
            int coverMask = readShort(raw, at);
            int holdMask = readShort(raw, at);
            for (int meta = 0; meta < 16; meta++) {
                Integer key = Integer.valueOf((id << 4) | meta);
                if ((coverMask & (1 << meta)) != 0) cover.add(key);
                if ((holdMask & (1 << meta)) != 0) holds.add(key);
            }
        }
        Map<Integer, DataShape> shapes = readShapes(raw, at);
        if (at[0] != raw.length) throw new IllegalArgumentException("trailing bytes after the table");
        return new Table(families, cover, holds, shapes);
    }

    /**
     * The shapes section: the distinct shapes, each as its kind, a flags byte (bit 0: sinks), its cap (0xFF for none),
     * its box count and six bytes a box; then each shaped block's id and sixteen bytes, an index into those shapes for
     * each metadata value or 0xFF for none.
     */
    private static void writeShapes(ByteArrayOutputStream raw, Map<Integer, DataShape> shapes) {
        java.util.LinkedHashMap<DataShape, Integer> distinct = new java.util.LinkedHashMap<DataShape, Integer>();
        TreeMap<Integer, int[]> byBlock = new TreeMap<Integer, int[]>();
        for (int key : sorted(shapes.keySet())) {
            DataShape shape = shapes.get(Integer.valueOf(key));
            if (shape == null) throw new IllegalArgumentException("no shape at key " + key);
            if (key < 0 || (key >>> 4) > 0xFFFF) throw new IllegalArgumentException("key out of range: " + key);
            Integer index = distinct.get(shape);
            if (index == null) {
                if (distinct.size() >= MOST_SHAPES) {
                    throw new IllegalArgumentException("more than " + MOST_SHAPES + " distinct shapes");
                }
                index = Integer.valueOf(distinct.size());
                distinct.put(shape, index);
            }
            int[] indexes = byBlock.get(Integer.valueOf(key >>> 4));
            if (indexes == null) {
                indexes = new int[16];
                java.util.Arrays.fill(indexes, NO_SHAPE);
                byBlock.put(Integer.valueOf(key >>> 4), indexes);
            }
            indexes[key & 0xF] = index.intValue();
        }
        writeShort(raw, distinct.size());
        for (DataShape shape : distinct.keySet()) {
            raw.write(shape.kind);
            raw.write(shape.sinks ? 1 : 0);
            raw.write(shape.maxSinkPixels == DataShape.NO_CAP ? 0xFF : shape.maxSinkPixels);
            raw.write(shape.boxCount());
            for (int b = 0; b < shape.boxCount(); b++) {
                for (int n : shape.box(b)) raw.write(n);
            }
        }
        writeShort(raw, byBlock.size());
        for (Map.Entry<Integer, int[]> entry : byBlock.entrySet()) {
            writeShort(
                raw,
                entry.getKey()
                    .intValue());
            for (int index : entry.getValue()) raw.write(index);
        }
    }

    private static Map<Integer, DataShape> readShapes(byte[] raw, int[] at) {
        int count = readShort(raw, at);
        if (count > MOST_SHAPES) throw new IllegalArgumentException(count + " distinct shapes");
        DataShape[] distinct = new DataShape[count];
        for (int s = 0; s < count; s++) {
            int kind = readByte(raw, at);
            int flags = readByte(raw, at);
            if ((flags & ~1) != 0) throw new IllegalArgumentException("shape flags " + flags);
            int cap = readByte(raw, at);
            int boxes = readByte(raw, at);
            if (boxes > DataShape.MOST_BOXES) throw new IllegalArgumentException(boxes + " boxes in a shape");
            int[][] made = new int[boxes][];
            for (int b = 0; b < boxes; b++) {
                made[b] = new int[6];
                for (int n = 0; n < 6; n++) made[b][n] = readByte(raw, at);
            }
            // The constructor holds every rule a file is held to, so a shape no file could give is refused here.
            distinct[s] = new DataShape(kind, (flags & 1) != 0, cap == 0xFF ? DataShape.NO_CAP : cap, made);
        }
        Map<Integer, DataShape> shapes = new HashMap<Integer, DataShape>();
        int blocks = readShort(raw, at);
        for (int b = 0; b < blocks; b++) {
            int id = readShort(raw, at);
            for (int meta = 0; meta < 16; meta++) {
                int index = readByte(raw, at);
                if (index == NO_SHAPE) continue;
                if (index >= count) throw new IllegalArgumentException("a shape index past the shapes sent");
                shapes.put(Integer.valueOf((id << 4) | meta), distinct[index]);
            }
        }
        return shapes;
    }

    private static void put(Map<Integer, Integer> families, int id, int meta, int ordinal) {
        if (ordinal == NONE) return;
        families.put(Integer.valueOf((id << 4) | meta), Integer.valueOf(ordinal));
    }

    private static void addMasks(TreeMap<Integer, int[]> masks, Set<Integer> keys, int which) {
        for (Integer boxed : keys) {
            int key = boxed.intValue();
            if (key < 0 || (key >>> 4) > 0xFFFF) throw new IllegalArgumentException("key out of range: " + key);
            int[] pair = masks.get(Integer.valueOf(key >>> 4));
            if (pair == null) {
                pair = new int[2];
                masks.put(Integer.valueOf(key >>> 4), pair);
            }
            pair[which] |= 1 << (key & 0xF);
        }
    }

    private static byte[] inflate(byte[] compressed) {
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(compressed);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            while (!inflater.finished()) {
                int got = inflater.inflate(buffer);
                if (got == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    throw new IllegalArgumentException("table bytes end part way through");
                }
                out.write(buffer, 0, got);
                if (out.size() > MAX_RAW) throw new IllegalArgumentException("table inflates past any real table");
            }
            // Anything after the end of the compressed stream is not part of a table, and accepting it
            // would make the trailing-byte check below a promise about half the payload.
            if (inflater.getRemaining() > 0) throw new IllegalArgumentException("bytes after the end of the table");
            return out.toByteArray();
        } catch (DataFormatException malformed) {
            throw new IllegalArgumentException("table bytes are not a compressed table", malformed);
        } finally {
            inflater.end();
        }
    }

    private static int[] sorted(Set<Integer> keys) {
        int[] out = new int[keys.size()];
        int i = 0;
        for (Integer key : keys) out[i++] = key.intValue();
        java.util.Arrays.sort(out);
        return out;
    }

    private static long mix(long hash, int value) {
        for (int shift = 0; shift < 32; shift += 8) {
            hash ^= (value >>> shift) & 0xFF;
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    private static void writeShort(ByteArrayOutputStream out, int value) {
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private static int readShort(byte[] raw, int[] at) {
        return (readByte(raw, at) << 8) | readByte(raw, at);
    }

    private static int readByte(byte[] raw, int[] at) {
        if (at[0] >= raw.length) throw new IllegalArgumentException("table ends part way through");
        return raw[at[0]++] & 0xFF;
    }
}
