package com.trmtgtnh.client.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.lwjgl.input.Keyboard;

import com.trmtgtnh.client.settings.Canvas;
import com.trmtgtnh.client.settings.Edits;
import com.trmtgtnh.client.settings.KeyedText;
import com.trmtgtnh.client.settings.SettingsGeometry.Rect;
import com.trmtgtnh.client.settings.SettingsKeys;
import com.trmtgtnh.client.settings.Slider;
import com.trmtgtnh.config.Presets;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.surface.SurfaceFamily;

/**
 * What the presets remember, under the Presets tab's Advanced fold (0.9.228, spec M16.2; Xep, from his GT New Horizons
 * notes: "The 'Remembered' section in general is very hard to understand from a UX perspective"). Until 0.9.228 these
 * were ten rows of raw text - {@code grass=0;dirt=8;sand=8}, {@code 80,4,262144,1024,1} - in the Presets list itself.
 *
 * <p>
 * Now each is what it means: under each preset, the preset it last applied, as its title, and the numbers Custom
 * brings back - a row per surface, with the surface's picture and name and a field for each number (its depth in
 * pixels, its days to heal a stage, its stages and layers per pixel, where its wear starts and ends), and for picture
 * quality its five controls: the two counts as sliders, the sprite ceiling and the textured surfaces as fields, the
 * per-surface switch. Every edit is written back through the shared {@link KeyedText} in the file's own form, so the
 * preset machinery reads it exactly as before; an empty value shows its rows empty, and the first number typed into
 * one starts it.
 *
 * <p>
 * Its own fields, drawn on the {@link Canvas} and typed into through {@link #keyTyped}, so a test can drive the whole
 * of it without a game: digits and a point, Backspace, Up and Down to step, Tab to the next field, Enter or Escape to
 * let go; Left and Right step a focused slider.
 */
public final class RememberedRows {

    /** A row's height; a field is {@link #FIELD_HEIGHT} high in it. */
    static final int LINE = 16;

    static final int FIELD_WIDTH = 50;

    static final int FIELD_HEIGHT = 12;

    static final int SLIDER_WIDTH = 100;

    /** The settings it shows: {@code presets.remembered.*}. */
    static final String PREFIX = ConfigLayout.REMEMBERED + ".";

    /** The five presets, in the order the Presets tab shows them. */
    static final String[] AXES = { "quality", "wear", "healing", "phases", "depth" };

    /** What a row is. */
    enum Kind {
        HEADING,
        LAST,
        COLUMNS,
        FAMILY,
        SLIDER,
        NUMBER,
        SWITCH
    }

    /** One row: its kind, its preset, and for a surface's row the surface, for a quality control its part. */
    static final class Line {

        final Kind kind;

        final String axis;

        final SurfaceFamily family;

        final int part;

        /** The word key it is titled with: a heading's, a quality control's setting title. */
        final String words;

        Line(Kind kind, String axis, SurfaceFamily family, int part, String words) {
            this.kind = kind;
            this.axis = axis;
            this.family = family;
            this.part = part;
            this.words = words;
        }

        /** How many numbers a surface's row of this preset holds. */
        int parts() {
            return "wear".equals(axis) || "phases".equals(axis) ? 2 : 1;
        }
    }

    private final Edits edits;

    private final int left;

    private final int width;

    private final List<Line> lines;

    private int top;

    /** The focused field, by {@link #cell}, and what is typed into it; null when none is. */
    private String focus;

    private Slider dragging;

    private int draggingPart;

    private final Slider[] sliders = new Slider[2];

    public RememberedRows(Edits edits, int left, int width) {
        this.edits = edits;
        this.left = left;
        this.width = width;
        this.lines = lines();
        sliders[0] = new Slider(number(part("quality", 0), 80), 16, 80, 'I');
        sliders[1] = new Slider(number(part("quality", 1), 4), 1, 4, 'I');
    }

    // ------------------------------------------------------------------
    // What it shows - plain
    // ------------------------------------------------------------------

