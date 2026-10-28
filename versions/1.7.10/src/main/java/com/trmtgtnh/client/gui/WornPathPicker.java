package com.trmtgtnh.client.gui;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.client.gui.GuiScreen;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.trmtgtnh.client.settings.Canvas;
import com.trmtgtnh.client.settings.SettingsGeometry;
import com.trmtgtnh.client.settings.SettingsGeometry.Rect;
import com.trmtgtnh.client.settings.SettingsKeys;

/**
 * A list's picker (spec M4): every block, item, fluid, enchantment, mob, texture or dimension the game has registered,
 * each with its picture, its own name and what is written for it, sorted by name and searched as the settings are
 * searched (M2.8), so a line is chosen by sight rather than typed from memory. It replaces the mob picker of step 5.
 *
 * <p>
 * A click adds the line and goes back; a Shift-click adds it and stays, the row ticked gold for a second
 * ({@code pick.shift}). A block with variants offers every variant as a second button ({@code pick.every}, the line's
 * {@code :*}), except on a list that takes no metadata. A line the list already holds is drawn grey and adds nothing.
 * The block list is gathered once a game session, four milliseconds a frame, and searching works over what has been
 * gathered while the rest comes in ({@code pick.gathering}) - in GT New Horizons that is tens of thousands of blocks.
 * What it decides is {@link PickerList.Model}'s, held by a test; this draws it.
 */
public final class WornPathPicker extends GuiScreen {

    private static final int HEADER = 40;

    private static final int SEARCH_TOP = 46;

    private static final int ROWS_TOP = 68;

    private final GuiScreen back;

    private final PickerList.Model model;

    private final Consumer<String> chosen;

    private final LineBox search = new LineBox("");

    private SettingsCanvas canvas;

    private int scroll;

    private Rect panel;

    WornPathPicker(GuiScreen back, PickerList.Kind kind, Collection<String> held, Consumer<String> chosen) {
        this.back = back;
        this.model = new PickerList.Model(kind, SettingsPictures.gathering(kind), held);
        this.chosen = chosen;
        search.focus(true);
    }

