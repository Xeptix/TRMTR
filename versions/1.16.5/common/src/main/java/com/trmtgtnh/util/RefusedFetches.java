package com.trmtgtnh.util;

/**
 * The kinds of worn ground a golem has already failed to fetch payment for, so that it stops asking.
 *
 * <p>
 * A golem with Settled Ways that meets ground nothing in reach pays for walks every container round
 * its anchor looking for payment, and without a memory of the answer it walks them again for every
 * square it tries - dozens of squares a stroke, and with a chunk tamper every column of every area
 * it tries. The ground is left as it was each time, so all of that work buys nothing. This remembers
 * the refusal and lets the golem skip the walk until something has changed that could make the
 * answer different.
 *
 * <p>
 * <b>Remembered per kind of block.</b> A kind is a block and its metadata, and what pays for a square
 * depends on nothing else but the config. Remembering per square would still walk once for every
 * square of the same ground, which is the very cost this exists to remove. Remembering per family
 * would be wrong, because two blocks of one family can be paid for differently - where
 * repairAnyInFamily is off, or where a block names its own item - so a refusal for one says nothing
 * about the other.
 *
 * <p>
 * <b>Two refusals, with two lifetimes.</b> "Nothing to be had" means no container in reach held
 * anything that pays. Only a change to the containers can alter that, so it stands until the golem
 * next looks at its stores, which it does every so often whether or not anything has changed; that
 * look is how a container restocked by hand or by a pipe, or a config reload, comes to be noticed.
 * "No room" means something that pays was there but the golem had nowhere to put it. That also ends
 * the moment anything the golem carries changes, because any such change may be the one that made
 * room, and working out which ones did would cost more than the walk it saves. The two are told apart
 * by two counters the caller supplies: the scan, naming the look at the stores a refusal was made
 * against, and the stock, naming the state of the golem's own storage. Neither means anything here
 * beyond being equal or not.
 *
 * <p>
 * <b>Nothing is saved.</b> A refusal is a shortcut, not a fact about the world. A golem loaded from
 * disk has not looked at its stores yet, and nothing is remembered against a look that has not
 * happened, so its first strokes walk the stores again and record whatever they find.
 *
 * <p>
 * <b>A full memo takes nothing new.</b> At most {@link #MOST_KINDS} kinds are held, and a kind that
 * arrives when every place is taken is simply not remembered, rather than pushing another out. A golem
 * looks at the squares of its box in the same order every stroke, so a memo that let its oldest entry
 * give way threw out the very next kind the golem was about to ask after - and past sixty-four kinds of
 * unpayable ground, which a round of stone variants and their metadata can reach, it walked the stores
 * for every one of them on every stroke, exactly the cost being avoided. Holding the first sixty-four
 * keeps those refused and walks only the kinds past them, and the fresh look at the stores that
 * forgets everything, at most ten seconds on, lets a different sixty-four in. A kind recorded again
 * keeps the place it already has.
 *
 * <p>
 * It has nothing from the game in it, so a test can pin the lifetimes without a world, and it is on
 * the list CoreStaysPortableTest keeps free of Minecraft. One golem owns one, and only the server
 * thread touches it.
 */
public final class RefusedFetches {

    /** How many kinds are remembered at once. Past this, a new kind is not remembered at all. */
    public static final int MOST_KINDS = 64;

    private final long[] kinds = new long[MOST_KINDS];

    /** The scan each refusal was made against. */
    private final int[] scans = new int[MOST_KINDS];

    /** The stock a refusal for want of room was made against; never read for nothing to be had. */
    private final int[] stocks = new int[MOST_KINDS];

    /** True where the refusal was for want of room, false where nothing was to be had. */
    private final boolean[] roomless = new boolean[MOST_KINDS];

    /** How many places are in use, always counted from the start of the arrays. */
    private int size;

    /**
     * The kind of a block: its id and metadata packed into one number.
     *
     * <p>
     * The id takes the high half and the metadata the low. The metadata is masked, so that a negative
     * figure cannot spread its sign across the id and make every block look alike.
     */
    public static long kindOf(int blockId, int meta) {
        return ((long) blockId << 32) | (meta & 0xFFFFFFFFL);
    }

    /**
     * True while a refusal of this kind still stands.
     *
     * <p>
     * It stands when it was made against this same scan and, if it was for want of room, against this
     * same stock as well. A scan of nought or below never matches: that is what a golem reads before it
     * has looked at its stores, and nothing was learned before that look.
     *
     * @param kind  the kind, from {@link #kindOf}
     * @param scan  the look at the stores that is current now
     * @param stock the golem's stock counter now
     */
    public boolean stillRefused(long kind, int scan, int stock) {
        if (scan <= 0) return false;
        int at = indexOf(kind);
        if (at < 0 || scans[at] != scan) return false;
        return !roomless[at] || stocks[at] == stock;
    }

    /**
     * Records that nothing in reach pays for this kind, as of this scan.
     *
     * <p>
     * It stands until the scan changes, whatever happens to the golem's own storage, because nothing the
     * golem carries can put payment into a container. A scan of nought or below records nothing.
     */
    public void nothingToBeHad(long kind, int scan) {
        record(kind, scan, 0, false);
    }

    /**
     * Records that something in reach pays for this kind but the golem had no room to take it.
     *
     * <p>
     * It stands until the scan or the stock changes. A scan of nought or below records nothing.
     */
    public void noRoomFor(long kind, int scan, int stock) {
        record(kind, scan, stock, true);
    }

    /**
     * Forgets every refusal.
     *
     * <p>
     * For a change that could alter the answer for every kind at once: a new anchor, a fresh look at the
     * stores, or the golem putting something away, which may have put into a container the very thing a
     * kind was refused for. A fresh look would leave every entry stale in any case; forgetting them then
     * also frees their places for the kinds that look finds.
     */
    public void forget() {
        size = 0;
    }

    /** How many kinds are held, stale ones included until they are forgotten or recorded again. */
    public int size() {
        return size;
    }

    private void record(long kind, int scan, int stock, boolean noRoom) {
        if (scan <= 0) return;
        int at = indexOf(kind);
        if (at < 0) {
            // Full: not remembered, rather than pushing out a kind the same stroke may ask after next.
            if (size >= MOST_KINDS) return;
            at = size++;
        }
        kinds[at] = kind;
        scans[at] = scan;
        stocks[at] = stock;
        roomless[at] = noRoom;
    }

    private int indexOf(long kind) {
        for (int i = 0; i < size; i++) {
            if (kinds[i] == kind) return i;
        }
        return -1;
    }
}
