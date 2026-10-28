package com.trmtgtnh.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;

import org.lwjgl.opengl.GL11;

/**
 * The scrolled list both of the mod's own settings pages draw their rows in ({@link WornPathScreen},
 * {@link WornPathListScreen}): where it starts and ends, how far it is scrolled, its background, and the clip that
 * keeps a row half scrolled out of view from being drawn over the heading or the buttons (0.9.228, as the 1.7.10
 * edition's).
 *
 * <p>
 * The game's own scrolling list, {@code GuiSlot}, draws the dirt background over everything above and below it, which
 * would cover the lawn these pages are drawn on; this draws only the list, as 26.3's list does.
 */
final class SettingsList {

    /** How far the wheel moves the list per notch: one row. */
    private static final int STEP = WornPathScreen.ROW_HEIGHT;

    final int top;
    final int bottom;
    private final int screenWidth;
    private final int rowHeight;
    int rows;
    int scroll;

    SettingsList(int screenWidth, int top, int bottom, int rowHeight) {
        this.screenWidth = screenWidth;
        this.top = top;
        this.bottom = bottom;
        this.rowHeight = rowHeight;
    }

    int rowWidth() {
        return Math.min(WornPathScreen.ROW_WIDTH, screenWidth - 40);
    }

    int rowLeft() {
        return screenWidth / 2 - rowWidth() / 2;
    }

    int rowRight() {
        return rowLeft() + rowWidth();
    }

    /** Where row {@code index} is drawn from, scrolled. */
    int rowTop(int index) {
        return top + 4 + index * rowHeight - scroll;
    }

    /** Whether any of row {@code index} is in view. */
    boolean shows(int index) {
        int at = rowTop(index);
        return at + rowHeight > top && at < bottom;
    }

    /** Whether a point is inside the list, where its rows may be clicked. */
    boolean holds(int x, int y) {
        return y >= top && y < bottom && x >= rowLeft() - 6 && x < rowRight() + 6;
    }

    private int furthest() {
        return Math.max(0, rows * rowHeight + 8 - (bottom - top));
    }

    void scrollTo(int to) {
        scroll = to < 0 ? 0 : Math.min(to, furthest());
    }

    /** One notch of the wheel: up is positive in the game's reading of it, and moves the list back towards its top. */
    void wheel(int notches) {
        if (notches != 0) scrollTo(scroll - Integer.signum(notches) * STEP);
    }

    /** The list's dark panel and its two edges, as 26.3 draws them. */
    void drawBackground() {
        Gui.drawRect(rowLeft() - 6, top, rowRight() + 6, bottom, 0x99101010);
        Gui.drawRect(rowLeft() - 6, top - 1, rowRight() + 6, top, 0x80000000);
        Gui.drawRect(rowLeft() - 6, bottom, rowRight() + 6, bottom + 1, 0x80000000);
        int furthest = furthest();
        if (furthest <= 0) return;
        // A thumb at the list's right edge, its length the share of the rows in view.
        int track = bottom - top;
        int thumb = Math.max(16, track * track / (track + furthest));
        int at = top + (track - thumb) * scroll / furthest;
        Gui.drawRect(rowRight() + 3, at, rowRight() + 6, at + thumb, 0xC0A0A0A0);
    }

    /** Only the list's own band is drawn into until {@link #unclip}. */
    void clip(Minecraft mc, int screenHeight) {
        int factor = new ScaledResolution(mc).getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(0, (screenHeight - bottom) * factor, screenWidth * factor, (bottom - top) * factor);
    }

    void unclip() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }
}