    /** The picker as the rig photographs it, searched for {@code query}; nothing it picks goes anywhere. */
    public static GuiScreen forPhotograph(GuiScreen parent, PickerList.Kind kind, String query) {
        WornPathPicker picker = new WornPathPicker(
            parent,
            kind,
            Collections.<String>emptyList(),
            new Consumer<String>() {

                @Override
                public void accept(String line) {
                    // A photograph's picker adds nothing.
                }
            });
        picker.search.setText(query == null ? "" : query);
        picker.model.query(picker.search.text());
        return picker;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        Keyboard.enableRepeatEvents(true);
        canvas = new SettingsCanvas(mc, width, height);
        int panelWidth = Math.min(width - 16, SettingsGeometry.PANEL_MAX);
        panel = new Rect((width - panelWidth) / 2, ROWS_TOP, (width + panelWidth) / 2, height - 30);
        clamp();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    private Rect rows() {
        return panel.inset(1);
    }

    /** Whether a press on the scroll bar holds it, so a drag moves the list. */
    private boolean draggingBar;

    private int contentHeight() {
        return model.shown()
            .size() * SettingsGeometry.PICKER_ROW;
    }

    private void clamp() {
        if (panel == null) return;
        scroll = Math.max(0, Math.min(scroll, contentHeight() - rows().height()));
    }

    private Rect cancel() {
        return Rect.sized(width - 8 - 70, height - 26, 70, 20);
    }

    /** The second button of a row, every variant, where it is offered. */
    private Rect everyButton(int top, String label) {
        Rect rows = rows();
        int w = canvas.width(label) + 12;
        return Rect.sized(rows.x1 - 8 - 4 - w, top + 3, w, 20);
    }

    private void close() {
        mc.displayGuiScreen(back);
    }

    /** A row pressed: its line added, and the picker shut after a plain press (M4.3). */
    private void pressed(PickerList.Entry entry, boolean every) {
        String line = model.press(entry, every, System.currentTimeMillis());
        if (line != null) chosen.accept(line);
        if (PickerList.Model.closes(isShiftKeyDown())) close();
    }

    @Override
    protected void keyTyped(char typed, int key) {
        if (key == Keyboard.KEY_ESCAPE) {
            close();
            return;
        }
        if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
            List<PickerList.Entry> shown = model.shown();
            if (!shown.isEmpty()) pressed(shown.get(0), false);
            return;
        }
        int page = rows().height() - SettingsGeometry.PICKER_ROW;
        if (key == Keyboard.KEY_PRIOR) scroll -= page;
        else if (key == Keyboard.KEY_NEXT) scroll += page;
        else if (isCtrlKeyDown() && key == Keyboard.KEY_V) search.insert(getClipboardString());
        else search.key(typed, key);
        if (!search.text()
            .equals(model.query())) {
            model.query(search.text());
            scroll = 0;
        }
        clamp();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (cancel().contains(mouseX, mouseY)) {
            close();
            return;
        }
        Rect rows = rows();
        if (!rows.contains(mouseX, mouseY) || button != 0) return;
        // The scroll bar grabs a press and drags the list; it never picks the row beside it (0.9.228).
        Rect bar = bar();
        if (bar != null && bar.contains(mouseX, mouseY)) {
            draggingBar = true;
            barTo(mouseY);
            return;
        }
        int index = (mouseY - rows.y0 + scroll) / SettingsGeometry.PICKER_ROW;
        List<PickerList.Entry> shown = model.shown();
        if (index < 0 || index >= shown.size()) return;
        PickerList.Entry entry = shown.get(index);
        int top = rows.y0 + index * SettingsGeometry.PICKER_ROW - scroll;
        boolean every = model.offersEvery(entry)
            && everyButton(top, canvas.words(SettingsKeys.PICK_EVERY)).contains(mouseX, mouseY);
        pressed(entry, every);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long held) {
        if (draggingBar) barTo(mouseY);
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int state) {
        super.mouseMovedOrUp(mouseX, mouseY, state);
        if (state == 0) draggingBar = false;
    }

    /** The scroll bar's track, where the list overflows its rows; null where it does not. */
    private Rect bar() {
        Rect rows = rows();
        if (contentHeight() <= rows.height()) return null;
        return Rect.sized(rows.x1 - SettingsGeometry.SCROLLBAR, rows.y0, SettingsGeometry.SCROLLBAR, rows.height());
    }

