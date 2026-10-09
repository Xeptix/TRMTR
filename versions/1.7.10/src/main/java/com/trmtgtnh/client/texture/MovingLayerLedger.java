package com.trmtgtnh.client.texture;

import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * What moving the layer behind a worn surface makes the game keep, and whether the budget for it has room.
 *
 * <p>
 * The budget used to charge a surface for its liquid's frames and nothing else, though the setting and the budget's own
 * javadoc both said it counted the shells. Each of the three hundred and twenty pictures of a surface at the settings
 * shipped keeps its shell, and the atlas keeps each one's still picture with every smaller mip level of it, which
 * vanilla lets go of for any sprite that declares no animation; a picture drawn at another size from the frames would
 * keep its own scaled copy of all of them. At sixteen pixels Chisel's fifteen faces hold a little over eleven megabytes
 * of that and were charged under four hundred kilobytes, so the default sixty-four megabytes would have let more than
 * two gigabytes through before refusing a single surface.
 *
 * <p>
 * So the whole price is worked out here before any of a surface is granted, and a surface is granted whole or not at
 * all; what each picture then keeps is counted here too, from the arrays themselves, so the two can be held against
 * each other at the end of a stitch. Nothing from the game is in it, which is what lets a test pin every figure, and
 * it is on the list CoreStaysPortableTest keeps free of Minecraft. Which sprites make a surface, what a texture is and
 * what the log says stay in WearTextures and InnerLayers, which hand every figure in.
 *
 * <p>
 * Bytes of pixels, four to a pixel, and not the arrays that hold them: a header is a few bytes against a picture's
 * thousand, and counting it would tie the figure to one virtual machine.
 */
public final class MovingLayerLedger {

    /** Bytes in one pixel of a picture as the game holds it: one int of packed color. */
    public static final int BYTES_PER_PIXEL = 4;

    /** Bytes in the kibibyte the log counts in. */
    public static final long KIBIBYTE = 1024L;

    /** Bytes in the mebibyte client.innerLayerAnimationBudgetMb counts in. */
    public static final long MEBIBYTE = 1024L * 1024L;

    /** What a surface's ask came to. */
    public enum Answer {
        /** Priced, and there was room for all of it. */
        GRANTED,
        /** Priced, and there was not room for all of it, so none of it moves. */
        REFUSED,
        /** Not priced, because a budget of nought moves nothing. */
        NOUGHT,
        /**
         * Not priced, because no picture of the surface has a size. The sprite pass builds nothing on a stitch that
         * loaded nothing, so it never asks this.
         */
        NOTHING_TO_MOVE
    }

    /** The bytes a budget of this many megabytes allows. Nought or less allows nothing. */
    public static long budgetBytes(int megabytes) {
        return megabytes <= 0 ? 0L : megabytes * MEBIBYTE;
    }

    /** One picture of this edge, and nothing for an edge of nought or less. */
    public static long pictureBytes(int edge) {
        return edge <= 0 ? 0L : (long) edge * edge * BYTES_PER_PIXEL;
    }

    /** This many frames of this edge, and nothing for a count of nought or less. */
    public static long framesBytes(int frameCount, int edge) {
        return frameCount <= 0 ? 0L : frameCount * pictureBytes(edge);
    }

    /**
     * A still picture of this edge with every mip level the atlas generates under it. Each level is a quarter the
     * length of the one above, rounded down, because that is how vanilla's mipmap generator sizes it; for an edge that
     * is a power of two it is the same as halving the edge. Once a level comes to no pixels every level after it does
     * too, so the count stops there rather than walking a hand-edited level count to its end.
     */
    public static long keptBytes(int edge, int mipLevels) {
        if (edge <= 0) return 0L;
        long level = (long) edge * edge;
        long total = level;
        for (int k = 1; k <= mipLevels && level > 0L; k++) {
            level >>= 2;
            total += level;
        }
        return total * BYTES_PER_PIXEL;
    }

    /** The bytes of one picture's pixels, or nothing where there is no picture. */
    public static long bytesOf(int[] pixels) {
        return pixels == null ? 0L : (long) pixels.length * BYTES_PER_PIXEL;
    }

