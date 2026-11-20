package com.trmtgtnh.dataload;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.trmtgtnh.data.DataKind;
import com.trmtgtnh.data.DataProblems;
import com.trmtgtnh.data.DataTable;

/**
 * What {@code /trmt data} says (W3 spec 2.3): the sources read and their counts, every refusal still standing, and,
 * for a named block, where each of its values came from ({@link DataTable#targets}).
 *
 * <p>
 * Free of the game, so the words are tested as a player reads them; the command colors each line by its
 * {@link Line.Tone}. The log's prefix ("TRMTR data: ") is left off the lines it repeats, because in chat the command's
 * own heading already says what they are about.
 */
public final class DataReport {

    private DataReport() {}

    /** The most refusals, conflicts or missing names listed in chat; the rest are in the log, each said once. */
    static final int MOST_LISTED = 12;

    /** One line of the answer. */
    public static final class Line {

        /** How a line is shown: a heading, a plain line, or one about something wrong. */
        public enum Tone {
            HEAD,
            PLAIN,
            WARN
        }

        public final Tone tone;
        public final String text;

        Line(Tone tone, String text) {
            this.tone = tone;
            this.text = text;
        }

        @Override
        public String toString() {
            return text;
        }
    }

    /**
     * {@code /trmt data}: the summary, each source with its files, every refusal and conflict of the last read, and the
     * names the files give that are not installed here.
     *
     * @param notInstalled the block and entity names the files give that this game does not have
     */
    public static List<Line> overview(DataFiles.Result read, List<String> notInstalled) {
        List<Line> out = new ArrayList<Line>();
        DataTable table = read.table;
        out.add(new Line(Line.Tone.HEAD, table.summary(notInstalled.size())));
        if (read.sources.isEmpty()) {
            out.add(
                new Line(
                    Line.Tone.PLAIN,
                    "No data files were found: no mod's jar or resource pack has an assets/<mod>/trmtr folder, and config/trmtr holds none."));
        } else {
            out.add(new Line(Line.Tone.HEAD, "Read from:"));
            for (Map.Entry<String, Integer> source : read.sources.entrySet()) {
                int n = source.getValue();
                out.add(new Line(Line.Tone.PLAIN, "  " + source.getKey() + ": " + n + (n == 1 ? " file" : " files")));
            }
        }
        List<DataProblems.Problem> problems = table.problems();
        if (!problems.isEmpty()) {
            out.add(new Line(Line.Tone.HEAD, "Refused or set aside (" + problems.size() + "), each also in the log:"));
            List<String> lines = new ArrayList<String>();
            for (DataProblems.Problem problem : problems) lines.add(unprefixed(problem.line()));
            listed(out, lines, Line.Tone.WARN);
        }
        if (!table.conflicts()
            .isEmpty()) {
            out.add(
                new Line(
                    Line.Tone.HEAD,
                    "Two sources of one layer disagree (" + table.conflicts()
                        .size() + "):"));
            List<String> lines = new ArrayList<String>();
            for (String conflict : table.conflicts()) lines.add(unprefixed(conflict));
            listed(out, lines, Line.Tone.WARN);
        }
        if (!notInstalled.isEmpty()) {
            out.add(
                new Line(
                    Line.Tone.HEAD,
                    "Named in the files but not installed in this game (" + notInstalled.size() + "):"));
            StringBuilder names = new StringBuilder("  ");
            int shown = Math.min(notInstalled.size(), MOST_LISTED);
            for (int i = 0; i < shown; i++) {
                if (i > 0) names.append(", ");
                names.append(notInstalled.get(i));
            }
            if (notInstalled.size() > shown) names.append(", and ")
                .append(notInstalled.size() - shown)
                .append(" more");
            out.add(new Line(Line.Tone.PLAIN, names.toString()));
        }
        out.add(
            new Line(
                Line.Tone.PLAIN,
                "Where a block's values came from: /trmt data <block>, such as /trmt data minecraft:dirt"));
        return out;
    }

    /**
     * {@code /trmt data <block>}: every selector the files give for this block (or entity), each of its kinds, and
     * each value with the source, file, entry and line that set it. A selector naming one metadata value of the block
     * is shown with the rest of the block's.
     */
    public static List<Line> block(DataFiles.Result read, String name) {
        List<Line> out = new ArrayList<Line>();
        String wanted = idOf(name.trim());
        boolean any = false;
        for (DataKind kind : DataKind.values()) {
            if (kind == DataKind.PLUGIN) continue;
            for (Map.Entry<String, DataTable.Facts> target : read.table.targets(kind)
                .entrySet()) {
                if (!idOf(target.getKey()).equals(wanted)) continue;
                any = true;
                out.add(new Line(Line.Tone.HEAD, target.getKey() + " (" + kind.folder + "):"));
                for (Map.Entry<String, DataTable.Fact> fact : target.getValue()
                    .all()
                    .entrySet()) {
                    out.add(new Line(Line.Tone.PLAIN, "  " + fact.getKey() + ": " + fact.getValue()));
                }
            }
        }
        if (!any) {
            out.add(
                new Line(
                    Line.Tone.PLAIN,
                    "No data file names " + name.trim()
                        + ", so every value it has is TRMTR's own: what detection found and what the settings say."));
        }
        return out;
    }

    /** A selector as written, down to the block or entity it names: no metadata, properties or shape qualifiers. */
    static String idOf(String target) {
        String id = target;
        int space = id.indexOf(' ');
        if (space >= 0) id = id.substring(0, space);
        int bracket = id.indexOf('[');
        if (bracket >= 0) id = id.substring(0, bracket);
        int first = id.indexOf(':');
        int second = first < 0 ? -1 : id.indexOf(':', first + 1);
        if (second >= 0) id = id.substring(0, second);
        return id;
    }

    private static String unprefixed(String line) {
        return line.startsWith(DataProblems.PREFIX) ? line.substring(DataProblems.PREFIX.length()) : line;
    }

    private static void listed(List<Line> out, List<String> lines, Line.Tone tone) {
        int shown = Math.min(lines.size(), MOST_LISTED);
        for (int i = 0; i < shown; i++) out.add(new Line(tone, "  " + lines.get(i)));
        if (lines.size() > shown) {
            out.add(new Line(Line.Tone.PLAIN, "  and " + (lines.size() - shown) + " more, in the log"));
        }
    }
}
