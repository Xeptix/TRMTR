package com.trmtgtnh.client.gui;

import com.mojang.blaze3d.platform.InputConstants;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.client.settings.Canvas;
import com.trmtgtnh.client.settings.Edits;
import com.trmtgtnh.client.settings.SettingsGeometry;
import com.trmtgtnh.client.settings.SettingsGeometry.Rect;
import com.trmtgtnh.client.settings.SettingsKeys;

/**
 * A long list's own page (spec S1.2, M3, M18): the list's title and what it does above its rows, then its lines as
 * {@link ListRows} draws them - each with its picture, its own name or "Not in this game" in red, its status, the
 * picker for its kind, Copy and Paste - and Done and Cancel.
 *
 * <p>
 * The page edits a copy of the list, kept in edits of its own, so Ctrl+Z steps back through the page's own changes and
 * Cancel or Escape leaves without any of them. Done hands the lines back to {@link WornPathScreen}, where they wait for
 * its own Save like every other edit, blank lines left out. A number that does not read - a mob's multiplier, a
 * grade's uses or reach - holds Done until it is fixed (M6.5); an unmatched name never does, being a warning only.
 */
public final class WornPathListScreen extends Screen {

    private static final int TOP_GAP = 4;

    private final WornPathScreen parent;

    private final SettingsModel model;

    private final SettingsModel.Setting setting;

    /** The page's own copy of the list, with the opening value to step back to. */
    private final Edits edits;

    private SettingsCanvas canvas;

    private ListRows rows;

    private SettingsGeometry.Frame frame;

    private Rect panel;

    private int scroll;

    private boolean draggingThumb;

    /** A photograph's hover, on one line's name (spec Part 4, {@code withHoverOnLine}); -1 in a game. */
    private int hoverOnLine = -1;

    WornPathListScreen(WornPathScreen parent, SettingsModel.Setting setting) {
        super(Component.literal(Trmt.NAME));
        this.parent = parent;
        this.model = parent.model;
        this.setting = setting;
        Map<String, String> opening = new HashMap<String, String>();
        opening.put(setting.id, model.value(setting));
        Map<String, String> defaults = new HashMap<String, String>();
        defaults.put(
            setting.id,
            model.edits()
                .defaultOf(setting.id));
        this.edits = new Edits(opening, defaults);
    }

    /** A photograph's hover, as if the pointer rested on line {@code index}'s name (spec Part 4). */
    public WornPathListScreen withHoverOnLine(int index) {
        hoverOnLine = index;
        return this;
    }

    @Override
    protected void init() {
        clearWidgets();
        canvas = new SettingsCanvas(minecraft, width, height);
        frame = SettingsGeometry.of(width, height, false, false);
        int top = frame.header.y1 + TOP_GAP;
        panel = new Rect(frame.panel.x0, top, frame.panel.x1, frame.panel.y1);
        Rect inside = inside();
        rows = new ListRows(
            ConfigLayout.listKind(setting.category, setting.key),
            SettingsModel.lines(edits.value(setting.id)),
            edits,
            setting.id,
            inside.x0 + 4,
            inside.width() - SettingsGeometry.SCROLLBAR - SettingsGeometry.SCROLLBAR_GAP - 8);
        rows.onResize(new Runnable() {

            @Override
            public void run() {
                clamp();
            }
        });
        clamp();
    }

    /** The typed characters kept coming while this screen is open (WidgetEvents.hearTyping). */
    @Override
    public void tick() {
        WidgetEvents.hearTyping(this);
    }

    @Override
    public void removed() {
    }

    private Rect inside() {
        return panel.inset(1);
    }

    private void clamp() {
        if (rows == null) return;
        scroll = Math.max(0, Math.min(scroll, rows.height() + 4 - inside().height()));
    }

    /** Whether Done is held: by a number that does not read, never by a name the game lacks (M3.3). */
    private boolean held() {
        return rows != null && rows.refusals() > 0;
    }

