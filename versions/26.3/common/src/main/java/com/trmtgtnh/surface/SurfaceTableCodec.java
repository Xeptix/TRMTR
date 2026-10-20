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
 */
public final class SurfaceTableCodec {

    /** The layout below. A decoder that meets a different one refuses rather than misreads. */
    public static final int FORMAT = 1;

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

    /** The most raw bytes a well-formed table can inflate to: every id with every value, twice over. */
    private static final int MAX_RAW = 1 + 2 + 4096 * 10 + 2 + 4096 * 6;

    /** A metadata value that belongs to no family, in the four bits a family ordinal is given. */
    private static final int NONE = 0xF;

    private SurfaceTableCodec() {}

    /** A decoded table: family ordinals by packed key, and the two ground-cover sets. */
    public static final class Table {

        public final Map<Integer, Integer> families;

        public final Set<Integer> cover;

        public final Set<Integer> holds;

        public Table(Map<Integer, Integer> families, Set<Integer> cover, Set<Integer> holds) {
            this.families = Collections.unmodifiableMap(new HashMap<Integer, Integer>(families));
            this.cover = Collections.unmodifiableSet(new HashSet<Integer>(cover));
            this.holds = Collections.unmodifiableSet(new HashSet<Integer>(holds));
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
        if (at[0] != raw.length) throw new IllegalArgumentException("trailing bytes after the table");
        return new Table(families, cover, holds);
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
