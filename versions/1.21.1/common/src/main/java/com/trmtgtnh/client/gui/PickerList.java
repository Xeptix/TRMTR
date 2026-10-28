package com.trmtgtnh.client.gui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

import com.trmtgtnh.client.settings.ListLine;
import com.trmtgtnh.client.settings.SearchWords;
import com.trmtgtnh.client.settings.Slicer;

/**
 * What a picker offers, decided without the game (spec M4, M5, Part 6): the entries of one kind, how they are sorted
 * and searched, which lines a block with variants adds, which are already in the list, and what a press adds.
 * {@link WornPathPicker} draws it and {@link Suggestions} completes from it; {@code SettingsPictures} gathers the
 * game's own entries into it, a slice a frame. Plain Java, so its rules are held by a test with stand-in entries.
 */
public final class PickerList {

    private PickerList() {}

    /** What a picker lists. */
    public enum Kind {
        /** Every block and its variants, as {@code modid:block:meta}. */
        BLOCK,
        /** Every block by its registry name alone, for a list that takes no metadata ({@code surfaces.exclude}). */
        BLOCK_NO_META,
        /** Every item and its variants. */
        ITEM,
        /** Every fluid, as {@code fluid:<name>}. */
        FLUID,
        /** Every enchantment, by the fragment of its own name the game matches. */
        ENCHANTMENT,
        /** Every mob the mob list can name. */
        MOB,
        /** Every sprite on the block atlas ({@code innerLayerTextures}' second half). */
        SPRITE,
        /** Every dimension the game knows. */
        DIMENSION,
        /** Every ore dictionary group ({@code tamperGrades}' ore). */
        ORE,
        /** The chest-loot pools the game names ({@code loot.wayfarerCategories}). */
        POOL
    }

    /** One thing a picker offers. */
    static final class Entry {

        /** The line a press adds. */
        final String line;

        /** The line the second button adds, every variant ({@code modid:block:*}); null where none is offered. */
        final String every;

        /** The thing's own display name. */
        final String name;

        /** What is written, shown grey under the name. */
        final String id;

        /** The picture, a ref the edition's canvas draws; null for none. */
        final Object picture;

        /** The words searched (M2.8): the name's and the id's, cut once as {@link SearchWords} cuts them. */
        final List<String> words;

        /**
         * The name lower case, made once (0.9.228): the order lowered both names at every comparison, and a step's sort
         * and merge in GT New Horizons' block list took up to 12.7 ms past its slice.
         */
        final String sortName;

        Entry(String line, String every, String name, String id, Object picture) {
            this.line = line;
            this.every = every;
            this.name = name == null || name.isEmpty() ? id : name;
            this.id = id;
            this.picture = picture;
            this.sortName = this.name == null ? "" : this.name.toLowerCase(Locale.ROOT);
            List<String> cut = new ArrayList<String>(SearchWords.words(this.name));
            cut.addAll(SearchWords.words(id));
            this.words = Collections.unmodifiableList(cut);
        }

        @Override
        public String toString() {
            return line;
        }
    }

    /** By display name, case aside, in {@link Locale#ROOT}; then by what is written (M4.2). */
    static final Comparator<Entry> ORDER = new Comparator<Entry>() {

        @Override
        public int compare(Entry one, Entry other) {
            int byName = one.sortName.compareTo(other.sortName);
            return byName != 0 ? byName : one.line.compareTo(other.line);
        }
    };

    /**
     * Where a list's scroll bar puts the list when it is pressed or dragged at {@code y} (0.9.228; Xep, from a picker
     * in GT New Horizons: a press on its scroll bar chose the row under the pointer, and only the wheel scrolled): the
     * thumb's middle under the pointer, the list clamped to its ends.
     */
    static int barScroll(int y, int trackTop, int trackHeight, int content, int view) {
        int size = Math.max(16, view * view / Math.max(1, content));
        int travel = Math.max(1, trackHeight - size);
        int most = Math.max(0, content - view);
        return Math.max(0, Math.min(most, (y - trackTop - size / 2) * most / travel));
    }

    /** A block's variant: its metadata, its own name and its picture. */
    static final class Variant {

        final int meta;

        final String name;

        final Object picture;

        Variant(int meta, String name, Object picture) {
            this.meta = meta;
            this.name = name;
            this.picture = picture;
        }
    }

