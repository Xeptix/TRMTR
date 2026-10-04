package com.trmtgtnh.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * The three controls the guide books use: a key at the foot of the leaf, a row of the contents,
 * and one of the two marks on the scroll rail.
 *
 * <p>
 * A button rather than a hand-rolled hit test, because everything a {@link GuiButton} already does
 * - enabling, the click sound, the routing into actionPerformed - is wanted. Only the drawing is
 * replaced. Vanilla's button art is a grey stone widget with a shadowed label, which on parchment
 * looks bolted on, and its disabled state is the same dark widget with grey text, which reads as
 * broken rather than as unavailable. Both of those are drawn here instead as ink on paper.
 *
 * <p>
 * Nothing in here binds a texture and nothing draws through {@code Gui.drawString}, so no label
 * carries a drop shadow and no leftover texture colour can tint the page.
 *
 * <p>
 * Every label is cut to the width it was given before it is stored, never at draw time, so a
 * translated string can no more overprint its neighbour than it can change the layout.
 */
@SideOnly(Side.CLIENT)
public class GuiBookButton extends GuiButton {

    /** Room either side of a key's label. */
    public static final int PAD = 6;

    /** What an arrow head and its gap cost a key that has one. */
    public static final int CHEV = 8;

    private static final int KIND_KEY = 0;

    private static final int KIND_ROW = 1;

    private static final int KIND_RAIL = 2;

    private final int kind;

    /**
     * On a key: minus one for an arrow before the label, plus one for one after it, nought for
     * neither. On a rail mark: minus one for up and plus one for down.
     */
    private final int chevron;

    /** The entry number and the page it goes to. Only a contents row has them. */
    private final String number;

    private final String folio;

    /** A row and a rail mark both reach outside the text column, so they need the panel's left. */
    private final int panelLeft;

    private GuiBookButton(int id, int x, int y, int width, int height, String label, int kind, int chevron,
        String number, String folio, int panelLeft) {
        super(id, x, y, width, height, label);
        this.kind = kind;
        this.chevron = chevron;
        this.number = number;
        this.folio = folio;
        this.panelLeft = panelLeft;
    }

    /**
     * A key at the foot of the leaf. Its width is worked out by the caller, which is the only
     * place that can see all of the row at once and therefore the only place that can promise the
     * keys do not touch.
     */
    public static GuiBookButton nav(int id, int x, int y, int width, String label, int chevron) {
        return new GuiBookButton(id, x, y, width, BookChrome.NAV_H, label, KIND_KEY, chevron, null, null, 0);
    }

    /** How wide a key with this label would be. */
    public static int navWidth(String label, int chevron, FontRenderer font) {
        return PAD * 2 + font.getStringWidth(label) + (chevron != 0 ? CHEV : 0);
    }

    /**
     * One entry of the contents: its number in the margin, its title, a leader, and the page it
     * goes to. The whole row is the control, because a list is easier to hit when the hit area is
     * the line rather than the words.
     */
    public static GuiBookButton row(int id, int panelLeft, int y, int width, String number, String title, String folio,
        FontRenderer font) {
        int titleX = BookChrome.TEXT_X + BookChrome.NUMBER_COL + BookChrome.TITLE_GAP;
        String shown = BookChrome.fit(font, title, BookChrome.TEXT_R - font.getStringWidth(folio) - titleX - 8);
        return new GuiBookButton(
            id,
            panelLeft + BookChrome.TEXT_X,
            y,
            width,
            BookChrome.ROW,
            shown,
            KIND_ROW,
            0,
            number,
            folio,
            panelLeft);
    }

    /**
     * One of the two marks on the scroll rail. It exists only while it can act, so there is never
     * a triangle on the page that looks like a control and does nothing - which is the same defect
     * as vanilla's disabled button looking broken, in a different costume.
     */
    public static GuiBookButton rail(int id, int panelLeft, int y, boolean up) {
        return new GuiBookButton(
            id,
            panelLeft + BookChrome.TEXT_R,
            y,
            BookChrome.RAIL_W,
            BookChrome.RAIL_BTN_H,
            "",
            KIND_RAIL,
            up ? -1 : 1,
            null,
            null,
            panelLeft);
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        if (!visible) return;
        // Worked out here rather than left to GuiButton's own hover field, which is named for its
        // obfuscated self and is one of the few things in this screen that would not survive being
        // carried to another version of the game.
        boolean over = enabled && mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
        if (kind == KIND_ROW) drawRow(mc.fontRenderer, over);
        else if (kind == KIND_RAIL) drawRail(over);
        else drawKey(mc.fontRenderer, over);
    }