    /** The bytes of every picture in a set, such as a still picture and its mip levels, skipping any missing. */
    public static long bytesOf(int[][] pictures) {
        long total = 0L;
        if (pictures == null) return total;
        for (int[] picture : pictures) {
            total += bytesOf(picture);
        }
        return total;
    }

    /** Whole kibibytes, rounded down, for the log. */
    public static long kibibytes(long bytes) {
        return bytes / KIBIBYTE;
    }

    /** Whole megabytes, rounded up, for a budget the log recommends: a figure rounded down would still refuse. */
    public static long mebibytesUp(long bytes) {
        return bytes <= 0L ? 0L : (bytes + MEBIBYTE - 1L) / MEBIBYTE;
    }

    /**
     * The pictures that would share one surface's moving layer, counted by the edge each was stitched at.
     *
     * <p>
     * By edge rather than as one number, because a picture at another size from its frames would keep a copy of them of
     * its own. The sprite pass makes none - every picture of one filing is stitched at the edge its frames are cut to -
     * but the price is worked out for any mixture, so a count that ever held two sizes would be priced for what they
     * keep rather than for what they were meant to.
     */
    public static final class Pictures {

        /** The edges met so far, in the order first met; only the first {@code kinds} places are used. */
        private int[] edges = new int[2];

        /** How many pictures loaded at each of those edges, one to one with them. */
        private int[] counts = new int[2];

        private int kinds;

        private int total;

        /** One more picture at this edge. A sprite with no size yet is never installed, so it is not a picture. */
        public void add(int edge) {
            add(edge, 1);
        }

        /** This many more pictures at this edge. A count or an edge of nought or less adds nothing. */
        public void add(int edge, int pictures) {
            if (edge <= 0 || pictures <= 0) return;
            total += pictures;
            for (int i = 0; i < kinds; i++) {
                if (edges[i] == edge) {
                    counts[i] += pictures;
                    return;
                }
            }
            if (kinds == edges.length) {
                edges = Arrays.copyOf(edges, kinds * 2);
                counts = Arrays.copyOf(counts, kinds * 2);
            }
            edges[kinds] = edge;
            counts[kinds] = pictures;
            kinds++;
        }

        /** How many pictures have been added, at every edge together. */
        public int count() {
            return total;
        }
    }

    /** What moving one surface's layer keeps, by what keeps it. */
    public static final class Price {

        /** How many pictures would share the layer. */
        public final int pictures;

        /** One per picture, at its own edge. */
        public final long shells;

        /** The still picture and its mip levels, which the atlas keeps only for a sprite that moves. */
        public final long kept;

        /** The frames as cut, once, where any picture is drawn at their edge and so shares them. */
        public final long frames;

        /** A copy of every frame for each picture drawn at another edge. */
        public final long copies;

        Price(int pictures, long shells, long kept, long frames, long copies) {
            this.pictures = pictures;
            this.shells = shells;
            this.kept = kept;
            this.frames = frames;
            this.copies = copies;
        }

        /** Everything together: the figure a surface is granted or refused on. */
        public long total() {
            return shells + kept + frames + copies;
        }
    }

    /**
     * The whole price of one surface. The frames as cut are counted only where some picture shares them. Where none
     * does, every picture keeps its own copy, and the set the surface was harvested with is let go of once the pass
     * ends.
     */
    public static Price price(Pictures pictures, int frameCount, int frameEdge, int mipLevels) {
        int count = pictures == null ? 0 : pictures.total;
        long shells = 0L;
        long kept = 0L;
        long copies = 0L;
        boolean shared = false;
        for (int i = 0; pictures != null && i < pictures.kinds; i++) {
            int edge = pictures.edges[i];
            long many = pictures.counts[i];
            shells += many * pictureBytes(edge);
            kept += many * keptBytes(edge, mipLevels);
            if (edge == frameEdge) shared = true;
            else copies += many * framesBytes(frameCount, edge);
        }
        return new Price(count, shells, kept, shared ? framesBytes(frameCount, frameEdge) : 0L, copies);
    }

    /** The bytes this stitch's moving layers may hold, never below nought. */
    private final long budget;

    /** The bytes granted so far, which never pass the budget. */
    private long granted;