    /**
     * A block's entries (M4.2, M4.3). A block with more than one variant offers each as {@code modid:block:meta} -
     * metadata nought written as {@code :0}, because {@code modid:block} with no metadata means every metadata to the
     * block lists, and picking white wool would otherwise add all sixteen - and offers every variant ({@code :*}) as
     * the second button. A block with one variant, or any block on a list that takes no metadata, is its name alone,
     * with no second button.
     */
    static List<Entry> forBlock(String id, List<Variant> variants, boolean takesMeta) {
        List<Entry> out = new ArrayList<Entry>();
        if (id == null || variants == null || variants.isEmpty()) return out;
        if (!takesMeta || variants.size() == 1) {
            Variant first = variants.get(0);
            out.add(new Entry(id, null, first.name, id, first.picture));
            return out;
        }
        String every = ListLine.block(id, ListLine.WILDCARD);
        for (Variant variant : variants) {
            String line = ListLine.block(id, variant.meta);
            out.add(new Entry(line, every, variant.name, line, variant.picture));
        }
        return out;
    }

    /** The words of a query, cut as a field is cut, so {@code minecraft:st} looks for "minecraft" and "st". */
    static List<String> queryWords(String query) {
        return SearchWords.words(query);
    }

    /** Whether every word of the query starts some word of the entry's name or id (M2.8). Empty matches all. */
    static boolean matches(Entry entry, List<String> query) {
        for (String word : query) {
            boolean found = false;
            for (String one : entry.words) {
                if (one.startsWith(word)) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }

    /** The entries a query finds, in their order. */
    static List<Entry> filter(List<Entry> all, String query) {
        List<String> words = queryWords(query);
        if (words.isEmpty()) return new ArrayList<Entry>(all);
        List<Entry> out = new ArrayList<Entry>();
        for (Entry entry : all) {
            if (matches(entry, words)) out.add(entry);
        }
        return out;
    }

    /** A line as compared with the list's own: trimmed, case aside, as the game matches a registry name. */
    static String held(String line) {
        return line == null ? ""
            : line.trim()
                .toLowerCase(Locale.ROOT);
    }

    /**
     * The game's entries of one kind, gathered a slice at a time (Part 6, M4.5): each source - a block, an item - is
     * made into its entries in a try of its own, as many as a frame's slice has time for, and what is gathered so far
     * is kept sorted, so a search works over it while the rest comes in.
     */
    static final class Gathering {

        private final Iterator<?> sources;

        private final Function<Object, List<Entry>> make;

        /** How many sources there are, for {@code pick.gathering}; 0 where it is not known. */
        final int total;

        private int done;

        private boolean finished;

        private List<Entry> entries = new ArrayList<Entry>();

        /** A step's entries, sorted, waiting to be merged in the next slice. */
        private List<Entry> pending = new ArrayList<Entry>();

        /** Moves on whenever entries are added, so a filter knows to look again. */
        private int version;

        private int failures;

        /** The source that took longest to gather, its time and what it gave, for the settingsperf part (0.9.228). */
        Object slowest;

        long slowestNanos;

        int slowestGave;

        /** The longest a merge took, inside its slice, for the same line. */
        long mergeNanos;

        Gathering(Collection<?> sources, Function<Object, List<Entry>> make) {
            this.sources = new ArrayList<Object>(sources).iterator();
            this.total = sources.size();
            this.make = make;
        }

        /**
         * Gathers for as long as the frame's slice has time - the caller starts the slice, so every gathering open at
         * once shares one - then merges what came in.
         */
        boolean step(Slicer slicer) {
            if (finished()) return false;
            // The last step's entries merged first, inside this slice (0.9.228): merged after the slice ran out, a
            // merge with twenty thousand gathered took a GT New Horizons frame past its budget.
            if (!pending.isEmpty() && slicer.more()) {
                long merging = System.nanoTime();
                entries = merge(entries, pending);
                pending = new ArrayList<Entry>();
                version++;
                mergeNanos = Math.max(mergeNanos, System.nanoTime() - merging);
            }
            if (finished) return true;
            List<Entry> fresh = new ArrayList<Entry>();
            while (slicer.more()) {
                if (!sources.hasNext()) {
                    finished = true;
                    break;
                }
                Object source = sources.next();
                done++;
                long started = System.nanoTime();
                int gave = 0;
                try {
                    List<Entry> made = make.apply(source);
                    if (made != null) fresh.addAll(made);
                    gave = made == null ? 0 : made.size();
                } catch (Throwable hostile) {
                    // A mod's block or item that throws when asked its variants offers nothing, and the rest go on.
                    failures++;
                }
                long took = System.nanoTime() - started;
                if (took > slowestNanos) {
                    slowestNanos = took;
                    slowest = source;
                    slowestGave = gave;
                }
            }
            if (!sources.hasNext()) finished = true;
            if (fresh.isEmpty()) return true;
            // Sorted now, which is this step's own few; merged with everything gathered in the next slice.
            Collections.sort(fresh, ORDER);
            pending = merge(pending, fresh);
            return true;
        }

        /** Two sorted lists as one, in a single pass, so a frame's slice never sorts everything gathered. */
        private static List<Entry> merge(List<Entry> held, List<Entry> fresh) {
            List<Entry> out = new ArrayList<Entry>(held.size() + fresh.size());
            int i = 0;
            int j = 0;
            while (i < held.size() && j < fresh.size()) {
                if (ORDER.compare(held.get(i), fresh.get(j)) <= 0) out.add(held.get(i++));
                else out.add(fresh.get(j++));
            }
            while (i < held.size()) out.add(held.get(i++));
            while (j < fresh.size()) out.add(fresh.get(j++));
            return out;
        }

        /** Every source asked and every entry merged. */
        boolean finished() {
            return finished && pending.isEmpty();
        }

        int done() {
            return done;
        }

        int failures() {
            return failures;
        }

        int version() {
            return version;
        }

        List<Entry> entries() {
            return entries;
        }
    }

    /** A picker's state: its entries, the search, and the lines the list already holds (M4.3, M4.4). */
    static final class Model {

        final Kind kind;

        private final Gathering source;

        private final Set<String> held = new HashSet<String>();

        private String query = "";

        private List<Entry> shown = new ArrayList<Entry>();

        private int seen = -1;

        private String seenQuery;

        /** The line added last, and when, for the gold tick a Shift-click leaves for a second (M4.3). */
        private String ticked;

        private long tickedAt;

        Model(Kind kind, Gathering source, Collection<String> already) {
            this.kind = kind;
            this.source = source;
            if (already != null) {
                for (String line : already) held.add(held(line));
            }
        }

        void query(String text) {
            query = text == null ? "" : text;
        }

        String query() {
            return query;
        }

        /** The entries the search finds among what is gathered so far, looked for again only when either moved. */
        List<Entry> shown() {
            if (source.version() != seen || !query.equals(seenQuery)) {
                shown = filter(source.entries(), query);
                seen = source.version();
                seenQuery = query;
            }
            return shown;
        }

        /** Whether the list already holds the line this press would add (M4.4): drawn grey, and adds nothing. */
        boolean holds(Entry entry, boolean every) {
            String line = every ? entry.every : entry.line;
            return line != null && held.contains(held(line));
        }

        /** Whether the second button, every variant, is offered for this entry: only where the list takes metadata. */
        boolean offersEvery(Entry entry) {
            return entry.every != null && kind != Kind.BLOCK_NO_META;
        }

        /**
         * A press on an entry, or on its every-variant button: the line it adds, or null where the list holds it
         * already. The picker closes after a plain press and stays open after a Shift-press ({@link #closes}).
         */
        String press(Entry entry, boolean every, long now) {
            if (every && !offersEvery(entry)) return null;
            if (holds(entry, every)) return null;
            String line = every ? entry.every : entry.line;
            held.add(held(line));
            ticked = line;
            tickedAt = now;
            return line;
        }

        /**
         * Whether a press closes the picker: a plain one does, a Shift-press keeps it open (M4.3, {@code pick.shift}).
         */
        static boolean closes(boolean shift) {
            return !shift;
        }

        /** Whether this entry carries the gold tick of a line just added, for a second. */
        boolean ticked(Entry entry, long now) {
            return ticked != null && now - tickedAt < 1000L
                && (ticked.equals(entry.line) || ticked.equals(entry.every));
        }

        Gathering source() {
            return source;
        }
    }
}