    private void barTo(int mouseY) {
        Rect rows = rows();
        scroll = PickerList.barScroll(mouseY, rows.y0, rows.height(), contentHeight(), rows.height());
        clamp();
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int notches = Integer.signum(Mouse.getEventDWheel());
        if (notches == 0) return;
        scroll -= notches * 3 * SettingsGeometry.PICKER_ROW;
        clamp();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partial) {
        long mark = System.nanoTime();
        SettingsPictures.frame();
        WornPathArt.plainState();
        canvas.background(0, 0, width, height);
        canvas.pushClip(0, 0, width, HEADER);
        WornPathArt.lawnBand(width, HEADER);
        canvas.popClip();
        canvas.fill(0, HEADER, width, HEADER + 1, Canvas.GOLD);
        String title = canvas.words(titleKey(model.kind));
        canvas.text(title, (width - canvas.width(title)) / 2, 16, Canvas.WHITE, true);
        mark = drawn(0, mark);

        // The search field, and while the list is still being gathered, how far it has got.
        Rect field = new Rect(panel.x0, SEARCH_TOP, panel.x0 + Math.min(220, panel.width()), SEARCH_TOP + 16);
        canvas.fill(field.x0, field.y0, field.x1, field.y1, Canvas.TAB_OPEN);
        Canvas.frame(canvas, field.x0, field.y0, field.x1, field.y1, Canvas.GOLD);
        canvas.icon("search", field.x0 + 1, field.y0);
        if (search.text()
            .isEmpty()) {
            canvas.text(canvas.words(SettingsKeys.SEARCH_MOB), field.x0 + 18, field.y0 + 4, Canvas.GREY, true);
        }
        search.draw(
            canvas,
            new Rect(field.x0 + 17, field.y0 + 2, field.x1 - 2, field.y1 - 2),
            null,
            System.currentTimeMillis());
        PickerList.Gathering source = model.source();
        if (!source.finished() && source.total > 0) {
            String gathering = canvas.words(SettingsKeys.PICK_GATHERING, source.done(), source.total);
            int x = field.x1 + 8;
            canvas.text(canvas.trim(gathering, panel.x1 - x), x, field.y0 + 1, Canvas.BODY, 0.75F);
            int bar = Math.max(0, panel.x1 - x);
            canvas.fill(x, field.y0 + 10, x + bar, field.y0 + 12, Canvas.TAB_OPEN);
            canvas.fill(x, field.y0 + 10, x + bar * source.done() / source.total, field.y0 + 12, Canvas.GOLD);
        }

        mark = drawn(1, mark);
        Canvas.frame(canvas, panel.x0, panel.y0, panel.x1, panel.y1, Canvas.GOLD);
        Rect rows = rows();
        canvas.panel(rows.x0, rows.y0, rows.x1, rows.y1);
        List<PickerList.Entry> shown = model.shown();
        clamp();
        long now = System.currentTimeMillis();
        canvas.pushClip(rows.x0, rows.y0, rows.x1, rows.y1);
        String every = canvas.words(SettingsKeys.PICK_EVERY);
        int first = Math.max(0, scroll / SettingsGeometry.PICKER_ROW);
        for (int i = first; i < shown.size(); i++) {
            int top = rows.y0 + i * SettingsGeometry.PICKER_ROW - scroll;
            if (top >= rows.y1) break;
            PickerList.Entry entry = shown.get(i);
            drawEntry(entry, top, rows, model.offersEvery(entry) ? every : null, mouseX, mouseY, now);
        }
        canvas.popClip();
        if (shown.isEmpty() && (source.finished() || !model.query()
            .trim()
            .isEmpty())) {
            String none = canvas.words(SettingsKeys.PICK_NONE, model.query());
            canvas.text(none, (width - canvas.width(none)) / 2, rows.y0 + rows.height() / 2 - 4, Canvas.BODY, true);
        }
        if (contentHeight() > rows.height()) {
            Rect track = Rect
                .sized(rows.x1 - SettingsGeometry.SCROLLBAR, rows.y0, SettingsGeometry.SCROLLBAR, rows.height());
            canvas.fill(track.x0, track.y0, track.x1, track.y1, Canvas.TAB_OPEN);
            int size = Math.max(16, rows.height() * rows.height() / contentHeight());
            int travel = Math.max(1, contentHeight() - rows.height());
            int y = track.y0 + (rows.height() - size) * scroll / travel;
            canvas.fill(track.x0, y, track.x1, y + size, Canvas.RULE);
        }

        mark = drawn(2, mark);
        // The footer: what a Shift-click does, and Cancel.
        canvas.text(
            canvas.trim(canvas.words(SettingsKeys.PICK_SHIFT), cancel().x0 - 16),
            8,
            height - 20,
            Canvas.GREY,
            true);
        Rect cancel = cancel();
        boolean under = cancel.contains(mouseX, mouseY);
        canvas.fill(cancel.x0, cancel.y0, cancel.x1, cancel.y1, under ? Canvas.TAB_IDLE : Canvas.TAB_OPEN);
        Canvas.frame(canvas, cancel.x0, cancel.y0, cancel.x1, cancel.y1, under ? Canvas.GOLD : Canvas.RULE);
        String label = canvas.words(SettingsKeys.GUI_CANCEL);
        canvas.text(
            label,
            cancel.x0 + (cancel.width() - canvas.width(label)) / 2,
            cancel.y0 + 6,
            under ? Canvas.GOLD : Canvas.WHITE,
            true);
        canvas.drawHover();
        GL11.glColor4f(1F, 1F, 1F, 1F);
        drawn(3, mark);
    }