    /** The rows, top to bottom: each preset's heading, its last choice, then its numbers. */
    static List<Line> lines() {
        List<Line> out = new ArrayList<Line>();
        for (String axis : AXES) {
            out.add(new Line(Kind.HEADING, axis, null, 0, heading(axis)));
            out.add(new Line(Kind.LAST, axis, null, 0, null));
            if ("quality".equals(axis)) {
                out.add(new Line(Kind.SLIDER, axis, null, 0, title(TrmtConfig.CATEGORY_CLIENT, "wearGradations")));
                out.add(new Line(Kind.SLIDER, axis, null, 1, title(TrmtConfig.CATEGORY_CLIENT, "wearRotations")));
                out.add(new Line(Kind.NUMBER, axis, null, 2, title(TrmtConfig.CATEGORY_CLIENT, "maxWearSprites")));
                out.add(
                    new Line(Kind.NUMBER, axis, null, 3, title(TrmtConfig.CATEGORY_SURFACES, "maxTexturedSurfaces")));
                out.add(new Line(Kind.SWITCH, axis, null, 4, title(TrmtConfig.CATEGORY_CLIENT, "perSurfaceTextures")));
                continue;
            }
            if ("wear".equals(axis) || "phases".equals(axis)) out.add(new Line(Kind.COLUMNS, axis, null, 0, null));
            for (SurfaceFamily family : SurfaceFamily.staged()) out.add(new Line(Kind.FAMILY, axis, family, 0, null));
        }
        return out;
    }

    /** A preset's heading: the words the specification gives it, or for wear its remembered setting's own title. */
    static String heading(String axis) {
        if ("quality".equals(axis)) return SettingsKeys.ADVANCED_QUALITY;
        if ("healing".equals(axis)) return SettingsKeys.ADVANCED_HEALING;
        if ("phases".equals(axis)) return SettingsKeys.ADVANCED_PHASES;
        if ("depth".equals(axis)) return SettingsKeys.ADVANCED_DEPTH;
        return title(ConfigLayout.REMEMBERED, axis + "Custom");
    }

    private static String title(String category, String key) {
        return SettingsKeys.title(category, key);
    }

    /** The setting a preset's numbers are kept in, and the one its last choice is. */
    static String custom(String axis) {
        return PREFIX + axis + "Custom";
    }

    static String lastApplied(String axis) {
        return PREFIX + axis + "LastApplied";
    }

    /** A field's name: the preset, the surface (or none, for quality's), the number's place. */
    static String cell(String axis, SurfaceFamily family, int part) {
        return axis + "/" + (family == null ? "" : family.key()) + "/" + part;
    }

    /** What a field holds now, as the file holds it: empty where nothing is remembered. */
    String value(String axis, SurfaceFamily family, int part) {
        if (family == null) return part(axis, part);
        String text = edits.value(custom(axis));
        String held = KeyedText.keyed(text == null ? "" : text)
            .get(family.key());
        List<String> parts = KeyedText.commas(held == null ? "" : held);
        return part < parts.size() ? parts.get(part)
            .trim() : "";
    }

    private String part(String axis, int part) {
        String text = edits.value(custom(axis));
        List<String> parts = KeyedText.commas(text == null ? "" : text);
        return part < parts.size() ? parts.get(part)
            .trim() : "";
    }

    /**
     * A number typed into a field, written back in the file's own form (spec M16.2): a surface's number into its
     * {@code key=value} entry, a quality number into its place in the comma list. One edit.
     */
    void type(String axis, SurfaceFamily family, int part, String typed) {
        String id = custom(axis);
        String text = edits.value(id);
        if (text == null) text = "";
        String written;
        if (family == null) {
            written = KeyedText.withPart(text, part, typed);
        } else {
            String held = KeyedText.keyed(text)
                .get(family.key());
            written = KeyedText.with(text, family.key(), KeyedText.withPart(held == null ? "" : held, part, typed));
        }
        edits.set(id, written);
    }

