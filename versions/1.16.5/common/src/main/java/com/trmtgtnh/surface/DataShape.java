package com.trmtgtnh.surface;

import java.util.Arrays;
import java.util.List;

/**
 * A shape a data file gives a block (W3 spec 2.4, task T6): which of the four it is, whether wear sinks into it, how
 * deep it may, and for {@code boxes} the boxes themselves.
 *
 * <p>
 * Game-free and the same file in every edition, because it is the arithmetic both sides of a visit must agree on: the
 * server's collision with a worn box and the client's, the drawn top and the footing, and the bytes the table carries
 * between them ({@link SurfaceTableCodec}). Each edition turns it into its own boxes and its own shape.
 *
 * <p>
 * <strong>The walked face.</strong> Of a {@code boxes} shape, the boxes whose tops stand highest are walked on, and
 * only those sink: the rest keep their tops whatever the wear. A worn walked box keeps at least a pixel of itself, so
 * the deepest wear is held below the shortest walked box's height ({@code api/FORMAT.md}: "Held below the walked box's
 * height"), and the same hold applies to a slab's half and a full block's whole.
 */
public final class DataShape {

    /** The four shapes, in the order the wire carries them. */
    public static final int FULL = 0, SLAB = 1, STAIR = 2, BOXES = 3;

    /** The shapes' names as a file spells them, by their number. */
    private static final String[] NAMES = { "full", "slab", "stair", "boxes" };

    /** The most boxes a shape may have, as the format allows. */
    public static final int MOST_BOXES = 8;

    /** A box's corners are whole pixels from 0 to this. */
    public static final int PIXELS = 16;

    /** {@link #maxSinkPixels} when the file gives none. */
    public static final int NO_CAP = -1;

    /** The deepest any record sinks, as {@code SinkProfile.MAX_SINK_PIXELS} holds it. */
    private static final int DEEPEST = 15;

    public final int kind;

    /** False keeps wear flat on the face ({@code "sinks": false}). */
    public final boolean sinks;

    /** The file's own cap on the depth, 0 to 15, or {@link #NO_CAP}. */
    public final int maxSinkPixels;

    private final int[][] boxes;

    /**
     * A shape, checked: a known kind, a cap of 0 to 15 or none, and boxes only for {@code boxes}, one to eight of them,
     * each with room on every axis inside the block.
     *
     * @throws IllegalArgumentException for anything else
     */
    public DataShape(int kind, boolean sinks, int maxSinkPixels, int[][] boxes) {
        if (kind < FULL || kind > BOXES) throw new IllegalArgumentException("no shape numbered " + kind);
        if (maxSinkPixels != NO_CAP && (maxSinkPixels < 0 || maxSinkPixels > DEEPEST)) {
            throw new IllegalArgumentException("a sink cap of " + maxSinkPixels + " pixels");
        }
        int count = boxes == null ? 0 : boxes.length;
        if (kind == BOXES ? count < 1 || count > MOST_BOXES : count != 0) {
            throw new IllegalArgumentException(NAMES[kind] + " with " + count + " boxes");
        }
        int[][] copied = new int[count][];
        for (int i = 0; i < count; i++) {
            int[] box = boxes[i];
            if (box == null || box.length != 6) throw new IllegalArgumentException("a box is six numbers");
            for (int n : box) {
                if (n < 0 || n > PIXELS) throw new IllegalArgumentException("a box corner at " + n + " pixels");
            }
            if (box[0] >= box[3] || box[1] >= box[4] || box[2] >= box[5]) {
                throw new IllegalArgumentException("a box with no room on an axis");
            }
            copied[i] = box.clone();
        }
        this.kind = kind;
        this.sinks = sinks;
        this.maxSinkPixels = maxSinkPixels;
        this.boxes = copied;
    }

    /** A shape's number from its name in a file, or -1. */
    public static int kindOf(String name) {
        for (int i = 0; i < NAMES.length; i++) {
            if (NAMES[i].equals(name)) return i;
        }
        return -1;
    }

    /** The name a file spells this shape with. */
    public String name() {
        return NAMES[kind];
    }

    /**
     * A shape from a block state's merged shapes facts ({@code shape}, {@code boxes}, {@code maxSinkPixels},
     * {@code sinks}), or null where they make none. The merge is key by key, so a higher layer's {@code "shape":
     * "slab"} over a lower layer's boxes leaves the boxes standing in the facts: they are read only with {@code boxes}.
     */
    public static DataShape fromFacts(Object shape, Object boxes, Object maxSinkPixels, Object sinks) {
        if (!(shape instanceof String)) return null;
        int kind = kindOf((String) shape);
        if (kind < 0) return null;
        int[][] made = null;
        if (kind == BOXES) {
            if (!(boxes instanceof List)) return null;
            List<?> listed = (List<?>) boxes;
            made = new int[listed.size()][];
            for (int i = 0; i < made.length; i++) {
                Object one = listed.get(i);
                if (!(one instanceof int[])) return null;
                made[i] = (int[]) one;
            }
        }
        int cap = maxSinkPixels instanceof Number ? ((Number) maxSinkPixels).intValue() : NO_CAP;
        boolean sinking = !(sinks instanceof Boolean) || ((Boolean) sinks).booleanValue();
        try {
            return new DataShape(kind, sinking, cap, made);
        } catch (IllegalArgumentException unusable) {
            return null;
        }
    }

