package com.trmtgtnh.api;

/**
 * Reads what TRMTR has published, never a half-built table. A question by key ({@code familyOf}, {@code chainLength})
 * may be asked on any thread. A question about a position ({@code familyAt}, {@code chainLengthAt}, {@code wearAt})
 * reads the world, so it is answered only on that world's own thread, the server's for a server's world and the
 * client's for a client's; anywhere else it gives its no-answer (null or 0) and TRMTR says so once in the log. It never
 * loads a chunk or waits for one: a position whose chunk is not loaded and whole gives its no-answer too (API.md,
 * "Chunk loading").
 */
@ImplementedByTrmtr
public interface Queries {

    /** Null when the block does not wear. Any thread. */
    String familyOf(String blockSelector);

    /**
     * On 1.7.10 a Block, which answers only where its every metadata value is in one family. Null when it does not
     * wear. Any thread.
     */
    String familyOf(Object blockState);

    /** The block there as it stands, with its metadata or state. Null when it does not wear. That world's thread. */
    String familyAt(Object level, int x, int y, int z);

    /** A family's own chain: how many steps its run takes from untouched to the deepest rut. Any thread. */
    int chainLength(String family);

    /** The chain of the block there, its own wearsThroughTo counted. 0 when it does not wear. That world's thread. */
    int chainLengthAt(Object level, int x, int y, int z);

    /** Null when the ground there is unworn. A client's world or a server's, on that world's thread. */
    Wear wearAt(Object level, int x, int y, int z);
}
