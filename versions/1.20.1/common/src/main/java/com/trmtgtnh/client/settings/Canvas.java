package com.trmtgtnh.client.settings;

import java.util.List;

/**
 * What an edition gives the shared settings widgets to draw with (0.9.228; docs/goals/0.9.228-settings-spec.md, Part
 * 2).
 *
 * <p>
 * The settings screen is drawn the same way in all eight editions: the same rectangles, the same colors, the same
 * words. Everything that decides where a thing goes and what color it is lives in this package, in plain Java; what
 * differs between the games is only how a rectangle, a line of text or a picture reaches the screen. So each edition
 * writes one class that answers these calls (its {@code SettingsCanvas}) and the widgets here draw through it. A test
 * draws through a stand-in that writes the calls down, which is how a widget's drawing is held without a game.
 *
 * <p>
 * Every color here is an ARGB int. The palette is the V2 artwork's (spec 1.3), and on the oldest editions no fill may
 * be fainter than {@code 0x1A} alpha, because their alpha test throws such a pixel away: every token below is opaque,
 * {@link #HOVER_BG} too since 0.9.228.
 */
public interface Canvas {

    /** Setting names, picture names, the open tab's label, chip names, the header's words. */
    int WHITE = 0xFFFCF8F0;

    /** Values, the body of a hover, idle tab labels, status text, the range line. */
    int BODY = 0xFFDED5C1;

    /** The panel frame, the header rule, the open tab's underline, folder headings, the changed mark, focus. */
    int GOLD = 0xFFF0C460;

    /** Rules between rows, the scrollbar thumb, chip frames. */
    int RULE = 0xFF664F2A;

    /** The rule under a list page's column headers. */
    int RULE_DARK = 0xFF614C27;

    /** An idle tab's fill, picture frames, chip fill. */
    int TAB_IDLE = 0xFF302115;

    /** The open tab's fill, the scrollbar track. */
    int TAB_OPEN = 0xFF271A11;

    /** Ids under names, ghost suggestion text, disabled controls' text, a server-locked value. */
    int GREY = 0xFF9A8F7A;

    /** The name of a setting greyed by its switch. */
    int GREYED = 0xFF6F6452;

    /** Unmatched names, refused values, refusal reasons, the refusal dot. */
    int RED = 0xFFFF5555;

    /** The banner's fill; its frame is {@link #RED}. */
    int BANNER = 0xFF5A1A14;

    /**
     * The hover box, framed in {@link #GOLD}. Opaque from 0.9.228: at the game's own tooltip alpha, {@code 0xF0}, the
     * rows' words showed through it behind the hover's own.
     */
    int HOVER_BG = 0xFF100A06;

    /** Text shadow, where an edition draws one. */
    int OUTLINE = 0xFF000000;

    /** The whole screen behind the panel is the pack's dirt with each channel times this. */
    float DIRT_BG_FACTOR = 0.22f;

    /** Inside the list panel, the pack's dirt with each channel times this. */
    float DIRT_PANEL_FACTOR = 0.16f;

    /**
     * The icon atlas's cells, left to right, top to bottom: {@code textures/gui/settings_icons.png} is 128 by 48, eight
     * cells of 16 by 16 to a row. The art script writes them in this order, so a name's cell is its index here.
     */
    String[] ICONS = { "reset", "undo", "lock", "warning", "every", "fold_closed", "fold_open", "search", "copy",
        "paste", "drag", "remove", "toggle", "help", "undo_all", "reset_tab", "push", "weartable", "dropdown", "add",
        "pick", "more", "shift", "info" };

    /** The atlas's size in pixels, and one cell's. */
    int ICON_ATLAS_WIDTH = 128;

    int ICON_ATLAS_HEIGHT = 48;

    int ICON_CELL = 16;

    void fill(int x0, int y0, int x1, int y1, int argb);

    /**
     * A fill in {@code DIRT_PANEL}: the pack's own dirt times {@link #DIRT_PANEL_FACTOR}, which only the edition can
     * compose, since it follows the resource pack. Inside the list panel, a dropdown and the color picker.
     */
    void panel(int x0, int y0, int x1, int y1);

    /** A rectangle with a color at each corner, blended across it (the color picker's square needs all four). */
    void gradient(int x0, int y0, int x1, int y1, int argbTopLeft, int argbTopRight, int argbBottomLeft,
        int argbBottomRight);

    void text(String text, int x, int y, int argb, boolean shadow);

    /** Text drawn smaller or larger, with its shadow, its top left at x and y in unscaled pixels. */
    void text(String text, int x, int y, int argb, float scale);

    int width(String text);

    int lineHeight();

    /** The text cut to fit the width, ending in "..." when it was cut. */
    String trim(String text, int width);

    /** Clips everything drawn until the matching {@link #popClip()}; clips stack, each inside the one before. */
    void pushClip(int x0, int y0, int x1, int y1);

    void popClip();

    /** One of {@link #ICONS}, 16 by 16 (the {@code every} badge is the cell's top left 8 by 8). */
    void icon(String name, int x, int y);

    /**
     * A picture the edition resolves: a block, an item, a mob, a sprite, one of {@link Slider.Piece}'s worn-bar pieces.
     * Drawn 16 by 16 unless the ref is a {@link Scaled}, which says its own size, or a piece, which carries its own.
     */
    void picture(Object ref, int x, int y);

    /** A hover, drawn after everything else this frame: the lines as given, placed by {@link HoverText}. */
    void hover(List<String> lines, int x, int y);

    /** A word key translated by the edition's language file, its arguments put in. */
    String words(String key, Object... args);

    /** A picture asked for at a size other than 16 by 16: the 32 by 32 picture of a dropdown's detail panel. */
    final class Scaled {

        public final Object ref;

        public final int size;

        public Scaled(Object ref, int size) {
            this.ref = ref;
            this.size = size;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Scaled)) return false;
            Scaled that = (Scaled) other;
            return size == that.size && (ref == null ? that.ref == null : ref.equals(that.ref));
        }

        @Override
        public int hashCode() {
            return (ref == null ? 0 : ref.hashCode()) * 31 + size;
        }

        @Override
        public String toString() {
            return ref + "@" + size;
        }
    }

    /** Where an icon's cell is in the atlas, by its index in {@link #ICONS}: -1 for a name the atlas does not hold. */
    static int iconCell(String name) {
        for (int i = 0; i < ICONS.length; i++) {
            if (ICONS[i].equals(name)) return i;
        }
        return -1;
    }

    /** The pack's dirt color darkened for a background: each channel times the factor, opaque. */
    static int darken(int rgb, float factor) {
        int out = 0xFF000000;
        for (int shift = 16; shift >= 0; shift -= 8) {
            int channel = Math.round(((rgb >>> shift) & 0xFF) * factor);
            if (channel > 255) channel = 255;
            out |= channel << shift;
        }
        return out;
    }

    /** A rectangle's 1-pixel frame, drawn inside its edges. */
    static void frame(Canvas canvas, int x0, int y0, int x1, int y1, int argb) {
        canvas.fill(x0, y0, x1, y0 + 1, argb);
        canvas.fill(x0, y1 - 1, x1, y1, argb);
        canvas.fill(x0, y0 + 1, x0 + 1, y1 - 1, argb);
        canvas.fill(x1 - 1, y0 + 1, x1, y1 - 1, argb);
    }
}