    /** Done: the lines that say something, handed back to the settings screen to wait for its own Save. */
    private void keep() {
        if (held()) return;
        List<String> kept = rows.lines();
        parent.editLines(setting.property, kept.toArray(new String[kept.size()]));
        minecraft.setScreen(parent);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int key = event.key();
        int modifiers = event.modifiers();
        keyTyped((char) 0, key);
        return true;
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        char typed = (char) event.codepoint();
        keyTyped(typed, -1);
        return true;
    }

    /** A key or a character, as the 1.7.10 edition's screen hears both in one: a character comes with no key. */
    private void keyTyped(char typed, int key) {
        if (rows.keyTyped(typed, key)) return;
        if (key == InputConstants.KEY_ESCAPE) {
            minecraft.setScreen(parent);
            return;
        }
        if (minecraft.hasControlDown() && key == InputConstants.KEY_Z) {
            edits.undoLast();
            return;
        }
        if (minecraft.hasControlDown() && key == InputConstants.KEY_S) {
            keep();
            return;
        }
        int page = inside().height() - ListRows.ROW;
        if (key == InputConstants.KEY_PAGEUP) scroll -= page;
        else if (key == InputConstants.KEY_PAGEDOWN) scroll += page;
        else if (key == InputConstants.KEY_HOME) scroll = 0;
        else if (key == InputConstants.KEY_END) scroll = Integer.MAX_VALUE / 2;
        clamp();
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubled) {
        double x = event.x();
        double y = event.y();
        int button = event.button();
        int mouseX = (int) x;
        int mouseY = (int) y;
        if (frame.done.contains(mouseX, mouseY)) {
            keep();
            return true;
        }
        if (frame.cancel.contains(mouseX, mouseY)) {
            minecraft.setScreen(parent);
            return true;
        }
        Rect inside = inside();
        Rect track = track();
        if (track != null && track.contains(mouseX, mouseY)) {
            draggingThumb = true;
            return true;
        }
        if (inside.contains(mouseX, mouseY)) rows.mouseClicked(mouseX, mouseY, button);
        else rows.blur();
        return true;
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) {
        double x = event.x();
        double y = event.y();
        int button = event.button();
        int mouseX = (int) x;
        int mouseY = (int) y;
        rows.mouseDragged(mouseX, mouseY);
        Rect track = track();
        if (draggingThumb && track != null) {
            scroll = (mouseY - track.y0) * (rows.height() + 4 - inside().height()) / Math.max(1, track.height());
            clamp();
        }
        return true;
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        double x = event.x();
        double y = event.y();
        int button = event.button();
        if (button == 0) {
            rows.mouseReleased((int) x, (int) y);
            draggingThumb = false;
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double across, double delta) {
        int notches = (int) Math.signum(delta);
        if (notches == 0) return false;
        scroll -= notches * 3 * ListRows.ROW;
        clamp();
        return true;
    }

    /** The scrollbar's track, where the rows overflow the panel; null where they do not. */
    private Rect track() {
        Rect inside = inside();
        if (rows == null || rows.height() + 4 <= inside.height()) return null;
        return Rect
            .sized(inside.x1 - SettingsGeometry.SCROLLBAR, inside.y0, SettingsGeometry.SCROLLBAR, inside.height());
    }

    /**
     * From 1.20.2 a screen draws its background itself, once a frame, and 1.21 blurs it (from 26.1 its
     * {@code extractBackground}). Answered here with nothing, so the dirt this screen draws first is not drawn over.
     */
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {}

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        canvas.frame(graphics);
        SettingsPictures.frame();
        canvas.background(0, 0, width, height);

        // The header: the lawn band and its gold rule, the list's plain title and what it does (M18.1, M21).
        canvas.pushClip(0, 0, width, frame.header.y1);
        WornPathArt.lawnBand(graphics, width, frame.header.y1);
        canvas.popClip();
        canvas.fill(frame.headerRule.x0, frame.headerRule.y0, frame.headerRule.x1, frame.headerRule.y1, Canvas.GOLD);
        String title = model.title(setting);
        canvas.text(title, (width - canvas.width(title)) / 2, frame.compact ? 3 : 8, Canvas.WHITE, true);
        String summary = model.summary(setting);
        if (summary != null && !summary.isEmpty()) {
            String shown = canvas.trim(summary, (int) ((width - 16) / 0.75F));
            canvas.text(
                shown,
                (width - (int) (canvas.width(shown) * 0.75F)) / 2,
                frame.compact ? 14 : 22,
                Canvas.BODY,
                0.75F);
        }

        // The rows, in the panel, scrolled and clipped; pictures only for the rows in sight (Part 6).
        Rect inside = inside();
        Canvas.frame(canvas, panel.x0, panel.y0, panel.x1, panel.y1, Canvas.GOLD);
        canvas.panel(inside.x0, inside.y0, inside.x1, inside.y1);
        clamp();
        rows.layout(inside.y0 + 2 - scroll);
        rows.visible(inside.y0, inside.y1);
        int pointerX = mouseX;
        int pointerY = mouseY;
        if (hoverOnLine >= 0) {
            // A photograph's hover rests on the line's name, as a pointer would.
            pointerX = inside.x0 + 4 + 40;
            pointerY = inside.y0 + 2 - scroll + ListRows.BAR + hoverOnLine * ListRows.ROW + 6;
        }
        canvas.pushClip(inside.x0, inside.y0, inside.x1, inside.y1);
        rows.draw(canvas, pointerX, pointerY);
        canvas.popClip();
        Rect track = track();
        if (track != null) {
            canvas.fill(track.x0, track.y0, track.x1, track.y1, Canvas.TAB_OPEN);
            int total = rows.height() + 4;
            int size = Math.max(16, inside.height() * inside.height() / total);
            int y = track.y0 + (inside.height() - size) * scroll / Math.max(1, total - inside.height());
            boolean lit = draggingThumb || Rect.sized(track.x0, y, track.width(), size)
                .contains(mouseX, mouseY);
            canvas.fill(track.x0, y, track.x1, y + size, lit ? Canvas.GOLD : Canvas.RULE);
        }

        // The footer: Cancel, and Done - held, and saying why, while a number does not read.
        boolean held = held();
        String done = held ? (rows.refusals() == 1 ? canvas.words(SettingsKeys.FIX_ONE)
            : canvas.words(SettingsKeys.FIX_MANY, rows.refusals())) : canvas.words(SettingsKeys.GUI_DONE);
        button(frame.done, done, !held, mouseX, mouseY);
        button(frame.cancel, canvas.words(SettingsKeys.GUI_CANCEL), true, mouseX, mouseY);
        canvas.drawHover();
    }

    /** A footer button, as the settings screen's own. */
    private void button(Rect at, String label, boolean enabled, int mouseX, int mouseY) {
        boolean under = enabled && at.contains(mouseX, mouseY);
        canvas.fill(at.x0, at.y0, at.x1, at.y1, under ? Canvas.TAB_IDLE : Canvas.TAB_OPEN);
        Canvas.frame(canvas, at.x0, at.y0, at.x1, at.y1, under ? Canvas.GOLD : Canvas.RULE);
        String shown = canvas.trim(label, at.width() - 6);
        canvas.text(
            shown,
            at.x0 + (at.width() - canvas.width(shown)) / 2,
            at.y0 + (at.height() - 8) / 2,
            !enabled ? Canvas.GREY : under ? Canvas.GOLD : Canvas.WHITE,
            true);
    }

    @Override
    public boolean isPauseScreen() {
        return parent == null || parent.isPauseScreen();
    }

    /** The lines as the page holds them now, for a test or a hook. */
    List<String> lines() {
        return rows == null ? Collections.<String>emptyList() : rows.lines();
    }
}
