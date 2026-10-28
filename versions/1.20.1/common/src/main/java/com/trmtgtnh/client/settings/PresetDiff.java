package com.trmtgtnh.client.settings;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What choosing a preset would change, setting by setting (S5): the lines of a preset option's detail panel,
 * {@code preset.line} - "Gradations: 80 to 24" - each from the setting's value now to the preset's.
 *
 * <p>
 * Values are compared as numbers wherever both read as one, because the file and a preset write the same number
 * differently - {@code 2} and {@code 2.0}, {@code 0.5} and {@code 0.50} - and a change that is no change would be one
 * more line telling a player something false.
 */
public final class PresetDiff {

    /** One setting a preset would change. */
    public static final class Change {

        /** The setting, as the screen names its values ({@code category.key}). */
        public final String id;

        /** Its value now, as written; null where it has none. */
        public final String from;

        /** The preset's value for it, as the preset writes it. */
        public final String to;

        public Change(String id, String from, String to) {
            this.id = id;
            this.from = from;
            this.to = to;
        }

        @Override
        public String toString() {
            return id + ": " + from + " to " + to;
        }
    }

    private PresetDiff() {}

    /**
     * Every setting the preset would change: those the preset holds a value for that is not the value now, in
     * {@code order} first (the screen's order, so the list reads as the tabs do), then any the order does not name in
     * the preset's own order. Empty when the preset changes nothing ({@code preset.none}).
     *
     * @param current the values now, by id
     * @param preset  the values the preset would set, by id
     * @param order   the ids in the order the lines are wanted
     */
    public static List<Change> diff(Map<String, String> current, Map<String, String> preset, List<String> order) {
        List<Change> out = new ArrayList<Change>();
        if (preset == null || preset.isEmpty()) return out;
        Set<String> done = new HashSet<String>();
        if (order != null) {
            for (String id : order) {
                if (!done.add(id) || !preset.containsKey(id)) continue;
                add(out, id, current, preset);
            }
        }
        for (String id : preset.keySet()) {
            if (done.add(id)) add(out, id, current, preset);
        }
        return Collections.unmodifiableList(out);
    }

    private static void add(List<Change> out, String id, Map<String, String> current, Map<String, String> preset) {
        String from = current == null ? null : current.get(id);
        String to = preset.get(id);
        if (!same(from, to)) out.add(new Change(id, from, to));
    }

    /**
     * Whether two values are the same setting: equal as numbers where both read as one, else equal as text. A number is
     * never the same as text, and nothing is the same as a value.
     */
    public static boolean same(String a, String b) {
        if (a == null || b == null) return a == b;
        BigDecimal x = number(a);
        BigDecimal y = number(b);
        if (x != null && y != null) return x.compareTo(y) == 0;
        return a.trim()
            .equals(b.trim());
    }

    /** The value as a decimal, or null where it is not plainly one (no {@code NaN}, no {@code Infinity}). */
    private static BigDecimal number(String text) {
        String t = text.trim();
        if (t.isEmpty()) return null;
        try {
            return new BigDecimal(t);
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }
}
