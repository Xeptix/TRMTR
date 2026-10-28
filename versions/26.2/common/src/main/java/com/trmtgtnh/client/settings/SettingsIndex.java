package com.trmtgtnh.client.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Every setting the screen can show, searched (0.9.228; spec M2).
 *
 * <p>
 * Built once each time the screen opens, from the file's settings, each with its words already cut, so a keystroke in
 * the search field costs a pass over a few hundred short lists - the test holds a search of 460 under 2 ms. A result
 * is scored by where its words matched: the shown name 100, the key 80, the tab's name 60, the summary 40, the
 * comment 10, each query word taking its best, summed. Results come best first, and among equals in the order the
 * screen shows them, so a search reads like the tabs it came from.
 */
public final class SettingsIndex {

    /** Where a query word matched, as {@link SearchWords#score} weighs it. */
    public static final int NAME = 100;

    public static final int KEY = 80;

    public static final int TAB = 60;

    public static final int SUMMARY = 40;

    public static final int COMMENT = 10;

    private static final int[] WEIGHTS = { NAME, KEY, TAB, SUMMARY, COMMENT };

    /** One setting as the search sees it. */
    public static final class Entry {

        /** The tab's name as the kicker shows it: "General", or "Surfaces - Grass" for a family's setting. */
        public final String tab;

        public final String category;

        public final String key;

        /** Its plain title, as its row shows it. */
        public final String shown;

        public final String summary;

        public final String comment;

        /** Its place in screen order, which breaks a tie. */
        public final int order;

        final List<List<String>> words;

        public Entry(String tab, String category, String key, String shown, String comment, int order) {
            this(tab, category, key, shown, "", comment, order);
        }

        public Entry(String tab, String category, String key, String shown, String summary, String comment, int order) {
            this.tab = tab;
            this.category = category;
            this.key = key;
            this.shown = shown;
            this.summary = summary;
            this.comment = comment;
            this.order = order;
            this.words = new ArrayList<List<String>>();
            this.words.add(SearchWords.words(shown));
            this.words.add(SearchWords.words(key));
            this.words.add(SearchWords.words(tab));
            this.words.add(SearchWords.words(summary));
            this.words.add(SearchWords.words(comment));
        }

        /** {@code category.key}. */
        public String id() {
            return category + "." + key;
        }
    }

    /** One result: the setting, its score, and the parts of its shown name to draw gold. */
    public static final class Hit {

        public final Entry entry;

        public final int score;

        public final List<int[]> nameSpans;

        Hit(Entry entry, int score, List<int[]> nameSpans) {
            this.entry = entry;
            this.score = score;
            this.nameSpans = nameSpans;
        }
    }

    private final List<Entry> entries;

    public SettingsIndex(List<Entry> entries) {
        this.entries = new ArrayList<Entry>(entries);
    }

    public int size() {
        return entries.size();
    }

    /** Every setting the query matches, best first. A blank query finds nothing: the screen shows its tab instead. */
    public List<Hit> find(String query) {
        List<Hit> hits = new ArrayList<Hit>();
        List<String> wanted = SearchWords.queryWords(query);
        if (wanted.isEmpty()) return hits;
        for (Entry entry : entries) {
            int score = SearchWords.score(wanted, entry.words, WEIGHTS);
            if (score > 0) hits.add(new Hit(entry, score, SearchWords.spans(query, entry.shown)));
        }
        Collections.sort(hits, new Comparator<Hit>() {

            @Override
            public int compare(Hit one, Hit other) {
                if (one.score != other.score) return one.score > other.score ? -1 : 1;
                return one.entry.order < other.entry.order ? -1 : one.entry.order == other.entry.order ? 0 : 1;
            }
        });
        return hits;
    }
}
