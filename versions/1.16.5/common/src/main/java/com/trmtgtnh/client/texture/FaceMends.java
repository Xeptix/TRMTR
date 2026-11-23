package com.trmtgtnh.client.texture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The mended sides of a lawn's flanks that draw a picture other than its north flank's (1.0.1 W2b; Codex's review of
 * W2, 2026-11-21).
 *
 * <p>
 * A state's mend is made from face 2, the north flank, and was drawn on all four flanks. A block may draw a different
 * picture on each flank, so a hole in face 4 alone went unfilled, or face 2's picture was drawn where face 4's belongs.
 * So every flank is read: a flank whose picture is face 2's takes the state's own mend, as before; a flank whose
 * picture is not takes a mend made from its own picture where that one wants one, and its own picture, untouched,
 * where it does not. Face 2's mend stays where it always was, in each edition's own filing, so a block whose four
 * flanks agree, which is every lawn read so far, is planned, priced and drawn exactly as before.
 *
 * <p>
 * The same class in all eight trees, keyed by whatever each edition files a state under. Faces are numbered as the
 * game numbers them: 2 north, 3 south, 4 west, 5 east.
 *
 * @param <K> the key a state is filed under, a block
 * @param <S> what each face holds: a sprite, or whether one was wanted
 */
public final class FaceMends<K, S> {

    /** The first flank that may hold a mend of its own, and the last flank. */
    static final int FIRST_OWN = 3, LAST = 5;

    /** Per state, bit {@code 1 << face} set for each of faces 3 to 5 whose picture is not face 2's; none for most. */
    private final StateFiling<K, Integer> own;

    /** Faces 3, 4 and 5, each the mends of the states whose picture there is its own. */
    private final List<StateFiling<K, S>> faces;

    public FaceMends(StateFiling<K, Integer> own, List<StateFiling<K, S>> faces) {
        if (faces.size() != LAST - FIRST_OWN + 1) throw new IllegalArgumentException("one filing per face 3 to 5");
        this.own = own;
        this.faces = Collections.unmodifiableList(new ArrayList<StateFiling<K, S>>(faces));
    }

    /** Nothing differs anywhere: every flank takes the state's own mend. */
    public static <K, S> FaceMends<K, S> none() {
        List<StateFiling<K, S>> empty = new ArrayList<StateFiling<K, S>>();
        for (int face = FIRST_OWN; face <= LAST; face++) empty.add(StateFiling.<K, S>empty());
        return new FaceMends<K, S>(StateFiling.<K, Integer>empty(), empty);
    }

    /**
     * What a face draws: the mend of its own picture where that is not face 2's, which may be none, and {@code main},
     * the state's own mend, made from face 2, on every other face.
     */
    public S pick(K key, int meta, int side, S main) {
        if (side < FIRST_OWN || side > LAST) return main;
        Integer bits = own.get(key, meta);
        if (bits == null || (bits.intValue() & (1 << side)) == 0) return main;
        return faces.get(side - FIRST_OWN)
            .get(key, meta);
    }

    /** The mends of one face, 3 to 5, for a stitch to resolve or report. */
    public StateFiling<K, S> face(int side) {
        return faces.get(side - FIRST_OWN);
    }

    /** Which states draw a picture of their own on some face, for a stitch to carry over unchanged. */
    public StateFiling<K, Integer> own() {
        return own;
    }

    /** How many mends the faces of their own hold, across the three. */
    public int states() {
        int count = 0;
        for (StateFiling<K, S> each : faces) count += each.states();
        return count;
    }

    /**
     * Which of faces 3 to 5 draw a picture other than face 2's, as bits {@code 1 << face}: {@code names} is faces 2 to
     * 5 in order. A face whose name could not be read is taken to draw face 2's, as every face was before; so is every
     * face where face 2's could not be.
     */
    public static int ownFaces(String[] names) {
        if (names == null || names.length < LAST - 1 || names[0] == null) return 0;
        int bits = 0;
        for (int face = FIRST_OWN; face <= LAST; face++) {
            String name = names[face - 2];
            if (name != null && !name.equals(names[0])) bits |= 1 << face;
        }
        return bits;
    }

    /** Whether a picture of this name wants a mend: one with any pixel less than opaque. */
    public interface Holes {

        boolean holed(String name);
    }

    /**
     * What one stitch wants mended, built while the states are walked and before anything is priced: face 2's mends in
     * {@link #main}, which is each edition's own wanted filing, and the faces of their own beside it.
     */
    public static final class Wanted<K> {

        /** The states whose own mend, made from face 2, is wanted. */
        public final StateFiling.Builder<K, Boolean> main = StateFiling.<K, Boolean>builder();

        private final StateFiling.Builder<K, Integer> own = StateFiling.<K, Integer>builder();

        private final List<StateFiling.Builder<K, Boolean>> faces = new ArrayList<StateFiling.Builder<K, Boolean>>();

        public Wanted() {
            for (int face = FIRST_OWN; face <= LAST; face++) faces.add(StateFiling.<K, Boolean>builder());
        }

        /**
         * One state: {@code names} its flanks' pictures, faces 2 to 5; {@code drawsItself} whether its block draws
         * itself and so wants a mend on every flank whatever its pictures hold; {@code holes} asked of each picture
         * at most once here, and, through the {@link SideHoles} each walk holds, once per stitch.
         */
        public void want(K key, int meta, String[] names, boolean drawsItself, Holes holes) {
            if (key == null || names == null || own.has(key, meta) || main.has(key, meta)) return;
            int bits = ownFaces(names);
            if (bits != 0) own.file(key, meta, Integer.valueOf(bits));
            if (drawsItself || holes.holed(names[0])) main.file(key, meta, Boolean.TRUE);
            for (int face = FIRST_OWN; face <= LAST; face++) {
                if ((bits & (1 << face)) == 0) continue;
                if (drawsItself || holes.holed(names[face - 2])) {
                    faces.get(face - FIRST_OWN)
                        .file(key, meta, Boolean.TRUE);
                }
            }
        }

        /** The states that want a mend of their own on this face, 3 to 5. */
        public StateFiling.Builder<K, Boolean> face(int side) {
            return faces.get(side - FIRST_OWN);
        }

        /** Every mend wanted: face 2's and the faces of their own. */
        public int states() {
            int count = main.states();
            for (StateFiling.Builder<K, Boolean> each : faces) count += each.states();
            return count;
        }

        /** Which states draw a picture of their own on some face. */
        public StateFiling<K, Integer> own() {
            return own.build();
        }

        /** The faces of their own, as wanted, for a flank to ask whether it wanted a mend it has not got. */
        public FaceMends<K, Boolean> built() {
            List<StateFiling<K, Boolean>> out = new ArrayList<StateFiling<K, Boolean>>();
            for (StateFiling.Builder<K, Boolean> each : faces) out.add(each.build());
            return new FaceMends<K, Boolean>(own.build(), out);
        }
    }
}
