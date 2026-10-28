package com.trmtgtnh.client.settings;

import java.util.ArrayList;
import java.util.List;

import com.trmtgtnh.client.settings.SettingsGeometry.Rect;

/**
 * The settings screen's tabs, their scrolling and their drawing (0.9.228; spec M1).
 *
 * <p>
 * Each tab is its label's width and 12 more, never under 40, and 2 apart. When they are wider than the strip it
 * scrolls a whole tab at a time: a 12-pixel arrow at an end is drawn only while there is more that way, and a tab is
 * shown only whole, so a click always lands on a tab it can read. Opening a tab scrolls it fully into view
 * ({@link #show}). The open tab is filled {@link Canvas#TAB_OPEN} with a 2-pixel gold underline, a tab under the
 * pointer has a gold label, and each tab carries its dot - gold for unsaved changes, red for a refused value.
 */
public final class TabStrip {

    /** What {@link #hit} answers for the arrows. */
    public static final int ARROW_LEFT = -2;

    public static final int ARROW_RIGHT = -3;

    /** A tab's least width, the padding round its label, the gap between tabs and an arrow's width. */
    public static final int MIN_WIDTH = 40;

    public static final int PADDING = 12;

    public static final int GAP = 2;

    public static final int ARROW = 12;

    /** A tab's dot: none, gold for changes, red for a refused value. */
    public static final int DOT_NONE = 0;

    public static final int DOT_CHANGED = 1;

    public static final int DOT_REFUSED = 2;

    private final List<String> labels;

    private int[] widths;

    private Rect strip;

    private int first;

    /** Where each tab stands this layout; null for a tab scrolled out of the strip. */
    private Rect[] placed;

    private Rect leftArrow;

    private Rect rightArrow;

    public TabStrip(List<String> labels) {
        this.labels = new ArrayList<String>(labels);
        this.widths = new int[labels.size()];
        this.placed = new Rect[labels.size()];
    }

    /** Measures every label and places the tabs inside the strip, keeping the scroll where it can. */
    public void layout(Canvas canvas, Rect strip) {
        this.strip = strip;
        for (int i = 0; i < labels.size(); i++) {
            widths[i] = Math.max(MIN_WIDTH, canvas.width(labels.get(i)) + PADDING);
        }
        first = clampFirst(first);
        place();
    }

    /** The width of a tab, as measured at the last layout. */
    public int tabWidth(int index) {
        return widths[index];
    }

    /** Whether every tab fits without scrolling. */
    public boolean fitsWhole() {
        return run(0, labels.size()) <= strip.width();
    }

    /** The tabs' widths and gaps from {@code from} up to {@code to}, exclusive. */
    private int run(int from, int to) {
        int total = 0;
        for (int i = from; i < to; i++) {
            total += widths[i] + (i > from ? GAP : 0);
        }
        return total;
    }

    /** The left edge the tabs start at with this many scrolled off, past the left arrow when there is one. */
    private int start(int scrolled) {
        return strip.x0 + (scrolled > 0 ? ARROW + GAP : 0);
    }

    /** How many tabs from {@code from} show whole, with the right arrow's room taken when any is left over. */
    private int fitting(int from) {
        int left = start(from);
        if (left + run(from, labels.size()) <= strip.x1) return labels.size() - from;
        int right = strip.x1 - ARROW - GAP;
        int count = 0;
        int x = left;
        for (int i = from; i < labels.size(); i++) {
            int end = x + widths[i];
            if (end > right) break;
            count++;
            x = end + GAP;
        }
        return Math.max(1, count);
    }

    /** The furthest the strip can scroll: the least scroll that shows the last tab. */
    private int lastFirst() {
        if (fitsWhole()) return 0;
        for (int from = 0; from < labels.size(); from++) {
            if (from + fitting(from) >= labels.size()) return from;
        }
        return labels.size() - 1;
    }

    private int clampFirst(int wanted) {
        if (labels.isEmpty()) return 0;
        int last = lastFirst();
        return wanted < 0 ? 0 : wanted > last ? last : wanted;
    }

