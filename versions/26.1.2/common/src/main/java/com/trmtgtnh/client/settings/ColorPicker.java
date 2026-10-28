package com.trmtgtnh.client.settings;

import com.trmtgtnh.client.settings.SettingsGeometry.Rect;

/**
 * The path color's picker: its state, its layout and its drawing (0.9.228; spec M10.3 and M10.4).
 *
 * <p>
 * A box 220 by 170 in the middle of the screen: a saturation-value square 128 by 128, a hue bar 12 by 128 beside it,
 * a row of eight ready colors under them, and at the right a preview strip - a run of worn path in the color, drawn by
 * the edition at the rectangle {@link #preview()} hands it - the hex field under that, and Apply and Cancel. Dragging
 * in the square or the bar moves its ring or marker, and the hex and the preview follow on the same frame; typing a
 * hex moves them back ({@link #typed}). A hex that does not read holds Apply, with its reason, until it does. The color
 * reaches the setting only on Apply, as one undoable step; Cancel and Escape leave it as it was.
 */
public final class ColorPicker {

    public static final int WIDTH = 220;

    public static final int HEIGHT = 170;

    public static final int SQUARE = 128;

    public static final int BAR = 12;

    /** A line of the small (0.75) font, and how many of them the preview's words may take under the strip. */
    public static final int SMALL_LINE = 7;

    public static final int PREVIEW_WORDS_LINES = 4;

    /** The shipped default first, then seven to try. */
    private static final int[] READY = { 0xAA44CC, 0xE04040, 0xE0A030, 0xE0E040, 0x40C040, 0x40C0E0, 0x4060E0,
        0xFFFFFF };

    /** What a press did. */
    public static final int NONE = 0;

    public static final int TOOK = 1;

    public static final int APPLY = 2;

    public static final int CANCEL = 3;

    private static final int SQUARE_PART = 1;

    private static final int BAR_PART = 2;

    private float hue;

    private float saturation;

    private float value;

    private int rgb;

    private String hexText;

    private NumberWords.Refusal refusal;

    private int dragging;

    private Rect box;

    private Rect square;

    private Rect bar;

    private Rect[] swatches;

    private Rect preview;

    private Rect previewWords;

    private Rect hexWords;

    private Rect hexField;

    private Rect apply;

    private Rect cancel;

    public ColorPicker(int rgb) {
        take(rgb & 0xFFFFFF, 0f);
    }

    /** The ready colors as the row draws them: eight, the default first. */
    public static int[] ready() {
        return READY.clone();
    }

    private void take(int color, float keepHue) {
        rgb = color;
        float[] hsv = ColorMath.rgbToHsv(color, keepHue);
        hue = hsv[0];
        saturation = hsv[1];
        value = hsv[2];
        hexText = ColorMath.hex(color);
        refusal = null;
    }

    /** Places the box in the middle of the screen and everything inside it. */
    public void layout(Rect screen) {
        int x = screen.x0 + (screen.width() - WIDTH) / 2;
        int y = screen.y0 + (screen.height() - HEIGHT) / 2;
        box = Rect.sized(x, y, WIDTH, HEIGHT);
        square = Rect.sized(x + 6, y + 18, SQUARE, SQUARE);
        bar = Rect.sized(square.x1 + 3, y + 18, BAR, SQUARE);
        int right = bar.x1 + 3;
        preview = Rect.sized(right, y + 18, 64, 16);
        // The preview's words under it, four lines of the small font (0.9.228): written on one line they ran into the
        // hex field's own label and the field, which stood 6 pixels under the strip.
        previewWords = Rect.sized(right, y + 36, 64, PREVIEW_WORDS_LINES * SMALL_LINE);
        hexWords = Rect.sized(right, previewWords.y1 + 2, 64, SMALL_LINE);
        hexField = Rect.sized(right, hexWords.y1 + 2, 64, 12);
        apply = Rect.sized(right, y + 110, 64, 16);
        cancel = Rect.sized(right, y + 130, 64, 16);
        int[] ready = ready();
        swatches = new Rect[ready.length];
        for (int i = 0; i < ready.length; i++) {
            swatches[i] = Rect.sized(square.x0 + i * 16, y + 150, 14, 14);
        }
    }

