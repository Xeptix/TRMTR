package com.trmtgtnh.client;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.trmtgtnh.erosion.ErosionKey;

/**
 * Which positions the client believes are glowing, and in what colour.
 *
 * <p>
 * Kept apart from {@link ClientErosionCache} rather than folded into its overlay, because the two
 * have opposite shapes. Wear is dense - a used path is hundreds of adjacent positions - while light
 * is a handful of blocks somebody deliberately lit. Widening the wear record by a byte would spend
 * that byte on every worn position in the world to carry information almost none of them have, and
 * would change a wire format that is working. A sparse map costs nothing where nothing is lit.
 *
 * <p>
 * Same immutability contract as the wear overlay, and for the same reason: block lighting and the
 * tint are read on chunk-meshing worker threads while the client thread is replacing them. Each
 * chunk's record is a final pair of sorted arrays swapped in whole, so a reader mid-mesh always
 * sees one coherent version rather than a half-updated one.
 */
public final class ClientLightCache {

    private static final ClientLightCache INSTANCE = new ClientLightCache();

    private final Map<Long, LitChunk> chunks = new ConcurrentHashMap<Long, LitChunk>();

    private ClientLightCache() {}

    public static ClientLightCache get() {
        return INSTANCE;
    }

    /** The packed light byte at a position: level in the low nibble, colour in the high one. */
    public int at(int x, int y, int z) {
        LitChunk chunk = chunks.get(Long.valueOf(ClientErosionCache.chunkKey(x >> 4, z >> 4)));
        if (chunk == null) return 0;
        return chunk.at(ErosionKey.packWorld(x, y, z));
    }

    public int levelAt(int x, int y, int z) {
        return at(x, y, z) & 0xF;
    }

    /** Replaces one chunk's lit positions wholesale. */
    public void put(int chunkX, int chunkZ, int[] keys, byte[] values) {
        Long key = Long.valueOf(ClientErosionCache.chunkKey(chunkX, chunkZ));
        if (keys == null || keys.length == 0) {
            chunks.remove(key);
        } else {
            com.trmtgtnh.block.GhostLight.noteLit();
            chunks.put(key, new LitChunk(keys, values));
        }
    }

    /**
     * Sets or clears one position, rebuilding that chunk's record around it.
     *
     * <p>
     * Rebuilding a whole chunk for one block sounds wasteful and is not: the arrays hold only lit
     * positions, so in practice this copies a handful of entries. Doing it this way keeps the
     * immutability contract that lets worker threads read without locking.
     */
    public void set(int x, int y, int z, int packed) {
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        Long key = Long.valueOf(ClientErosionCache.chunkKey(chunkX, chunkZ));
        LitChunk existing = chunks.get(key);
        if ((packed & 0xF) != 0) com.trmtgtnh.block.GhostLight.noteLit();
        LitChunk updated = LitChunk.with(existing, ErosionKey.packWorld(x, y, z), (byte) packed);
        if (updated == null) {
            chunks.remove(key);
        } else {
            chunks.put(key, updated);
        }
    }

    public void remove(int chunkX, int chunkZ) {
        chunks.remove(Long.valueOf(ClientErosionCache.chunkKey(chunkX, chunkZ)));
    }

    public void clear() {
        chunks.clear();
    }

    public int chunkCount() {
        return chunks.size();
    }

    /** One chunk's lit positions: sorted keys and their packed light bytes. */
    private static final class LitChunk {

        private final int[] keys;

        private final byte[] values;

        LitChunk(int[] keys, byte[] values) {
            this.keys = keys;
            this.values = values;
        }

        int at(int key) {
            int index = Arrays.binarySearch(keys, key);
            return index < 0 ? 0 : values[index] & 0xFF;
        }

        /** This chunk with one position set, or null once nothing in it is lit. */
        static LitChunk with(LitChunk existing, int key, byte packed) {
            int[] oldKeys = existing == null ? new int[0] : existing.keys;
            byte[] oldValues = existing == null ? new byte[0] : existing.values;
            int found = Arrays.binarySearch(oldKeys, key);

            if ((packed & 0xF) == 0) {
                if (found < 0) return existing; // already unlit
                if (oldKeys.length == 1) return null;
                int[] keys = new int[oldKeys.length - 1];
                byte[] values = new byte[keys.length];
                System.arraycopy(oldKeys, 0, keys, 0, found);
                System.arraycopy(oldValues, 0, values, 0, found);
                System.arraycopy(oldKeys, found + 1, keys, found, keys.length - found);
                System.arraycopy(oldValues, found + 1, values, found, values.length - found);
                return new LitChunk(keys, values);
            }

            if (found >= 0) {
                byte[] values = oldValues.clone();
                values[found] = packed;
                return new LitChunk(oldKeys, values);
            }

            int insert = -found - 1;
            int[] keys = new int[oldKeys.length + 1];
            byte[] values = new byte[keys.length];
            System.arraycopy(oldKeys, 0, keys, 0, insert);
            System.arraycopy(oldValues, 0, values, 0, insert);
            keys[insert] = key;
            values[insert] = packed;
            System.arraycopy(oldKeys, insert, keys, insert + 1, oldKeys.length - insert);
            System.arraycopy(oldValues, insert, values, insert + 1, oldValues.length - insert);
            return new LitChunk(keys, values);
        }
    }
}
