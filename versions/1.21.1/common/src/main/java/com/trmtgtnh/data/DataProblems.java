package com.trmtgtnh.data;

import java.util.HashSet;
import java.util.Set;

/**
 * The said-once log of data problems (W3 spec 2.3).
 *
 * <p>
 * Each (source, file, problem) is said once a session, at WARN, naming the file, where it came from and whom to tell -
 * "TRMTR data: refused assets/example/trmtr/families/loam.json in example-1.0.jar (mod example) - line 4, column 17:
 * expected ',' - report this to that mod". Once, because the same files are read again on every {@code /trmt reload}
 * (and, from 1.16.5, every data-pack reload), and a log that repeated a broken file's line at each would bury
 * everything else. The set it remembers by lives as long as this object, which the adapter keeps for the session.
 *
 * <p>
 * Never through {@code Trmt.error}. That line ends by sending the player to TRMTR's issue tracker, which is wrong for
 * another mod's file: the line here names that mod instead. The adapter's {@link Sink} writes to the mod's logger at
 * WARN, INFO and DEBUG, and nothing in this package logs at ERROR ({@code ErrorsSayWhereToReportTest}'s one named
 * exception is the per-tree {@code DataErrorsBlameTheirSourceTest}).
 *
 * <p>
 * Portable: the same file in all eight trees.
 */
public final class DataProblems {

    /** Where the lines go: the adapter's logger. */
    public interface Sink {

        void warn(String line);

        void info(String line);

        void debug(String line);
    }

    /** A sink that keeps nothing, for a read that is only being counted. */
    public static final Sink QUIET = new Sink() {

        @Override
        public void warn(String line) {}

        @Override
        public void info(String line) {}

        @Override
        public void debug(String line) {}
    };

    /** How many distinct lines one session says before it only counts, so no loop of reloads grows the set for ever. */
    public static final int MOST_SAID = 4096;

    /** Every line begins so, so a player searching the log finds them all. */
    public static final String PREFIX = "TRMTR data: ";

    /** One problem with one file. */
    public static final class Problem {

        /** What became of the file or the part of it. */
        public enum Outcome {

            /** The whole file was refused. */
            FILE_REFUSED("refused"),
            /** One entry was refused; the rest of the file kept. */
            ENTRY_REFUSED("refused"),
            /** A key or a folder was ignored; the rest kept. */
            IGNORED("ignored"),
            /** The whole file was skipped, without fault of its own (it needs a newer TRMTR). */
            SKIPPED("skipped");

            public final String verb;

            Outcome(String verb) {
                this.verb = verb;
            }
        }

        public final Outcome outcome;
        public final DataSource source;
        public final String path;
        /** The entry's number, from 1, for an entry or a key inside one; 0 for the file. */
        public final int entry;
        /** The line and column, or 0 when the problem has no one place. */
        public final int line;
        public final int column;
        public final String message;
        /** The ending: whom to tell or what to do. */
        public final String action;

        Problem(Outcome outcome, DataSource source, String path, int entry, int line, int column, String message,
            String action) {
            this.outcome = outcome;
            this.source = source;
            this.path = path;
            this.entry = entry;
            this.line = line;
            this.column = column;
            this.message = message;
            this.action = action;
        }

        /** The whole line, as it is logged. */
        public String line() {
            StringBuilder out = new StringBuilder(PREFIX).append(outcome.verb)
                .append(' ');
            if (outcome == Outcome.ENTRY_REFUSED) out.append("entry ")
                .append(entry)
                .append(" of ");
            else if (entry > 0) out.append("in entry ")
                .append(entry)
                .append(" of ");
            out.append(path)
                .append(' ')
                .append(source.where())
                .append(" - ");
            if (line > 0) out.append(JsonValue.where(line, column))
                .append(": ");
            out.append(message);
            if (action != null && !action.isEmpty()) out.append(" - ")
                .append(action);
            return out.toString();
        }

        @Override
        public String toString() {
            return line();
        }
    }

    private final Sink sink;
    private final Set<String> said = new HashSet<String>();
    private boolean full;

    public DataProblems(Sink sink) {
        this.sink = sink == null ? QUIET : sink;
    }

    public Sink sink() {
        return sink;
    }

    /** Says a problem at WARN, unless it was said already this session. Returns whether it was said now. */
    public boolean say(Problem problem) {
        return warnOnce(
            problem.source.id() + "|"
                + problem.path
                + "|"
                + problem.entry
                + "|"
                + problem.line
                + "|"
                + problem.column
                + "|"
                + problem.message,
            problem.line());
    }

    /** Says any other line at WARN once a session, by a key of the caller's (a conflict, a side's folder). */
    public boolean warnOnce(String key, String line) {
        if (said.contains(key)) return false;
        if (said.size() >= MOST_SAID) {
            if (!full) {
                full = true;
                sink.warn(
                    PREFIX + "more problems than " + MOST_SAID + " this session; further ones are counted, not said");
            }
            return false;
        }
        said.add(key);
        sink.warn(line);
        return true;
    }

    /** How many distinct lines have been said this session. */
    public int saidCount() {
        return said.size();
    }
}
