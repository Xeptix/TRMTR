package com.trmtgtnh.client.gui;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.trmtgtnh.client.settings.Canvas;
import com.trmtgtnh.client.settings.SettingsKeys;
import com.trmtgtnh.erosion.WearBackupSet;
import com.trmtgtnh.server.WearBackups;
import com.trmtgtnh.util.MainThread;

/**
 * The restore screen (0.9.229; docs/goals/0.9.229-wear-backup-spec.md, "The restore screen"), opened from the settings
 * screen's header in single player: the world's wear backups newest first, each with its date, size and chunk count;
 * under them a line saying what restoring the chosen one would change; and Restore, Undo restore and Done. A restore
 * and an undo go to the world's own server, on its thread, which says what it did in chat.
 */
public class GuiWearBackups extends GuiScreen {

    private static final int ID_RESTORE = 0;

    private static final int ID_UNDO = 1;

    private static final int ID_DONE = 2;

    private static final int ROW = 24;

    private final GuiScreen parent;

    private SettingsCanvas canvas;

    private List<WearBackupSet.Backup> backups;

    private int chosen = -1;

    private int scroll;

    /** What restoring the chosen backup would change, once the server has said; null while it is asked. */
    private volatile String change;

    private GuiButton restore;

    private GuiButton undo;

