package com.trmtgtnh.client.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.trmtgtnh.client.settings.SettingsGeometry.Rect;

/**
 * A chooser opened as a list of its values, each saying what it does (0.9.228; spec M23).
 *
 * <p>
 * Xep, shown "Cost curve" cycling through its values a click at a time: "How about drop down menus in some places
 * also? For better UX than clicking through things? Perhaps images where applicable to help you understand, or
 * separate prompts when hovering over each dropdown option to explain things in more detail?" So every chooser opens
 * this: 240 wide, a row of 24 per value with its picture where the chooser has pictures, its title and its one line,
 * at most eight rows at once and the rest scrolled; and beside it, the detail of the option under the pointer or the
 * keyboard. It opens under its control, or above it where under would leave the panel, and the detail goes to its
 * right, or its left where the right has no room.
 *
 * <p>
 * The keys (spec 1.6): Up, Down, Home and End move the highlight, Enter takes it, Escape closes with the choice as it
 * was. Left and Right step a closed chooser without opening it ({@link #step}).
 */
public final class Dropdown {

    public static final int WIDTH = 240;

    public static final int ROW = 24;

    public static final int SHOWN = 8;

    public static final int DETAIL_WIDTH = 180;

    /** The keys an open dropdown answers. */
    public static final int UP = 0;

    public static final int DOWN = 1;

    public static final int HOME = 2;

    public static final int END = 3;

    public static final int ENTER = 4;

    public static final int ESCAPE = 5;

    /** What a key or a click did. */
    public static final int NOTHING = 0;

    public static final int MOVED = 1;

    public static final int CHOSE = 2;

    public static final int CLOSED = 3;

    /** One value: what the file writes, its words, and its picture, or null. */
    public static final class Option {

        public final String value;

        public final String title;

        public final String line;

        /** The detail panel's words; null or empty when the title and the line say enough. */
        public final String detail;

        public final Object picture;

        /** More lines for the detail panel: what choosing a preset would change (spec S5). */
        public final List<String> more;

        public Option(String value, String title, String line, String detail, Object picture) {
            this(value, title, line, detail, picture, Collections.<String>emptyList());
        }

        public Option(String value, String title, String line, String detail, Object picture, List<String> more) {
            this.value = value;
            this.title = title;
            this.line = line;
            this.detail = detail;
            this.picture = picture;
            this.more = more == null ? Collections.<String>emptyList() : more;
        }
    }

    private final List<Option> options;

    private final boolean pictures;

    private int chosen;

    private int highlighted;

    private int top;

    private boolean open = true;

    private Rect list;

    private Rect detail;

    private Rect screen;

    private int lastMouseX = Integer.MIN_VALUE;

    private int lastMouseY = Integer.MIN_VALUE;

    public Dropdown(List<Option> options, int chosen) {
        this.options = new ArrayList<Option>(options);
        this.chosen = chosen;
        this.highlighted = chosen < 0 ? 0 : chosen;
        boolean any = false;
        for (Option option : options) {
            if (option.picture != null) any = true;
        }
        this.pictures = any;
    }

    /** The next value along from {@code chosen}, either way, wrapping: Left and Right on a closed chooser. */
    public static int step(int chosen, int size, int direction) {
        if (size <= 0) return -1;
        int to = chosen + (direction < 0 ? -1 : 1);
        return ((to % size) + size) % size;
    }

    /** Places the list against the whole screen. */
    public void layout(Rect control, Rect screen) {
        layout(control, screen, screen);
    }

    /**
     * Places the list under its control, or above it where under would leave {@code panel}, inside the screen; and the
     * detail panel to the list's right, or its left where the right has no room.
     */
    public void layout(Rect control, Rect panel, Rect screen) {
        this.screen = screen;
        int rows = Math.min(SHOWN, options.size());
        int height = rows * ROW + 2;
        int x = control.x0;
        if (x + WIDTH > screen.x1) x = screen.x1 - WIDTH;
        if (x < screen.x0) x = screen.x0;
        int y = control.y1;
        if (y + height > panel.y1) y = control.y0 - height;
        if (y < screen.y0) y = screen.y0;
        list = Rect.sized(x, y, WIDTH, height);
        int dx = list.x1 + 2;
        if (dx + DETAIL_WIDTH > screen.x1) dx = list.x0 - 2 - DETAIL_WIDTH;
        detail = Rect.sized(dx, list.y0, DETAIL_WIDTH, list.height());
        show(highlighted);
    }