    /** The preset a remembered record says was last applied, as its title's word key, or the value where unknown. */
    String lastChosen(String axis) {
        String value = edits.value(lastApplied(axis));
        return value == null || value.trim()
            .isEmpty() ? Presets.CUSTOM : value.trim();
    }

    // ------------------------------------------------------------------
    // The widget
    // ------------------------------------------------------------------

    public void layout(int top) {
        this.top = top;
    }

    public int height() {
        return lines.size() * LINE + 4;
    }

    public boolean focused() {
        return focus != null;
    }

    public void blur() {
        focus = null;
    }

    private int rowTop(int index) {
        return top + 2 + index * LINE;
    }

    /** The fields of a surface's row, right to left from the panel's edge. */
    private Rect field(int index, Line line, int part) {
        int parts = line.kind == Kind.FAMILY || line.kind == Kind.COLUMNS ? line.parts() : 1;
        int x = left + width - 6 - (parts - part) * (FIELD_WIDTH + 4);
        return Rect.sized(x, rowTop(index) + 2, FIELD_WIDTH, FIELD_HEIGHT);
    }

    private Rect sliderBar(int index) {
        return Rect.sized(left + width - 6 - FIELD_WIDTH - 4 - SLIDER_WIDTH, rowTop(index) + 3, SLIDER_WIDTH, 10);
    }