    private int surfacesGranted;

    private int picturesGranted;

    /** Surfaces that asked while the budget was nought, and so were never priced. */
    private int declinedAtNought;

    private int surfacesRefused;

    /** What every refused surface would have held, together. */
    private long refusedBytes;

    private long cheapestRefused;

    /** Refusals by texture, in the order met: surfaces, then bytes. */
    private final Map<String, long[]> refusals = new LinkedHashMap<String, long[]>();

    private long shellsHeld;

    private long keptHeld;

    private long framesHeld;

    private long copiesHeld;

    private int picturesHolding;

    /** Frames already counted as held, so the pictures sharing one copy count it once. */
    private final Set<int[][]> framesCounted = Collections.newSetFromMap(new IdentityHashMap<int[][], Boolean>());

    /** A ledger for one stitch with this many bytes to grant, nought or less granting nothing. */
    public MovingLayerLedger(long budgetBytes) {
        this.budget = Math.max(0L, budgetBytes);
    }

    /**
     * Whether this surface moves. Whole or not at all, and a refusal does not close the ledger, so a cheaper surface
     * asked for afterwards can still move.
     */
    public Answer ask(String texture, Price price) {
        if (price == null || price.pictures <= 0) return Answer.NOTHING_TO_MOVE;
        if (budget <= 0L) {
            declinedAtNought++;
            return Answer.NOUGHT;
        }
        long wanted = price.total();
        if (wanted > budget - granted) {
            String name = String.valueOf(texture);
            long[] row = refusals.get(name);
            if (row == null) {
                row = new long[2];
                refusals.put(name, row);
            }
            row[0]++;
            row[1] += wanted;
            cheapestRefused = surfacesRefused == 0 ? wanted : Math.min(cheapestRefused, wanted);
            surfacesRefused++;
            refusedBytes += wanted;
            return Answer.REFUSED;
        }
        granted += wanted;
        surfacesGranted++;
        picturesGranted += price.pictures;
        return Answer.GRANTED;
    }

    /**
     * Counts what one picture keeps as it takes its layer, from the arrays themselves. Frames shared by a surface's
     * pictures are counted by the first of them; a picture's own scaled copy by that picture. Counted whatever the
     * ledger granted, because it is a measure and not a price: a picture that takes a layer nothing granted is exactly
     * what the end of the stitch has to be able to see.
     */
    public void took(int[] shell, int[][] keptPicture, int[][] frames, boolean ownCopy) {
        picturesHolding++;
        shellsHeld += bytesOf(shell);
        keptHeld += bytesOf(keptPicture);
        if (ownCopy) copiesHeld += bytesOf(frames);
        else if (frames != null && framesCounted.add(frames)) framesHeld += bytesOf(frames);
    }

    /**
     * Lets go of the frames counted, so a finished stitch's ledger does not keep its arrays alive. The figures stay as
     * they are; only the arrays are forgotten, so frames handed in after this are counted again.
     */
    public void closeBook() {
        framesCounted.clear();
    }

    /** The bytes this ledger may grant. */
    public long budget() {
        return budget;
    }

    /** The bytes granted so far. */
    public long granted() {
        return granted;
    }

    /** How many surfaces were granted a moving layer. */
    public int surfacesGranted() {
        return surfacesGranted;
    }

    /** How many pictures those surfaces were priced for. */
    public int picturesGranted() {
        return picturesGranted;
    }

    /** How many surfaces asked while the budget was nought. */
    public int declinedAtNought() {
        return declinedAtNought;
    }

    /** How many surfaces were priced and refused. */
    public int surfacesRefused() {
        return surfacesRefused;
    }

    /** What the refused surfaces would have held, together. */
    public long refusedBytes() {
        return refusedBytes;
    }

    /** The cheapest surface refused, or nought when none was. */
    public long cheapestRefused() {
        return cheapestRefused;
    }

    /** The bytes of worn shells the pictures took. */
    public long shellsHeld() {
        return shellsHeld;
    }

    /** The bytes of still pictures and their mip levels the pictures took. */
    public long keptHeld() {
        return keptHeld;
    }

    /** The bytes of frames as cut, each set counted once however many pictures share it. */
    public long framesHeld() {
        return framesHeld;
    }