    public Rect list() {
        return list;
    }

    /** The detail panel's left edge and top; its height is the detail's own, worked out as it is drawn. */
    public Rect detail() {
        return detail;
    }

    public List<Option> options() {
        return options;
    }

    public int chosen() {
        return chosen;
    }

    public int highlighted() {
        return highlighted;
    }

    public int top() {
        return top;
    }

    public boolean isOpen() {
        return open;
    }

    /** Puts the highlight on an option and scrolls it into view: the photograph's pointer on {@code crack}. */
    public void highlight(int index) {
        if (index < 0 || index >= options.size()) return;
        highlighted = index;
        show(index);
    }

    private void show(int index) {
        if (index < top) top = index;
        if (index >= top + SHOWN) top = index - SHOWN + 1;
        int last = Math.max(0, options.size() - SHOWN);
        if (top > last) top = last;
        if (top < 0) top = 0;
    }

    /** The wheel over the list: scrolls it a row a notch. */
    public void scroll(int direction) {
        int last = Math.max(0, options.size() - SHOWN);
        top += direction < 0 ? -1 : 1;
        if (top < 0) top = 0;
        if (top > last) top = last;
    }

    /** Where an option's row is, or null while it is scrolled out. */
    public Rect row(int index) {
        if (list == null || index < top || index >= top + SHOWN || index >= options.size()) return null;
        return Rect.sized(list.x0 + 1, list.y0 + 1 + (index - top) * ROW, WIDTH - 2, ROW);
    }

    /**
     * The scroll bar's column at the list's right edge, where it scrolls (0.9.228; Xep, from a picker in GT New
     * Horizons: a press on a list's scroll bar chose the row beside it). A press there moves the list and chooses
     * nothing.
     */
    public static final int BAR_HIT = 5;

    /** Whether a point is on the scroll bar, which the list has only while it holds more options than it shows. */
    public boolean onBar(int x, int y) {
        return list != null && options.size() > SHOWN && list.contains(x, y) && x >= list.x1 - BAR_HIT;
    }

    /** Moves the list so the bar's thumb stands where it was pressed or dragged. */
    public void barTo(int y) {
        int last = Math.max(0, options.size() - SHOWN);
        int track = Math.max(1, list.height() - 2);
        top = Math.max(0, Math.min(last, Math.round((y - list.y0 - 1) * (float) last / track)));
    }

    /** The option at a point, or -1. */
    public int hit(int x, int y) {
        if (list == null || !list.contains(x, y) || onBar(x, y)) return -1;
        for (int i = top; i < top + SHOWN && i < options.size(); i++) {
            if (row(i).contains(x, y)) return i;
        }
        return -1;
    }

    /** A key while open. */
    public int key(int action) {
        if (!open || options.isEmpty()) return NOTHING;
        switch (action) {
            case UP:
                highlight(Math.max(0, highlighted - 1));
                return MOVED;
            case DOWN:
                highlight(Math.min(options.size() - 1, highlighted + 1));
                return MOVED;
            case HOME:
                highlight(0);
                return MOVED;
            case END:
                highlight(options.size() - 1);
                return MOVED;
            case ENTER:
                chosen = highlighted;
                open = false;
                return CHOSE;
            case ESCAPE:
                open = false;
                return CLOSED;
            default:
                return NOTHING;
        }
    }

    /** A click while open: on an option it takes it; anywhere else it closes with the choice as it was. */
    public int click(int x, int y) {
        if (!open) return NOTHING;
        if (onBar(x, y)) {
            barTo(y);
            return MOVED;
        }
        int at = hit(x, y);
        if (at >= 0) {
            chosen = at;
            open = false;
            return CHOSE;
        }
        if (list != null && list.contains(x, y)) return NOTHING;
        open = false;
        return CLOSED;
    }

    /** The pointer moved: the highlight follows it onto a row. A pointer standing still leaves the keyboard's. */
    public void pointer(int x, int y) {
        if (x == lastMouseX && y == lastMouseY) return;
        lastMouseX = x;
        lastMouseY = y;
        int at = hit(x, y);
        if (at >= 0) highlighted = at;
    }

