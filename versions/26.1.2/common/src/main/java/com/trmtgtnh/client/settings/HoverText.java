package com.trmtgtnh.client.settings;

import java.util.ArrayList;
import java.util.List;

import com.trmtgtnh.client.settings.SettingsGeometry.Rect;

/**
 * Hovers that fit (0.9.228; spec M19).
 *
 * <p>
 * Xep, playing it in GT New Horizons: "Some of the tooltips are too big to fit on my screen" - a setting's whole
 * comment, a paragraph, running off the top. So every hover here is wrapped to {@code min(260, W - 16)}, is never
 * taller than the screen less 16, and is placed so it stays inside an 8-pixel margin whatever corner the pointer is
 * in. A text too long to fit - a whole description, shown while Shift is held - shows the lines that fit with a line
 * at its foot saying which they are, and the wheel pages it ({@link #page}, {@link #scroll}).
 */
public final class HoverText {

    /** The widest a hover's text is wrapped to, and the margin it keeps from the screen's edges. */
    public static final int MAX_WIDTH = 260;

    public static final int MARGIN = 8;

    /** The box's padding round its text, and the height of one line. */
    public static final int PAD = 4;

    public static final int LINE = 10;

    /** How far from the pointer a hover is drawn, as the game's own are. */
    public static final int OFFSET = 12;

    private HoverText() {}

    /**
     * The width a hover's text is wrapped to on a screen this wide: {@code min(260, W - 16 - 2 * PAD)}, so the box,
     * which adds its padding round the text, stays inside the margins (0.9.228: below 276 GUI pixels wide it crossed
     * them by its padding).
     */
    public static int width(int screenW) {
        return Math.min(MAX_WIDTH, screenW - 2 * MARGIN - 2 * PAD);
    }

    /**
     * Paragraphs cut into lines no wider than {@code width}, at spaces where there are any; a word longer than a line
     * is
     * cut where it must be. An empty paragraph is an empty line, so a hover can leave a gap.
     */
    public static List<String> wrap(Canvas canvas, List<String> paragraphs, int width) {
        List<String> out = new ArrayList<String>();
        for (String paragraph : paragraphs) {
            if (paragraph == null || paragraph.isEmpty()) {
                out.add("");
                continue;
            }
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                if (word.isEmpty()) continue;
                String tried = line.length() == 0 ? word : line + " " + word;
                if (canvas.width(tried) <= width) {
                    line.setLength(0);
                    line.append(tried);
                    continue;
                }
                if (line.length() > 0) {
                    out.add(line.toString());
                    line.setLength(0);
                }
                String rest = word;
                while (canvas.width(rest) > width) {
                    int cut = fits(canvas, rest, width);
                    out.add(rest.substring(0, cut));
                    rest = rest.substring(cut);
                }
                line.append(rest);
            }
            if (line.length() > 0) out.add(line.toString());
        }
        return out;
    }

    /** How many of a word's characters fit a width; at least one, so a cut always moves on. */
    private static int fits(Canvas canvas, String word, int width) {
        int count = 1;
        while (count < word.length() && canvas.width(word.substring(0, count + 1)) <= width) count++;
        return count;
    }

    /**
     * Where a hover box {@code w} by {@code h} goes: below and right of the pointer as the game's own hovers go, to its
     * left where the right would cross the margin, and moved in from any edge it would cross. A box larger than the
     * screen less its margins starts at the margin.
     */
    public static Rect place(int w, int h, int mouseX, int mouseY, int screenW, int screenH) {
        int x = mouseX + OFFSET;
        if (x + w > screenW - MARGIN) x = mouseX - OFFSET - w;
        if (x + w > screenW - MARGIN) x = screenW - MARGIN - w;
        if (x < MARGIN) x = MARGIN;
        int y = mouseY - OFFSET;
        if (y + h > screenH - MARGIN) y = screenH - MARGIN - h;
        if (y < MARGIN) y = MARGIN;
        return Rect.sized(x, y, w, h);
    }

    /** The lines of a long hover that show at once, and which they are. */
    public static final class Page {

        public final List<String> lines;

        /** The first line shown, counted from 1, and the last; and how many there are in all. */
        public final int from;

        public final int to;

        public final int of;

        /** Whether some lines are not shown, so the page's foot line ({@code hover.page}) is drawn under them. */
        public final boolean paged;

        Page(List<String> lines, int from, int to, int of, boolean paged) {
            this.lines = lines;
            this.from = from;
            this.to = to;
            this.of = of;
            this.paged = paged;
        }
    }

    /** How many lines of text fit a hover on a screen this tall, inside its margins and padding. */
    public static int fits(int screenH) {
        return Math.max(1, (screenH - 2 * MARGIN - 2 * PAD) / LINE);
    }

    /**
     * The lines that show from {@code firstLine} (counted from nought): all of them when they fit; otherwise as many as
     * fit with one line kept for the foot, never past the last, so paging ends at the last line.
     */
    public static Page page(List<String> lines, int firstLine, int screenH) {
        int total = lines.size();
        int room = fits(screenH);
        if (total <= room) return new Page(new ArrayList<String>(lines), total == 0 ? 0 : 1, total, total, false);
        int shown = Math.max(1, room - 1);
        int first = scroll(firstLine, 0, total, shown);
        List<String> out = new ArrayList<String>(lines.subList(first, first + shown));
        return new Page(out, first + 1, first + shown, total, true);
    }

    /** The first line after one notch of the wheel, never before the first or past where the last line shows. */
    public static int scroll(int firstLine, int direction, int total, int fits) {
        int last = Math.max(0, total - fits);
        int to = firstLine + direction;
        return to < 0 ? 0 : to > last ? last : to;
    }

    /** A hover ready to draw: its lines and its box. */
    public static final class Box {

        public final List<String> lines;

        public final Rect rect;

        Box(List<String> lines, Rect rect) {
            this.lines = lines;
            this.rect = rect;
        }
    }

    /**
     * Lines sized and placed by the pointer, wrapped first to the width a hover may take (0.9.228): a line longer than
     * that - a list row's status, one sentence - ran out past the box, which is never wider.
     */
    public static Box box(Canvas canvas, List<String> given, int mouseX, int mouseY, int screenW, int screenH) {
        List<String> lines = wrap(canvas, given, width(screenW));
        int widest = 0;
        for (String line : lines) widest = Math.max(widest, canvas.width(line));
        widest = Math.min(widest, width(screenW));
        int h = lines.size() * LINE - 2 + 2 * PAD;
        Rect rect = place(widest + 2 * PAD, h, mouseX, mouseY, screenW, screenH);
        return new Box(lines, rect);
    }

    /** Draws a box: {@link Canvas#HOVER_BG} framed in gold, the first line gold as a title, the rest in body color. */
    public static void draw(Canvas canvas, Box box) {
        Rect at = box.rect;
        canvas.fill(at.x0, at.y0, at.x1, at.y1, Canvas.HOVER_BG);
        Canvas.frame(canvas, at.x0, at.y0, at.x1, at.y1, Canvas.GOLD);
        int y = at.y0 + PAD;
        for (int i = 0; i < box.lines.size(); i++) {
            canvas.text(box.lines.get(i), at.x0 + PAD, y, i == 0 ? Canvas.GOLD : Canvas.BODY, true);
            y += LINE;
        }
    }
}