    /** The bytes of frames copied for pictures drawn at another size from them. */
    public long copiesHeld() {
        return copiesHeld;
    }

    /** How many pictures took a layer. */
    public int picturesHolding() {
        return picturesHolding;
    }

    /** Everything the pictures took, together. */
    public long held() {
        return shellsHeld + keptHeld + framesHeld + copiesHeld;
    }

    /** Whether the pictures took more than was granted: the price and what a picture keeps have come apart. */
    public boolean overdrawn() {
        return held() > granted;
    }

    /** The bytes granted and not taken, or nought when everything granted was. */
    public long givenBack() {
        return Math.max(0L, granted - held());
    }

    /** How many of the pictures priced never took their layer, or nought when as many or more did. */
    public int picturesNotTaken() {
        return Math.max(0, picturesGranted - picturesHolding);
    }

    /** The budget, in whole megabytes, that would have moved every surface asked for. */
    public long everythingAskedMebibytes() {
        return mebibytesUp(granted + refusedBytes);
    }

    /**
     * The refusals by texture, as the log gives them: {@code lava_still on 2 surfaces (6130 KiB), ...}. Empty when
     * nothing was refused.
     */
    public String describeRefusals() {
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, long[]> entry : refusals.entrySet()) {
            if (text.length() > 0) text.append(", ");
            long many = entry.getValue()[0];
            text.append(entry.getKey())
                .append(" on ")
                .append(many)
                .append(many == 1L ? " surface (" : " surfaces (")
                .append(kibibytes(entry.getValue()[1]))
                .append(" KiB)");
        }
        return text.toString();
    }

    /**
     * How many pictures one tick let redraw their layer and how many it turned away, and whether a stitch has yet said
     * that the ceiling turned any away. Nothing is taken or counted before the first tick has set an allowance.
     *
     * <p>
     * It changes nothing about which pictures are redrawn. It grants exactly what the plain count of uploads left
     * granted before it, refusing everything before the first tick as that did, and only keeps the figures a tick
     * measured so that the log can quote them.
     */
    public static final class UploadTally {

        /** This tick's allowance, or minus one before the first tick has set one. */
        private int allowed = -1;

        private int left;

        private int asked;

        private int turnedAway;

        /** Whether a tick that turned pictures away has been said since the last {@link #forget}. */
        private boolean said;

        private int lastAsked;

        private int lastAllowed;

        private int lastTurnedAway;

        /**
         * Closes the tick just drawn and opens the next with this allowance, nought for anything below it; true when
         * the one closed turned pictures away for the first time since {@link #forget}, whose figures are then kept.
         */
        public boolean newTick(int allowance) {
            boolean due = !said && turnedAway > 0;
            if (due) {
                said = true;
                lastAsked = asked;
                lastAllowed = allowed;
                lastTurnedAway = turnedAway;
            }
            allowed = Math.max(0, allowance);
            left = allowed;
            asked = 0;
            turnedAway = 0;
            return due;
        }

        /** Whether one more picture may be redrawn this tick, counting it either way once a tick has begun. */
        public boolean take() {
            if (allowed < 0) return false;
            asked++;
            if (left <= 0) {
                turnedAway++;
                return false;
            }
            left--;
            return true;
        }

        /**
         * A new stitch: the next tick that turns pictures away is said again, and whatever the old atlas asked on the
         * tick
         * it was replaced in is dropped.
         *
         * <p>
         * A reload runs inside a tick, after that tick's redraws and before the tick is closed, so counts kept here
         * would
         * be closed as the new stitch's first tick: its line would give the old atlas's figures, and the new stitch's
         * own
         * first overrun would go unsaid. What this tick has already granted stands, so nothing more is granted than the
         * ceiling allows.
         */
        public void forget() {
            said = false;
            asked = 0;
            turnedAway = 0;
        }

        /** How many pictures asked on the tick last said. */
        public int lastAsked() {
            return lastAsked;
        }

        /** The allowance that tick had. */
        public int lastAllowed() {
            return lastAllowed;
        }

        /** How many pictures that tick turned away. */
        public int lastTurnedAway() {
            return lastTurnedAway;
        }
    }
}
