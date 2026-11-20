package com.trmtgtnh.original;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The original mod's {@code config/trmt.json}, read as found and put into this mod's {@code faithful} settings.
 *
 * <p>
 * What "Use your TRMT settings" offers (docs/goals/0.9.229-w5-spec.md, 2.8). Every key the file holds is read, and only
 * a key it does not hold falls back to 0.5.1's default, because the original kept a file's values when it loaded one:
 * a file 0.4 wrote still says sand wears at 4 to 8 and grass heals over 1, 2, 3, 5 and 8 days, and that is the player's
 * setting, not a stale one (tools/original_trmt_051.json, writtenBy04).
 *
 * <p>
 * The {@code faithful} category exists so that this is one key to one key. What still cannot be matched is said, never
 * dropped in silence: the coarse-dirt thresholds, which nothing in 0.5.1 reads; a plant this edition does not have; a
 * value outside this mod's range, given with the number applied in its place; and a file that cannot be read at all,
 * from which nothing is taken. A key missing from the file is said as well, with the default used for it.
 *
 * <p>
 * Pure: it is handed the file's text and this edition's name, and names nothing from the game, so it is the same file
 * in all eight editions and its rules are tried without one.
 */
public final class OriginalSettings {

    /** What kind of value a key holds. */
    public enum Kind {
        /** On or off. */
        SWITCH,
        /** A number, held to a range. */
        NUMBER,
        /** A list of plant names. */
        PLANTS
    }

    /** One key of the original's file and the {@code faithful} setting it becomes. */
    public static final class Key {

        /** Where it sits in {@code trmt.json}, dotted: {@code erosionThresholds.grass.min}. */
        public final String original;

        /** The setting it fills: {@code faithful.grass.thresholdMin}. */
        public final String faithful;

        /** Its place in a five-value setting (the heal days per stage), or -1 for a setting of its own. */
        public final int slot;

        public final Kind kind;

        /** 0.5.1's default, used when the file does not hold the key; a switch's is 1 for on. */
        public final double fallback;

        /** This mod's range for the setting; a value outside it is held to it and said. */
        public final double min;

        public final double max;

        Key(String original, String faithful, int slot, Kind kind, double fallback, double min, double max) {
            this.original = original;
            this.faithful = faithful;
            this.slot = slot;
            this.kind = kind;
            this.fallback = fallback;
            this.min = min;
            this.max = max;
        }
    }

    /** Something the panel says under "Could not be matched", or that a key was not in the file. */
    public static final class Problem {

        /** Which of the kinds the detail panel says. */
        public enum What {
            /** The file could not be read; nothing is taken from it. */
            UNREADABLE,
            /** A key the original itself never reads. */
            NOTHING_READS_IT,
            /** A plant this edition does not have. */
            NO_SUCH_PLANT,
            /** A value outside this mod's range: {@link #given} held to {@link #applied}. */
            OUT_OF_RANGE,
            /** A key the file does not hold: the original's default, {@link #applied}, is used. */
            NOT_IN_FILE
        }

        public final What what;

        /** The original's key, dotted, or the plant's name for {@link What#NO_SUCH_PLANT}. */
        public final String key;

        public final double given;

        public final double applied;

        /** Where reading stopped, for {@link What#UNREADABLE}; 0 otherwise. */
        public final int line;

        public final int column;

        /** Why a file could not be read, in the reader's words; null otherwise. */
        public final String why;

        Problem(What what, String key, double given, double applied, int line, int column, String why) {
            this.what = what;
            this.key = key;
            this.given = given;
            this.applied = applied;
            this.line = line;
            this.column = column;
            this.why = why;
        }

        /** The line in English, for the log and for tests; the screen words its own from the language file. */
        public String words() {
            switch (what) {
                case UNREADABLE:
                    return "trmt.json could not be read (line " + line
                        + ", column "
                        + column
                        + ": "
                        + why
                        + "), so nothing was changed";
                case NOTHING_READS_IT:
                    return key + ": the original mod never reads this, so there is nothing to match it to";
                case NO_SUCH_PLANT:
                    return key + ": this edition of Minecraft has no such plant";
                case OUT_OF_RANGE:
                    return key + ": "
                        + number(given)
                        + " is outside this mod's range, so "
                        + number(applied)
                        + " is used";
                default:
                    return key + ": not in your file; the original's default used (" + number(applied) + ")";
            }
        }