    public void draw(Canvas canvas, int mouseX, int mouseY) {
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            int y = rowTop(i);
            switch (line.kind) {
                case HEADING:
                    canvas.text(canvas.trim(canvas.words(line.words), width - 8), left + 4, y + 4, Canvas.GOLD, true);
                    canvas.fill(left, y + LINE - 1, left + width, y + LINE, Canvas.RULE);
                    break;
                case LAST: {
                    String value = lastChosen(line.axis);
                    String key = SettingsKeys.option(Presets.CATEGORY + "." + line.axis, value);
                    String named = canvas.words(key);
                    canvas.text(
                        canvas.words(SettingsKeys.ADVANCED_LAST_CHOSEN, key.equals(named) ? value : named),
                        left + 8,
                        y + 5,
                        Canvas.GREY,
                        0.75F);
                    break;
                }
                case COLUMNS: {
                    String[] heads = "wear".equals(line.axis)
                        ? new String[] { title("families.grass", "thresholdMin"),
                            title("families.grass", "thresholdMax") }
                        : new String[] { SettingsKeys.ADVANCED_STAGES, SettingsKeys.ADVANCED_LAYERS };
                    for (int part = 0; part < heads.length; part++) {
                        Rect at = field(i, line, part);
                        canvas.text(
                            canvas.trim(canvas.words(heads[part]), (int) ((FIELD_WIDTH + 2) / 0.75F)),
                            at.x0,
                            y + 6,
                            Canvas.GREY,
                            0.75F);
                    }
                    break;
                }
                case FAMILY: {
                    // The surface's sub-tab picture, its road block, which the canvas resolves.
                    canvas.fill(left + 8, y, left + 24, y + 16, Canvas.TAB_IDLE);
                    canvas.picture(line.family, left + 8, y);
                    canvas.text(
                        canvas.words(SettingsKeys.OWN + "family." + line.family.key()),
                        left + 28,
                        y + 4,
                        Canvas.WHITE,
                        true);
                    for (int part = 0; part < line.parts(); part++) {
                        drawField(
                            canvas,
                            field(i, line, part),
                            cell(line.axis, line.family, part),
                            value(line.axis, line.family, part));
                    }
                    break;
                }
                case SLIDER: {
                    label(canvas, line, y);
                    Slider slider = sliders[line.part];
                    slider.layout(sliderBar(i));
                    slider.draw(canvas, cell(line.axis, null, line.part).equals(focus), mouseX, mouseY);
                    drawField(
                        canvas,
                        field(i, line, 0),
                        cell(line.axis, null, line.part),
                        value(line.axis, null, line.part));
                    break;
                }
                case NUMBER:
                    label(canvas, line, y);
                    drawField(
                        canvas,
                        field(i, line, 0),
                        cell(line.axis, null, line.part),
                        value(line.axis, null, line.part));
                    break;
                case SWITCH: {
                    label(canvas, line, y);
                    Rect at = field(i, line, 0);
                    String value = value(line.axis, null, line.part);
                    boolean on = !value.isEmpty() && !"0".equals(value);
                    canvas.fill(at.x0, at.y0, at.x1, at.y1, Canvas.TAB_OPEN);
                    Canvas.frame(
                        canvas,
                        at.x0,
                        at.y0,
                        at.x1,
                        at.y1,
                        at.contains(mouseX, mouseY) ? Canvas.GOLD : Canvas.RULE);
                    String said = value.isEmpty() ? ""
                        : canvas.words(on ? SettingsKeys.OPTIONS_ON : SettingsKeys.OPTIONS_OFF);
                    canvas.text(
                        said,
                        at.x0 + (at.width() - canvas.width(said)) / 2,
                        at.y0 + 2,
                        on ? Canvas.WHITE : Canvas.BODY,
                        true);
                    break;
                }
                default:
                    break;
            }
        }
    }

    private void label(Canvas canvas, Line line, int y) {
        canvas.text(
            canvas.trim(canvas.words(line.words), width - FIELD_WIDTH - SLIDER_WIDTH - 24),
            left + 8,
            y + 4,
            Canvas.BODY,
            true);
    }

    private void drawField(Canvas canvas, Rect at, String cell, String value) {
        boolean focused = cell.equals(focus);
        canvas.fill(at.x0, at.y0, at.x1, at.y1, Canvas.TAB_OPEN);
        Canvas.frame(canvas, at.x0, at.y0, at.x1, at.y1, focused ? Canvas.GOLD : Canvas.RULE);
        boolean reads = value.isEmpty() || reads(value);
        String shown = focused && System.currentTimeMillis() / 500 % 2 == 0 ? value + "_" : value;
        canvas.text(canvas.trim(shown, at.width() - 4), at.x0 + 2, at.y0 + 2, reads ? Canvas.BODY : Canvas.RED, true);
    }

    private static boolean reads(String value) {
        try {
            Double.parseDouble(value);
            return true;
        } catch (NumberFormatException no) {
            return false;
        }
    }

    private static double number(String value, double otherwise) {
        try {
            return Double.parseDouble(value.trim());
        } catch (RuntimeException no) {
            return otherwise;
        }
    }

    /** The fields in the order Tab walks them. */
    private List<String> cells() {
        List<String> out = new ArrayList<String>();
        for (Line line : lines) {
            if (line.kind == Kind.FAMILY) {
                for (int part = 0; part < line.parts(); part++) out.add(cell(line.axis, line.family, part));
            } else if (line.kind == Kind.SLIDER || line.kind == Kind.NUMBER) {
                out.add(cell(line.axis, null, line.part));
            }
        }
        return out;
    }

    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            switch (line.kind) {
                case FAMILY:
                    for (int part = 0; part < line.parts(); part++) {
                        if (field(i, line, part).contains(mouseX, mouseY)) {
                            focus = cell(line.axis, line.family, part);
                            return true;
                        }
                    }
                    break;
                case SLIDER: {
                    Slider slider = sliders[line.part];
                    if (slider.bar() != null && slider.press(mouseX, mouseY)) {
                        focus = cell(line.axis, null, line.part);
                        dragging = slider;
                        draggingPart = line.part;
                        type(line.axis, null, line.part, slider.text());
                        return true;
                    }
                    if (field(i, line, 0).contains(mouseX, mouseY)) {
                        focus = cell(line.axis, null, line.part);
                        return true;
                    }
                    break;
                }
                case NUMBER:
                    if (field(i, line, 0).contains(mouseX, mouseY)) {
                        focus = cell(line.axis, null, line.part);
                        return true;
                    }
                    break;
                case SWITCH:
                    if (field(i, line, 0).contains(mouseX, mouseY)) {
                        String value = value(line.axis, null, line.part);
                        type(line.axis, null, line.part, "0".equals(value) ? "1" : "0");
                        focus = null;
                        return true;
                    }
                    break;
                default:
                    break;
            }
        }
        focus = null;
        return false;
    }

    public void mouseDragged(int mouseX, int mouseY) {
        if (dragging != null && dragging.drag(mouseX, mouseY)) type("quality", null, draggingPart, dragging.text());
    }

    public void mouseReleased(int mouseX, int mouseY) {
        if (dragging != null) dragging.release();
        dragging = null;
    }

    /**
     * A key while a field is focused: digits and a point type, Backspace takes one back, Up and Down step by one, Left
     * and Right step a slider, Tab walks the fields, Enter and Escape let go. False when no field is focused, so the
     * screen has the key.
     */
    public boolean keyTyped(char typed, int key) {
        if (focus == null) return false;
        String[] at = focus.split("/", -1);
        String axis = at[0];
        SurfaceFamily family = at[1].isEmpty() ? null : SurfaceFamily.byKey(at[1]);
        int part = Integer.parseInt(at[2]);
        String value = value(axis, family, part);
        if (key == Keyboard.KEY_ESCAPE || key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
            focus = null;
            return true;
        }
        if (key == Keyboard.KEY_TAB) {
            List<String> cells = cells();
            int index = cells.indexOf(focus);
            boolean back = Keyboard.isCreated()
                && (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT));
            int next = index + (back ? -1 : 1);
            focus = next >= 0 && next < cells.size() ? cells.get(next) : null;
            return focus != null;
        }
        if (family == null && part < sliders.length && (key == Keyboard.KEY_LEFT || key == Keyboard.KEY_RIGHT)) {
            Slider slider = sliders[part];
            if (slider.key(key == Keyboard.KEY_LEFT ? -1 : 1, false)) type(axis, null, part, slider.text());
            return true;
        }
        if (key == Keyboard.KEY_UP || key == Keyboard.KEY_DOWN) {
            double now = number(value, 0D);
            double stepped = Math.max(0D, now + (key == Keyboard.KEY_UP ? 1 : -1));
            String written = value.indexOf('.') >= 0 ? com.trmtgtnh.client.settings.NumberWords.number(stepped)
                : String.valueOf((long) stepped);
            type(axis, family, part, written);
            follow(family, part, written);
            return true;
        }
        if (key == Keyboard.KEY_BACK) {
            if (!value.isEmpty()) {
                String written = value.substring(0, value.length() - 1);
                type(axis, family, part, written);
                follow(family, part, written);
            }
            return true;
        }
        if (Character.isDigit(typed) || typed == '.' && value.indexOf('.') < 0) {
            String written = value + typed;
            type(axis, family, part, written);
            follow(family, part, written);
            return true;
        }
        // Anything else is not a number's: taken, so it reaches no other control while a field is being typed in.
        return true;
    }

    /** A quality count typed into its field moves its slider with it. */
    private void follow(SurfaceFamily family, int part, String written) {
        if (family == null && part < sliders.length && reads(written)) sliders[part].set(number(written, 0D));
    }

    /** The rows' values, for a test: every remembered setting's id and value as the edits hold it. */
    Map<String, String> held() {
        Map<String, String> out = new java.util.LinkedHashMap<String, String>();
        for (String axis : AXES) {
            out.put(custom(axis), edits.value(custom(axis)));
            out.put(lastApplied(axis), edits.value(lastApplied(axis)));
        }
        return Collections.unmodifiableMap(out);
    }

    /** The rows, for a test. */
    List<Line> shown() {
        return Collections.unmodifiableList(lines);
    }

    /** Focuses a field, for a test or a photograph. */
    void focus(String axis, SurfaceFamily family, int part) {
        focus = cell(axis, family, part);
    }
}