    public int rgb() {
        return rgb;
    }

    public float hue() {
        return hue;
    }

    public float saturation() {
        return saturation;
    }

    public float value() {
        return value;
    }

    /** The hex as its field shows it: the color's, or what was typed while it does not read. */
    public String hexText() {
        return hexText;
    }

    /** Why the typed hex is refused, or null. */
    public NumberWords.Refusal refusal() {
        return refusal;
    }

    /** Whether Apply is held: while the typed hex does not read. */
    public boolean applyHeld() {
        return refusal != null;
    }

    public Rect box() {
        return box;
    }

    public Rect square() {
        return square;
    }

    public Rect bar() {
        return bar;
    }

    public Rect swatch(int index) {
        return swatches[index];
    }

    /** The preview strip's place: the edition draws a run of worn path in {@link #rgb()} there. */
    public Rect preview() {
        return preview;
    }

    /**
     * Where the edition writes what the preview shows, wrapped to its width in the small font, at most
     * {@link #PREVIEW_WORDS_LINES} lines.
     */
    public Rect previewWords() {
        return previewWords;
    }

    /** The hex field's label, which this class writes. */
    public Rect hexWords() {
        return hexWords;
    }

    /** Where the edition puts the hex field. */
    public Rect hexField() {
        return hexField;
    }

    public Rect apply() {
        return apply;
    }

    public Rect cancel() {
        return cancel;
    }

    /** Where the ring stands in the square. */
    public int ringX() {
        return square.x0 + Math.round(saturation * (SQUARE - 1));
    }

    public int ringY() {
        return square.y0 + Math.round((1f - value) * (SQUARE - 1));
    }

    /** Where the marker stands on the hue bar. */
    public int markerY() {
        return bar.y0 + Math.round(hue * (SQUARE - 1));
    }

    /** A press: in the square or the bar it starts a drag, on a ready color it takes it, on a button it says which. */
    public int press(int x, int y) {
        if (square.contains(x, y)) {
            dragging = SQUARE_PART;
            drag(x, y);
            return TOOK;
        }
        if (bar.contains(x, y)) {
            dragging = BAR_PART;
            drag(x, y);
            return TOOK;
        }
        int[] ready = ready();
        for (int i = 0; i < swatches.length; i++) {
            if (swatches[i].contains(x, y)) {
                take(ready[i], hue);
                return TOOK;
            }
        }
        if (apply.contains(x, y)) return applyHeld() ? TOOK : APPLY;
        if (cancel.contains(x, y)) return CANCEL;
        return box.contains(x, y) ? TOOK : NONE;
    }

    /** A drag that started in the square or the bar follows the pointer, clamped to its part. */
    public void drag(int x, int y) {
        if (dragging == SQUARE_PART) {
            saturation = unit((x - square.x0) / (float) (SQUARE - 1));
            value = 1f - unit((y - square.y0) / (float) (SQUARE - 1));
        } else if (dragging == BAR_PART) {
            hue = unit((y - bar.y0) / (float) (SQUARE - 1));
            if (hue >= 1f) hue = 0.999f;
        } else {
            return;
        }
        rgb = ColorMath.hsvToRgb(hue, saturation, value);
        hexText = ColorMath.hex(rgb);
        refusal = null;
    }

    public void release() {
        dragging = 0;
    }

    public boolean dragging() {
        return dragging != 0;
    }

    private static float unit(float at) {
        return at < 0f ? 0f : at > 1f ? 1f : at;
    }

    /** The hex field's text as typed: a reading moves the ring and the marker; a refusal holds Apply. */
    public void typed(String text) {
        ColorMath.Parsed parsed = ColorMath.parse(text);
        if (parsed.reads()) {
            take(parsed.rgb, hue);
            return;
        }
        hexText = text;
        refusal = parsed.refusal;
    }

