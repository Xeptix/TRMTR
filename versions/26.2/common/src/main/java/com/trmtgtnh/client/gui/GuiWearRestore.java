package com.trmtgtnh.client.gui;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.resources.language.I18n;

import com.trmtgtnh.client.settings.SettingsKeys;
import com.trmtgtnh.server.WearBackups;
import com.trmtgtnh.util.MainThread;

/**
 * The single-player screen as a world opens with wear lost (0.9.229; the 1.7.10 tree's
 * docs/goals/0.9.229-wear-backup-spec.md, "Single player"): how many chunks lost wear and when the newest backup was
 * taken, with Restore, Ignore, and Don't ask again for this world. Escape is Ignore, which changes nothing. Each answer
 * goes to the world's own server, on its thread. Carried from 1.7.10's GuiWearRestore.
 */
public class GuiWearRestore extends SettingsQuestion {

    /** What the server offered, waiting for the client to be in its world with no other screen open. */
    private static volatile int[] offeredChunks;

    private static volatile long offeredBackup;

    private static volatile boolean offeredNewer;

    private static volatile boolean offeredMirror;

    private static volatile boolean offeredData;

    /** Called on the server's thread in single player; shown by {@link #tick} on the client's. */
    public static void offer(int chunks, long newestBackupMillis, boolean newerWorld, boolean mirrorHolds,
        boolean dataPutBack) {
        offeredBackup = newestBackupMillis;
        offeredNewer = newerWorld;
        offeredMirror = mirrorHolds;
        offeredData = dataPutBack;
        offeredChunks = new int[] { chunks };
    }

    /** Client ticks the player has stood in the world with nothing open, counted to {@link #SETTLE_TICKS}. */
    private static int settled;

    /** Two seconds in the world before the question, so it is not opened under the world's own first screens. */
    static final int SETTLE_TICKS = 40;

    /**
     * Once a client tick: the offered screen, once the player has been in the world a moment and nothing else is open;
     * and again whenever something else closed it without an answer, until it is answered. Forgotten with the world's
     * own server.
     */
    public static void tick(Minecraft mc) {
        int[] chunks = offeredChunks;
        if (chunks == null) return;
        if (!mc.hasSingleplayerServer()) {
            offeredChunks = null;
            settled = 0;
            return;
        }
        if (mc.level == null || mc.player == null || mc.gui.screen() != null) {
            settled = 0;
            return;
        }
        if (++settled < SETTLE_TICKS) return;
        settled = 0;
        mc.gui.setScreen(new GuiWearRestore(chunks[0], offeredBackup, offeredNewer, offeredMirror, offeredData));
    }

    GuiWearRestore(int chunks, long newestBackupMillis, boolean newerWorld, boolean mirrorHolds, boolean dataPutBack) {
        super(lines(chunks, newestBackupMillis, newerWorld, mirrorHolds, dataPutBack), headline(chunks), answers());
    }

    /**
     * Its lines, already in words: a question's lines are keys it looks up, and a key with no translation is its own
     * text, so these are passed as they are to be read.
     */
    private static List<String> lines(int chunks, long newest, boolean newer, boolean mirror, boolean data) {
        List<String> out = new ArrayList<String>();
        out.add(headline(chunks).get(0));
        // This world's own records missing at load count as the whole gone (Xep, after phase 1): said, and put back.
        if (data && chunks > 0) out.add(I18n.get(SettingsKeys.BACKUPS_ASK_DATA));
        // The mirror, where it holds the wear, is the newest copy, and offered as such; then the newest backup.
        if (mirror) out.add(I18n.get(SettingsKeys.BACKUPS_ASK_MIRROR));
        out.add(
            newest < 0L ? I18n.get(SettingsKeys.BACKUPS_ASK_NONE)
                : I18n.get(SettingsKeys.BACKUPS_ASK_NEWEST, GuiWearBackups.when(newest)));
        if (newer) out.add(I18n.get(SettingsKeys.BACKUPS_ASK_NEWER));
        return out;
    }

    private static List<String> headline(int chunks) {
        List<String> out = new ArrayList<String>();
        out.add(
            chunks == 0 ? I18n.get(SettingsKeys.BACKUPS_ASK_DATA_ONLY)
                : chunks == 1 ? I18n.get(SettingsKeys.BACKUPS_ASK_ONE)
                    : I18n.get(SettingsKeys.BACKUPS_ASK_MANY, Integer.toString(chunks)));
        return out;
    }

    private static List<Answer> answers() {
        List<Answer> out = new ArrayList<Answer>();
        out.add(new Answer(SettingsKeys.BACKUPS_RESTORE, () -> answer("restore")));
        out.add(new Answer(SettingsKeys.BACKUPS_IGNORE, () -> answer("ignore")));
        out.add(new Answer(SettingsKeys.BACKUPS_NEVER, () -> answer("never")));
        return out;
    }

    private static void answer(final String choice) {
        offeredChunks = null;
        Minecraft.getInstance().gui.setScreen(null);
        MainThread.onServer(
            () -> WearBackups.get()
                .answer(choice));
    }

    /** Escape is Ignore, the answer that changes nothing, though it is not the last of the three. */
    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            answer("ignore");
            return true;
        }
        return super.keyPressed(event);
    }

    /**
     * The three buttons as wide as "Don't ask again for this world" needs where the window has the room: a question's
     * own width, 120, cuts it.
     */
    @Override
    protected void init() {
        super.init();
        List<AbstractWidget> shown = new ArrayList<AbstractWidget>();
        for (GuiEventListener each : children()) {
            if (each instanceof AbstractWidget) shown.add((AbstractWidget) each);
        }
        int count = shown.size();
        int each = Math.max(120, Math.min(180, (width - 16 - 4 * (count - 1)) / Math.max(1, count)));
        int row = each * count + 4 * (count - 1);
        int x = (width - row) / 2;
        for (int i = 0; i < count; i++) {
            AbstractWidget button = shown.get(i);
            button.setWidth(each);
            button.setX(x + i * (each + 4));
        }
    }

    /** Presses one of the three by its answer, as a click on it does: for the test harness's played check. */
    public void press(String choice) {
        answer(choice);
    }
}
