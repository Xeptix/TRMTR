package com.trmtgtnh.api;

/**
 * Server thread, and never inside a chunk load, so a listener may read the world as any server code may (API.md, "Chunk
 * loading").
 */
public interface WearListener {

    /**
     * Asked before every bit of wear, with what caused it. Return 0 to cancel, or a changed amount.
     */
    default float beforeWear(WearContext context, float amount) {
        return amount;
    }

    /**
     * Told after a record changes for any reason, with the record before and after; either is null where there was
     * none. A change made while a chunk loaded, the heal that catches it up, is told at the end of that server tick,
     * in order, once the chunk is whole, so the world may have moved on from {@code after} by then.
     */
    default void afterChange(Object level, int x, int y, int z, Wear before, Wear after) {}
}
