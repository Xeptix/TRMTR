package com.trmtgtnh.erosion;

/**
 * What one record tells a player looking at it, and what it keeps back.
 *
 * <p>
 * The server answers a client's inspection with the figures a tooltip is drawn from. For worn ground
 * each means what the tooltip says: wear toward the next gradation, how long the ground has been left
 * alone, where it sits on the run and when it recovers. A record with nothing drawn is another matter,
 * and it is one of three things. A leaf's or a plant's trample tally, whose wear is the crossings that
 * will break the block rather than progress toward a gradation it does not have. Ground walked on short
 * of its first gradation, whose wear is real but belongs to no line the tooltip draws. Or a record kept
 * only for a reinforcement or a ward, on ground nobody has walked or on a block that never wears. A
 * reinforced or warded tally is the first carrying the third's protections, and it is never trampled
 * again, since a protection refuses trampling outright.
 *
 * <p>
 * So a record with nothing drawn reports its protections and the length of the run its block belongs
 * to, which is a fact about the block rather than about any wear, and withholds everything else. The
 * tooltip draws wear only on a ghost anyway; withholding the figures here as well means no reader of
 * the reply, of this version or a later one, can draw a line from them by mistake.
 *
 * <p>
 * Nothing from the game is in it, so the rule is pinned by a test rather than by a comment.
 */
public final class RecordReadout {

    /**
     * What a withheld count or time is sent as. Minus one rather than nought, because nought is a real
     * answer for all three: no time since the last step, recovering now, the first step of the run.
     */
    public static final int WITHHELD = -1;

    public final float wear;
    public final float threshold;
    public final int untouchedSeconds;
    public final int recoverySeconds;
    public final int chainIndex;
    /** How many steps the run of the block standing there has, nought when it has none. Never withheld. */
    public final int chainLength;
    public final int reinforce;
    public final int ward;

    private RecordReadout(float wear, float threshold, int untouchedSeconds, int recoverySeconds, int chainIndex,
        int chainLength, int reinforce, int ward) {
        this.wear = wear;
        this.threshold = threshold;
        this.untouchedSeconds = untouchedSeconds;
        this.recoverySeconds = recoverySeconds;
        this.chainIndex = chainIndex;
        this.chainLength = chainLength;
        this.reinforce = reinforce;
        this.ward = ward;
    }

    /**
     * The readout for a record, from the figures the server worked out for it against the world.
     *
     * <p>
     * For a record with nothing drawn the caller's figures are replaced whatever they say, so a caller
     * that works out a place on the run or a recovery for one anyway cannot leak it.
     *
     * @param entry never null; a position with no record gets no reply
     */
    public static RecordReadout of(ErosionEntry entry, int untouchedSeconds, int recoverySeconds, int chainIndex,
        int chainLength) {
        if (!entry.isVisible()) {
            return new RecordReadout(
                0f,
                0f,
                WITHHELD,
                WITHHELD,
                WITHHELD,
                chainLength,
                entry.getReinforce(),
                entry.getWard());
        }
        return new RecordReadout(
            entry.getWear(),
            entry.effectiveThreshold(),
            untouchedSeconds,
            recoverySeconds,
            chainIndex,
            chainLength,
            entry.getReinforce(),
            entry.getWard());
    }

    /**
     * Whether a threshold from a reply tells any progress at all. False for a withheld one, and for a
     * drawn record whose threshold is nought, should one ever exist; nothing is known to make one, and it
     * would show no progress line where it used to show nought per cent.
     */
    public static boolean tellsProgress(float threshold) {
        return threshold > 0f;
    }

    /** How far wear has come toward a threshold, from nought to one; nought when the threshold tells none. */
    public static float progress(float wear, float threshold) {
        if (!tellsProgress(threshold) || !(wear > 0f)) return 0f;
        float share = wear / threshold;
        return share >= 1f ? 1f : share;
    }

    /**
     * A place on the run as the reply carries it, turned back into one.
     *
     * <p>
     * The place travels as one byte and is read back signed, so that minus one survives the trip. Read
     * back signed and nothing more, every place from 128 on arrived negative, and the Overall line, which
     * asks for nought or more, vanished on any run longer than 128 steps - which a family's maxSinkPixels
     * raised to fifteen at eight layers a pixel already reaches. The server sends no negative but minus
     * one, so any other negative byte is a place past 127. Place 255 is the one that cannot be told from
     * withheld, and it exists only as the last step of a run of exactly 256, whose length arrives as
     * nought anyway.
     */
    public static int indexFromWire(int signedByte) {
        return signedByte == WITHHELD ? WITHHELD : signedByte & 0xFF;
    }
}
