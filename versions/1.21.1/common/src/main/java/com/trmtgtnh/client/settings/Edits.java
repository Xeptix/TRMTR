package com.trmtgtnh.client.settings;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Every value the settings screen is holding, what it opened with, and the way back (0.9.228; spec M8 and M9).
 *
 * <p>
 * Each setting is named by an id, {@code category.key}, and held as the text the file would write. <b>Changed</b>
 * means different from what the screen opened with, not from the default: a value typed back to what it was is not a
 * change, and Save counts only what would actually be written. Numbers are compared as numbers, so "2" and "2.0" are
 * the same value and showing a long float as its own decimal is not an edit.
 *
 * <p>
 * Every change is a step that Ctrl+Z takes back ({@link #undoLast}); a preset, a Reset tab or a paste is one step
 * however many settings it moves ({@link #group}). A row's undo arrow is not a step back but the way to its opening
 * value ({@link #revert}), as Forge's "undo changes" is. The history keeps the last 200 steps.
 */
public final class Edits {

    /** The most steps Ctrl+Z can take back. */
    public static final int HISTORY = 200;

    private static final Pattern NUMBER = Pattern.compile("[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?");

    private final Map<String, String> opening;

    private final Map<String, String> defaults;

    private final Map<String, String> values;

    /** Each step: the ids it moved and what each held before. Oldest first. */
    private final List<Map<String, String>> history = new ArrayList<Map<String, String>>();

    public Edits(Map<String, String> opening, Map<String, String> defaults) {
        this.opening = new LinkedHashMap<String, String>(opening);
        this.defaults = new LinkedHashMap<String, String>(defaults);
        this.values = new LinkedHashMap<String, String>(opening);
    }

    /** Whether two values are the same: as numbers when both read as one, else as text. */
    public static boolean same(String one, String other) {
        if (one == null || other == null) return one == null && other == null;
        if (one.equals(other)) return true;
        String a = one.trim();
        String b = other.trim();
        if (NUMBER.matcher(a)
            .matches()
            && NUMBER.matcher(b)
                .matches()) {
            return new BigDecimal(a).compareTo(new BigDecimal(b)) == 0;
        }
        return false;
    }

    public String value(String id) {
        return values.get(id);
    }

    public String opening(String id) {
        return opening.get(id);
    }

    public String defaultOf(String id) {
        return defaults.get(id);
    }

    /** Every value as held now, in the order the screen opened with them. */
    public Map<String, String> values() {
        return new LinkedHashMap<String, String>(values);
    }

    /** One setting set: one step. Setting a value it already holds is no step at all. */
    public void set(String id, String value) {
        if (same(values.get(id), value) && values.containsKey(id)) {
            values.put(id, value);
            return;
        }
        Map<String, String> step = new LinkedHashMap<String, String>();
        step.put(id, values.get(id));
        values.put(id, value);
        push(step);
    }

    /** Several settings set together - a preset, a Reset tab, a paste: one step. */
    public void group(Map<String, String> changes) {
        Map<String, String> step = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> change : changes.entrySet()) {
            String id = change.getKey();
            if (same(values.get(id), change.getValue()) && values.containsKey(id)) continue;
            step.put(id, values.get(id));
            values.put(id, change.getValue());
        }
        if (!step.isEmpty()) push(step);
    }

    private void push(Map<String, String> step) {
        history.add(step);
        while (history.size() > HISTORY) history.remove(0);
    }

    /** How many steps Ctrl+Z can take back. */
    public int steps() {
        return history.size();
    }

    /** Ctrl+Z with nothing focused: the last step taken back. Answers the ids it moved, empty when there was none. */
    public Set<String> undoLast() {
        if (history.isEmpty()) return new LinkedHashSet<String>();
        Map<String, String> step = history.remove(history.size() - 1);
        restore(step);
        return new LinkedHashSet<String>(step.keySet());
    }

    /** Ctrl+Z on a focused control: the last step that moved this setting, for this setting alone. */
    public boolean undo(String id) {
        for (int i = history.size() - 1; i >= 0; i--) {
            Map<String, String> step = history.get(i);
            if (!step.containsKey(id)) continue;
            String before = step.remove(id);
            put(id, before);
            if (step.isEmpty()) history.remove(i);
            return true;
        }
        return false;
    }

    private void restore(Map<String, String> step) {
        for (Map.Entry<String, String> one : step.entrySet()) put(one.getKey(), one.getValue());
    }

    private void put(String id, String value) {
        if (value == null) {
            values.remove(id);
        } else {
            values.put(id, value);
        }
    }

    /** A row's undo arrow: back to what the screen opened with, as one step. */
    public void revert(String id) {
        set(id, opening.get(id));
    }

    /** "Undo all": every setting back to what the screen opened with, as one step. */
    public void revertAll() {
        Map<String, String> back = new LinkedHashMap<String, String>();
        for (String id : changedIds()) back.put(id, opening.get(id));
        group(back);
    }

    /** "Reset tab...": every one of these settings to its default, as one step. */
    public void resetAll(Collection<String> ids) {
        Map<String, String> reset = new LinkedHashMap<String, String>();
        for (String id : ids) {
            if (defaults.containsKey(id)) reset.put(id, defaults.get(id));
        }
        group(reset);
    }

    /** Whether a setting differs from what the screen opened with. */
    public boolean changed(String id) {
        return !same(values.get(id), opening.get(id));
    }

    /** Every setting that differs from what the screen opened with, in the order it opened with them. */
    public List<String> changedIds() {
        Set<String> ids = new LinkedHashSet<String>(opening.keySet());
        ids.addAll(values.keySet());
        List<String> out = new ArrayList<String>();
        for (String id : ids) {
            if (changed(id)) out.add(id);
        }
        return out;
    }

    /** How many settings Save would write: "Save 3 changes". */
    public int changedCount() {
        return changedIds().size();
    }

    /** How many of these settings are changed: a tab's gold dot. */
    public int changedIn(Collection<String> ids) {
        int count = 0;
        for (String id : ids) {
            if (changed(id)) count++;
        }
        return count;
    }

    /** Whether a setting holds its default, so its Reset is greyed. */
    public boolean atDefault(String id) {
        return same(values.get(id), defaults.get(id));
    }
}