    /** Draws the list and the highlighted option's detail. */
    public void draw(Canvas canvas, int mouseX, int mouseY) {
        if (!open || list == null) return;
        pointer(mouseX, mouseY);
        canvas.panel(list.x0, list.y0, list.x1, list.y1);
        Canvas.frame(canvas, list.x0, list.y0, list.x1, list.y1, Canvas.GOLD);
        int textX = pictures ? 24 : 6;
        for (int i = top; i < top + SHOWN && i < options.size(); i++) {
            Rect at = row(i);
            Option option = options.get(i);
            if (i == highlighted) canvas.fill(at.x0, at.y0, at.x1, at.y1, Canvas.TAB_IDLE);
            if (i == chosen) canvas.fill(at.x0, at.y0, at.x0 + 2, at.y1, Canvas.GOLD);
            if (option.picture != null) canvas.picture(option.picture, at.x0 + 4, at.y0 + 4);
            canvas
                .text(canvas.trim(option.title, at.width() - textX - 4), at.x0 + textX, at.y0 + 3, Canvas.WHITE, true);
            String line = option.line == null ? "" : option.line;
            canvas.text(
                canvas.trim(line, (int) ((at.width() - textX - 4) / 0.75f)),
                at.x0 + textX,
                at.y0 + 14,
                Canvas.BODY,
                0.75f);
        }
        if (options.size() > SHOWN) {
            int track = list.height() - 2;
            int thumb = Math.max(4, track * SHOWN / options.size());
            int y = list.y0 + 1 + (track - thumb) * top / Math.max(1, options.size() - SHOWN);
            canvas.fill(list.x1 - 3, y, list.x1 - 1, y + thumb, Canvas.RULE);
        }
        drawDetail(canvas);
    }

    /** The lines of the highlighted option's detail panel, wrapped to its width. */
    public List<String> detailLines(Canvas canvas) {
        List<String> out = new ArrayList<String>();
        if (highlighted < 0 || highlighted >= options.size()) return out;
        Option option = options.get(highlighted);
        List<String> paragraphs = new ArrayList<String>();
        if (option.line != null && !option.line.isEmpty()) paragraphs.add(option.line);
        if (option.detail != null && !option.detail.isEmpty()) paragraphs.add(option.detail);
        paragraphs.addAll(option.more);
        out.addAll(HoverText.wrap(canvas, paragraphs, DETAIL_WIDTH - 2 * HoverText.PAD));
        return out;
    }

    private void drawDetail(Canvas canvas) {
        if (highlighted < 0 || highlighted >= options.size()) return;
        Option option = options.get(highlighted);
        List<String> lines = detailLines(canvas);
        int pictureRoom = option.picture != null ? 36 : 0;
        int head = 2 * HoverText.LINE + pictureRoom;
        int room = Math.max(1, (screen.height() - 2 * HoverText.MARGIN - 2 * HoverText.PAD - head) / HoverText.LINE);
        if (lines.size() > room) {
            lines = new ArrayList<String>(lines.subList(0, Math.max(0, room - 1)));
            lines.add("...");
        }
        int height = 2 * HoverText.PAD + head + lines.size() * HoverText.LINE;
        int y = detail.y0;
        if (y + height > screen.y1 - HoverText.MARGIN) y = screen.y1 - HoverText.MARGIN - height;
        if (y < screen.y0 + HoverText.MARGIN) y = screen.y0 + HoverText.MARGIN;
        Rect at = Rect.sized(detail.x0, y, DETAIL_WIDTH, height);
        canvas.panel(at.x0, at.y0, at.x1, at.y1);
        Canvas.frame(canvas, at.x0, at.y0, at.x1, at.y1, Canvas.GOLD);
        int x = at.x0 + HoverText.PAD;
        int ty = at.y0 + HoverText.PAD;
        canvas.text(canvas.trim(option.title, DETAIL_WIDTH - 2 * HoverText.PAD), x, ty, Canvas.GOLD, true);
        ty += HoverText.LINE;
        // The file's own word, so a player reading the file can find the value it names.
        canvas.text(canvas.trim(option.value, DETAIL_WIDTH - 2 * HoverText.PAD), x, ty, Canvas.GREY, true);
        ty += HoverText.LINE;
        if (option.picture != null) {
            canvas.picture(new Canvas.Scaled(option.picture, 32), x, ty + 2);
            ty += pictureRoom;
        }
        for (String line : lines) {
            canvas.text(line, x, ty, Canvas.BODY, true);
            ty += HoverText.LINE;
        }
    }
}
