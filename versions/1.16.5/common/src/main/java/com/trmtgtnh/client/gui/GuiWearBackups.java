package com.trmtgtnh.client.gui;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.TextComponent;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.client.settings.Canvas;
import com.trmtgtnh.client.settings.SettingsKeys;
import com.trmtgtnh.erosion.WearBackupSet;
import com.trmtgtnh.server.WearBackups;
import com.trmtgtnh.util.MainThread;

/**
 * The restore screen (0.9.229; the 1.7.10 tree's docs/goals/0.9.229-wear-backup-spec.md, "The restore screen"),
 * opened from the settings screen's header in single player: the world's wear backups newest first, each with its
 * date, size and chunk count; under them a line saying what restoring the chosen one would change; and Restore, Undo
 * restore and Done. A restore and an undo go to the world's own server, on its thread, which says what it did in chat.
 * Carried from 1.7.10's GuiWearBackups.
 */
public class GuiWearBackups extends Screen {

    private static final int ROW = 24;

    private final Screen parent;

    private SettingsCanvas canvas;

    private List<WearBackupSet.Backup> backups;

    private int chosen = -1;

    private int scroll;

    /** What restoring the chosen backup would change, once the server has said; null while it is asked. */
    private volatile String change;

    private Button restore;

    private Button undo;

    public GuiWearBackups(Screen parent) {
        super(new TextComponent(Trmt.NAME));
        this.parent = parent;
    }

    /** A backup's time, in this player's own way of writing a date. */
    static String when(long millis) {
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(new Date(millis));
    }

    private int listTop() {
        return 30;
    }

    private int listBottom() {
        return height - 64;
    }

    private int listLeft() {
        return width / 2 - Math.min(160, width / 2 - 8);
    }

    private int listRight() {
        return width / 2 + Math.min(160, width / 2 - 8);
    }

    @Override
    protected void init() {
        canvas = new SettingsCanvas(minecraft, width, height);
        backups = new java.util.ArrayList<WearBackupSet.Backup>();
        // The mirror first, where it holds the wear: the newest copy there is (Xep, after phase 1).
        if (WearBackups.get()
            .mirrorHolds()) {
            backups.add(new WearBackupSet.Backup(com.trmtgtnh.erosion.WearRestore.LATEST, 0L, "mirror", "", 0L, 0, 0L));
        }
        backups.addAll(
            WearBackups.get()
                .backups());
        int each = Math.min(100, (width - 24) / 3);
        int x = width / 2 - (each * 3 + 8) / 2;
        int y = height - 28;
        restore = addButton(
            new Button(x, y, each, 20, new TextComponent(I18n.get(SettingsKeys.BACKUPS_RESTORE)), pressed -> restore()));
        undo = addButton(
            new Button(
                x + each + 4,
                y,
                each,
                20,
                new TextComponent(I18n.get(SettingsKeys.BACKUPS_UNDO)),
                pressed -> undoRestore()));
        addButton(
            new Button(
                x + 2 * (each + 4),
                y,
                each,
                20,
                new TextComponent(I18n.get("gui.done")),
                pressed -> minecraft.setScreen(parent)));
        enable();
        if (chooseFirst && chosen < 0 && !backups.isEmpty()) choose(0);
    }

    private void enable() {
        restore.active = chosen >= 0 && chosen < backups.size();
        undo.active = WearBackups.get()
            .canUndo();
    }

    private void choose(int index) {
        chosen = index;
        change = null;
        enable();
        final String name = backups.get(index).name;
        MainThread.onServer(() -> {
            final String said = WearBackups.get()
                .wouldChange(name);
            MainThread.onClient(
                () -> {
                    if (chosen >= 0 && chosen < backups.size() && backups.get(chosen).name.equals(name)) change = said;
                });
        });
    }

