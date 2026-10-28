package com.trmtgtnh.client.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import org.lwjgl.input.Keyboard;

import com.trmtgtnh.client.settings.Canvas;
import com.trmtgtnh.client.settings.Dropdown;
import com.trmtgtnh.client.settings.SettingsGeometry.Rect;
import com.trmtgtnh.client.settings.SettingsKeys;

/**
 * A chooser's button and the dropdown it opens (spec M23).
 *
 * <p>
 * Xep, 2026-10-21, shown "Cost curve" = "pack" cycled on click: "How about drop down menus in some places also? For
 * better UX than clicking through things? Perhaps images where applicable to help you understand, or separate prompts
 * when hovering over each dropdown option to explain things in more detail?" So the button shows the chosen value's
 * title with a chevron, and opens the shared {@link Dropdown} of every value with its title, its line and its detail;
 * Left and Right step it closed, the wheel steps it while it has the focus, and a right-click steps it back.
 *
 * <p>
 * Where a picture helps (spec M23.2, S3) each value carries one, drawn in its row, at 32 by 32 in its detail panel, and
 * the chosen value's at the button's left: a family's wear look on its road block, its cost curve, the depth a preset
 * sinks a rut to and the steps a quality preset wears grass through ({@link ChooserPictures}). {@link #details} is the
 * one seam beside the specification's fixed signature: the lines a preset's detail panel lists (spec S5), which the
 * screen works out.
 */
public final class ChooserButton {

    private final String setting;

    private final String category;

    private final String key;

    private final List<String> values;

    private final Consumer<String> chose;

    private String chosen;

    private Function<String, List<String>> details;

    private Rect control;

    private Dropdown dropdown;

    /** Where the open dropdown should put its highlight once it is laid out: a photograph's pointer. */
    private String highlightWanted;

    public ChooserButton(String category, String key, List<String> values, String chosen, Consumer<String> chose) {
        this.setting = category + "." + key;
        this.category = category;
        this.key = key;
        this.values = new ArrayList<String>(values);
        // A value the file holds that is not on the list is kept, as its own option at the end (spec M23.4).
        if (chosen != null && !this.values.contains(chosen)) this.values.add(chosen);
        this.chosen = chosen;
        this.chose = chose;
    }

    /** The lines a value's detail panel adds (spec S5): what choosing a preset would change. */
    public ChooserButton details(Function<String, List<String>> details) {
        this.details = details;
        return this;
    }

    public void layout(Rect control) {
        this.control = control;
    }

    public Rect control() {
        return control;
    }

    public String chosen() {
        return chosen;
    }

    /** Set from outside - a reset, an undo - without telling anybody. */
    public void show(String value) {
        if (value != null && !values.contains(value)) values.add(value);
        chosen = value;
    }

    /** Whether the dropdown is open or opens on the next frame, which builds it: the screen draws it then. */
    public boolean wantsOpen() {
        return opening || dropdownOpen();
    }

    public boolean dropdownOpen() {
        return dropdown != null && dropdown.isOpen();
    }

    /** Opens the dropdown, its highlight on {@code value} where given: Enter, Space, a click, a photograph. */
    public void open(String highlight) {
        dropdown = null;
        highlightWanted = highlight;
        opening = true;
    }

    private boolean opening;

    public void close() {
        dropdown = null;
        opening = false;
    }

    /** A value's words as the dropdown shows them. */
    private Dropdown.Option option(Canvas canvas, String value) {
        String titleKey = SettingsKeys.option(setting, value);
        String title = canvas.words(titleKey);
        boolean known = !titleKey.equals(title);
        if (!known) {
            return new Dropdown.Option(value, value, canvas.words(SettingsKeys.STATUS_NOT_HERE), null, picture(value));
        }
        String detailKey = SettingsKeys.optionDetail(setting, value);
        String detail = canvas.words(detailKey);
        List<String> more = details == null ? Collections.<String>emptyList() : details.apply(value);
        return new Dropdown.Option(
            value,
            title,
            canvas.words(SettingsKeys.optionLine(setting, value)),
            detailKey.equals(detail) ? null : detail,
            picture(value),
            more);
    }

    /** A value's picture, or null where this chooser has none (spec M23.2). */
    Object picture(String value) {
        return ChooserPictures.of(category, key, value);
    }

    /** The chosen value's title, as the button and a preset row's summary read it. */
    public String title(Canvas canvas) {
        return option(canvas, chosen).title;
    }

