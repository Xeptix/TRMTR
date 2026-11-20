package com.trmtgtnh.apihost;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * What a render hook's {@code WearDraw.box} calls leave of a block (api/API.md, "Drawing a hooked block"): each box is
 * cut out of the block, and what is drawn is the block's own faces where they still stand and the floor and walls each
 * cut opens. Game-free, so every edition draws the same faces from the same boxes; each edition's adapter turns the
 * faces into its own vertices.
 *
 * <p>
 * <strong>Units.</strong> Block units, 0 to 1 on each axis from the block's own lower corner (its smallest x, y and z),
 * as the game's own block bounds are. A box is clamped to the block, its corners may be given either way round, and a
 * box with no volume left (or a coordinate that is no number) cuts nothing. Every int is a stage, the smallest
 * included: which cells are cut is kept apart from the stage they were cut at (Codex review R21).
 *
 * <p>
 * <strong>How.</strong> Every box edge splits the block along its axis, so the block becomes a grid of cells, each
 * either still solid or cut by a box; a face stands wherever a solid cell meets a cut one or the block's edge. A cell
 * cut by several boxes opens with the stage of the last box given. The faces of one plane that face the same way and
 * take the same picture (the block's own, or one stage) are merged into as few rectangles as a greedy sweep finds
 * (Codex review R21): sixteen boxes can split each axis 33 ways, and drawn cell by cell that was thousands of faces
 * for one block in a chunk build. Merging changes nothing drawn, because every edition lays a face's picture by its
 * place on the block, so a merged face shows exactly what its pieces did, with no seam where they met.
 *
 * <p>
 * Portable: the same file in all eight trees.
 */
public final class WearCuts {

    /** The most boxes one draw may cut; past it a box is ignored, and the adapter says so once. */
    public static final int MOST_BOXES = 16;

    /** Faces by their number, as every edition numbers its directions: down, up, north, south, west, east. */
    public static final int DOWN = 0, UP = 1, NORTH = 2, SOUTH = 3, WEST = 4, EAST = 5;

    /** One face to draw: a rectangle in one plane of the block, facing out of what is left of it. */
    public static final class Face {

        /** Which way it faces, {@link #DOWN} to {@link #EAST}. */
        public final int face;
        /** Its corners, in block units: one pair of the three is equal, the plane it lies in. */
        public final float x0, y0, z0, x1, y1, z1;
        /** True for a face on the block's own surface; false for a floor or wall a cut opened. */
        public final boolean outer;
        /** The stage of the box whose cut opened it; for an outer face, -1 (read {@link #outer}, not this). */
        public final int stage;

        Face(int face, float x0, float y0, float z0, float x1, float y1, float z1, boolean outer, int stage) {
            this.face = face;
            this.x0 = x0;
            this.y0 = y0;
            this.z0 = z0;
            this.x1 = x1;
            this.y1 = y1;
            this.z1 = z1;
            this.outer = outer;
            this.stage = stage;
        }

        /** The area it covers, in square block units. */
        public float area() {
            float dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
            if (face == DOWN || face == UP) return dx * dz;
            if (face == NORTH || face == SOUTH) return dx * dy;
            return dy * dz;
        }

        @Override
        public String toString() {
            return "face " + face
                + " ["
                + x0
                + ","
                + y0
                + ","
                + z0
                + " - "
                + x1
                + ","
                + y1
                + ","
                + z1
                + "]"
                + (outer ? " outer" : " stage " + stage);
        }
    }

    /** The whole block's six faces: what is drawn when nothing was cut, built once. */
    private static final List<Face> WHOLE = Collections.unmodifiableList(
        Arrays.asList(
            new Face(DOWN, 0f, 0f, 0f, 1f, 0f, 1f, true, -1),
            new Face(UP, 0f, 1f, 0f, 1f, 1f, 1f, true, -1),
            new Face(NORTH, 0f, 0f, 0f, 1f, 1f, 0f, true, -1),
            new Face(SOUTH, 0f, 0f, 1f, 1f, 1f, 1f, true, -1),
            new Face(WEST, 0f, 0f, 0f, 0f, 1f, 1f, true, -1),
            new Face(EAST, 1f, 0f, 0f, 1f, 1f, 1f, true, -1)));

    private final List<float[]> boxes = new ArrayList<float[]>();
    private final List<Integer> stages = new ArrayList<Integer>();
    private int refused;

    /**
     * Takes one box out of the block. False when it was refused: past {@link #MOST_BOXES}, or with no volume left
     * once clamped to the block.
     */
    public boolean cut(float x0, float y0, float z0, float x1, float y1, float z1, int stage) {
        if (boxes.size() >= MOST_BOXES) {
            refused++;
            return false;
        }
        float[] box = { clamp(Math.min(x0, x1)), clamp(Math.min(y0, y1)), clamp(Math.min(z0, z1)),
            clamp(Math.max(x0, x1)), clamp(Math.max(y0, y1)), clamp(Math.max(z0, z1)) };
        for (float each : box) if (Float.isNaN(each)) return false;
        if (box[3] <= box[0] || box[4] <= box[1] || box[5] <= box[2]) return false;
        boxes.add(box);
        stages.add(stage);
        return true;
    }

    /** How many boxes were cut. */
    public int count() {
        return boxes.size();
    }

    /** How many boxes were refused for being past {@link #MOST_BOXES}. */
    public int refused() {
        return refused;
    }

    private static float clamp(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return Float.NaN;
        // At or below nought rather than below it, so a negative zero is read as the block's own edge: the edges are
        // searched by Float.compare, which holds the two zeros apart.
        return value <= 0f ? 0f : value > 1f ? 1f : value;
    }

    /** A cell no box cut; any other value is one more than the index of the last box that cut it. */
    private static final byte SOLID = 0;

    /**
     * A face on the block's own surface, in the sweep's labels; a cut face is {@code OPENED} plus its stage's class.
     */
    private static final int NONE = 0, OUTER = 1, OPENED = 2;

    /** The faces of the block with every box cut out: the whole block's six faces when nothing was cut. */
    public List<Face> faces() {
        if (boxes.isEmpty()) return WHOLE;
        float[][] edges = { edges(0), edges(1), edges(2) };
        int nx = edges[0].length - 1, ny = edges[1].length - 1, nz = edges[2].length - 1;
        // Each box's stage class: the first box with the same stage, so faces of two boxes at one stage merge.
        int[] stageOf = new int[boxes.size()], stageClass = new int[boxes.size()];
        for (int b = 0; b < boxes.size(); b++) {
            stageOf[b] = stages.get(b);
            stageClass[b] = b;
            for (int earlier = 0; earlier < b; earlier++) {
                if (stageOf[earlier] == stageOf[b]) {
                    stageClass[b] = earlier;
                    break;
                }
            }
        }
        byte[] cut = new byte[nx * ny * nz];
        for (int b = 0; b < boxes.size(); b++) {
            float[] box = boxes.get(b);
            int ax = index(edges[0], box[0]), bx = index(edges[0], box[3]);
            int ay = index(edges[1], box[1]), by = index(edges[1], box[4]);
            int az = index(edges[2], box[2]), bz = index(edges[2], box[5]);
            byte mark = (byte) (b + 1);
            for (int i = ax; i < bx; i++) {
                for (int j = ay; j < by; j++) {
                    int row = (i * ny + j) * nz;
                    for (int k = az; k < bz; k++) cut[row + k] = mark;
                }
            }
        }
        List<Face> out = new ArrayList<Face>();
        int[] n = { nx, ny, nz };
        for (int face = DOWN; face <= EAST; face++) sweep(out, face, cut, n, edges, stageOf, stageClass);
        return Collections.unmodifiableList(out);
    }

    /** The axis a face is square to: y for down and up, z for north and south, x for west and east. */
    private static int axisOf(int face) {
        return face <= UP ? 1 : face <= SOUTH ? 2 : 0;
    }

    /**
     * Every face of one direction, plane by plane: each plane's labels laid out on its grid, then covered greedily by
     * rectangles of one label, each grown along its row as far as it goes and then down as far as the whole row
     * holds.
     */
    private static void sweep(List<Face> out, int face, byte[] cut, int[] n, float[][] edges, int[] stageOf,
        int[] stageClass) {
        int axis = axisOf(face);
        boolean positive = (face & 1) == 1;
        // The plane's two axes, in x y z order.
        int ua = axis == 0 ? 1 : 0, va = axis == 2 ? 1 : 2;
        int nu = n[ua], nv = n[va];
        int[] labels = new int[nu * nv];
        int[] cell = new int[3];
        for (int p = 0; p < n[axis]; p++) {
            boolean any = false;
            for (int u = 0; u < nu; u++) {
                for (int v = 0; v < nv; v++) {
                    cell[axis] = p;
                    cell[ua] = u;
                    cell[va] = v;
                    int label = NONE;
                    if (cut[(cell[0] * n[1] + cell[1]) * n[2] + cell[2]] == SOLID) {
                        cell[axis] = positive ? p + 1 : p - 1;
                        if (cell[axis] < 0 || cell[axis] >= n[axis]) label = OUTER;
                        else {
                            int there = cut[(cell[0] * n[1] + cell[1]) * n[2] + cell[2]];
                            if (there != SOLID) label = OPENED + stageClass[there - 1];
                        }
                    }
                    labels[u * nv + v] = label;
                    if (label != NONE) any = true;
                }
            }
            if (!any) continue;
            float plane = positive ? edges[axis][p + 1] : edges[axis][p];
            for (int u = 0; u < nu; u++) {
                for (int v = 0; v < nv; v++) {
                    int label = labels[u * nv + v];
                    if (label == NONE) continue;
                    int v1 = v + 1;
                    while (v1 < nv && labels[u * nv + v1] == label) v1++;
                    int u1 = u + 1;
                    grow: while (u1 < nu) {
                        for (int w = v; w < v1; w++) if (labels[u1 * nv + w] != label) break grow;
                        u1++;
                    }
                    for (int a = u; a < u1; a++) for (int w = v; w < v1; w++) labels[a * nv + w] = NONE;
                    float[] lo = new float[3], hi = new float[3];
                    lo[axis] = plane;
                    hi[axis] = plane;
                    lo[ua] = edges[ua][u];
                    hi[ua] = edges[ua][u1];
                    lo[va] = edges[va][v];
                    hi[va] = edges[va][v1];
                    boolean outer = label == OUTER;
                    out.add(
                        new Face(
                            face,
                            lo[0],
                            lo[1],
                            lo[2],
                            hi[0],
                            hi[1],
                            hi[2],
                            outer,
                            outer ? -1 : stageOf[label - OPENED]));
                }
            }
        }
    }

    /** Every distinct edge along one axis, the block's own two included, in order. */
    private float[] edges(int axis) {
        float[] all = new float[2 + boxes.size() * 2];
        all[0] = 0f;
        all[1] = 1f;
        int n = 2;
        for (float[] box : boxes) {
            all[n++] = box[axis];
            all[n++] = box[axis + 3];
        }
        Arrays.sort(all, 0, n);
        int distinct = 0;
        for (int i = 0; i < n; i++) if (distinct == 0 || all[i] != all[distinct - 1]) all[distinct++] = all[i];
        return Arrays.copyOf(all, distinct);
    }

    private static int index(float[] edges, float value) {
        return Arrays.binarySearch(edges, value);
    }
}