    private void drawKey(FontRenderer font, boolean over) {
        int textY = y + (height - 8) / 2;
        int chevY = y + (height - 5) / 2;
        int ink = !enabled ? BookChrome.INK_PALE : (over ? BookChrome.INK : BookChrome.INK_SOFT);

        // A control that cannot be used simply goes quiet: no key, no border, pale ink. There is
        // nothing there to look broken.
        if (enabled) {
            int x1 = x + width;
            int y1 = y + height;
            drawRect(x, y, x1, y1, over ? BookChrome.KEY_HOVER : BookChrome.KEY_FILL);
            int edge = over ? BookChrome.KEY_HOVER_EDGE : BookChrome.KEY_EDGE;
            drawRect(x + 1, y, x1 - 1, y + 1, edge);
            drawRect(x + 1, y1 - 1, x1 - 1, y1, edge);
            drawRect(x, y + 1, x + 1, y1 - 1, edge);
            drawRect(x1 - 1, y + 1, x1, y1 - 1, edge);
        }

        int textX = x + PAD;
        if (chevron < 0) {
            BookChrome.chevron(textX, chevY, true, ink);
            textX += CHEV;
        }
        font.drawString(displayString, textX, textY, ink);
        if (chevron > 0) {
            BookChrome.chevron(textX + font.getStringWidth(displayString) + 3, chevY, false, ink);
        }
    }

    private void drawRail(boolean over) {
        BookChrome.arrow(x + BookChrome.RAIL_W / 2, y + 3, chevron < 0, over ? BookChrome.INK : BookChrome.INK_SOFT);
    }

    private void drawRow(FontRenderer font, boolean over) {
        int textY = y + (height - 8) / 2;
        if (over) {
            drawRect(x, y, x + width, y + height, BookChrome.ROW_HOVER);
            // The nib sits inside the row, so it is inside the wash and inside the hit box. Put it
            // in the gutter, as an earlier draft did, and the mouse over the mark does not light
            // the row the mark belongs to.
            drawRect(x + 1, y + 2, x + 4, y + height - 2, BookChrome.INK);
        }

        int numRight = panelLeft + BookChrome.TEXT_X + BookChrome.NUMBER_COL;
        font.drawString(
            number,
            numRight - font.getStringWidth(number),
            textY,
            over ? BookChrome.INK : BookChrome.INK_SOFT);

        int titleX = numRight + BookChrome.TITLE_GAP;
        font.drawString(displayString, titleX, textY, BookChrome.INK);

        int folioRight = panelLeft + BookChrome.TEXT_R;
        int folioW = font.getStringWidth(folio);
        font.drawString(folio, folioRight - folioW, textY, over ? BookChrome.INK : BookChrome.INK_SOFT);

        // The leader starts on a fixed grid rather than wherever this row's title happened to end,
        // so the dots line up down the whole list instead of shivering from row to row. One
        // drawString of ". " repeated, not eighteen rectangles.
        int from = titleX + font.getStringWidth(displayString) + 5;
        int off = (from - panelLeft - BookChrome.TEXT_X) % BookChrome.LEADER_PITCH;
        if (off != 0) from += BookChrome.LEADER_PITCH - off;
        int to = folioRight - folioW - 5;
        int dots = (to - from) / BookChrome.LEADER_PITCH;
        if (dots > 0) {
            StringBuilder leader = new StringBuilder(dots * 2);
            for (int i = 0; i < dots; i++) leader.append(". ");
            font.drawString(leader.toString(), from, textY, over ? BookChrome.INK_SOFT : BookChrome.INK_FAINT);
        }
    }
}
