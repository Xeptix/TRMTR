package com.trmtgtnh.surface;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The chains data files give single blocks (trmtr-api, a families entry's {@code wearsThroughTo}; W3 T9), on the wire
 * beside the surface table and folded into its fingerprint. A block's own chain decides which of its records the
 * server keeps and what its ruts show, so it is the server's, as the families and shapes are: a client that has other
 * data, or none, draws each such block on the server's chain for the visit.
 *
 * <p>
 * Carried beside {@link SurfaceTableCodec}'s bytes rather than inside them, so the codec every edition shares byte for
 * byte stays as it is until each edition carries this: the bytes sent are the codec's table, length first, then the
 * chains section. The section is the distinct lists once each, as family ordinals in order (none at all for a block
 * that sinks as itself), then each such block's id and sixteen bytes, an index into those lists for each metadata
 * value or 0xFF for none. Keys are the registry's packing, as the codec's are.
 *
 * <p>
 * Game-free, so the round trip and the fingerprint are tried without a game.
 */
public final class ChainTable {

    /** The most distinct lists a table may carry, each named by a byte; the byte's last value means "none". */
    public static final int MOST_CHAINS = 255;

    /** The longest list a block's own chain may name: every other family. */
    public static final int LONGEST_CHAIN = 15;

    /** A metadata value with no list of its own, in its block's sixteen bytes. */
    private static final int NO_CHAIN = 0xFF;

    private ChainTable() {}

    /** The codec's table and the chains, as the wire carries them. */
    public static final class Unwrapped {

        /** The codec's table, for {@link SurfaceTableCodec#decode}. */
        public final byte[] table;

        /** The blocks' own lists, family ordinals in order, by packed key. Not to be written to. */
        public final Map<Integer, int[]> chains;

        Unwrapped(byte[] table, Map<Integer, int[]> chains) {
            this.table = table;
            this.chains = Collections.unmodifiableMap(chains);
        }
    }

    /**
     * A fingerprint of a table with the chains folded in. None leaves the table's own exactly as it was, so a side with
     * no lists agrees with one from before them; any moves it, by state and by every family of the list in order.
     */
    public static long fingerprint(long table, Map<Integer, int[]> chains) {
        if (chains == null || chains.isEmpty()) return table;
        long hash = mix(table, -4);
        int[] keys = sorted(chains);
        hash = mix(hash, keys.length);
        for (int key : keys) {
            int[] list = chains.get(Integer.valueOf(key));
            hash = mix(hash, key);
            hash = mix(hash, list.length);
            for (int ordinal : list) hash = mix(hash, ordinal);
        }
        return hash == 0L ? 1L : hash;
    }

    /**
     * The codec's table with the chains after it.
     *
     * @throws IllegalArgumentException for a key or a list that cannot be packed, or more distinct lists than
     *                                  {@link #MOST_CHAINS}
     */
    public static byte[] wrap(byte[] table, Map<Integer, int[]> chains) {
        LinkedHashMap<String, Integer> distinct = new LinkedHashMap<String, Integer>();
        List<int[]> lists = new ArrayList<int[]>();
        TreeMap<Integer, int[]> byBlock = new TreeMap<Integer, int[]>();
        Map<Integer, int[]> given = chains == null ? Collections.<Integer, int[]>emptyMap() : chains;
        for (int key : sorted(given)) {
            int[] list = given.get(Integer.valueOf(key));
            if (list == null) throw new IllegalArgumentException("no chain at key " + key);
            if (key < 0 || (key >>> 4) > 0xFFFF) throw new IllegalArgumentException("key out of range: " + key);
            check(list);
            String named = Arrays.toString(list);
            Integer index = distinct.get(named);
            if (index == null) {
                if (distinct.size() >= MOST_CHAINS) {
                    throw new IllegalArgumentException("more than " + MOST_CHAINS + " distinct chains");
                }
                index = Integer.valueOf(distinct.size());
                distinct.put(named, index);
                lists.add(list);
            }
            int[] indexes = byBlock.get(Integer.valueOf(key >>> 4));
            if (indexes == null) {
                indexes = new int[16];
                Arrays.fill(indexes, NO_CHAIN);
                byBlock.put(Integer.valueOf(key >>> 4), indexes);
            }
            indexes[key & 0xF] = index.intValue();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int length = table.length;
        out.write((length >>> 24) & 0xFF);
        out.write((length >>> 16) & 0xFF);
        out.write((length >>> 8) & 0xFF);
        out.write(length & 0xFF);
        out.write(table, 0, table.length);
        writeShort(out, lists.size());
        for (int[] list : lists) {
            out.write(list.length);
            for (int ordinal : list) out.write(ordinal);
        }
        writeShort(out, byBlock.size());
        for (Map.Entry<Integer, int[]> entry : byBlock.entrySet()) {
            writeShort(
                out,
                entry.getKey()
                    .intValue());
            for (int index : entry.getValue()) out.write(index);
        }
        return out.toByteArray();
    }

    /**
     * The codec's table and the chains back out of the wire's bytes. Every list is held to what a file could give.
     *
     * @throws IllegalArgumentException for anything that is not a table with its chains
     */
    public static Unwrapped unwrap(byte[] bytes) {
        if (bytes == null || bytes.length < 4) throw new IllegalArgumentException("no table before the chains");
        int[] at = { 0 };
        int length = (readByte(bytes, at) << 24) | (readByte(bytes, at) << 16)
            | (readByte(bytes, at) << 8)
            | readByte(bytes, at);
        if (length < 0 || length > bytes.length - 4) throw new IllegalArgumentException("a table longer than sent");
        byte[] table = Arrays.copyOfRange(bytes, 4, 4 + length);
        at[0] = 4 + length;
        int count = readShort(bytes, at);
        if (count > MOST_CHAINS) throw new IllegalArgumentException(count + " distinct chains");
        int[][] distinct = new int[count][];
        for (int c = 0; c < count; c++) {
            int size = readByte(bytes, at);
            if (size > LONGEST_CHAIN) throw new IllegalArgumentException("a chain of " + size + " families");
            distinct[c] = new int[size];
            for (int i = 0; i < size; i++) distinct[c][i] = readByte(bytes, at);
            check(distinct[c]);
        }
        Map<Integer, int[]> chains = new HashMap<Integer, int[]>();
        int blocks = readShort(bytes, at);
        for (int b = 0; b < blocks; b++) {
            int id = readShort(bytes, at);
            for (int meta = 0; meta < 16; meta++) {
                int index = readByte(bytes, at);
                if (index == NO_CHAIN) continue;
                if (index >= count) throw new IllegalArgumentException("a chain index past the chains sent");
                chains.put(Integer.valueOf((id << 4) | meta), distinct[index].clone());
            }
        }
        if (at[0] != bytes.length) throw new IllegalArgumentException("trailing bytes after the chains");
        return new Unwrapped(table, chains);
    }

    /** A list a file could give: at most {@link #LONGEST_CHAIN} families, each one this build has with stages. */
    private static void check(int[] list) {
        if (list.length > LONGEST_CHAIN) throw new IllegalArgumentException("a chain of " + list.length + " families");
        SurfaceFamily[] all = SurfaceFamily.values();
        for (int ordinal : list) {
            if (ordinal < 0 || ordinal >= all.length || !all[ordinal].staged) {
                throw new IllegalArgumentException("a chain names family " + ordinal);
            }
        }
    }

    private static int[] sorted(Map<Integer, int[]> chains) {
        int[] out = new int[chains.size()];
        int i = 0;
        for (Integer key : chains.keySet()) out[i++] = key.intValue();
        Arrays.sort(out);
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

    private static void writeShort(ByteArrayOutputStream out, int value) {
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private static int readShort(byte[] raw, int[] at) {
        return (readByte(raw, at) << 8) | readByte(raw, at);
    }

    private static int readByte(byte[] raw, int[] at) {
        if (at[0] >= raw.length) throw new IllegalArgumentException("chains end part way through");
        return raw[at[0]++] & 0xFF;
    }
}