    /**
     * How long the last frame spent on each part of the picker, in nanoseconds - the header, the search row, the rows
     * and the footer - read by the harness's settingsperf part (0.9.228): GT New Horizons' mob picker spent 20 to 50 ms
     * of its first frame in its own drawing, with no mob drawn and nothing gathered.
     */
    static final long[] DRAWN_NANOS = new long[4];

    /** A copy of {@link #DRAWN_NANOS}, for the harness, which lives in another package. */
    public static long[] drawnNanos() {
        return DRAWN_NANOS.clone();
    }

    private static long drawn(int part, long since) {
        long now = System.nanoTime();
        DRAWN_NANOS[part] = now - since;
        return now;
    }

    /** One picker row (1.2): picture, name, what is written, and the every-variant button where offered. */
    private void drawEntry(PickerList.Entry entry, int top, Rect rows, String every, int mouseX, int mouseY, long now) {
        int statusWidth = every == null ? 0 : canvas.width(every) + 12;
        SettingsGeometry.PickerRow at = SettingsGeometry.pickerRow(rows.x0, top, rows.width() - 8, statusWidth);
        boolean held = model.holds(entry, false);
        boolean under = mouseY >= Math.max(top, rows.y0) && mouseY < top + SettingsGeometry.PICKER_ROW
            && mouseX >= rows.x0
            && mouseX < rows.x1;
        if (under && !held) canvas.fill(rows.x0, top, rows.x1, top + SettingsGeometry.PICKER_ROW, Canvas.TAB_IDLE);
        if (model.ticked(entry, now))
            canvas.fill(rows.x0, top, rows.x0 + 2, top + SettingsGeometry.PICKER_ROW, Canvas.GOLD);
        canvas.fill(at.frame.x0, at.frame.y0, at.frame.x1, at.frame.y1, Canvas.TAB_IDLE);
        if (entry.picture != null) canvas.picture(entry.picture, at.picture.x0, at.picture.y0);
        canvas.text(
            canvas.trim(entry.name, at.name.width()),
            at.name.x0,
            at.name.y0,
            held ? Canvas.GREY : Canvas.WHITE,
            true);
        canvas.text(canvas.trim(entry.id, (int) (at.id.width() / 0.75F)), at.id.x0, at.id.y0, Canvas.GREY, 0.75F);
        if (every != null) {
            Rect button = everyButton(top, every);
            boolean lit = button.contains(mouseX, mouseY) && !model.holds(entry, true);
            canvas.fill(button.x0, button.y0, button.x1, button.y1, lit ? Canvas.TAB_IDLE : Canvas.TAB_OPEN);
            Canvas.frame(canvas, button.x0, button.y0, button.x1, button.y1, lit ? Canvas.GOLD : Canvas.RULE);
            canvas
                .text(every, button.x0 + 6, button.y0 + 6, model.holds(entry, true) ? Canvas.GREY : Canvas.WHITE, true);
        }
        canvas.fill(
            rows.x0,
            top + SettingsGeometry.PICKER_ROW - 1,
            rows.x1,
            top + SettingsGeometry.PICKER_ROW,
            Canvas.RULE);
    }

    /** The picker's title (M4.1). */
    static String titleKey(PickerList.Kind kind) {
        String key = ListRows.pickWords(kind);
        return key == null ? SettingsKeys.SEARCH_MOB : key;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return back == null || back.doesGuiPauseGame();
    }
}
