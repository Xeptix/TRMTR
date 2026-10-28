package com.trmtgtnh.client.gui;

import com.trmtgtnh.client.settings.Canvas;
import com.trmtgtnh.client.settings.SettingsGeometry.Rect;

/**
 * One edit box of a list row (spec 1.2's picture row, M5): the line as written, a caret, and the ghost of a suggestion
 * after it, drawn through the shared {@link Canvas}.
 *
 * <p>
 * The game's own {@code GuiTextField} draws its own frame and asks the game's font for every caret move, so a row
 * built from it cannot be drawn into a recording canvas or held by a test; this does only what a list line needs -
 * type, delete, move, home and end - and nothing a list line does not. The keys are LWJGL 2's codes, which are the
 * game's on 1.7.10 and 1.12.2.
 */
final class LineBox {

    static final int KEY_BACK = 14;

    static final int KEY_DELETE = 211;

    static final int KEY_LEFT = 203;

    static final int KEY_RIGHT = 205;

    static final int KEY_HOME = 199;

    static final int KEY_END = 207;

    /** The longest line a box holds: Forge keeps a list line of any length, and no list here needs more. */
    static final int MOST = 1024;

    /** What a key did. */
    enum Did {
        NOTHING,
        MOVED,
        CHANGED
    }

    private String text = "";

    private int caret;

    private boolean focused;

    private int color = Canvas.BODY;

    /** Where it was last drawn, for the pointer. */
    Rect at;

    LineBox(String text) {
        setText(text);
    }

    String text() {
        return text;
    }

    /** The text, the caret at its end. */
    void setText(String value) {
        text = value == null ? "" : value.length() > MOST ? value.substring(0, MOST) : value;
        caret = text.length();
    }

    boolean focused() {
        return focused;
    }

    void focus(boolean on) {
        focused = on;
        if (on && caret > text.length()) caret = text.length();
    }

    void color(int argb) {
        color = argb;
    }

    int caret() {
        return caret;
    }

    boolean caretAtEnd() {
        return caret == text.length();
    }

    /** A character a list line may hold: printable, and never the game's formatting mark. */
    static boolean allowed(char typed) {
        return typed >= 32 && typed != 127 && typed != '§';
    }

    /** Text put in at the caret, as typed or pasted; a line end ends it. */
    Did insert(String more) {
        if (more == null || more.isEmpty()) return Did.NOTHING;
        StringBuilder kept = new StringBuilder();
        for (int i = 0; i < more.length(); i++) {
            char one = more.charAt(i);
            if (one == '\n' || one == '\r') break;
            if (allowed(one)) kept.append(one);
        }
        if (kept.length() == 0 || text.length() >= MOST) return Did.NOTHING;
        String add = kept.length() + text.length() > MOST ? kept.substring(0, MOST - text.length()) : kept.toString();
        text = text.substring(0, caret) + add + text.substring(caret);
        caret += add.length();
        return Did.CHANGED;
    }

    /** One key, typed while the box has the focus. */
    Did key(char typed, int code) {
        switch (code) {
            case KEY_BACK:
                if (caret == 0) return Did.NOTHING;
                text = text.substring(0, caret - 1) + text.substring(caret);
                caret--;
                return Did.CHANGED;
            case KEY_DELETE:
                if (caret >= text.length()) return Did.NOTHING;
                text = text.substring(0, caret) + text.substring(caret + 1);
                return Did.CHANGED;
            case KEY_LEFT:
                if (caret == 0) return Did.NOTHING;
                caret--;
                return Did.MOVED;
            case KEY_RIGHT:
                if (caret >= text.length()) return Did.NOTHING;
                caret++;
                return Did.MOVED;
            case KEY_HOME:
                caret = 0;
                return Did.MOVED;
            case KEY_END:
                caret = text.length();
                return Did.MOVED;
            default:
                return allowed(typed) ? insert(String.valueOf(typed)) : Did.NOTHING;
        }
    }

    /** The caret put where the pointer pressed, by the widths of the text before it. */
    void click(Canvas canvas, int x) {
        if (at == null) return;
        int start = start(canvas, at.width() - 2);
        int along = x - at.x0 - 1;
        caret = text.length();
        for (int i = start; i <= text.length(); i++) {
            if (canvas.width(text.substring(start, i)) > along) {
                caret = Math.max(start, i - 1);
                return;
            }
        }
    }

    /** Where the shown part begins, so the caret is always inside the box. */
    private int start(Canvas canvas, int width) {
        int start = 0;
        while (start < caret && canvas.width(text.substring(start, caret)) > width) start++;
        return start;
    }

    /**
     * Drawn at {@code at}: a dark fill while focused, the text in its color, the caret blinking, and where given the
     * suggestion's ghost after a caret at the end (M5.1).
     */
    void draw(Canvas canvas, Rect where, String ghost, long now) {
        at = where;
        if (where == null || where.width() <= 2) return;
        if (focused) canvas.fill(where.x0, where.y0, where.x1, where.y1, Canvas.TAB_OPEN);
        int width = where.width() - 2;
        int start = start(canvas, width);
        String shown = canvas.trim(text.substring(start), width);
        if (shown.endsWith("...") && !text.substring(start)
            .endsWith("...")) shown = clip(canvas, text.substring(start), width);
        int y = where.y0 + Math.max(0, (where.height() - 8) / 2);
        canvas.text(shown, where.x0 + 1, y, color, true);
        int caretX = where.x0 + 1 + canvas.width(text.substring(start, caret));
        if (focused && ghost != null && !ghost.isEmpty() && caretAtEnd()) {
            canvas.text(canvas.trim(ghost, Math.max(0, where.x1 - caretX - 1)), caretX, y, Canvas.GREY, true);
        }
        if (focused && (now / 300L) % 2L == 0L && caretX < where.x1) {
            canvas.fill(caretX, where.y0 + 1, caretX + 1, where.y1 - 1, Canvas.GOLD);
        }
    }

    /** The text cut at the width without an ellipsis, as an edit box shows what runs past its edge. */
    private static String clip(Canvas canvas, String value, int width) {
        int end = value.length();
        while (end > 0 && canvas.width(value.substring(0, end)) > width) end--;
        return value.substring(0, end);
    }
}