        private static String number(double value) {
            if (value == Math.rint(value) && !Double.isInfinite(value) && Math.abs(value) < 1e15) {
                return Long.toString((long) value);
            }
            return Double.toString(value);
        }
    }

    /** What reading a file gave: the settings to apply, and what to say about it. */
    public static final class Reading {

        private final Problem refusal;
        private final Map<String, Object> values;
        private final List<Problem> unmatched;
        private final List<Problem> absent;

        Reading(Problem refusal, Map<String, Object> values, List<Problem> unmatched, List<Problem> absent) {
            this.refusal = refusal;
            this.values = Collections.unmodifiableMap(values);
            this.unmatched = Collections.unmodifiableList(unmatched);
            this.absent = Collections.unmodifiableList(absent);
        }

        /** Whether the file could be read. When it could not, there are no values and nothing is to be changed. */
        public boolean readable() {
            return refusal == null;
        }

        /** Why the file could not be read, or null. */
        public Problem refusal() {
            return refusal;
        }

        /**
         * Each {@code faithful} setting and its value, in the order of {@link #KEYS}: a {@code Boolean} for a switch, a
         * {@code Double} for a number, a {@code double[]} of five for heal days per stage, and a {@code String[]} of
         * this edition's plant names for the plant list. Empty when the file could not be read.
         */
        public Map<String, Object> values() {
            return values;
        }

        /** The lines under "Could not be matched", in the order of the file's keys. */
        public List<Problem> unmatched() {
            return unmatched;
        }

        /** The keys the file did not hold, each with the default used in its place. */
        public List<Problem> absent() {
            return absent;
        }
    }

    /** Wear thresholds: this mod's {@code thresholdMin} and {@code thresholdMax} range. */
    private static final double THRESHOLD_MAX = 1000000d;

    /** Heal days per stage: this mod's {@code healDaysPerStage} range. */
    private static final double HEAL_MIN = 0.01d;

    private static final double HEAL_MAX = 100000d;

    /** The wear multipliers: this mod's {@code multipliers} range. */
    private static final double MULTIPLIER_MAX = 100d;

    /** The key in the original's file that nothing in 0.5.1 reads: coarse dirt is never walked on as itself. */
    public static final String UNREAD = "erosionThresholds.coarseDirt";

    /** Every key read from the file, in the file's own order, with 0.5.1's default (tools/original_trmt_051.json). */
    public static final List<Key> KEYS;

