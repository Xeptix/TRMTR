package com.trmtgtnh.client;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.trmtgtnh.erosion.ErosionState;

/**
 * What the client believes is worn, and what was underneath before it painted over.
 *
 * <p>
 * Read from Celeritas' chunk-meshing worker threads — {@code getIcon} and
 * {@code colorMultiplier} are called there, once per face — and written from the client
 * tick. Rather than lock a hot path, each chunk's overlay is an immutable
 * {@link ChunkOverlay} of parallel arrays published into a concurrent map. Updates rebuild
 * the array for one chunk and swap it in; readers binary-search whatever snapshot they got
 * and never block.
 *
 * <p>
 * The origin block is stored per position because it is the only record of what the ghost
 * replaced. It is what the overlay is restored to when the player switches the mod off, and
 * what the wear texture is derived from so a Biomes O' Plenty grass wears into Biomes O'
 * Plenty earth rather than vanilla's.
 */
public final class ClientErosionCache {

    private static final ClientErosionCache INSTANCE = new ClientErosionCache();

    private final Map<Long, ChunkOverlay> chunks = new ConcurrentHashMap<Long, ChunkOverlay>();

    private ClientErosionCache() {}

    public static ClientErosionCache get() {
        return INSTANCE;
    }

    public static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    public ChunkOverlay overlay(int chunkX, int chunkZ) {
        return chunks.get(Long.valueOf(chunkKey(chunkX, chunkZ)));
    }

    public Collection<ChunkOverlay> overlays() {
        return chunks.values();
    }

    public int chunkCount() {
        return chunks.size();
    }

    public void put(int chunkX, int chunkZ, ChunkOverlay overlay) {
        Long key = Long.valueOf(chunkKey(chunkX, chunkZ));
        if (overlay == null || overlay.isEmpty()) {
            chunks.remove(key);
        } else {
            chunks.put(key, overlay);
        }
    }

    public ChunkOverlay remove(int chunkX, int chunkZ) {
        return chunks.remove(Long.valueOf(chunkKey(chunkX, chunkZ)));
    }

    public void clear() {
        chunks.clear();
    }

    /** Packed {@code blockId << 4 | meta} of the block that was painted over, or -1. */
    public int originAt(int x, int y, int z) {
        ChunkOverlay overlay = overlay(x >> 4, z >> 4);
        if (overlay == null) return -1;
        return overlay.originAt(com.trmtgtnh.erosion.ErosionKey.packWorld(x, y, z));
    }

    /** The packed wear state at a position, or {@link ErosionState#NONE} when nothing is known. */
    public short stateAt(int x, int y, int z) {
        ChunkOverlay overlay = overlay(x >> 4, z >> 4);
        if (overlay == null) return com.trmtgtnh.erosion.ErosionState.NONE;
        return overlay.stateAt(com.trmtgtnh.erosion.ErosionKey.packWorld(x, y, z));
    }

    /**
     * One chunk's overlay. Immutable: every field is final and the arrays are never handed
     * out, so a worker thread mid-mesh always sees a coherent snapshot even while the client
     * thread is building its replacement.
     */
    public static final class ChunkOverlay {

        public final int chunkX;
        public final int chunkZ;

        /** Chunk-local packed positions, sorted so lookups can binary-search. */
        private final int[] keys;

        /** Appearance byte per position, parallel to {@link #keys}. */
        private final short[] states;

        /** Packed {@code blockId << 4 | meta} that each ghost replaced, or -1 if not painted. */
        private final int[] origin;

        public ChunkOverlay(int chunkX, int chunkZ, int[] keys, short[] states, int[] origin) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.keys = keys;
            this.states = states;
            this.origin = origin;
        }

        public boolean isEmpty() {
            return keys.length == 0;
        }

        public int size() {
            return keys.length;
        }

        public int keyAt(int index) {
            return keys[index];
        }

        public short stateAtIndex(int index) {
            return states[index];
        }

        public int originAtIndex(int index) {
            return origin[index];
        }

        public short stateAt(int key) {
            int index = Arrays.binarySearch(keys, key);
            return index < 0 ? ErosionState.NONE : states[index];
        }

        public int originAt(int key) {
            int index = Arrays.binarySearch(keys, key);
            return index < 0 ? -1 : origin[index];
        }

        public int indexOf(int key) {
            return Arrays.binarySearch(keys, key);
        }

