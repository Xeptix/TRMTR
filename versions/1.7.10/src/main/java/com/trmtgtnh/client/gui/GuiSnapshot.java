package com.trmtgtnh.client.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.StatCollector;

import com.trmtgtnh.network.PacketSnapshotAction;
import com.trmtgtnh.network.TrmtNetwork;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * What each snapshot side holds, and the three things that can be done about it.
 *
 * <p>
 * The screen holds no snapshots of its own - only three bits saying which sides have something
 * and whether there is a way back. Every button asks the server, and the server answers with a
 * fresh set of those bits, so what is on screen is always what the server actually has rather
 * than what this client last assumed.
 *
 * <p>
 * Commit turns into Undo once something has been applied, and goes back to Commit as soon as the
 * config is changed again - which is the same moment the undo mark moves.
 */
@SideOnly(Side.CLIENT)
public class GuiSnapshot extends GuiScreen {

    private static final int ID_COMMIT_LEFT = 10;
    private static final int ID_CLEAR_LEFT = 11;
    private static final int ID_COMMIT_RIGHT = 20;
    private static final int ID_CLEAR_RIGHT = 21;
    private static final int ID_UNDO = 30;
    private static final int ID_DONE = 40;

    private int flags;

    public GuiSnapshot(int flags) {
        this.flags = flags;
    }

    /** Called when the server sends a fresh state while the screen is already open. */
    public void update(int newFlags) {
        this.flags = newFlags;
        initGui();
    }

    private boolean hasLeft() {
        return (flags & 0x1) != 0;
    }

    private boolean hasRight() {
        return (flags & 0x2) != 0;
    }

    private boolean canUndo() {
        return (flags & 0x4) != 0;
    }

    private int top() {
        return Math.max(4, height / 2 - 90);
    }

    /** Half the panel's width. Wide enough for two hundred-pixel columns and a margin either side. */
    private static final int HALF = 124;

    /**
     * The blurb, broken to fit inside the panel.
     *
     * <p>
     * It used to be one centred string against a two hundred and forty pixel panel, and it is two
     * hundred and sixty-four pixels long, so it hung over both edges and out onto the world behind.
     * Wrapping rather than shortening because a translation will be a different length again, and a
     * sentence that fits in English is not a sentence that fits.
     */
    @SuppressWarnings("unchecked")
    private java.util.List<String> blurb() {
        return fontRendererObj
            .listFormattedStringToWidth(StatCollector.translateToLocal("trmtgtnh.snapshot.gui.blurb"), HALF * 2 - 24);
    }

    /**
     * Where the panel's body begins, which is under however many lines the blurb turned out to be.
     *
     * <p>
     * Everything below is placed against this rather than against the panel top, so a longer
     * translation pushes the screen down instead of writing over the columns.
     */
    private int body() {
        return top() + 24 + blurb().size() * 10;
    }

