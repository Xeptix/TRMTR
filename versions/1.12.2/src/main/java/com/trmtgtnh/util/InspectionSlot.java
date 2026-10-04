package com.trmtgtnh.util;

/**
 * Which one position the client is asking the server about, when to ask again, and whether the
 * answer held still stands.
 *
 * <p>
 * The server answers only for a position it has a record for. That keeps a plain block to a request
 * and no reply, and it means the only way the server can say a record has gone - the last
 * reinforcement taken off an unworn floor, its ward lifted - is to stop answering. Held for as long as
 * the crosshair rested there, the old answer went on saying "Reinforced" over a block that no longer
 * was. So an answer is believed while at most the request just sent is outstanding. A server with
 * something to say answers every request once, so under steady lag of any size a reply still lands
 * between one refresh and the next and the answer stands; only when two refreshes pass with no reply
 * between them is it dropped. A line taken off goes within two refreshes, two seconds.
 *
 * <p>
 * The price is paid when a server stalls for longer than a refresh: its lines go until its next reply
 * lands, where they used to stand stale. The stage and a pin are read off the client's own overlay
 * and stand throughout.
 *
 * <p>
 * Nothing here knows the game or the network; the caller sends the request when told to, and forgets
 * the position whenever the crosshair leaves it.
 */
public final class InspectionSlot {

    /** Ticks between repeat requests for the position held, so a held gaze stays roughly current. */
    public static final int REFRESH_TICKS = 20;

    private boolean holding;

    private int x;

    private int y;

    private int z;

    private int ticksSinceRequest;

    /** Requests sent for the position held since its last reply, the one just sent included. */
    private int outstanding;

    private boolean answered;

    /**
     * Whether to ask the server about this position now. Called once a tick while the crosshair rests on it:
     * a new position is asked about at once, and one already held every {@link #REFRESH_TICKS}.
     */
    public boolean poll(int px, int py, int pz) {
        if (!holds(px, py, pz)) {
            holding = true;
            x = px;
            y = py;
            z = pz;
            answered = false;
            ticksSinceRequest = 0;
            outstanding = 1;
            return true;
        }
        if (++ticksSinceRequest < REFRESH_TICKS) return false;
        ticksSinceRequest = 0;
        if (outstanding < Integer.MAX_VALUE) outstanding++;
        return true;
    }

    /** Takes a reply: true when it is for the position held; one for anywhere else is refused. */
    public boolean accept(int px, int py, int pz) {
        if (!holds(px, py, pz)) return false;
        answered = true;
        outstanding = 0;
        return true;
    }

    /** Whether an answer for this position stands: one has arrived, and at most the request just sent is unanswered. */
    public boolean has(int px, int py, int pz) {
        return answered && outstanding <= 1 && holds(px, py, pz);
    }

    /**
     * Forgets the position. On a reconnect, so a reply from a previous world is refused; and whenever
     * nothing worth asking about is under the crosshair, so looking back asks again rather than
     * trusting an answer from before the player looked away.
     */
    public void clear() {
        holding = false;
        answered = false;
        outstanding = 0;
        ticksSinceRequest = 0;
    }

    private boolean holds(int px, int py, int pz) {
        return holding && px == x && py == y && pz == z;
    }
}