    static {
        List<Key> keys = new ArrayList<Key>();
        String[] wearing = { "grass", "dirt", "sand", "leaves", "vegetation" };
        for (String surface : wearing) {
            keys.add(
                new Key(
                    "erosion." + surface + "Enabled",
                    "faithful." + surface + ".wears",
                    -1,
                    Kind.SWITCH,
                    1d,
                    0d,
                    1d));
        }
        String[] healing = { "grass", "dirt", "sand" };
        for (String surface : healing) {
            keys.add(
                new Key(
                    "deErosion." + surface + "Enabled",
                    "faithful." + surface + ".heals",
                    -1,
                    Kind.SWITCH,
                    1d,
                    0d,
                    1d));
        }
        keys.add(number("erosionMultipliers.player", "faithful.player", 0.5d, 0d, MULTIPLIER_MAX));
        keys.add(number("erosionMultipliers.mounted", "faithful.mounted", 2.0d, 0d, MULTIPLIER_MAX));
        keys.add(number("erosionMultipliers.leash", "faithful.leashed", 1.5d, 0d, MULTIPLIER_MAX));
        thresholds(keys, "grass", 2.0d, 4.0d);
        thresholds(keys, "dirt", 8.0d, 12.0d);
        thresholds(keys, "sand", 1.5d, 3.0d);
        thresholds(keys, "leaves", 2.0d, 3.0d);
        keys.add(number("erosionThresholds.leaves.dropChance", "faithful.leaves.dropChance", 0.1d, 0d, 1d));
        thresholds(keys, "vegetation", 2.0d, 3.0d);
        keys.add(number("erosionThresholds.vegetation.dropChance", "faithful.vegetation.dropChance", 0.2d, 0d, 1d));
        keys.add(new Key("erodableVegetation", "faithful.vegetation.blocks", -1, Kind.PLANTS, 0d, 0d, 0d));
        for (int stage = 1; stage <= 5; stage++) {
            keys.add(
                new Key(
                    "deErosionTimeoutDays.grass.stage" + stage,
                    "faithful.grassHealDays",
                    stage - 1,
                    Kind.NUMBER,
                    5.0d,
                    HEAL_MIN,
                    HEAL_MAX));
        }
        keys.add(number("deErosionTimeoutDays.dirt.erodedDirt", "faithful.dirtHealDays", 8.0d, HEAL_MIN, HEAL_MAX));
        keys.add(
            number("deErosionTimeoutDays.dirt.erodedCoarseDirt", "faithful.coarseHealDays", 13.0d, HEAL_MIN, HEAL_MAX));
        double[] sand = { 3.0d, 5.0d, 8.0d, 13.0d, 13.0d };
        for (int stage = 1; stage <= 5; stage++) {
            keys.add(
                new Key(
                    "deErosionTimeoutDays.sand.stage" + stage,
                    "faithful.sandHealDays",
                    stage - 1,
                    Kind.NUMBER,
                    sand[stage - 1],
                    HEAL_MIN,
                    HEAL_MAX));
        }
        KEYS = Collections.unmodifiableList(keys);
    }

    private OriginalSettings() {}

    private static Key number(String original, String faithful, double fallback, double min, double max) {
        return new Key(original, faithful, -1, Kind.NUMBER, fallback, min, max);
    }

    private static void thresholds(List<Key> keys, String surface, double min, double max) {
        keys.add(
            number(
                "erosionThresholds." + surface + ".min",
                "faithful." + surface + ".thresholdMin",
                min,
                0d,
                THRESHOLD_MAX));
        keys.add(
            number(
                "erosionThresholds." + surface + ".max",
                "faithful." + surface + ".thresholdMax",
                max,
                0d,
                THRESHOLD_MAX));
    }

    /**
     * Reads a {@code trmt.json} as found, for this edition (one of {@link Vegetation#EDITIONS}).
     *
     * <p>
     * A value of the wrong kind - a word where a number belongs - makes the whole file unreadable, as it did in the
     * original, whose reader gave up on the file and wrote its defaults over it. A number written in quotes, or a
     * switch
     * written as the word "true", the original's reader took, and so does this. A null is read as the key being absent.
     */
    public static Reading read(String text, String edition) {
        Vegetation.row(edition);
        OriginalJson.Node root;
        try {
            root = OriginalJson.read(text);
        } catch (OriginalJson.Refused refused) {
            return unreadable(refused);
        }

        Map<String, Object> values = new LinkedHashMap<String, Object>();
        List<Problem> unmatched = new ArrayList<Problem>();
        List<Problem> absent = new ArrayList<Problem>();
        try {
            for (Key key : KEYS) {
                OriginalJson.Node node = find(root, key.original);
                if (key.kind == Kind.PLANTS) {
                    values.put(key.faithful, plants(node, key, edition, unmatched, absent));
                    continue;
                }
                double value;
                if (node == null) {
                    value = key.fallback;
                    absent.add(new Problem(Problem.What.NOT_IN_FILE, key.original, value, value, 0, 0, null));
                } else if (key.kind == Kind.SWITCH) {
                    value = switchOf(node) ? 1d : 0d;
                } else {
                    double given = numberOf(node);
                    value = held(given, key);
                    if (value != given) {
                        unmatched.add(new Problem(Problem.What.OUT_OF_RANGE, key.original, given, value, 0, 0, null));
                    }
                }
                put(values, key, value);
            }
        } catch (OriginalJson.Refused refused) {
            return unreadable(refused);
        }

        if (find(root, UNREAD) != null) {
            unmatched.add(0, new Problem(Problem.What.NOTHING_READS_IT, UNREAD, 0d, 0d, 0, 0, null));
        }
        return new Reading(null, values, unmatched, absent);
    }