    private int panelBottom() {
        return body() + 131;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public void initGui() {
        buttonList.clear();
        int midX = width / 2;
        int body = body();

        GuiButton commitLeft = new GuiButton(
            ID_COMMIT_LEFT,
            midX - 108,
            body + 28,
            100,
            20,
            StatCollector.translateToLocal("trmtgtnh.snapshot.gui.commit"));
        commitLeft.enabled = hasLeft();
        buttonList.add(commitLeft);

        GuiButton clearLeft = new GuiButton(
            ID_CLEAR_LEFT,
            midX - 108,
            body + 50,
            100,
            20,
            StatCollector.translateToLocal("trmtgtnh.snapshot.gui.clear"));
        clearLeft.enabled = hasLeft();
        buttonList.add(clearLeft);

        GuiButton commitRight = new GuiButton(
            ID_COMMIT_RIGHT,
            midX + 8,
            body + 28,
            100,
            20,
            StatCollector.translateToLocal("trmtgtnh.snapshot.gui.commit"));
        commitRight.enabled = hasRight();
        buttonList.add(commitRight);

        GuiButton clearRight = new GuiButton(
            ID_CLEAR_RIGHT,
            midX + 8,
            body + 50,
            100,
            20,
            StatCollector.translateToLocal("trmtgtnh.snapshot.gui.clear"));
        clearRight.enabled = hasRight();
        buttonList.add(clearRight);

        GuiButton undo = new GuiButton(
            ID_UNDO,
            midX - 108,
            body + 78,
            216,
            20,
            StatCollector.translateToLocal("trmtgtnh.snapshot.gui.undo"));
        undo.enabled = canUndo();
        buttonList.add(undo);

        buttonList
            .add(new GuiButton(ID_DONE, midX - 50, body + 104, 100, 20, StatCollector.translateToLocal("gui.done")));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case ID_COMMIT_LEFT:
                TrmtNetwork.sendSnapshotAction(PacketSnapshotAction.COMMIT_LEFT);
                break;
            case ID_COMMIT_RIGHT:
                TrmtNetwork.sendSnapshotAction(PacketSnapshotAction.COMMIT_RIGHT);
                break;
            case ID_CLEAR_LEFT:
                TrmtNetwork.sendSnapshotAction(PacketSnapshotAction.CLEAR_LEFT);
                break;
            case ID_CLEAR_RIGHT:
                TrmtNetwork.sendSnapshotAction(PacketSnapshotAction.CLEAR_RIGHT);
                break;
            case ID_UNDO:
                TrmtNetwork.sendSnapshotAction(PacketSnapshotAction.UNDO);
                break;
            case ID_DONE:
                mc.displayGuiScreen(null);
                break;
            default:
                break;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partial) {
        drawDefaultBackground();
        int midX = width / 2;
        int top = top();
        int body = body();
        int bottom = panelBottom();

        // The panel now encloses the Done key as well. It used to stop above it, so the one control
        // that closes the screen was the one control standing outside the box it belonged to.
        drawRect(midX - HALF - 1, top - 1, midX + HALF + 1, bottom + 1, 0xFF6A6A7A);
        drawRect(midX - HALF, top, midX + HALF, bottom, 0xE6141420);
        drawGradientRect(midX - HALF, top, midX + HALF, top + 18, 0xFF32324A, 0xFF20202C);
        drawRect(midX - HALF, top + 18, midX + HALF, top + 19, 0xFF6A6A7A);

        drawCenteredString(
            fontRendererObj,
            StatCollector.translateToLocal("trmtgtnh.snapshot.gui.title"),
            midX,
            top + 5,
            0xFFFFFF);
        int line = top + 24;
        for (String part : blurb()) {
            drawCenteredString(fontRendererObj, part, midX, line, 0xB8B8C4);
            line += 10;
        }

        // One column per side, each naming what it holds.
        drawCenteredString(
            fontRendererObj,
            StatCollector.translateToLocal("trmtgtnh.snapshot.gui.left"),
            midX - 58,
            body + 4,
            0xFFFFFF);
        drawCenteredString(
            fontRendererObj,
            StatCollector
                .translateToLocal(hasLeft() ? "trmtgtnh.snapshot.gui.stored" : "trmtgtnh.snapshot.gui.emptySlot"),
            midX - 58,
            body + 16,
            hasLeft() ? 0x9FE0A0 : 0x8A8A96);

        drawCenteredString(
            fontRendererObj,
            StatCollector.translateToLocal("trmtgtnh.snapshot.gui.right"),
            midX + 58,
            body + 4,
            0xFFFFFF);
        drawCenteredString(
            fontRendererObj,
            StatCollector
                .translateToLocal(hasRight() ? "trmtgtnh.snapshot.gui.stored" : "trmtgtnh.snapshot.gui.emptySlot"),
            midX + 58,
            body + 16,
            hasRight() ? 0x9FE0A0 : 0x8A8A96);

        super.drawScreen(mouseX, mouseY, partial);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