        /**
         * Copies this overlay carrying a freshly built origin array.
         *
         * <p>
         * Painting a chunk records what it covered at every position, so the origins are
         * gathered into one array and swapped in once rather than copied per position, which
         * would make painting a busy chunk quadratic.
         */
        public ChunkOverlay copyWithOrigins(int[] newOrigins) {
            return new ChunkOverlay(chunkX, chunkZ, keys, states, newOrigins);
        }

        /** A mutable copy of the recorded origins, for the painter to fill in. */
        public int[] originsCopy() {
            return origin.clone();
        }

        /** Copies this overlay with every recorded origin cleared. */
        public ChunkOverlay withoutOrigins() {
            int[] cleared = new int[origin.length];
            Arrays.fill(cleared, -1);
            return new ChunkOverlay(chunkX, chunkZ, keys, states, cleared);
        }
    }

    /**
     * Builds a sorted overlay from an unsorted packet payload, carrying forward whatever
     * origins a previous overlay for the same chunk had already recorded.
     */
    public static ChunkOverlay build(int chunkX, int chunkZ, int[] rawKeys, short[] rawStates,
        ChunkOverlay previous) {
        int count = Math.min(rawKeys.length, rawStates.length);
        Integer[] order = new Integer[count];
        for (int i = 0; i < count; i++) {
            order[i] = Integer.valueOf(i);
        }
        final int[] sortKeys = rawKeys;
        Arrays.sort(order, new java.util.Comparator<Integer>() {

            @Override
            public int compare(Integer a, Integer b) {
                return Integer.compare(sortKeys[a.intValue()], sortKeys[b.intValue()]);
            }
        });

        int[] keys = new int[count];
        short[] states = new short[count];
        int[] origin = new int[count];
        for (int i = 0; i < count; i++) {
            int source = order[i].intValue();
            keys[i] = rawKeys[source];
            states[i] = rawStates[source];
            origin[i] = previous == null ? -1 : previous.originAt(keys[i]);
        }
        return new ChunkOverlay(chunkX, chunkZ, keys, states, origin);
    }

    /** Builds an overlay with one position added, changed or removed. */
    public static ChunkOverlay withSingle(ChunkOverlay previous, int chunkX, int chunkZ, int key, short state) {
        if (previous == null) {
            if (state == ErosionState.NONE) return null;
            return new ChunkOverlay(chunkX, chunkZ, new int[] { key }, new short[] { state }, new int[] { -1 });
        }

        int index = previous.indexOf(key);
        if (index >= 0) {
            if (state == ErosionState.NONE) {
                int size = previous.size() - 1;
                int[] keys = new int[size];
                short[] newFlags = new short[size];
                int[] origin = new int[size];
                for (int i = 0, j = 0; i < previous.size(); i++) {
                    if (i == index) continue;
                    keys[j] = previous.keyAt(i);
                    newFlags[j] = previous.stateAtIndex(i);
                    origin[j] = previous.originAtIndex(i);
                    j++;
                }
                return new ChunkOverlay(chunkX, chunkZ, keys, newFlags, origin);
            }
            int[] keys = new int[previous.size()];
            short[] newFlags = new short[previous.size()];
            int[] origin = new int[previous.size()];
            for (int i = 0; i < previous.size(); i++) {
                keys[i] = previous.keyAt(i);
                newFlags[i] = i == index ? state : previous.stateAtIndex(i);
                origin[i] = previous.originAtIndex(i);
            }
            return new ChunkOverlay(chunkX, chunkZ, keys, newFlags, origin);
        }

        if (state == ErosionState.NONE) return previous;

        int insert = -(index + 1);
        int size = previous.size() + 1;
        int[] keys = new int[size];
        short[] newFlags = new short[size];
        int[] origin = new int[size];
        for (int i = 0; i < insert; i++) {
            keys[i] = previous.keyAt(i);
            newFlags[i] = previous.stateAtIndex(i);
            origin[i] = previous.originAtIndex(i);
        }
        keys[insert] = key;
        newFlags[insert] = state;
        origin[insert] = -1;
        for (int i = insert; i < previous.size(); i++) {
            keys[i + 1] = previous.keyAt(i);
            newFlags[i + 1] = previous.stateAtIndex(i);
            origin[i + 1] = previous.originAtIndex(i);
        }
        return new ChunkOverlay(chunkX, chunkZ, keys, newFlags, origin);
    }
}