    public int boxCount() {
        return boxes.length;
    }

    /** One box as six pixels, {@code [x0, y0, z0, x1, y1, z1]}, a copy. */
    public int[] box(int index) {
        return boxes[index].clone();
    }

    /** The top of the walked face in pixels: the highest box top, or the whole block where there are no boxes. */
    public int walkedTop() {
        if (boxes.length == 0) return PIXELS;
        int top = 0;
        for (int[] box : boxes) top = Math.max(top, box[4]);
        return top;
    }

    /** Whether a box is walked on: its top stands as high as any. */
    public boolean walked(int index) {
        return boxes[index][4] == walkedTop();
    }

    /** The shortest walked box's height in pixels, which the wear is held below; the whole block without boxes. */
    public int walkedHeight() {
        if (boxes.length == 0) return PIXELS;
        int top = walkedTop();
        int least = PIXELS;
        for (int[] box : boxes) {
            if (box[4] == top) least = Math.min(least, box[4] - box[1]);
        }
        return least;
    }

    /** The bottom of the lowest box in pixels; nought without boxes. */
    public int floor() {
        if (boxes.length == 0) return 0;
        int floor = PIXELS;
        for (int[] box : boxes) floor = Math.min(floor, box[1]);
        return floor;
    }

    /**
     * How far this shape lets the ground sink, in pixels, given the depth the family and the shape's own rule allow:
     * nothing when it does not sink, no deeper than its own cap, and always a pixel short of the shape's height (the
     * shortest walked box's for {@code boxes}, the caller's {@code heightPixels} for the others).
     */
    public int sunk(int allowed, int heightPixels) {
        if (!sinks || allowed <= 0) return 0;
        int depth = allowed;
        if (maxSinkPixels != NO_CAP) depth = Math.min(depth, maxSinkPixels);
        int height = kind == BOXES ? walkedHeight() : heightPixels;
        depth = Math.min(depth, height - 1);
        return depth < 0 ? 0 : depth;
    }

    /**
     * The boxes in block units, 0 to 1, with the walked boxes' tops lowered by {@code sunkPixels} (already held by
     * {@link #sunk}); the others as the file gives them.
     */
    public double[][] boxesSunk(int sunkPixels) {
        double[][] out = new double[boxes.length][];
        int top = walkedTop();
        for (int i = 0; i < boxes.length; i++) {
            int[] box = boxes[i];
            int y1 = box[4] == top ? Math.max(box[1] + 1, box[4] - Math.max(0, sunkPixels)) : box[4];
            out[i] = new double[] { box[0] / (double) PIXELS, box[1] / (double) PIXELS, box[2] / (double) PIXELS,
                box[3] / (double) PIXELS, y1 / (double) PIXELS, box[5] / (double) PIXELS };
        }
        return out;
    }

    /** The one box around all of {@link #boxesSunk}, in block units: what an outline or a single box is given. */
    public double[] outline(int sunkPixels) {
        double[][] all = boxesSunk(sunkPixels);
        if (all.length == 0) return new double[] { 0, 0, 0, 1, 1, 1 };
        double[] out = all[0].clone();
        for (double[] box : all) {
            for (int axis = 0; axis < 3; axis++) {
                out[axis] = Math.min(out[axis], box[axis]);
                out[axis + 3] = Math.max(out[axis + 3], box[axis + 3]);
            }
        }
        return out;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof DataShape)) return false;
        DataShape that = (DataShape) other;
        return kind == that.kind && sinks == that.sinks
            && maxSinkPixels == that.maxSinkPixels
            && Arrays.deepEquals(boxes, that.boxes);
    }

    @Override
    public int hashCode() {
        return ((kind * 31 + (sinks ? 1 : 0)) * 31 + maxSinkPixels) * 31 + Arrays.deepHashCode(boxes);
    }

    @Override
    public String toString() {
        StringBuilder out = new StringBuilder(name());
        for (int[] box : boxes) out.append(' ')
            .append(Arrays.toString(box));
        if (!sinks) out.append(" flat");
        if (maxSinkPixels != NO_CAP) out.append(" sink<=")
            .append(maxSinkPixels);
        return out.toString();
    }
}
