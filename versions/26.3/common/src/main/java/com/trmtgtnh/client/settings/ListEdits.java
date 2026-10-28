package com.trmtgtnh.client.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * What can be done to a list as a whole: move a line (S7), copy and paste the lines (C4), and whether the list is short
 * enough to open in place (S1).
 *
 * <p>
 * Every operation returns a new list and leaves the one it was given alone, because the screen keeps the list it
 * opened with to undo back to, and a list changed in place would be the opening value changing under it.
 */
public final class ListEdits {

    /** The longest list edited in place on its tab; a longer one opens its own page (S1, Xep: "up to eight lines"). */
    public static final int FOLD_MOST = 8;

    /** The most lines a paste leaves a list holding (C4). */
    public static final int MOST_LINES = 1024;

    private ListEdits() {}

    /** Whether a list of this many lines folds open in place (S1): at most {@link #FOLD_MOST}. */
    public static boolean foldable(int lines) {
        return lines <= FOLD_MOST;
    }

    /**
     * The list with the line at {@code from} moved so that it stands at {@code to} (S7): Alt+Up is
     * {@code to = from - 1},
     * a drag places it at the index it was dropped at, counted in the list as it will be. An index outside the list
     * gives the list back unchanged.
     */
    public static List<String> move(List<String> lines, int from, int to) {
        List<String> out = new ArrayList<String>(lines);
        if (from < 0 || from >= out.size() || to < 0 || to >= out.size() || from == to) return out;
        String moved = out.remove(from);
        out.add(to, moved);
        return out;
    }

    /** Every line, one per line, for the clipboard (C4). */
    public static String copy(List<String> lines) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) out.append('\n');
            out.append(lines.get(i));
        }
        return out.toString();
    }

    /** What a paste did. */
    public static final class PasteResult {

        /** The list after the paste. */
        public final List<String> lines;

        /** How many lines were added - the count {@code paste.added.*} says, or {@code paste.none} for 0. */
        public final int added;

        /** How many lines of the clipboard were already in the list, and so not added again. */
        public final int alreadyThere;

        /** Whether lines were left out because the list reached {@link #MOST_LINES}. */
        public final boolean full;

        private PasteResult(List<String> lines, int added, int alreadyThere, boolean full) {
            this.lines = Collections.unmodifiableList(lines);
            this.added = added;
            this.alreadyThere = alreadyThere;
            this.full = full;
        }
    }

    /**
     * The list with the clipboard's lines added after its own (C4). The clipboard is split on line ends - any of
     * {@code \r\n}, {@code \r} and {@code \n} - or, where it holds one line, on commas, which is how a list is written
     * in a message. Each line is trimmed, blank ones dropped, and a line already in the list (or earlier in the same
     * paste) is not added again: the same text, once trimmed, and case counts, as the loot pools' names are matched.
     * A {@code *} is a line like any other. The list never grows past {@link #MOST_LINES}.
     */
    public static PasteResult paste(List<String> into, String clipboard) {
        List<String> out = new ArrayList<String>(into);
        Set<String> held = new HashSet<String>();
        for (String line : into) {
            if (line != null) held.add(line.trim());
        }
        if (clipboard == null) return new PasteResult(out, 0, 0, false);

        String[] pieces = clipboard.split("\r\n|\r|\n", -1);
        int written = 0;
        for (String piece : pieces) {
            if (!piece.trim()
                .isEmpty()) written++;
        }
        if (written == 1) pieces = clipboard.split(",", -1);

        int added = 0;
        int alreadyThere = 0;
        boolean full = false;
        for (String piece : pieces) {
            String line = piece.trim();
            if (line.isEmpty()) continue;
            if (held.contains(line)) {
                alreadyThere++;
                continue;
            }
            if (out.size() >= MOST_LINES) {
                full = true;
                break;
            }
            out.add(line);
            held.add(line);
            added++;
        }
        return new PasteResult(out, added, alreadyThere, full);
    }
}
