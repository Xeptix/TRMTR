package com.trmtgtnh.client.texture;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * Values filed by a key object and a metadata, such as the wear set, the mended side or the side wall a block
 * state was given at a stitch.
 *
 * <p>
 * The wear tables used to be filed by the block's id and metadata packed into one number, and that number is not
 * the block. Forge renumbers modded blocks when a world is loaded or a server is joined, to the numbering that
 * world was saved under or that server sends, and puts the client's own back when the visit ends. A table filed at
 * a stitch kept the numbering in force as it was stitched and outlived every such move, so a world that numbered two
 * blocks the other way round from that stitch drew each one's worn ground with the other's pictures, and a stitch
 * made inside such a world carried its numbering into the next, where it lasted until the atlas was stitched again.
 * The block object is the one key every move leaves alone: the registry is emptied and refilled with the
 * very same objects under their new numbers. A registry name survives the move but not the middle of it, because
 * the name is read back out of that same registry while it stands empty, and it would cost a second lookup for
 * every face drawn besides.
 *
 * <p>
 * Filed by identity rather than by equality, because the object is the point: two keys that call themselves equal
 * are still two blocks. Each key holds a row of sixteen, one place for each value the four bits of a block's
 * metadata can take, so a lookup is one probe of an identity map and an index into the row. A block's identity
 * hash is already in its header, put there when the registry filed it in a map of the same kind, so the probe
 * costs nothing the first time either. Nothing is allocated, where the packed number boxed a fresh Integer for
 * every face of every block past the first eight, and nothing is locked.
 *
 * <p>
 * A filing is made by a {@link Builder}, which belongs to the one thread that fills it, and is never written once
 * built: {@link Builder#build} copies every row into a map of its own, so a filing can be published through a
 * single volatile field and read from any thread, and nothing filed afterwards reaches it. The first filing of a
 * state keeps its value, which is what the maps this replaces did by asking before they put, and the filings come
 * back in the order they were made, so a walk over them is the walk that made them.
 *
 * <p>
 * Numbers are still kept, but only to be told about. {@link #numbersNow} records the number each key had when the
 * filing was built, and {@link #audit} holds those to the numbering in force after a move, so that the log can say
 * which blocks were renumbered, which have no number on this side at all, and whether any number now leads
 * somewhere other than its own block. Nothing is ever looked up by them.
 *
 * <p>
 * It has nothing from the game in it: the key is whatever the caller files by, and the registry reaches it only
 * through a {@link Numbering} the caller supplies. So a test can move numbers about without a world, and it is on
 * the list CoreStaysPortableTest keeps free of Minecraft.
 */
public final class StateFiling<K, V> {

    /** The places in one key's row: every value four bits of metadata can take. */
    private static final int METAS = 16;

    private static final StateFiling<?, ?> EMPTY = new StateFiling<Object, Object>(
        new IdentityHashMap<Object, Object[]>(0),
        new ArrayList<Object>(0),
        new ArrayList<Object>(0),
        new int[0]);

    /** Handed each filed state in turn, in the order the states were filed. */
    public interface Visitor<K, V> {

        /** One filed state: its key, its metadata between nought and fifteen, and the value filed under them. */
        void visit(K key, int meta, V value);
    }

    /**
     * The numbers keys go by, as the caller's registry has them at the moment of asking.
     *
     * <p>
     * Asked both ways, because a move caught half done can leave the two directions disagreeing: a key with a
     * number that leads to some other key, or to nothing.
     */
    public interface Numbering<K> {

        /** The key's number, or minus one where it has none. */
        int idOf(K key);

        /** The key a number leads to, or null where it leads to nothing. */
        K keyAt(int id);
    }

    /** A key that carries a different number from the one it had when its filing was built. */
    public static final class Moved<K> {

        /** The key that was renumbered. */
        public final K key;

        /** The number it had when the filing was built. */
        public final int from;

        /** The number it carries now. */
        public final int to;

        Moved(K key, int from, int to) {
            this.key = key;
            this.from = from;
            this.to = to;
        }
    }

    /**
     * What the numbering in force says about a filing's keys, against the numbers they had when it was built.
     *
     * <p>
     * Each key is counted at most once, under the first of these that fits it: absent, where it has no number now;
     * misread, where its number leads to another object or to nothing; moved, where its number is not the one it
     * was built under. A key under none of them is where it was.
     */
    public static final class Audit<K> {

        /** How many distinct keys the filing holds. */
        public final int keys;

        /** How many keys carry a number other than the one they were built under, and still lead back to themselves. */
        public final int moved;

        /**
         * How many keys have no number at all. For a block that is either a mod the other side of the connection does
         * not have, or a block Forge has put a substitute in place of under its name; either way it cannot appear in
         * the world, so it is a fact about the world rather than a fault.
         */
        public final int absent;

        /** How many states the absent keys hold between them. */
        public final int absentStates;

        /**
         * How many keys have a number that leads to another object, or to nothing. The only finding that means
         * something is wrong: whatever remembers such a key by its number and reads the number back gets the wrong
         * object.
         */
        public final int misread;

        /** How many states the misread keys hold between them. */
        public final int misreadStates;

        /** The first of the moved keys, in the order they were first filed, no more than the limit asked for. */
        public final List<Moved<K>> movedExamples;

        /** The first of the misread keys, in the order they were first filed, no more than the limit asked for. */
        public final List<K> misreadExamples;

        Audit(int keys, int moved, int absent, int absentStates, int misread, int misreadStates,
            List<Moved<K>> movedExamples, List<K> misreadExamples) {
            this.keys = keys;
            this.moved = moved;
            this.absent = absent;
            this.absentStates = absentStates;
            this.misread = misread;
            this.misreadStates = misreadStates;
            this.movedExamples = movedExamples;
            this.misreadExamples = misreadExamples;
        }
    }

    private final IdentityHashMap<K, Object[]> rows;

    /** Every distinct key, in the order it was first filed. */
    private final List<K> keys;

    /** The key of every filing, in the order the filings were made, beside its metadata below. */
    private final List<K> filedKeys;

    /** The metadata of every filing, masked to four bits, one to one with the keys above. */
    private final int[] filedMetas;

    private StateFiling(IdentityHashMap<K, Object[]> rows, List<K> keys, List<K> filedKeys, int[] filedMetas) {
        this.rows = rows;
        this.keys = keys;
        this.filedKeys = filedKeys;
        this.filedMetas = filedMetas;
    }

    /** A filing with nothing in it, shared, since nothing can be filed into it. */
    @SuppressWarnings("unchecked")
    public static <K, V> StateFiling<K, V> empty() {
        return (StateFiling<K, V>) EMPTY;
    }

    /** A builder with nothing filed yet. */
    public static <K, V> Builder<K, V> builder() {
        return new Builder<K, V>();
    }

    /**
     * The value filed under this key and metadata, or null where there is none or the key is null.
     *
     * <p>
     * Only the low four bits of the metadata are read, as the world reads them, so seventeen finds one and minus
     * one finds fifteen. Read once for every face drawn, so it allocates nothing.
     */
    public V get(K key, int meta) {
        return read(rows, key, meta);
    }

    /** Whether anything is filed under this key and metadata. */
    public boolean has(K key, int meta) {
        return read(rows, key, meta) != null;
    }

    /** How many states are filed. */
    public int states() {
        return filedMetas.length;
    }

    /** How many distinct keys the states are filed under. */
    public int keys() {
        return keys.size();
    }

    /** Whether nothing at all is filed. */
    public boolean isEmpty() {
        return filedMetas.length == 0;
    }

    /** Hands the visitor every filed state, in exactly the order the states were filed. */
    public void forEach(Visitor<K, V> visitor) {
        visit(rows, filedKeys, filedMetas, filedMetas.length, visitor);
    }

    /**
     * The number each distinct key carries now, one to one with the keys in the order they were first filed, minus
     * one for a key with none. Taken when a filing is published, so that a later {@link #audit} has something to
     * hold the numbering to; a fresh array every time, which is the caller's to keep.
     */
    public int[] numbersNow(Numbering<K> numbering) {
        int[] numbers = new int[keys.size()];
        for (int i = 0; i < numbers.length; i++) {
            numbers[i] = numbering.idOf(keys.get(i));
        }
        return numbers;
    }

    /**
     * Holds every key to the numbering in force, against the numbers {@link #numbersNow} gave when the filing was
     * published.
     *
     * <p>
     * A key past the end of the numbers given, or given a negative number, or every key where no numbers are given
     * at all, is taken to have had none, and so can be found absent or misread but never moved. Numbers taken from
     * some other filing therefore cannot invent moves past their own length; within it they are believed, since
     * nothing here can tell whose they were.
     *
     * @param numbersThen  what {@link #numbersNow} returned for this filing, or null
     * @param numbering    the numbering in force now
     * @param exampleLimit the most examples kept of each kind; nought or below keeps none, and every count is kept
     *                     whatever the limit
     */
    public Audit<K> audit(int[] numbersThen, Numbering<K> numbering, int exampleLimit) {
        int limit = Math.max(0, exampleLimit);
        int moved = 0;
        int absent = 0;
        int absentStates = 0;
        int misread = 0;
        int misreadStates = 0;
        List<Moved<K>> movedExamples = new ArrayList<Moved<K>>();
        List<K> misreadExamples = new ArrayList<K>();
        for (int i = 0; i < keys.size(); i++) {
            K key = keys.get(i);
            int then = numbersThen != null && i < numbersThen.length ? numbersThen[i] : -1;
            int now = numbering.idOf(key);
            // Absent before misread: a key with no number has nothing to misread, and joining a server with fewer
            // mods is ordinary, so it must never be counted with the one finding that means a fault.
            if (now < 0) {
                absent++;
                absentStates += statesIn(rows.get(key));
            } else if (numbering.keyAt(now) != key) {
                // Misread before moved: a number that leads elsewhere has not merely changed, whatever it was.
                misread++;
                misreadStates += statesIn(rows.get(key));
                if (misreadExamples.size() < limit) misreadExamples.add(key);
            } else if (then >= 0 && then != now) {
                moved++;
                if (movedExamples.size() < limit) movedExamples.add(new Moved<K>(key, then, now));
            }
        }
        return new Audit<K>(
            keys.size(),
            moved,
            absent,
            absentStates,
            misread,
            misreadStates,
            Collections.unmodifiableList(movedExamples),
            Collections.unmodifiableList(misreadExamples));
    }

    /**
     * Gathers one filing on the thread that makes it.
     *
     * <p>
     * Not safe to share between threads, and never published: the stitch fills one, reads it back while it plans,
     * and publishes what {@link #build} makes of it.
     */
    public static final class Builder<K, V> {

        private final IdentityHashMap<K, Object[]> rows = new IdentityHashMap<K, Object[]>();

        /** Every distinct key, in the order it was first filed. */
        private final List<K> keys = new ArrayList<K>();

        /** The key of every filing, in the order the filings were made. */
        private final List<K> filedKeys = new ArrayList<K>();

        /** The metadata of every filing, masked to four bits; only the first {@link #states()} places are used. */
        private int[] filedMetas = new int[64];

        private int states;

        Builder() {}

        /**
         * Files a value under this key and the low four bits of this metadata.
         *
         * <p>
         * Refused, returning false, for a null key, a null value, or a state already filed, whose first value
         * stands. First rather than last, because every walk that fills one of these meets its states in the order
         * it prefers them, and the maps this replaces kept the first by asking before they put.
         */
        public boolean file(K key, int meta, V value) {
            if (key == null || value == null) return false;
            int at = meta & 0xF;
            Object[] row = rows.get(key);
            if (row == null) {
                row = new Object[METAS];
                rows.put(key, row);
                keys.add(key);
            } else if (row[at] != null) {
                return false;
            }
            row[at] = value;
            if (states == filedMetas.length) filedMetas = Arrays.copyOf(filedMetas, states * 2);
            filedKeys.add(key);
            filedMetas[states++] = at;
            return true;
        }

        /** The value filed so far under this key and metadata, or null; read as {@link StateFiling#get} reads. */
        public V get(K key, int meta) {
            return read(rows, key, meta);
        }

        /** Whether anything is filed so far under this key and metadata. */
        public boolean has(K key, int meta) {
            return read(rows, key, meta) != null;
        }

        /** How many states are filed so far. */
        public int states() {
            return states;
        }

        /** How many distinct keys they are filed under. */
        public int keys() {
            return keys.size();
        }

        /**
         * Hands the visitor every state filed so far, in exactly the order they were filed. A state the visitor
         * files into this same builder is not handed to it.
         */
        public void forEach(Visitor<K, V> visitor) {
            visit(rows, filedKeys, filedMetas, states, visitor);
        }

        /**
         * A filing of everything filed so far, sharing nothing with this builder: a fresh map, every row copied,
         * both orders copied. A row is copied rather than handed over because filing another metadata of a key
         * already filed writes into its row, which would otherwise reach a filing already published.
         */
        public StateFiling<K, V> build() {
            IdentityHashMap<K, Object[]> copied = new IdentityHashMap<K, Object[]>(keys.size());
            for (K key : keys) {
                copied.put(
                    key,
                    rows.get(key)
                        .clone());
            }
            return new StateFiling<K, V>(
                copied,
                new ArrayList<K>(keys),
                new ArrayList<K>(filedKeys),
                Arrays.copyOf(filedMetas, states));
        }
    }

    @SuppressWarnings("unchecked")
    private static <K, V> V read(IdentityHashMap<K, Object[]> rows, K key, int meta) {
        if (key == null) return null;
        Object[] row = rows.get(key);
        return row == null ? null : (V) row[meta & 0xF];
    }

    /**
     * The first count filings, in order. The count is taken before the first visit, so a visitor filing into the
     * builder being walked is not walked into what it files, and the array it grows is not the one being read.
     */
    @SuppressWarnings("unchecked")
    private static <K, V> void visit(IdentityHashMap<K, Object[]> rows, List<K> filedKeys, int[] filedMetas, int count,
        Visitor<K, V> visitor) {
        for (int i = 0; i < count; i++) {
            K key = filedKeys.get(i);
            int meta = filedMetas[i];
            visitor.visit(key, meta, (V) rows.get(key)[meta]);
        }
    }

    /** How many places of a row hold a value. */
    private static int statesIn(Object[] row) {
        if (row == null) return 0;
        int count = 0;
        for (Object value : row) {
            if (value != null) count++;
        }
        return count;
    }
}
