package com.trmtgtnh.erosion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wear travels with the block a piston moves (Xep, 2026-11-08: "Wear travels").
 *
 * <p>
 * A record is kept by position, so a block pushed or pulled off its square used to land with no record and leave its
 * own on the square it left. Here a move reads every square it touches before it changes any, so a row is carried
 * whole whatever order the game moves it in, and then sets each record down where its block lands. A record already
 * on a landing square goes, as the block standing there does; a square the move leaves, or whose block the piston
 * destroys, or that the piston's head moves into, ends with no record. Reinforcement, a ward, a pin and a glow are
 * the record's, so they travel with it.
 *
 * <p>
 * Nothing here knows the game: each edition reads its own piston, hands over the squares, and tells its clients and
 * listeners of the changes this returns. The same file in all eight editions.
 */
public final class WearTravels {

    private WearTravels() {}

    /** Where an edition keeps its records, a square at a time. A square is anything with equals and hashCode. */
    public interface Squares<P> {

        /** The record on a square, or null where there is none. */
        ErosionEntry get(P at);

        /** Sets the record on a square, or takes it off when null. */
        void set(P at, ErosionEntry entry);
    }

    /** One square whose record the move changed: what it held before and what it holds now, either null. */
    public static final class Change<P> {

        public final P at;
        public final ErosionEntry before;
        public final ErosionEntry after;

        Change(P at, ErosionEntry before, ErosionEntry after) {
            this.at = at;
            this.before = before;
            this.after = after;
        }

        @Override
        public String toString() {
            return at + ": " + (before == null ? "none" : "record") + " to " + (after == null ? "none" : "record");
        }
    }

    /**
     * Moves the records of one piston move.
     *
     * @param from    the squares the moved blocks leave
     * @param to      where each lands, in the same order: the block from {@code from.get(i)} lands on {@code to.get(i)}
     * @param emptied squares the move leaves with no record of their own: those whose blocks the piston destroys, and
     *                the one its head moves into
     * @return every square whose record changed, each once, in the order first touched
     */
    public static <P> List<Change<P>> carry(Squares<P> squares, List<P> from, List<P> to, List<P> emptied) {
        if (from.size() != to.size()) {
            throw new IllegalArgumentException(from.size() + " blocks moved and " + to.size() + " landings named");
        }
        // Everything read before anything is written, so no record is set down over one not yet picked up.
        Map<P, ErosionEntry> before = new LinkedHashMap<P, ErosionEntry>();
        read(squares, before, emptied);
        read(squares, before, from);
        read(squares, before, to);

        // Every square touched ends empty unless a block lands on it with a record.
        Map<P, ErosionEntry> after = new LinkedHashMap<P, ErosionEntry>();
        for (P at : before.keySet()) after.put(at, null);
        for (int i = 0; i < to.size(); i++) after.put(to.get(i), before.get(from.get(i)));

        List<Change<P>> changes = new ArrayList<Change<P>>();
        for (Map.Entry<P, ErosionEntry> each : after.entrySet()) {
            ErosionEntry was = before.get(each.getKey());
            ErosionEntry now = each.getValue();
            if (was == now) continue;
            squares.set(each.getKey(), now);
            changes.add(new Change<P>(each.getKey(), was, now));
        }
        return changes;
    }

    private static <P> void read(Squares<P> squares, Map<P, ErosionEntry> into, List<P> at) {
        for (P each : at) {
            if (!into.containsKey(each)) into.put(each, squares.get(each));
        }
    }
}
