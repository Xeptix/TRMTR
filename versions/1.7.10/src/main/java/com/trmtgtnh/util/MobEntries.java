package com.trmtgtnh.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The lines of multipliers.mobs: how the server reads them, and how the magic tamper's two buttons
 * rewrite them.
 *
 * <p>
 * Each line is a mob's registered name, optionally followed by a colon and a weight. The weight follows
 * the last colon, provided something stands before that colon and what follows it reads as a number;
 * otherwise the whole line is taken as a name at a weight of one. That is how the reader has always
 * behaved, and lists written by hand already rely on it. Names are matched without regard to case,
 * because "villager" is what somebody will type. A named line outranks a '*' line, and nought is a real
 * answer rather than an absent one, so 'Zombie:0' keeps zombies off the ground while they walk loose
 * even when '*' names everything else.
 *
 * <p>
 * That is why stopping a mob writes Name:0 rather than taking its line out. Taking a line out only
 * works while nothing else names the mob: under a '*' line the mob went straight back to the
 * wildcard's weight, and a weighted line or another spelling of the name went on counting it. Name:0
 * means the same thing whatever else the list says. A mob on a lead is the one exception the list
 * cannot reach: while fromLeashedMobs is on, anything being led is counted before the list is read.
 *
 * <p>
 * The reading and the rewriting live together so that they cannot disagree about what a line means.
 * It has nothing from the game in it, so a test can check every rewrite against the reader, and it is
 * on the list CoreStaysPortableTest keeps free of Minecraft.
 */
public final class MobEntries {

    /** The wildcard, which stands for every mob no line names. */
    private static final String EVERY_MOB = "*";

    private MobEntries() {}

    /** One line as the reader understands it. */
    public static final class Entry {

        /** The name as written, trimmed; the whole line where the weight did not read as a number. */
        public final String name;

        /** The weight, one where none was given or it did not read; may be negative or not a number. */
        public final float weight;

        /**
         * Whether something followed a colon and did not read as a number. The reader warns about such
         * a line and files it under its whole text at a weight of one.
         */
        public final boolean badWeight;

        private Entry(String name, float weight, boolean badWeight) {
            this.name = name;
            this.weight = weight;
            this.badWeight = badWeight;
        }
    }

    /**
     * Reads one line, or returns null for a line that is missing or blank.
     *
     * <p>
     * A colon at the very start is not a separator, because nothing stands before it to be a name, so
     * ':5' is a name. Whatever {@link Float#parseFloat} accepts is a weight, including a figure that is
     * not a number, which {@link #table} then leaves out.
     */
    public static Entry parse(String raw) {
        if (raw == null) return null;
        String entry = raw.trim();
        if (entry.isEmpty()) return null;
        int colon = entry.lastIndexOf(':');
        if (colon > 0) {
            try {
                float weight = Float.parseFloat(entry.substring(colon + 1));
                return new Entry(
                    entry.substring(0, colon)
                        .trim(),
                    weight,
                    false);
            } catch (NumberFormatException notANumber) {
                return new Entry(entry, 1f, true);
            }
        }
        return new Entry(entry, 1f, false);
    }

    /**
     * The lines as a lookup from lower-case name to weight.
     *
     * <p>
     * A weight of nought or more is kept; a negative one, or one that is not a number, has nothing the
     * ground could act on and is left out, so it neither counts nor hides an earlier line for the same
     * name. Where a name is kept twice the later line wins. The result cannot be changed: it becomes the
     * live table, which every reload replaces whole, and a table edited in place would count mobs the
     * config does not name.
     */
    public static Map<String, Float> table(String[] entries) {
        if (entries == null || entries.length == 0) return Collections.emptyMap();
        Map<String, Float> built = new HashMap<String, Float>();
        for (String raw : entries) {
            Entry entry = parse(raw);
            if (entry == null) continue;
            if (entry.weight >= 0f) built.put(key(entry.name), Float.valueOf(entry.weight));
        }
        return Collections.unmodifiableMap(built);
    }

    /** How hard this mob wears the ground: its own line, else the '*' line, else nought. */
    public static float lookup(Map<String, Float> table, String entityName) {
        if (entityName == null || table == null || table.isEmpty()) return 0f;
        Float named = table.get(key(entityName));
        if (named != null) return named.floatValue();
        Float all = table.get(EVERY_MOB);
        return all == null ? 0f : all.floatValue();
    }

    /**
     * The list with this mob stopped: its first line rewritten as Name:0, any later line naming it
     * dropped, and Name:0 appended where no line named it.
     *
     * <p>
     * Written even for a mob the list never named, because a '*' line may be counting it, and Name:0 is
     * the only line that settles the answer whatever else is there. Every other line keeps its place.
     * A '*' line is never a match, and nor is a line whose weight did not read, which the reader files
     * under its whole text. So a mob whose registered name itself holds a colon is the one case in which
     * such a line, standing after the rewritten one, could still count it.
     *
     * @param entries the list as it stands; null is read as empty
     * @param name    the mob's registered name, written as given once trimmed
     * @return a new list, which is a copy of the old one when the name is missing or blank
     */
    public static String[] neverWears(String[] entries, String name) {
        String[] list = entries == null ? new String[0] : entries;
        String wanted = name == null ? "" : name.trim();
        if (wanted.isEmpty()) return list.clone();

        String written = wanted + ":0";
        List<String> out = new ArrayList<String>(list.length + 1);
        boolean placed = false;
        for (String line : list) {
            if (matching(line, wanted) == null) {
                out.add(line);
            } else if (!placed) {
                out.add(written);
                placed = true;
            }
        }
        if (!placed) out.add(written);
        return out.toArray(new String[out.size()]);
    }

    /**
     * The list with this mob wearing the ground again.
     *
     * <p>
     * Where the reader already counts the mob above nought, the list comes back as it was, so
     * somebody's 1.5 is not flattened to one. That holds whether the weight comes from a line naming
     * the mob or from a {@code *} line: under {@code *:2} alone the mob already wears at two, and
     * appending its bare name would bring it down to one. Otherwise the first line naming it becomes
     * the bare name, which reads as one, any later line naming it is dropped, and the bare name is
     * appended where no line named it. A negative weight, or one that is not a number, is ignored by
     * the reader, so where the mob would otherwise count for nothing such a line is replaced here
     * rather than trusted.
     *
     * @param entries the list as it stands; null is read as empty
     * @param name    the mob's registered name, written as given once trimmed
     * @return a new list, which is a copy of the old one when nothing needs to change or the name is
     *         missing or blank
     */
    public static String[] wears(String[] entries, String name) {
        String[] list = entries == null ? new String[0] : entries;
        String wanted = name == null ? "" : name.trim();
        if (wanted.isEmpty()) return list.clone();

        if (lookup(table(list), wanted) > 0f) return list.clone();

        List<String> out = new ArrayList<String>(list.length + 1);
        boolean placed = false;
        for (String line : list) {
            if (matching(line, wanted) == null) {
                out.add(line);
            } else if (!placed) {
                out.add(wanted);
                placed = true;
            }
        }
        if (!placed) out.add(wanted);
        return out.toArray(new String[out.size()]);
    }

    /** The line read, when it names this mob and is one a rewrite may touch; otherwise null. */
    private static Entry matching(String line, String wanted) {
        Entry entry = parse(line);
        if (entry == null || entry.badWeight || EVERY_MOB.equals(entry.name)) return null;
        return key(entry.name).equals(key(wanted)) ? entry : null;
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
