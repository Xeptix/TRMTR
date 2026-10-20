package com.trmtgtnh.client.gui;

import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;

import com.trmtgtnh.network.PacketSnapshotAction;
import com.trmtgtnh.network.TrmtNetwork;

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
 * Undo is a button of its own, open once something has been applied and closed again as soon as
 * the config is changed - which is the same moment the undo mark moves.
 */
public class GuiSnapshot extends Screen {

    private static final int ID_COMMIT_LEFT = 10;
    private static final int ID_CLEAR_LEFT = 11;
    private static final int ID_COMMIT_RIGHT = 20;
    private static final int ID_CLEAR_RIGHT = 21;
    private static final int ID_UNDO = 30;
    private static final int ID_DONE = 40;

    // A screen carries its own name at this version, for the narrator and for anything that
    // lists what is open. Neither older edition has one; the heading's own key is handed over so
    // the two cannot disagree.
    private static final net.minecraft.network.chat.Component TITLE =
        net.minecraft.network.chat.Component.translatable("trmtgtnh.snapshot.gui.title");

    private int flags;

    public GuiSnapshot(int flags) {
        super(TITLE);
        this.flags = flags;
    }

    /** Called when the server sends a fresh state while the screen is already open. */
    public void update(int newFlags) {
        this.flags = newFlags;
        // Through the three-argument init, which clears the buttons before laying them out again.
        // The bare init() only adds, so every answer stacked a fresh row of buttons over the last,
        // the old ones still clickable underneath with the old answer's greying - unseen until
        // 0.9.223, when answers began refreshing this screen rather than opening a new one (CF81).
        // From 26.1 that init takes the size alone - a screen is handed its game when it is made - and on a screen
        // already open it lays the buttons out again from clear just as 1.21.1's did.
        if (minecraft != null) init(width, height);
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
    private java.util.List<String> blurb() {
        return GuiText.wrap(font, com.trmtgtnh.util.Translate.get("trmtgtnh.snapshot.gui.blurb"), HALF * 2 - 24);
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

    @Override
    public void init() {
        int midX = width / 2;
        int body = body();

        Tagged commitLeft = new Tagged(this, ID_COMMIT_LEFT,
            midX - 108,
            body + 28,
            100,
            20,
            com.trmtgtnh.util.Translate.get("trmtgtnh.snapshot.gui.commit"));
        commitLeft.active = hasLeft();
        addRenderableWidget(commitLeft);

        Tagged clearLeft = new Tagged(this, ID_CLEAR_LEFT,
            midX - 108,
            body + 50,
            100,
            20,
            com.trmtgtnh.util.Translate.get("trmtgtnh.snapshot.gui.clear"));
        clearLeft.active = hasLeft();
        addRenderableWidget(clearLeft);

        Tagged commitRight = new Tagged(this, ID_COMMIT_RIGHT,
            midX + 8,
            body + 28,
            100,
            20,
            com.trmtgtnh.util.Translate.get("trmtgtnh.snapshot.gui.commit"));
        commitRight.active = hasRight();
        addRenderableWidget(commitRight);

        Tagged clearRight = new Tagged(this, ID_CLEAR_RIGHT,
            midX + 8,
            body + 50,
            100,
            20,
            com.trmtgtnh.util.Translate.get("trmtgtnh.snapshot.gui.clear"));
        clearRight.active = hasRight();
        addRenderableWidget(clearRight);

        Tagged undo = new Tagged(this, ID_UNDO,
            midX - 108,
            body + 78,
            216,
            20,
            com.trmtgtnh.util.Translate.get("trmtgtnh.snapshot.gui.undo"));
        undo.active = canUndo();
        addRenderableWidget(undo);

        addRenderableWidget(new Tagged(this, ID_DONE, midX - 50, body + 104, 100, 20, com.trmtgtnh.util.Translate.get("gui.done")));
    }

    void actionPerformed(Button button, int id) {
        switch (id) {
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
                minecraft.setScreen(null);
                break;
            default:
                break;
        }
    }

    /**
     * The panel, then the buttons over it. From 26.1 the game draws a screen's background itself, in a layer of its
     * own before this one: the blur and the menu background that 1.21.1 drew by calling {@code super.renderBackground}
     * at the top of {@code render}. So that call is gone, and so is the empty {@code renderBackground} that kept
     * {@code super.render} from drawing the background a second time over the panel. The order is 1.21.1's:
     * background, panel, buttons.
     */
    @Override
    public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        int midX = width / 2;
        int top = top();
        int body = body();
        int bottom = panelBottom();

        // The panel now encloses the Done key as well. It used to stop above it, so the one control
        // that closes the screen was the one control standing outside the box it belonged to.
        graphics.fill(midX - HALF - 1, top - 1, midX + HALF + 1, bottom + 1, 0xFF6A6A7A);
        graphics.fill(midX - HALF, top, midX + HALF, bottom, 0xE6141420);
        graphics.fillGradient(midX - HALF, top, midX + HALF, top + 18, 0xFF32324A, 0xFF20202C);
        graphics.fill(midX - HALF, top + 18, midX + HALF, top + 19, 0xFF6A6A7A);

        graphics.centeredText(font, com.trmtgtnh.util.Translate.get("trmtgtnh.snapshot.gui.title"), midX, top + 5, GuiText.argb(0xFFFFFF));
        int line = top + 24;
        for (String part : blurb()) {
            graphics.centeredText(font, part, midX, line, GuiText.argb(0xB8B8C4));
            line += 10;
        }

        // One column per side, each naming what it holds.
        graphics.centeredText(font,
            com.trmtgtnh.util.Translate.get("trmtgtnh.snapshot.gui.left"),
            midX - 58,
            body + 4,
            GuiText.argb(0xFFFFFF));
        graphics.centeredText(font,
            com.trmtgtnh.util.Translate.get(hasLeft() ? "trmtgtnh.snapshot.gui.stored" : "trmtgtnh.snapshot.gui.emptySlot"),
            midX - 58,
            body + 16,
            GuiText.argb(hasLeft() ? 0x9FE0A0 : 0x8A8A96));

        graphics.centeredText(font,
            com.trmtgtnh.util.Translate.get("trmtgtnh.snapshot.gui.right"),
            midX + 58,
            body + 4,
            GuiText.argb(0xFFFFFF));
        graphics.centeredText(font,
            com.trmtgtnh.util.Translate.get(hasRight() ? "trmtgtnh.snapshot.gui.stored" : "trmtgtnh.snapshot.gui.emptySlot"),
            midX + 58,
            body + 16,
            GuiText.argb(hasRight() ? 0x9FE0A0 : 0x8A8A96));

        super.extractRenderState(graphics, mouseX, mouseY, partial);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * A button that remembers which one it is. See {@code GuiTamper.Tagged}, which is the same class
     * for the same reason: vanilla's id is gone, and this screen's dispatch is a switch over six of
     * them that is worth keeping as it was.
     */
    private static final class Tagged extends Button.Plain {

        Tagged(GuiSnapshot screen, int id, int x, int y, int width, int height, String label) {
            super(
                x,
                y,
                width,
                height,
                net.minecraft.network.chat.Component.literal(label),
                pressed -> screen.actionPerformed((Button) pressed, id),
                DEFAULT_NARRATION);
        }
    }
}
