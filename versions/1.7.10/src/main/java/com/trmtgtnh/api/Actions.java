package com.trmtgtnh.api;

/**
 * Server thread only. Elsewhere each returns false, and says so once. Each returns whether it did anything, and each
 * passes through the listeners and rules with the cause {@link WearContext.Cause#API}. None loads a chunk or waits for
 * one: on a position whose chunk is not loaded and whole each returns false (API.md, "Chunk loading").
 */
@ImplementedByTrmtr
public interface Actions {

    /** Adds wear as if that much traffic had passed. */
    boolean wear(Object level, int x, int y, int z, float amount);

    /** Mends steps. */
    boolean restore(Object level, int x, int y, int z, int steps);

    /** Holds or releases a record. */
    boolean freeze(Object level, int x, int y, int z, boolean frozen);

    /** Drops a record, so the ground is untouched. */
    boolean forget(Object level, int x, int y, int z);
}
