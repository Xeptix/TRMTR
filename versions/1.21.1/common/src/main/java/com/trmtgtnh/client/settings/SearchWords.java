package com.trmtgtnh.client.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * When a search matches a setting (0.9.228; spec M2.2 and M2.8).
 *
 * <p>
 * A query matches when every word of it is the start of some word in the fields searched - the setting's shown name,
 * its key, its tab's name, its summary, its comment - case aside. Only the start of a word: "ear" does not find
 * "wear", because a player typing three letters means the start of what they are after, and a match in the middle of
 * a word floods the list with settings that only happen to spell it. A field's words are cut at spaces and at anything
 * else that is not a letter or a digit ({@code .}, {@code _}, {@code :}, brackets, commas) and at a camelCase step, so
 * {@code wearDropChance} answers "drop". Lower case is taken in {@link Locale#ROOT}, so a Turkish game, whose own lower
 * case of "I" is a dotless i, still finds "Inner".
 */
public final class SearchWords {

    private SearchWords() {}

    /** A field's words, lower case, in order. */
    public static List<String> words(String text) {
        List<String> out = new ArrayList<String>();
        if (text == null) return out;
        for (int[] token : tokens(text)) {
            out.add(
                text.substring(token[0], token[1])
                    .toLowerCase(Locale.ROOT));
        }
        return out;
    }

    /** Where each word of a field starts and ends, as {@code {start, end}} into the text as given. */
    static List<int[]> tokens(String text) {
        List<int[]> out = new ArrayList<int[]>();
        int length = text.length();
        int start = -1;
        for (int at = 0; at < length; at++) {
            char one = text.charAt(at);
            if (!Character.isLetterOrDigit(one)) {
                if (start >= 0) out.add(new int[] { start, at });
                start = -1;
                continue;
            }
            if (start >= 0 && step(text, at)) {
                out.add(new int[] { start, at });
                start = at;
            }
            if (start < 0) start = at;
        }
        if (start >= 0) out.add(new int[] { start, length });
        return out;
    }

    /**
     * Whether a camelCase word starts at {@code at}: an upper case letter after a lower case letter or a digit
     * ({@code wearDrop}), or the last capital of a run that a lower case letter follows ({@code HTMLPage} is "html",
     * "page").
     */
    private static boolean step(String text, int at) {
        char one = text.charAt(at);
        if (!Character.isUpperCase(one)) return false;
        char before = text.charAt(at - 1);
        if (Character.isLowerCase(before) || Character.isDigit(before)) return true;
        return Character.isUpperCase(before) && at + 1 < text.length() && Character.isLowerCase(text.charAt(at + 1));
    }

    /**
     * A query's words, cut as a field's are (0.9.228): a key typed whole, {@code maxWearSprites}, is the words "max",
     * "wear", "sprites", each found as a field's are - cut at spaces only, it was one word no field held, and found
     * nothing.
     */
    static List<String> queryWords(String query) {
        return words(query);
    }

    /** Whether every word of the query starts some word of the fields. A blank query matches nothing. */
    public static boolean matches(String query, String... fields) {
        List<String> wanted = queryWords(query);
        if (wanted.isEmpty()) return false;
        List<List<String>> all = new ArrayList<List<String>>();
        for (String field : fields) all.add(words(field));
        for (String word : wanted) {
            if (!found(word, all)) return false;
        }
        return true;
    }

    private static boolean found(String word, List<List<String>> fields) {
        for (List<String> field : fields) {
            if (starts(word, field)) return true;
        }
        return false;
    }

    static boolean starts(String word, List<String> field) {
        for (String one : field) {
            if (one.startsWith(word)) return true;
        }
        return false;
    }

    /**
     * How well the query matches: for each of its words the weight of the best field it starts a word of, summed.
     * Nought
     * when any word matches nowhere, so a score above nought is a match.
     */
    public static int score(String query, String[] fields, int[] weights) {
        List<String> wanted = queryWords(query);
        if (wanted.isEmpty()) return 0;
        List<List<String>> all = new ArrayList<List<String>>();
        for (String field : fields) all.add(words(field));
        return score(wanted, all, weights);
    }

    /** The same score from words already cut, as {@link SettingsIndex} keeps them. */
    static int score(List<String> wanted, List<List<String>> fields, int[] weights) {
        if (wanted.isEmpty()) return 0;
        int total = 0;
        for (String word : wanted) {
            int best = 0;
            for (int i = 0; i < fields.size(); i++) {
                if (weights[i] > best && starts(word, fields.get(i))) best = weights[i];
            }
            if (best == 0) return 0;
            total += best;
        }
        return total;
    }

    /**
     * The parts of a field the query matched, as {@code {start, end}} into the field as given, in order and never
     * overlapping: the start of each word some query word starts, as long as that query word, which is what is drawn
     * gold.
     */
    public static List<int[]> spans(String query, String field) {
        List<int[]> out = new ArrayList<int[]>();
        List<String> wanted = queryWords(query);
        if (wanted.isEmpty() || field == null) return out;
        for (int[] token : tokens(field)) {
            String word = field.substring(token[0], token[1])
                .toLowerCase(Locale.ROOT);
            int longest = 0;
            for (String one : wanted) {
                if (word.startsWith(one) && one.length() > longest) longest = one.length();
            }
            if (longest > 0) out.add(new int[] { token[0], Math.min(token[1], token[0] + longest) });
        }
        return out;
    }
}
