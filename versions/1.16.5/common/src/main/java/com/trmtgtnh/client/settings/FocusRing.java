package com.trmtgtnh.client.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * The order Tab walks the settings screen in (0.9.228; spec 1.6 and M11).
 *
 * <p>
 * Reading order, and wrapping: the header's buttons, the search field, "Changed only", each row's controls top to
 * bottom - in a setting row its control, its undo arrow, its reset; in a picture row its edit box, its number, its
 * remove - then the footer, and round again. The screen names each control by an id and hands the list over each time
 * it lays out; a control that is disabled or locked is skipped, so Tab never lands where nothing can be done.
 */
public final class FocusRing {

    private final List<String> ids;

    private Predicate<String> disabled = new Predicate<String>() {

        @Override
        public boolean test(String id) {
            return false;
        }
    };

    public FocusRing(List<String> ids) {
        this.ids = new ArrayList<String>(ids);
    }

    /** Which controls to skip; asked at each step, so a control greyed since the last layout is skipped at once. */
    public FocusRing skip(Predicate<String> disabled) {
        this.disabled = disabled;
        return this;
    }

    public List<String> ids() {
        return ids;
    }

    /**
     * The next control from {@code at}, or the previous one going back, wrapping at either end. From nothing, or from a
     * control the ring no longer holds, the first (or going back the last). Null when every control is skipped.
     */
    public String next(String at, boolean back) {
        int size = ids.size();
        if (size == 0) return null;
        int from = at == null ? -1 : ids.indexOf(at);
        int index;
        if (from < 0) {
            index = back ? size - 1 : 0;
        } else {
            index = back ? from - 1 : from + 1;
        }
        for (int tried = 0; tried < size; tried++) {
            int wrapped = ((index % size) + size) % size;
            String id = ids.get(wrapped);
            if (!disabled.test(id)) return id;
            index += back ? -1 : 1;
        }
        return null;
    }
}
