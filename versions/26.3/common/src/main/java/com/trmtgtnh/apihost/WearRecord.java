package com.trmtgtnh.apihost;

import com.trmtgtnh.api.Wear;

/**
 * One record as trmtr-api hands it to a mod: a snapshot, never the live record, so a plugin holding one cannot see it
 * change under it or change it.
 *
 * <p>
 * Each edition's adapter reads the figures from its own record (the server's store or the client's cache) and makes
 * one of these; the arithmetic that is the same everywhere - the share of the run, and whether two records look
 * different - is here. Portable: the same file in all eight trees.
 */
public final class WearRecord implements Wear {

    /** What {@link #progress()} is on a client, which is not told it ({@code RecordReadout.WITHHELD}). */
    public static final float WITHHELD = -1f;

    private final String family;
    private final String appearance;
    private final int stage;
    private final int stages;
    private final int sinkPixels;
    private final int step;
    private final int steps;
    private final boolean frozen;
    private final int reinforcement;
    private final float progress;

    /** A record whose count of stages is not known: its stage is taken to be the last. */
    public WearRecord(String family, String appearance, int stage, int sinkPixels, int step, int steps, boolean frozen,
        int reinforcement, float progress) {
        this(family, appearance, stage, stage + 1, sinkPixels, step, steps, frozen, reinforcement, progress);
    }

    /**
     * @param family        the family the ground is
     * @param appearance    the family it is drawn as by now
     * @param stage         the drawn stage
     * @param stages        how many stages this stretch of the run has
     * @param sinkPixels    how far it is drawn sunk
     * @param step          its place on its run, from 0; -1 where the run has no place for it
     * @param steps         the run's length
     * @param frozen        whether it is held as it is
     * @param reinforcement its reinforcement level, 0 to 3
     * @param progress      the share of the way to the next step, 0 to 1, or {@link #WITHHELD}
     */
    public WearRecord(String family, String appearance, int stage, int stages, int sinkPixels, int step, int steps,
        boolean frozen, int reinforcement, float progress) {
        this.family = family;
        this.appearance = appearance;
        this.stage = stage;
        this.stages = stages;
        this.sinkPixels = sinkPixels;
        this.step = step;
        this.steps = steps < 0 ? 0 : steps;
        this.frozen = frozen;
        this.reinforcement = reinforcement;
        this.progress = progress;
    }

    @Override
    public String family() {
        return family;
    }

    @Override
    public String appearance() {
        return appearance;
    }

    @Override
    public int stage() {
        return stage;
    }

    @Override
    public int stages() {
        return stages;
    }

    @Override
    public int sinkPixels() {
        return sinkPixels;
    }

    @Override
    public int step() {
        return step;
    }

    @Override
    public int steps() {
        return steps;
    }

    /**
     * The place on the run as a share of it: the first worn step a little above 0, the deepest 1. Counted from the
     * step's own number plus one, because step 0 is already worn ground and untouched ground has no record at all.
     */
    @Override
    public float fraction() {
        if (steps <= 0 || step < 0) return 0f;
        float share = (step + 1) / (float) steps;
        return share > 1f ? 1f : share;
    }

    @Override
    public boolean frozen() {
        return frozen;
    }

    @Override
    public int reinforcement() {
        return reinforcement;
    }

    @Override
    public float progress() {
        return progress;
    }

    /**
     * Whether two records look the same: the same ground, drawn the same, sunk the same, held the same. What
     * {@code WearListener.afterChange} is told about is a change of these, the change a client is sent; wear banked
     * toward the next step, which changes on every footstep and shows nothing, is not one. Null is no record.
     */
    public static boolean sameLook(Wear a, Wear b) {
        if (a == null || b == null) return a == b;
        return same(a.family(), b.family()) && same(a.appearance(), b.appearance())
            && a.stage() == b.stage()
            && a.sinkPixels() == b.sinkPixels()
            && a.frozen() == b.frozen()
            && a.reinforcement() == b.reinforcement();
    }

    private static boolean same(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    @Override
    public String toString() {
        return appearance + " stage " + stage + " sunk " + sinkPixels + " step " + step + "/" + steps;
    }
}