    public GuiWearBackups(GuiScreen parent) {
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
    public void initGui() {
        canvas = new SettingsCanvas(mc, width, height);
        backups = new java.util.ArrayList<WearBackupSet.Backup>();
        // The mirror first, where it holds the wear: the newest copy there is (Xep, after phase 1).
        if (WearBackups.get()
            .mirrorHolds()) {
            backups.add(new WearBackupSet.Backup(com.trmtgtnh.erosion.WearRestore.LATEST, 0L, "mirror", "", 0L, 0, 0L));
        }
        backups.addAll(
            WearBackups.get()
                .backups());
        buttonList.clear();
        int each = Math.min(100, (width - 24) / 3);
        int x = width / 2 - (each * 3 + 8) / 2;
        int y = height - 28;
        restore = new GuiButton(ID_RESTORE, x, y, each, 20, I18n.format(SettingsKeys.BACKUPS_RESTORE));
        undo = new GuiButton(ID_UNDO, x + each + 4, y, each, 20, I18n.format(SettingsKeys.BACKUPS_UNDO));
        buttonList.add(restore);
        buttonList.add(undo);
        buttonList.add(new GuiButton(ID_DONE, x + 2 * (each + 4), y, each, 20, I18n.format("gui.done")));
        enable();
    }

    private void enable() {
        restore.enabled = chosen >= 0 && chosen < backups.size();
        undo.enabled = WearBackups.get()
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
    public void drawScreen(int mouseX, int mouseY, float partial) {
        WornPathArt.plainState();
        canvas.background(0, 0, width, height);
        String title = I18n.format(SettingsKeys.BACKUPS_TITLE);
        canvas.text(title, (width - canvas.width(title)) / 2, 12, Canvas.GOLD, true);

        int x0 = listLeft();
        int x1 = listRight();
        int top = listTop();
        int bottom = listBottom();
        canvas.panel(x0, top, x1, bottom);
        Canvas.frame(canvas, x0, top, x1, bottom, Canvas.RULE);
        if (backups.isEmpty()) {
            String none = I18n.format(SettingsKeys.BACKUPS_NONE);
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
                    canvas.trim(I18n.format(SettingsKeys.BACKUPS_MIRROR), x1 - x0 - 12),
                    x0 + 6,
                    y + 3,
                    i == chosen ? Canvas.GOLD : Canvas.WHITE,
                    true);
                canvas.text(
                    canvas.trim(I18n.format(SettingsKeys.BACKUPS_MIRROR_DETAIL), x1 - x0 - 12),
                    x0 + 6,
                    y + 13,
                    Canvas.BODY,
                    true);
                continue;
            }
            canvas.text(
                canvas.trim(when(each.made) + "  " + I18n.format(reasonKey(each.reason)), x1 - x0 - 12),
                x0 + 6,
                y + 3,
                i == chosen ? Canvas.GOLD : Canvas.WHITE,
                true);
            String detail = each.chunks == 1 ? I18n.format(SettingsKeys.BACKUPS_ROW_ONE, WearBackups.size(each.bytes))
                : I18n
                    .format(SettingsKeys.BACKUPS_ROW_MANY, WearBackups.size(each.bytes), Integer.toString(each.chunks));
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
        GL11.glColor4f(1F, 1F, 1F, 1F);
        super.drawScreen(mouseX, mouseY, partial);
    }

    /** The line under the list: what restoring the chosen backup would change, or what to do first. */
    private String line() {
        if (backups.isEmpty()) return null;
        if (chosen < 0) return I18n.format(SettingsKeys.BACKUPS_CHOOSE);
        String said = change;
        if (said == null) return I18n.format(SettingsKeys.BACKUPS_CHANGES_ASKING);
        if (said.isEmpty()) return I18n.format(SettingsKeys.BACKUPS_CHANGES_UNREAD);
        if ("0".equals(said)) return I18n.format(SettingsKeys.BACKUPS_CHANGES_NONE);
        if ("1".equals(said)) return I18n.format(SettingsKeys.BACKUPS_CHANGES_ONE);
        return I18n.format(SettingsKeys.BACKUPS_CHANGES_MANY, said);
    }

    static String reasonKey(String reason) {
        if (WearBackupSet.UPDATE.equals(reason)) return SettingsKeys.BACKUPS_REASON_UPDATE;
        if (WearBackupSet.COMMAND.equals(reason)) return SettingsKeys.BACKUPS_REASON_COMMAND;
        if (WearBackupSet.RESTORE.equals(reason)) return SettingsKeys.BACKUPS_REASON_RESTORE;
        return SettingsKeys.BACKUPS_REASON_SCHEDULED;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws java.io.IOException {
        super.mouseClicked(mouseX, mouseY, button);
        if (button != 0 || mouseX < listLeft() || mouseX >= listRight()) return;
        if (mouseY < listTop() + 2 || mouseY >= listBottom()) return;
        int index = scroll + (mouseY - listTop() - 2) / ROW;
        if (index >= 0 && index < backups.size()) choose(index);
    }

    @Override
    public void handleMouseInput() throws java.io.IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel > 0) scroll--;
        else if (wheel < 0) scroll++;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == ID_RESTORE && chosen >= 0 && chosen < backups.size()) {
            final String name = backups.get(chosen).name;
            mc.displayGuiScreen((GuiScreen) null);
            MainThread.onServer(
                () -> WearBackups.get()
                    .restore(name, null));
        } else if (button.id == ID_UNDO) {
            mc.displayGuiScreen((GuiScreen) null);
            MainThread.onServer(
                () -> WearBackups.get()
                    .undo(null));
        } else if (button.id == ID_DONE) {
            mc.displayGuiScreen(parent);
        }
    }

    @Override
    protected void keyTyped(char typed, int key) {
        if (key == Keyboard.KEY_ESCAPE) mc.displayGuiScreen(parent);
    }

    /** Presses Undo restore, as a click on it does: for the test harness's played check. */
    public void pressUndo() {
        if (undo != null && undo.enabled) actionPerformed(undo);
    }

    /** Whether Undo restore is offered now, for the test harness to say before it presses it. */
    public boolean undoOffered() {
        return undo != null && undo.enabled;
    }

    /** What the line under the list says now, for the test harness to log beside its photograph. */
    public String lineShown() {
        return line();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return true;
    }

    @Override
    public void updateScreen() {
        if (restore != null) enable();
    }

    /** The screen as a photograph takes it: the newest backup chosen, nothing pressed. */
    public static GuiWearBackups chosenFirst(GuiScreen parent) {
        GuiWearBackups screen = new GuiWearBackups(parent);
        screen.chooseFirst = true;
        return screen;
    }

    private boolean chooseFirst;

    @Override
    public void setWorldAndResolution(Minecraft mc, int width, int height) {
        super.setWorldAndResolution(mc, width, height);
        if (chooseFirst && chosen < 0 && !backups.isEmpty()) choose(0);
    }
}