    private static Reading unreadable(OriginalJson.Refused refused) {
        Problem problem = new Problem(
            Problem.What.UNREADABLE,
            "trmt.json",
            0d,
            0d,
            refused.line,
            refused.column,
            refused.why);
        return new Reading(
            problem,
            new LinkedHashMap<String, Object>(),
            Collections.singletonList(problem),
            new ArrayList<Problem>());
    }

    private static void put(Map<String, Object> values, Key key, double value) {
        if (key.kind == Kind.SWITCH) {
            values.put(key.faithful, Boolean.valueOf(value != 0d));
        } else if (key.slot < 0) {
            values.put(key.faithful, Double.valueOf(value));
        } else {
            double[] five = (double[]) values.get(key.faithful);
            if (five == null) {
                five = new double[5];
                values.put(key.faithful, five);
            }
            five[key.slot] = value;
        }
    }

    /** A number held to the key's range; not a number at all gives the default. */
    private static double held(double given, Key key) {
        if (Double.isNaN(given)) return key.fallback;
        if (given < key.min) return key.min;
        if (given > key.max) return key.max;
        return given;
    }

    private static String[] plants(OriginalJson.Node node, Key key, String edition, List<Problem> unmatched,
        List<Problem> absent) throws OriginalJson.Refused {
        List<String> names = new ArrayList<String>();
        if (node == null) {
            absent.add(new Problem(Problem.What.NOT_IN_FILE, key.original, 0d, 0d, 0, 0, null));
            for (String name : Vegetation.original()) names.add(name);
        } else {
            if (node.type != OriginalJson.Type.ARRAY) {
                throw new OriginalJson.Refused(
                    key.original + " should be a list of plant names",
                    node.line,
                    node.column);
            }
            for (OriginalJson.Node item : node.items) {
                if (item.type == OriginalJson.Type.NULL) continue;
                if (item.type == OriginalJson.Type.OBJECT || item.type == OriginalJson.Type.ARRAY) {
                    throw new OriginalJson.Refused("a plant should be named by a string", item.line, item.column);
                }
                names.add(item.text);
            }
        }
        List<String> out = new ArrayList<String>();
        for (String name : names) {
            String ours = Vegetation.inEdition(edition, name);
            if (ours == null) {
                String said = Vegetation.normalise(name);
                if (said != null) {
                    unmatched.add(new Problem(Problem.What.NO_SUCH_PLANT, said, 0d, 0d, 0, 0, null));
                }
            } else if (!out.contains(ours)) {
                out.add(ours);
            }
        }
        return out.toArray(new String[out.size()]);
    }

    /** The node at a dotted path, or null when any step of it is missing, null, or not an object. */
    private static OriginalJson.Node find(OriginalJson.Node root, String path) {
        OriginalJson.Node at = root;
        for (String step : path.split("\\.")) {
            if (at == null || at.type != OriginalJson.Type.OBJECT) return null;
            at = at.field(step);
        }
        return at == null || at.type == OriginalJson.Type.NULL ? null : at;
    }

    private static boolean switchOf(OriginalJson.Node node) throws OriginalJson.Refused {
        if (node.type == OriginalJson.Type.BOOLEAN || node.type == OriginalJson.Type.STRING) {
            return "true".equalsIgnoreCase(node.text);
        }
        throw new OriginalJson.Refused("expected true or false", node.line, node.column);
    }

    private static double numberOf(OriginalJson.Node node) throws OriginalJson.Refused {
        if (node.type == OriginalJson.Type.NUMBER || node.type == OriginalJson.Type.STRING) {
            try {
                return Double.parseDouble(node.text.trim());
            } catch (NumberFormatException notOne) {
                // Said below with where it is.
            }
        }
        throw new OriginalJson.Refused("expected a number", node.line, node.column);
    }
}
