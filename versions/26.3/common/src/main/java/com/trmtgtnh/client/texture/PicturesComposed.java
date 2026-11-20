package com.trmtgtnh.client.texture;

/**
 * Whether this mod's wear pictures were composed for the block atlas, kept for the harness to judge (0.9.229, the
 * 1.7.10 tree's docs/goals/0.9.229-found.md, "The walk passed with no worn picture composed").
 *
 * <p>
 * The harness's walk read the wear records and photographed the ground, and under FastCraft on 1.7.10 it passed with
 * every stitch saying that the injection that builds every worn picture never ran: the records were all there, and no
 * ground showed any of them. A test that cannot fail on the fault it exists for is no test, so every edition tells this
 * class what each block-atlas stitch did with its pictures, and the harness asks it for a verdict before it opens a
 * world.
 *
 * <p>
 * The same file in every edition (CoreStaysPortableTest). Each edition's WearTextures calls {@link #stitchEnded} at the
 * end of a stitch that carried this mod's sprites, by its own route; each edition's harness calls {@link #verdict}
 * with whatever of its own settings or limits leaves the pictures legitimately unbuilt, or null.
 */
public final class PicturesComposed {

    /** What a stitch that composed nothing because it loaded nothing hands {@link #stitchEnded}. */
    public static final int SKIPPED = -1;

    /** The words every verdict begins with, after the harness's "Spike walk: ". */
    public static final String PASSED = "pictures - PASS";

    /** The words a verdict not judged begins with. */
    public static final String NOT_JUDGED = "pictures - not judged";

    /** The words a failed verdict begins with. */
    public static final String FAILED = "FAIL - pictures";

    /** Stitches that carried this mod's sprites and reached their end, skipped ones included. */
    private static int stitches;

    /** Whether the last stitch that was not skipped never reached the pass that composes the pictures. */
    private static boolean missed;

    /** The pictures the last stitch that was not skipped composed, or SKIPPED while none has been. */
    private static int last = SKIPPED;

    private PicturesComposed() {}

    /**
     * A block-atlas stitch that carried this mod's sprites has ended.
     *
     * @param reached  whether the pass that composes the pictures ran at all this stitch
     * @param composed the pictures it composed and the atlas took, or {@link #SKIPPED} for a stitch that loaded none
     *                 of this mod's sprites by design, which is Forge's skipped first stitch at start-up on 1.7.10 and
     *                 leaves the last stitch's verdict standing
     */
    public static synchronized void stitchEnded(boolean reached, int composed) {
        stitches++;
        if (!reached) {
            missed = true;
            last = SKIPPED;
        } else if (composed != SKIPPED) {
            missed = false;
            last = Math.max(0, composed);
        }
    }

    /** Whether any stitch that carried this mod's sprites has ended yet, so a verdict can be given. */
    public static synchronized boolean heard() {
        return stitches > 0;
    }

    /**
     * The harness's verdict on the pictures, the line after "Spike walk: ".
     *
     * @param notJudged why this edition's settings or limits leave the pictures legitimately unbuilt, or null
     */
    public static synchronized String verdict(String notJudged) {
        if (notJudged != null) {
            return NOT_JUDGED + " - "
                + notJudged
                + ", so the worn ground in the photographs is not this run's to judge";
        }
        if (stitches == 0) {
            return FAILED + " - no stitch of the block atlas carrying this mod's wear sprites reached its end, so no "
                + "worn picture was built and no ground shows any wear however far it is walked";
        }
        if (missed) {
            return FAILED + " - the injection that builds every worn picture never ran at the last stitch of the block "
                + "atlas, so no ground shows any wear however far it is walked";
        }
        if (last == SKIPPED) {
            return FAILED + " - none of the "
                + stitches
                + " stitch(es) of the block atlas loaded this mod's wear "
                + "sprites, so no worn picture was composed and no ground shows any wear";
        }
        if (last == 0) {
            return FAILED + " - the last stitch of the block atlas composed none of this mod's wear pictures, so no "
                + "ground shows any wear however far it is walked";
        }
        return PASSED + " - "
            + last
            + " wear pictures composed for the block atlas at the last of "
            + stitches
            + " stitch(es)";
    }

    /** Back to a game that has stitched nothing, for tests. */
    static synchronized void forget() {
        stitches = 0;
        missed = false;
        last = SKIPPED;
    }
}
