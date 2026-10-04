package com.trmtgtnh.client;

import com.trmtgtnh.erosion.RecordReadout;
import com.trmtgtnh.network.TrmtNetwork;
import com.trmtgtnh.util.InspectionSlot;

/**
 * The server's figures for the one block the player is looking at.
 *
 * <p>
 * A client can already see which surface and stage a position wears, because that is what it is drawing. What
 * it cannot see - how far wear has come toward the next stage, how long the ground has been left alone, and the
 * reinforcement and spawn ward a block carries, worn or not - lives only in the server's record. Sending those
 * with every position in every chunk would double the overlay for numbers nobody is reading, so they are
 * fetched one position at a time, and only while somebody is pointing at it.
 *
 * <p>
 * Exactly one position is held, and when to ask again and whether the answer held still stands are
 * {@link InspectionSlot}'s to say: the server says nothing about a position with no record, so an answer it
 * stops giving is dropped rather than shown for as long as the player looks.
 */
public final class InspectionCache {

    private static final InspectionSlot SLOT = new InspectionSlot();

    private static float wear;
    private static float threshold;
    private static int untouchedSeconds;
    private static int recoverySeconds;
    private static int chainIndex;
    private static int chainLength;
    private static int reinforce;
    private static int ward;

    private InspectionCache() {}

    /**
     * Asks the server about a position when the slot says to.
     *
     * <p>
     * Called once per client tick while the crosshair rests on a block worth asking about - see
     * {@link com.trmtgtnh.util.InspectionReach#asks} - so the slot's throttle is what keeps a player staring at
     * the ground from sending twenty packets a second.
     */
    public static void poll(int px, int py, int pz) {
        if (SLOT.poll(px, py, pz)) TrmtNetwork.requestInspection(px, py, pz);
    }

    /** Stores a reply, ignoring one for a position the player has already looked away from. */
    public static void accept(int px, int py, int pz, float replyWear, float replyThreshold, int replyUntouched,
        int replyRecovery, int replyChainIndex, int replyChainLength, int replyReinforce, int replyWard) {
        if (!SLOT.accept(px, py, pz)) return;
        wear = replyWear;
        threshold = replyThreshold;
        untouchedSeconds = replyUntouched;
        recoverySeconds = replyRecovery;
        chainIndex = RecordReadout.indexFromWire(replyChainIndex);
        chainLength = replyChainLength;
        reinforce = replyReinforce;
        ward = replyWard;
    }

    /** The reinforcement level the server last reported for the tracked position, 0 when none. */
    public static int reinforce() {
        return reinforce;
    }

    /** The spawn-ward flags the server last reported: bit 0 hostile barred, bit 1 passive barred. */
    public static int ward() {
        return ward;
    }

    /** Whether an answer for this exact position stands. */
    public static boolean has(int px, int py, int pz) {
        return SLOT.has(px, py, pz);
    }

    /** How far this block has worn toward its next stage, from 0 to 1; nought when the reply tells none. */
    public static float progress() {
        return RecordReadout.progress(wear, threshold);
    }

    /**
     * Whether the last reply told any progress. A server from 0.9.213 on withholds the threshold of a
     * record with nothing drawn, so this is false for a trample tally, for ground short of its first
     * gradation and for a record kept only for a reinforcement or a ward. The wear lines are drawn only on
     * a ghost anyway; this covers a ghost that outlives its record on this client. An older server sends
     * the threshold regardless.
     */
    public static boolean tellsProgress() {
        return RecordReadout.tellsProgress(threshold);
    }

    /** How long the ground here has gone untrodden, in in-game seconds, or -1 when the server withheld it. */
    public static int untouchedSeconds() {
        return untouchedSeconds;
    }

    /**
     * In-game seconds until this position recovers a stage, 0 if it is recovering now, or -1 if
     * it never will. The server works this out, because the rates that decide it are its own.
     */
    public static int recoverySeconds() {
        return recoverySeconds;
    }

    /**
     * How far along the whole run from pristine to fully worn this is, or -1 if the server withheld it or does not
     * know.
     */
    public static int chainIndex() {
        return chainIndex;
    }

    /** How many steps the run of the block standing there has, nought when it has none. */
    public static int chainLength() {
        return chainLength;
    }

    /**
     * Forgets the held position: on disconnect, so a reconnect cannot answer a question from a
     * previous world, and on every tick nothing worth asking about is under the crosshair, so an
     * answer from before the player looked away is never shown again.
     */
    public static void clear() {
        SLOT.clear();
    }
}