    /** The chosen value's line: a preset row's summary (spec M22.1). */
    public String line(Canvas canvas) {
        return option(canvas, chosen).line;
    }

    private void build(Canvas canvas, Rect panel, Rect screen) {
        List<Dropdown.Option> options = new ArrayList<Dropdown.Option>();
        for (String value : values) options.add(option(canvas, value));
        dropdown = new Dropdown(options, values.indexOf(chosen));
        dropdown.layout(control, panel, screen);
        if (highlightWanted != null) dropdown.highlight(values.indexOf(highlightWanted));
        opening = false;
    }

    /** The button: the chosen value's title, and the dropdown's chevron at its right. */
    public void draw(Canvas canvas, int mouseX, int mouseY, boolean focused, boolean enabled) {
        if (control == null) return;
        boolean under = enabled && control.contains(mouseX, mouseY);
        canvas.fill(control.x0, control.y0, control.x1, control.y1, under ? Canvas.TAB_IDLE : Canvas.TAB_OPEN);
        Canvas.frame(
            canvas,
            control.x0,
            control.y0,
            control.x1,
            control.y1,
            focused || dropdownOpen() ? Canvas.GOLD : Canvas.RULE);
        // The chosen value's picture at the button's left, inside its 150 (spec S3.1), the title after it.
        Object picture = picture(chosen);
        int textX = control.x0 + 6;
        if (picture != null) {
            canvas.picture(picture, control.x0 + 2, control.y0 + 2);
            textX = control.x0 + 22;
        }
        String title = canvas.trim(title(canvas), control.x1 - 20 - textX);
        canvas.text(title, textX, control.y0 + 6, enabled ? Canvas.WHITE : Canvas.GREY, true);
        canvas.icon("dropdown", control.x1 - 18, control.y0 + 2);
    }

    /** The open dropdown, drawn over everything else on the screen. */
    public void drawDropdown(Canvas canvas, int mouseX, int mouseY, Rect panel, Rect screen) {
        if (opening) build(canvas, panel, screen);
        if (dropdownOpen()) dropdown.draw(canvas, mouseX, mouseY);
    }

    /** A press: on the open dropdown it takes an option or closes it; on the button it opens it, right-click steps. */
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (dropdownOpen()) {
            int did = dropdown.click(mouseX, mouseY);
            if (did == Dropdown.CHOSE) take(dropdown.chosen());
            if (did != Dropdown.NOTHING) dropdown = null;
            return true;
        }
        if (control == null || !control.contains(mouseX, mouseY)) return false;
        if (button == 1) {
            step(-1);
        } else {
            open(chosen);
        }
        return true;
    }

    /** A key while focused: Enter or Space opens; Left and Right step it closed; Up, Down, Enter, Escape when open. */
    public boolean keyTyped(char typed, int key) {
        if (dropdownOpen()) {
            int action = key == Keyboard.KEY_UP ? Dropdown.UP
                : key == Keyboard.KEY_DOWN ? Dropdown.DOWN
                    : key == Keyboard.KEY_HOME ? Dropdown.HOME
                        : key == Keyboard.KEY_END ? Dropdown.END
                            : key == Keyboard.KEY_RETURN || key == Keyboard.KEY_SPACE ? Dropdown.ENTER
                                : key == Keyboard.KEY_ESCAPE ? Dropdown.ESCAPE : -1;
            if (action < 0) return true;
            int did = dropdown.key(action);
            if (did == Dropdown.CHOSE) take(dropdown.chosen());
            if (did == Dropdown.CHOSE || did == Dropdown.CLOSED) dropdown = null;
            return true;
        }
        if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_SPACE) {
            open(chosen);
            return true;
        }
        if (key == Keyboard.KEY_LEFT || key == Keyboard.KEY_RIGHT) {
            step(key == Keyboard.KEY_LEFT ? -1 : 1);
            return true;
        }
        return false;
    }

    /** The wheel over a focused chooser, or over the dropdown while it is open. */
    public void wheel(int direction) {
        if (dropdownOpen()) {
            dropdown.scroll(direction < 0 ? 1 : -1);
            return;
        }
        step(direction < 0 ? 1 : -1);
    }

    private void step(int direction) {
        int at = Dropdown.step(values.indexOf(chosen), values.size(), direction);
        if (at >= 0) take(at);
    }

    private void take(int index) {
        if (index < 0 || index >= values.size()) return;
        String value = values.get(index);
        if (value.equals(chosen)) return;
        chosen = value;
        chose.accept(value);
    }
}
