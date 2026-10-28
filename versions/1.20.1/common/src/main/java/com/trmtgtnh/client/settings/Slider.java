package com.trmtgtnh.client.settings;

import java.math.BigDecimal;

import com.trmtgtnh.client.settings.SettingsGeometry.Rect;

/**
 * A slider's state and drawing: a strip of lawn the handle wears to dirt as it moves, path behind it and grass ahead
 * (0.9.228; spec M7).
 *
 * <p>
 * The look is {@code WearSlider}'s from 0.9.228's first screen, made general for decimals: the edition draws the bar
 * and the handle when asked for a {@link Piece} - its {@code WornPathArt} composes the worn strip - and everything
 * else, where the handle stands, what a drag or a key does, what it snaps to, is here. The handle snaps as it moves
 * ({@link SliderRule#value}), so it always stands where the number says.
 */
public final class Slider {

    /** The handle's width; the bar's ends keep half of it, so the handle's middle reaches each end of the range. */
    public static final int HANDLE = 8;

    /**
     * A piece of the slider the edition draws at its own size: {@code bar} is the worn strip, worn up to
     * {@code fraction}; {@code handle} the game's own slider handle.
     */
    public static final class Piece {

        public static final String BAR = "bar";

        public static final String HANDLE = "handle";

        public final String name;

        public final int width;

        public final int height;

        public final double fraction;

        /** How many wear stages the bar's track runs through ({@link Slider#stagesFor}). */
        public final int stages;

        public Piece(String name, int width, int height, double fraction) {
            this(name, width, height, fraction, LEAST_STAGES);
        }

