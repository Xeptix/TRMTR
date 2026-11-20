package com.trmtgtnh.erosion;

/**
 * Noticing that a chunk has lost its wear (0.9.229; docs/goals/0.9.229-wear-backup-spec.md, "Noticing a problem"), on
 * chunk load: a chunk that loads without wear the mirror holds for that same save, or with damaged records, and what
 * is to be done about it. Portable, the same file in every edition.
 *
 * <p>
 * Three answers, decided by the chunk's own save stamp against the mirror's:
 * <ul>
 * <li>{@link Kind#FINE}: the chunk's wear is whole, or the mirror says the chunk saved with none (healed or cleared),
 * or nothing anywhere holds wear for it.</li>
 * <li>{@link Kind#QUIET}: the wear is missing or damaged, and the mirror holds it from this same save - the stamps
 * agree - so putting it back loses nothing and changes nothing anybody did. Done without asking; one log line a
 * session says how many ({@link #quietLine}).</li>
 * <li>{@link Kind#LOST}: missing or damaged, and the only copy is older than the chunk's last save, which this mod
 * did not make (the world was opened without it), or the mirror itself is gone and a backup holds it. That is a real
 * loss: single player asks, a dedicated server puts the missing wear back and tells its operators.</li>
 * </ul>
 */
public final class WearLoss {

    public enum Kind {
        FINE,
        QUIET,
        LOST
    }

    /**
     * The bytes of one record in the format {@link ChunkErosionData} writes, so a blob says how many it holds. A test
     * holds this to what the class writes, so a change of format there cannot leave it counting the old size.
     */
    static final int RECORD_BYTES = 20;

    private WearLoss() {}

    /**
     * How many of a blob's records did not survive the reading: the records it holds less those read, for the format
     * this build writes; every record for a blob nothing could be read from (a format newer than this build, or
     * nothing but rejects). Nought for an older format, which is upgraded rather than checked, and for no blob.
     */
    public static int rejected(byte[] blob, int read) {
        if (blob == null || blob.length <= 1) return 0;
        int format = blob[0];
        if (format == ChunkErosionData.currentFormat()) {
            return Math.max(0, (blob.length - 1) / RECORD_BYTES - Math.max(0, read));
        }
        if (read <= 0) return Math.max(1, (blob.length - 1) / RECORD_BYTES);
        return 0;
    }

    /** Whether a blob read back damaged: some of its records rejected, or none of it readable. */
    public static boolean damaged(byte[] blob, int read) {
        return rejected(blob, read) > 0;
    }

    /**
     * What a chunk that has just been read needs.
     *
     * @param tagged      whether the chunk's save held this mod's tag at all
     * @param damaged     whether its blob read back {@link #damaged}
     * @param mirror      what the mirror holds for the chunk, or null
     * @param savedAt     the chunk's own save stamp, as the save it was read from wrote it
     * @param backupHolds whether the newest backup holds wear for it; asked only where the mirror is gone as a whole
     */
    public static Kind of(boolean tagged, boolean damaged, WearMirror.Entry mirror, long savedAt, boolean backupHolds) {
        if (tagged && !damaged) return Kind.FINE;
        if (mirror != null) {
            if (!mirror.hasWear()) {
                // Saved by this mod with no wear: a chunk with no tag is right. Damage with nothing to mend it from is
                // left as it is, unless a backup holds it.
                return tagged && backupHolds ? Kind.LOST : Kind.FINE;
            }
            return mirror.savedAt == savedAt ? Kind.QUIET : Kind.LOST;
        }
        return backupHolds ? Kind.LOST : Kind.FINE;
    }

    /**
     * The most chunks a session puts back quietly (Xep, after phase 1: "restore it quietly... and only prompt for
     * bigger losses: many chunks, or the whole world's wear gone"). Past it, what the mirror holds from the same save
     * is a bigger loss like any other: single player is asked, a dedicated server puts it back and tells its
     * operators.
     */
    public static final int QUIET_MOST = 16;

    /**
     * Whether a chunk the mirror holds from the same save goes back quietly: only while the session has put back fewer
     * than {@link #QUIET_MOST} that way, and never when the world's wear or this mod's world data has gone as a whole.
     *
     * @param quietSoFar how many chunks this session has already put back quietly
     * @param wholeGone  whether the world's wear (the mirror) or its world data went as a whole
     */
    public static boolean staysQuiet(int quietSoFar, boolean wholeGone) {
        return staysQuiet(quietSoFar, wholeGone, QUIET_MOST);
    }

    /** As above, against a threshold a test run sets lower ({@link WearBackupSet#quietMost}). */
    public static boolean staysQuiet(int quietSoFar, boolean wholeGone, int most) {
        return !wholeGone && quietSoFar < most;
    }

    /** The one log line a session about the chunks put back quietly from the mirror. */
    public static String quietLine(int chunks) {
        return "Wear backups: put back the wear of " + chunks
            + (chunks == 1 ? " chunk" : " chunks")
            + " from the mirror, missing or damaged in the same save the mirror holds, so nothing was lost.";
    }
}
