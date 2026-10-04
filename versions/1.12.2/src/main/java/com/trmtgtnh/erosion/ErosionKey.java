package com.trmtgtnh.erosion;

/**
 * Packs a chunk-local block position into a single key.
 *
 * <p>
 * Storing three ints per eroded block would be wasteful given how many entries a well-travelled
 * world accumulates, and 1.7.10 has no BlockPos to store anyway. Chunk-local coordinates fit in a
 * fraction of an int:
 *
 * <pre>
 *   x : 0-15               (4 bits, 20-23)
 *   z : 0-15               (4 bits, 16-19)
 *   y : -32768 to 32767   (16 bits, 0-15, signed)
 * </pre>
 *
 * Twenty-four bits used of thirty-two, and the chunk a key belongs to is implied by which
 * {@link ChunkErosionData} holds it.
 *
 * <h2>Why an int, when a short did</h2>
 *
 * <p>
 * It did until 1.18, which lowered the world floor to -64 and raised the ceiling to 319. The old key
 * gave y eight unsigned bits - 0 to 255 - in a sixteen-bit word that was completely full, so there
 * was nowhere to put either the extra range or the sign. Widening it is the one change in this class
 * that no amount of care elsewhere could have avoided.
 *
 * <p>
 * <strong>Done in every edition at once, including the ones that do not need it.</strong> This class
 * is one of the twenty-six copied byte for byte into each edition and checked by a test that fails
 * either build on any difference, and that property is worth more than the eight bits per record the
 * older editions now spend for nothing. They simply never pass a y outside 0-255.
 *
 * <p>
 * The y field is read back with a plain cast to {@code short}, which sign-extends for free, so
 * nothing here has to do its own two's-complement arithmetic.
 */
public final class ErosionKey {

    private ErosionKey() {}

    /** Packs chunk-local x/z (0-15) and world y into a key. */
    public static int pack(int localX, int y, int localZ) {
        return ((localX & 0xF) << 20) | ((localZ & 0xF) << 16) | (y & 0xFFFF);
    }

    /** Packs from world coordinates, deriving the chunk-local component. */
    public static int packWorld(int worldX, int y, int worldZ) {
        return pack(worldX & 0xF, y, worldZ & 0xF);
    }

    public static int localX(int key) {
        return (key >> 20) & 0xF;
    }

    public static int localZ(int key) {
        return (key >> 16) & 0xF;
    }

    /** The y this key was packed with, sign included. */
    public static int y(int key) {
        return (short) key;
    }

    /** Reconstructs the world X coordinate given the owning chunk's X. */
    public static int worldX(int key, int chunkX) {
        return (chunkX << 4) + localX(key);
    }

    /** Reconstructs the world Z coordinate given the owning chunk's Z. */
    public static int worldZ(int key, int chunkZ) {
        return (chunkZ << 4) + localZ(key);
    }

    /**
     * A key written by a build before the widening, read into the current layout.
     *
     * <p>
     * The old key was {@code x<<12 | z<<8 | y}, with y unsigned in the low byte, so every field has
     * moved and y has gained a sign it never used. Called only by the readers for formats 2 through
     * 5 in {@link ChunkErosionData}, once per entry, the first time a chunk is loaded after the
     * update - and the chunk is written back in the current format, so no key is ever upgraded twice.
     */
    public static int upgradeFromShort(short old) {
        return pack((old >> 12) & 0xF, old & 0xFF, (old >> 8) & 0xF);
    }
}
