package com.trmtgtnh.client.settings;

import java.util.function.LongSupplier;

/**
 * A slice of each frame for slow work done a piece at a time (spec Part 6, M4.5): the block picker's gathering, which
 * in GT New Horizons is tens of thousands of blocks and their subtypes, each asked of a mod that may be slow to
 * answer.
 *
 * <pre>
 * slicer.frame();
 * while (slicer.more() &amp;&amp; gathering.hasNext()) gathering.next();
 * </pre>
 *
 * The clock is handed in so a test can stand in for it; the edition passes {@code System::nanoTime}.
 */
public final class Slicer {

    private final long budgetNanos;

    private final LongSupplier clock;

    private long start;

    /**
     * @param budgetNanos how long a frame's slice is - 4 ms, {@code 4000000L}, in the screens
     * @param clock       the time in nanoseconds
     */
    public Slicer(long budgetNanos, LongSupplier clock) {
        if (clock == null) throw new IllegalArgumentException("a slicer is timed by some clock");
        this.budgetNanos = budgetNanos;
        this.clock = clock;
        this.start = clock.getAsLong();
    }

    /** Call at the start of every frame's slice: the slice starts now. */
    public void frame() {
        start = clock.getAsLong();
    }

    /** Whether this frame's slice has time left for another piece of work. */
    public boolean more() {
        return clock.getAsLong() - start < budgetNanos;
    }
}