    private void place() {
        placed = new Rect[labels.size()];
        leftArrow = null;
        rightArrow = null;
        if (labels.isEmpty() || strip == null) return;
        int count = fitting(first);
        int x = start(first);
        for (int i = first; i < first + count && i < labels.size(); i++) {
            placed[i] = new Rect(x, strip.y0, x + widths[i], strip.y1);
            x += widths[i] + GAP;
        }
        if (first > 0) leftArrow = new Rect(strip.x0, strip.y0, strip.x0 + ARROW, strip.y1);
        if (first + count < labels.size()) rightArrow = new Rect(strip.x1 - ARROW, strip.y0, strip.x1, strip.y1);
    }

    /** Scrolls by so many tabs, either way, no further than the ends. */
    public void scrollBy(int tabs) {
        first = clampFirst(first + tabs);
        place();
    }

    /** Scrolls just far enough that the tab is shown whole. */
    public void show(int index) {
        if (index < 0 || index >= labels.size()) return;
        if (index < first) {
            first = index;
        } else {
            while (first < index && index >= first + fitting(first)) first++;
        }
        first = clampFirst(first);
        place();
    }

    /** The first tab shown. */
    public int first() {
        return first;
    }

    /** Where a tab is drawn, or null while it is scrolled out of the strip. */
    public Rect tab(int index) {
        return index >= 0 && index < placed.length ? placed[index] : null;
    }

    public Rect leftArrow() {
        return leftArrow;
    }

    public Rect rightArrow() {
        return rightArrow;
    }

    /** The tab at a point, {@link #ARROW_LEFT} or {@link #ARROW_RIGHT} for an arrow, or -1 for neither. */
    public int hit(int x, int y) {
        if (leftArrow != null && leftArrow.contains(x, y)) return ARROW_LEFT;
        if (rightArrow != null && rightArrow.contains(x, y)) return ARROW_RIGHT;
        for (int i = 0; i < placed.length; i++) {
            if (placed[i] != null && placed[i].contains(x, y)) return i;
        }
        return -1;
    }

    /**
     * Draws the strip.
     *
     * @param open the open tab
     * @param dots each tab's dot, {@link #DOT_NONE}, {@link #DOT_CHANGED} or {@link #DOT_REFUSED}; may be null
     */
    public void draw(Canvas canvas, int open, int[] dots, int mouseX, int mouseY) {
        for (int i = 0; i < placed.length; i++) {
            Rect tab = placed[i];
            if (tab == null) continue;
            boolean isOpen = i == open;
            boolean under = tab.contains(mouseX, mouseY);
            canvas.fill(tab.x0, tab.y0, tab.x1, tab.y1, isOpen ? Canvas.TAB_OPEN : Canvas.TAB_IDLE);
            if (isOpen) canvas.fill(tab.x0, tab.y1 - 2, tab.x1, tab.y1, Canvas.GOLD);
            String label = canvas.trim(labels.get(i), tab.width() - 4);
            int color = under ? Canvas.GOLD : isOpen ? Canvas.WHITE : Canvas.BODY;
            canvas.text(
                label,
                tab.x0 + (tab.width() - canvas.width(label)) / 2,
                tab.y0 + (tab.height() - canvas.lineHeight() + 2) / 2,
                color,
                true);
            dot(canvas, tab, dots == null || i >= dots.length ? DOT_NONE : dots[i]);
        }
        arrow(canvas, leftArrow, "<", mouseX, mouseY);
        arrow(canvas, rightArrow, ">", mouseX, mouseY);
    }

    /** A tab's dot: 3 by 3 at its top right corner, 2 in. */
    static void dot(Canvas canvas, Rect tab, int dot) {
        if (dot == DOT_NONE) return;
        canvas.fill(tab.x1 - 5, tab.y0 + 2, tab.x1 - 2, tab.y0 + 5, dot == DOT_REFUSED ? Canvas.RED : Canvas.GOLD);
    }

    private static void arrow(Canvas canvas, Rect arrow, String sign, int mouseX, int mouseY) {
        if (arrow == null) return;
        canvas.fill(arrow.x0, arrow.y0, arrow.x1, arrow.y1, Canvas.TAB_IDLE);
        canvas.text(
            sign,
            arrow.x0 + (arrow.width() - canvas.width(sign)) / 2,
            arrow.y0 + (arrow.height() - canvas.lineHeight() + 2) / 2,
            arrow.contains(mouseX, mouseY) ? Canvas.GOLD : Canvas.BODY,
            true);
    }
}