    @Override
    public void render(PoseStack pose, int mouseX, int mouseY, float partial) {
        canvas.frame(pose);
        WornPathArt.plainState();
        canvas.background(0, 0, width, height);
        String title = I18n.get(SettingsKeys.BACKUPS_TITLE);
        canvas.text(title, (width - canvas.width(title)) / 2, 12, Canvas.GOLD, true);

        int x0 = listLeft();
        int x1 = listRight();
        int top = listTop();
        int bottom = listBottom();
        canvas.panel(x0, top, x1, bottom);
        Canvas.frame(canvas, x0, top, x1, bottom, Canvas.RULE);
        if (backups.isEmpty()) {
            String none = I18n.get(SettingsKeys.BACKUPS_NONE);
            canvas.text(none, (width - canvas.width(none)) / 2, top + 10, Canvas.BODY, true);
        }
        int shown = Math.max(1, (bottom - top - 4) / ROW);
        scroll = Math.max(0, Math.min(scroll, backups.size() - shown));
        for (int i = scroll; i < backups.size() && i < scroll + shown; i++) {
            WearBackupSet.Backup each = backups.get(i);
            int y = top + 2 + (i - scroll) * ROW;
            boolean under = mouseX >= x0 && mouseX < x1 && mouseY >= y && mouseY < y + ROW;
            if (i == chosen) Canvas.frame(canvas, x0 + 2, y, x1 - 2, y + ROW - 1, Canvas.GOLD);
            else if (under) Canvas.frame(canvas, x0 + 2, y, x1 - 2, y + ROW - 1, Canvas.RULE);
            if (com.trmtgtnh.erosion.WearRestore.LATEST.equals(each.name)) {
                canvas.text(
                    canvas.trim(I18n.get(SettingsKeys.BACKUPS_MIRROR), x1 - x0 - 12),
                    x0 + 6,
                    y + 3,
                    i == chosen ? Canvas.GOLD : Canvas.WHITE,
                    true);
                canvas.text(
                    canvas.trim(I18n.get(SettingsKeys.BACKUPS_MIRROR_DETAIL), x1 - x0 - 12),
                    x0 + 6,
                    y + 13,
                    Canvas.BODY,
                    true);
                continue;
            }
            canvas.text(
                canvas.trim(when(each.made) + "  " + I18n.get(reasonKey(each.reason)), x1 - x0 - 12),
                x0 + 6,
                y + 3,
                i == chosen ? Canvas.GOLD : Canvas.WHITE,
                true);
            String detail = each.chunks == 1 ? I18n.get(SettingsKeys.BACKUPS_ROW_ONE, WearBackups.size(each.bytes))
                : I18n.get(SettingsKeys.BACKUPS_ROW_MANY, WearBackups.size(each.bytes), Integer.toString(each.chunks));
            canvas.text(canvas.trim(detail, x1 - x0 - 12), x0 + 6, y + 13, Canvas.BODY, true);
        }

        String line = line();
        if (line != null) {
            List<String> wrapped = com.trmtgtnh.client.settings.HoverText
                .wrap(canvas, java.util.Collections.singletonList(line), x1 - x0);
            int y = bottom + 6;
            for (String one : wrapped) {
                canvas.text(one, (width - canvas.width(one)) / 2, y, Canvas.BODY, true);
                y += 10;
            }
        }
        RenderSystem.color4f(1F, 1F, 1F, 1F);
        super.render(pose, mouseX, mouseY, partial);
    }

    /** The line under the list: what restoring the chosen backup would change, or what to do first. */
    private String line() {
        if (backups.isEmpty()) return null;
        if (chosen < 0) return I18n.get(SettingsKeys.BACKUPS_CHOOSE);
        String said = change;
        if (said == null) return I18n.get(SettingsKeys.BACKUPS_CHANGES_ASKING);
        if (said.isEmpty()) return I18n.get(SettingsKeys.BACKUPS_CHANGES_UNREAD);
        if ("0".equals(said)) return I18n.get(SettingsKeys.BACKUPS_CHANGES_NONE);
        if ("1".equals(said)) return I18n.get(SettingsKeys.BACKUPS_CHANGES_ONE);
        return I18n.get(SettingsKeys.BACKUPS_CHANGES_MANY, said);
    }

    static String reasonKey(String reason) {
        if (WearBackupSet.UPDATE.equals(reason)) return SettingsKeys.BACKUPS_REASON_UPDATE;
        if (WearBackupSet.COMMAND.equals(reason)) return SettingsKeys.BACKUPS_REASON_COMMAND;
        if (WearBackupSet.RESTORE.equals(reason)) return SettingsKeys.BACKUPS_REASON_RESTORE;
        return SettingsKeys.BACKUPS_REASON_SCHEDULED;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0 || mouseX < listLeft() || mouseX >= listRight()) return false;
        if (mouseY < listTop() + 2 || mouseY >= listBottom()) return false;
        int index = scroll + ((int) mouseY - listTop() - 2) / ROW;
        if (index >= 0 && index < backups.size()) {
            choose(index);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double wheel) {
        if (wheel > 0) scroll--;
        else if (wheel < 0) scroll++;
        return true;
    }

    private void restore() {
        if (chosen < 0 || chosen >= backups.size()) return;
        final String name = backups.get(chosen).name;
        minecraft.setScreen(null);
        MainThread.onServer(
            () -> WearBackups.get()
                .restore(name, null));
    }

    private void undoRestore() {
        minecraft.setScreen(null);
        MainThread.onServer(
            () -> WearBackups.get()
                .undo(null));
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            minecraft.setScreen(parent);
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    /** Presses Undo restore, as a click on it does: for the test harness's played check. */
    public void pressUndo() {
        if (undo != null && undo.active) undoRestore();
    }

    /** Whether Undo restore is offered now, for the test harness to say before it presses it. */
    public boolean undoOffered() {
        return undo != null && undo.active;
    }

    /** What the line under the list says now, for the test harness to log beside its photograph. */
    public String lineShown() {
        return line();
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public void tick() {
        if (restore != null) enable();
    }

    /** The screen as a photograph takes it: the newest backup chosen, nothing pressed. */
    public static GuiWearBackups chosenFirst(Screen parent) {
        GuiWearBackups screen = new GuiWearBackups(parent);
        screen.chooseFirst = true;
        return screen;
    }

    private boolean chooseFirst;
}
