package com.trmtgtnh.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.trmtgtnh.client.settings.Canvas;
import com.trmtgtnh.client.settings.SettingsGeometry;
import com.trmtgtnh.client.settings.SettingsGeometry.Rect;

/**
 * Suggestions in a list line's edit box (spec M5): the best completion of what is typed drawn after the caret in grey,
 * which Tab takes, and under the box a dropdown of up to eight matches, each a picker row, which Up, Down, Enter and
 * a click choose from and Escape closes.
 *
 * <p>
 * The completions come from the entries the picker of that kind offers (M5.3), matched first by what is written - the
 * id - starting with what is typed ({@code minecraft:st} to {@code minecraft:stone}), then by the picker's own search
 * over the display name. A line already written exactly as typed suggests nothing.
 */
final class Suggestions {

    /** The most matches the dropdown lists (M5.2). */
    static final int MOST = 8;

    /** How many characters are typed before the dropdown opens (M5.2). */
    static final int TYPED_LEAST = 2;

    private List<PickerList.Entry> matches = new ArrayList<PickerList.Entry>();

    private String typed = "";

    private int highlight;

    private boolean closed;

    private Rect drawnAt;

    /**
     * The best matches for what is typed, best first: those whose line starts with it (case aside), then those whose
     * name or id the search finds, each once, at most {@code most}.
     */
    static List<PickerList.Entry> matches(List<PickerList.Entry> entries, String typed, int most) {
        List<PickerList.Entry> out = new ArrayList<PickerList.Entry>();
        String want = typed == null ? ""
            : typed.trim()
                .toLowerCase(Locale.ROOT);
        if (want.isEmpty() || entries == null) return out;
        for (PickerList.Entry entry : entries) {
            if (out.size() >= most) return out;
            String line = entry.line.toLowerCase(Locale.ROOT);
            if (line.startsWith(want) && !line.equals(want)) out.add(entry);
        }
        List<String> words = PickerList.queryWords(want);
        if (words.isEmpty()) return out;
        for (PickerList.Entry entry : entries) {
            if (out.size() >= most) return out;
            if (out.contains(entry) || entry.line.equalsIgnoreCase(want)) continue;
            if (PickerList.matches(entry, words)) out.add(entry);
        }
        return out;
    }

    /** What the ghost after the caret reads: the rest of the best match's line, where it starts with what is typed. */
    static String ghost(String typed, List<PickerList.Entry> matches) {
        if (typed == null || typed.isEmpty() || matches == null || matches.isEmpty()) return "";
        String line = matches.get(0).line;
        if (line.length() <= typed.length() || !line.regionMatches(true, 0, typed, 0, typed.length())) return "";
        return line.substring(typed.length());
    }

    /** What is typed now, and the entries to complete it from; a change of text opens the dropdown again. */
    void update(String text, List<PickerList.Entry> entries) {
        String now = text == null ? "" : text;
        if (!now.equals(typed)) {
            closed = false;
            highlight = 0;
        }
        typed = now;
        matches = now.trim()
            .length() < TYPED_LEAST ? new ArrayList<PickerList.Entry>() : matches(entries, now, MOST);
        if (highlight >= matches.size()) highlight = 0;
    }

    String ghost() {
        return closed ? "" : ghost(typed, matches);
    }

    boolean open() {
        return !closed && !matches.isEmpty();
    }

    List<PickerList.Entry> shown() {
        return matches;
    }

    int highlighted() {
        return highlight;
    }

    /** Escape: closed until the text changes. */
    void close() {
        closed = true;
    }

    /** Up or Down: the highlight moves, wrapping. */
    void move(int direction) {
        if (matches.isEmpty()) return;
        highlight = (highlight + direction + matches.size()) % matches.size();
    }

    /** The line Enter takes: the highlighted match's, or null with the dropdown shut. */
    String taken() {
        return open() ? matches.get(highlight).line : null;
    }

    /** The line Tab takes: the whole best match, where the ghost shows it. */
    String completed() {
        return ghost().isEmpty() ? null : matches.get(0).line;
    }

    /**
     * The dropdown's place (M5.4): under the box, or over it where under would leave the panel; each row a picker row.
     */
    static Rect place(Rect box, int width, int rows, int panelTop, int panelBottom) {
        int height = rows * SettingsGeometry.PICKER_ROW + 2;
        int below = box.y1 + 1;
        int top = below + height <= panelBottom || box.y0 - 1 - height < panelTop ? below : box.y0 - 1 - height;
        return SettingsGeometry.Rect.sized(box.x0, top, width, height);
    }

    /** A click on the dropdown: the line chosen, or null where it missed. */
    String click(int x, int y) {
        if (!open() || drawnAt == null || !drawnAt.contains(x, y)) return null;
        int at = (y - drawnAt.y0 - 1) / SettingsGeometry.PICKER_ROW;
        return at >= 0 && at < matches.size() ? matches.get(at).line : null;
    }

    boolean holds(int x, int y) {
        return open() && drawnAt != null && drawnAt.contains(x, y);
    }

    /** The dropdown, over everything else: a gold frame, each match a picker row, the highlight filled. */
    void draw(Canvas canvas, Rect box, int width, int panelTop, int panelBottom, int mouseX, int mouseY) {
        if (!open()) {
            drawnAt = null;
            return;
        }
        Rect at = place(box, width, matches.size(), panelTop, panelBottom);
        drawnAt = at;
        canvas.panel(at.x0, at.y0, at.x1, at.y1);
        Canvas.frame(canvas, at.x0, at.y0, at.x1, at.y1, Canvas.GOLD);
        for (int i = 0; i < matches.size(); i++) {
            PickerList.Entry entry = matches.get(i);
            int top = at.y0 + 1 + i * SettingsGeometry.PICKER_ROW;
            SettingsGeometry.PickerRow row = SettingsGeometry.pickerRow(at.x0 + 1, top, at.width() - 2, 0);
            boolean lit = i == highlight
                || mouseX >= at.x0 && mouseX < at.x1 && mouseY >= top && mouseY < top + SettingsGeometry.PICKER_ROW;
            if (lit) canvas.fill(at.x0 + 1, top, at.x1 - 1, top + SettingsGeometry.PICKER_ROW, Canvas.TAB_IDLE);
            if (i == highlight) canvas.fill(at.x0 + 1, top, at.x0 + 3, top + SettingsGeometry.PICKER_ROW, Canvas.GOLD);
            canvas.fill(row.frame.x0, row.frame.y0, row.frame.x1, row.frame.y1, Canvas.TAB_IDLE);
            if (entry.picture != null) canvas.picture(entry.picture, row.picture.x0, row.picture.y0);
            canvas.text(canvas.trim(entry.name, row.name.width()), row.name.x0, row.name.y0, Canvas.WHITE, true);
            canvas
                .text(canvas.trim(entry.id, (int) (row.id.width() / 0.75F)), row.id.x0, row.id.y0, Canvas.GREY, 0.75F);
        }
    }
}