    /**
     * Draws the box, the square, the bar, the ready colors and the buttons. The hex field and the preview strip are the
     * edition's, at {@link #hexField()} and {@link #preview()}.
     */
    public void draw(Canvas canvas, int mouseX, int mouseY) {
        canvas.panel(box.x0, box.y0, box.x1, box.y1);
        Canvas.frame(canvas, box.x0, box.y0, box.x1, box.y1, Canvas.GOLD);
        canvas.text(canvas.words(SettingsKeys.COLOR_PICK), box.x0 + 6, box.y0 + 5, Canvas.GOLD, true);

        int pure = 0xFF000000 | ColorMath.hsvToRgb(hue, 1f, 1f);
        canvas.gradient(square.x0, square.y0, square.x1, square.y1, 0xFFFFFFFF, pure, 0xFF000000, 0xFF000000);
        int ringColor = value > 0.5f ? 0xFF000000 : 0xFFFFFFFF;
        int rx = ringX();
        int ry = ringY();
        Canvas.frame(canvas, rx - 2, ry - 2, rx + 3, ry + 3, ringColor);

        for (int part = 0; part < 6; part++) {
            int top = bar.y0 + part * SQUARE / 6;
            int bottom = bar.y0 + (part + 1) * SQUARE / 6;
            int upper = 0xFF000000 | ColorMath.hsvToRgb(part / 6f, 1f, 1f);
            int lower = 0xFF000000 | ColorMath.hsvToRgb((part + 1) / 6f, 1f, 1f);
            canvas.gradient(bar.x0, top, bar.x1, bottom, upper, upper, lower, lower);
        }
        int my = markerY();
        canvas.fill(bar.x0 - 2, my, bar.x1 + 2, my + 1, 0xFFFFFFFF);
        canvas.fill(bar.x0 - 2, my - 1, bar.x1 + 2, my, 0xFF000000);
        canvas.fill(bar.x0 - 2, my + 1, bar.x1 + 2, my + 2, 0xFF000000);

        int[] ready = ready();
        for (int i = 0; i < swatches.length; i++) {
            Rect one = swatches[i];
            canvas.fill(one.x0, one.y0, one.x1, one.y1, 0xFF000000 | ready[i]);
            Canvas.frame(
                canvas,
                one.x0,
                one.y0,
                one.x1,
                one.y1,
                one.contains(mouseX, mouseY) || ready[i] == rgb ? Canvas.GOLD : Canvas.RULE);
        }

        canvas.text(canvas.words(SettingsKeys.COLOR_HEX), hexWords.x0, hexWords.y0, Canvas.GREY, 0.75f);
        if (refusal != null) {
            String why = canvas.words(refusal.key, refusal.arg == null ? new Object[0] : new Object[] { refusal.arg });
            canvas.text(
                canvas.trim(why, (int) ((box.x1 - hexField.x0 - 4) / 0.75f)),
                hexField.x0,
                hexField.y1 + 2,
                Canvas.RED,
                0.75f);
        }
        button(canvas, apply, canvas.words(SettingsKeys.COLOR_APPLY), !applyHeld(), mouseX, mouseY);
        button(canvas, cancel, canvas.words(SettingsKeys.GUI_CANCEL), true, mouseX, mouseY);
    }

    private static void button(Canvas canvas, Rect at, String label, boolean enabled, int mouseX, int mouseY) {
        canvas.fill(at.x0, at.y0, at.x1, at.y1, Canvas.TAB_IDLE);
        boolean under = enabled && at.contains(mouseX, mouseY);
        Canvas.frame(canvas, at.x0, at.y0, at.x1, at.y1, under ? Canvas.GOLD : Canvas.RULE);
        String shown = canvas.trim(label, at.width() - 4);
        canvas.text(
            shown,
            at.x0 + (at.width() - canvas.width(shown)) / 2,
            at.y0 + (at.height() - canvas.lineHeight() + 2) / 2,
            enabled ? Canvas.WHITE : Canvas.GREY,
            true);
    }
}
