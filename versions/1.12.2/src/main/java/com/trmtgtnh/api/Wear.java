package com.trmtgtnh.api;

/**
 * One record of worn ground.
 */
@ImplementedByTrmtr
public interface Wear {

    /** What the ground is. */
    String family();

    /** The family it is drawn as by now, which differs once it has worn through. */
    String appearance();

    /** The drawn stage, counted from 0 to one less than {@link #stages()}. */
    int stage();

    /** How many stages this stretch of its run has: the face's while it stands flat, one pixel's once sunk. */
    int stages();

    /**
     * How far it is drawn sunk, in pixels. {@code wearAt} and {@code WearDraw.wear()} give the depth drawn on that
     * block, its shape and the world's depth switch counted. {@code WearListener.afterChange} is told what it can
     * without the block: where a record changed by its chain alone, a shape's own hold is not counted (API.md).
     */
    int sinkPixels();

    /** Its place on its run. */
    int step();

    /** The run's length. */
    int steps();

    /** Its step as a share of its run. */
    float fraction();

    /** Whether it is held as it is. */
    boolean frozen();

    /** Its reinforcement level, 0 to 3. */
    int reinforcement();

    /** The server's count toward the next step. -1 on a client, which is not told it. */
    float progress();
}