        public Piece(String name, int width, int height, double fraction, int stages) {
            this.name = name;
            this.width = width;
            this.height = height;
            this.fraction = fraction;
            this.stages = stages;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Piece)) return false;
            Piece that = (Piece) other;
            return name.equals(that.name) && width == that.width
                && height == that.height
                && Double.compare(fraction, that.fraction) == 0
                && stages == that.stages;
        }

        @Override
        public int hashCode() {
            return name.hashCode() * 31 + width * 7 + height;
        }

        @Override
        public String toString() {
            return name + " " + width + "x" + height + " at " + fraction;
        }
    }

    /**
     * The fewest wear stages a slider's track runs through (0.9.228; Xep, 2026-10-26: "0 to 1 sliders should be more
     * like this, with more possible options for wear ... They should have say 10 by default?", and then every slider:
     * "Ideally the min should be 10"). The track had a square of wear per bar height, five on every bar. Six from the
     * photographs of the worn-grass track, each cell walking grass's own wear chain (Xep, 2026-10-26, shown ten cells
     * beside six: "6 cells").
     */
    public static final int LEAST_STAGES = 6;

    /**
     * The stages a slider's track runs through: {@link #LEAST_STAGES}, but one a value for a whole-number slider with
     * fewer values than that (Wear variations, 1 to 4, shows four).
     */
    public static int stagesFor(double min, double max, char kind) {
        if (kind == 'I') {
            long values = Math.round(max - min) + 1;
            if (values >= 2 && values < LEAST_STAGES) return (int) values;
        }
        return LEAST_STAGES;
    }

    /**
     * How worn each of a track's {@code cells} is at the slider's {@code along}, its value as a fraction of its range:
     * nought for untouched grass to one for grass worn as far as it goes before the ground sinks (0.9.228; Xep,
     * 2026-10-26: "It should show the grass texture for that version, degrading to fully worn (top layer only) grass as
     * you slide. So a slider in the middle of a segment would be half worn"). Every cell behind the handle fully worn,
     * every cell ahead untouched, and the cell it stands in worn as far as the handle has come into it - so a slider at
     * its least is all grass and at its most all worn. Each edition draws a cell as its own grass top at the matching
     * gradation of this mod's own worn-grass pictures.
     */
    public static double[] cellWear(int cells, double along) {
        int count = Math.max(1, cells);
        double at = Math.max(0D, Math.min(1D, along)) * count;
        double[] wear = new double[count];
        for (int i = 0; i < count; i++) {
            wear[i] = Math.max(0D, Math.min(1D, at - i));
        }
        return wear;
    }

    private final double min;

    private final double max;

    private final char kind;

    private double value;

    private Rect bar;

    private boolean held;

    /**
     * @param kind {@code 'I'} whole; {@code 'D'} or {@code 'F'} decimal
     */
    public Slider(double value, double min, double max, char kind) {
        this.min = min;
        this.max = max;
        this.kind = kind;
        this.value = SliderRule.value(SliderRule.fraction(value, min, max), min, max, kind);
    }

    public void layout(Rect bar) {
        this.bar = bar;
    }

    public Rect bar() {
        return bar;
    }

    public double value() {
        return value;
    }

    /** The value as the typed field beside it writes it. */
    public String text() {
        if (kind == 'I') return Long.toString(Math.round(value));
        return NumberWords.number(value);
    }

    /** Moved by the typed field beside it (spec M7.2): snapped and clamped, as a drag would leave it. */
    public void set(double to) {
        value = SliderRule.value(SliderRule.fraction(to, min, max), min, max, kind);
    }

    public double fraction() {
        return SliderRule.fraction(value, min, max);
    }

    public boolean held() {
        return held;
    }

    /** Holds the handle as if dragged, for the photograph of a slider in use. */
    public void hold(boolean held) {
        this.held = held;
    }

    /** A press on the bar takes the handle to the pointer and holds it. Answers whether the press was the bar's. */
    public boolean press(int x, int y) {
        if (bar == null || !bar.contains(x, y)) return false;
        held = true;
        follow(x);
        return true;
    }

    /** While held, the handle follows the pointer, wherever it has gone. Answers whether the value moved. */
    public boolean drag(int x, int y) {
        if (!held) return false;
        double before = value;
        follow(x);
        return before != value;
    }

    public void release() {
        held = false;
    }

    private void follow(int x) {
        int travel = bar.width() - HANDLE;
        double placed = travel <= 0 ? 0D : (x - (bar.x0 + HANDLE / 2)) / (double) travel;
        value = SliderRule.value(placed, min, max, kind);
    }

    /**
     * Left or Right on a focused slider: one step, ten with Shift (spec 1.6). Answers whether the value moved.
     *
     * @param direction 1 right, -1 left
     */
    public boolean key(int direction, boolean shift) {
        double before = value;
        BigDecimal step = SliderRule.stepDecimal(kind, min, max);
        BigDecimal by = step.multiply(new BigDecimal((direction < 0 ? -1 : 1) * (shift ? 10 : 1)));
        double to = BigDecimal.valueOf(value)
            .add(by)
            .doubleValue();
        to = to < min ? min : to > max ? max : to;
        value = SliderRule.value(SliderRule.fraction(to, min, max), min, max, kind);
        return before != value;
    }

    /** The handle's left edge for the value it stands at. */
    public int handleX() {
        return bar.x0 + (int) Math.round(fraction() * (bar.width() - HANDLE));
    }

    /**
     * Draws the worn bar, the handle and a frame: gold while held, focused or under the pointer, black otherwise.
     */
    public void draw(Canvas canvas, boolean focused, int mouseX, int mouseY) {
        if (bar == null) return;
        canvas.picture(
            new Piece(Piece.BAR, bar.width(), bar.height(), fraction(), stagesFor(min, max, kind)),
            bar.x0,
            bar.y0);
        canvas.picture(new Piece(Piece.HANDLE, HANDLE, bar.height(), fraction()), handleX(), bar.y0);
        boolean lit = held || focused || bar.contains(mouseX, mouseY);
        Canvas.frame(canvas, bar.x0, bar.y0, bar.x1, bar.y1, lit ? Canvas.GOLD : Canvas.OUTLINE);
    }
}
