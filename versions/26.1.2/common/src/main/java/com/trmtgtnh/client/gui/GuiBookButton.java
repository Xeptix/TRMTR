package com.trmtgtnh.client.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;

/**
 * The three controls the guide books use: a key at the foot of the leaf, a row of the contents,
 * and one of the two marks on the scroll rail.
 *
 * <p>
 * A button rather than a hand-rolled hit test, because everything a {@link Button} already does
 * - enabling, the click sound, the routing into actionPerformed - is wanted. Only the drawing is
 * replaced. Vanilla's button art is a grey stone widget with a shadowed label, which on parchment
 * looks bolted on, and its disabled state is the same dark widget with grey text, which reads as
 * broken rather than as unavailable. Both of those are drawn here instead as ink on paper.
 *
 * <p>
 * Nothing in here binds a texture and nothing draws through {@code Gui.drawString}, so no label
 * carries a drop shadow and no leftover texture color can tint the page.
 *
 * <p>
 * Every label is cut to the width it was given before it is stored, never at draw time, so a
 * translated string can no more overprint its neighbour than it can change the layout.
 */
public class GuiBookButton extends Button {

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

    /** Which control this is, since vanilla's own id is gone. See GuiGolem's Flat. */
    private final int id;

    private GuiBookButton(GuiGuideBook screen, int id, int x, int y, int width, int height, String label, int kind,
        int chevron, String number, String folio, int panelLeft) {
        super(x, y, width, height, net.minecraft.network.chat.Component.literal(label),
            ignored -> screen.actionPerformed(id),
                DEFAULT_NARRATION);
        this.id = id;
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
    public static GuiBookButton nav(GuiGuideBook screen, int id, int x, int y, int width, String label,
        int chevron) {
        return new GuiBookButton(screen, id, x, y, width, BookChrome.NAV_H, label, KIND_KEY, chevron,
            null, null, 0);
    }

    /** How wide a key with this label would be. */
    public static int navWidth(String label, int chevron, Font font) {
        return PAD * 2 + font.width(label) + (chevron != 0 ? CHEV : 0);
    }

    /**
     * One entry of the contents: its number in the margin, its title, a leader, and the page it
     * goes to. The whole row is the control, because a list is easier to hit when the hit area is
     * the line rather than the words.
     */
    public static GuiBookButton row(GuiGuideBook screen, int id, int panelLeft, int y, int width, String number,
        String title, String folio, Font font) {
        int titleX = BookChrome.TEXT_X + BookChrome.NUMBER_COL + BookChrome.TITLE_GAP;
        String shown = BookChrome.fit(font, title, BookChrome.TEXT_R - font.width(folio) - titleX - 8);
        return new GuiBookButton(
            screen,
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
    public static GuiBookButton rail(GuiGuideBook screen, int id, int panelLeft, int y, boolean up) {
        return new GuiBookButton(
            screen,
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

    /**
     * Drawn as 1.21.1 drew it. From 26.1 a button's drawing is its {@code extractContents}, which the game calls
     * where 1.21.1 called {@code renderWidget}; nothing of vanilla's art is drawn either way.
     */
    @Override
    protected void extractContents(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int mouseX, int mouseY,
        float partialTicks) {
        if (!visible) return;
        Minecraft mc = Minecraft.getInstance();
        // Worked out here rather than left to Button's own hover field, which is named for its
        // obfuscated self and is one of the few things in this screen that would not survive being
        // carried to another version of the game.
        boolean over = active && mouseX >= getX() && mouseY >= getY() && mouseX < getX() + width && mouseY < getY() + height;
        if (kind == KIND_ROW) drawRow(graphics, mc.font, over);
        else if (kind == KIND_RAIL) drawRail(graphics, over);
        else drawKey(graphics, mc.font, over);
    }

    private void drawKey(net.minecraft.client.gui.GuiGraphicsExtractor graphics, Font font, boolean over) {
        int textY = getY() + (height - 8) / 2;
        int chevY = getY() + (height - 5) / 2;
        int ink = !active ? BookChrome.INK_PALE : (over ? BookChrome.INK : BookChrome.INK_SOFT);

        // A control that cannot be used simply goes quiet: no key, no border, pale ink. There is
        // nothing there to look broken.
        if (active) {
            int x1 = getX() + width;
            int y1 = getY() + height;
            graphics.fill(getX(), getY(), x1, y1, over ? BookChrome.KEY_HOVER : BookChrome.KEY_FILL);
            int edge = over ? BookChrome.KEY_HOVER_EDGE : BookChrome.KEY_EDGE;
            graphics.fill(getX() + 1, getY(), x1 - 1, getY() + 1, edge);
            graphics.fill(getX() + 1, y1 - 1, x1 - 1, y1, edge);
            graphics.fill(getX(), getY() + 1, getX() + 1, y1 - 1, edge);
            graphics.fill(x1 - 1, getY() + 1, x1, y1 - 1, edge);
        }

        int textX = getX() + PAD;
        if (chevron < 0) {
            BookChrome.chevron(graphics, textX, chevY, true, ink);
            textX += CHEV;
        }
        graphics.text(font, getMessage().getString(), textX, textY, GuiText.argb(ink), false);
        if (chevron > 0) {
            BookChrome.chevron(graphics, textX + font.width(getMessage().getString()) + 3, chevY, false, ink);
        }
    }

    private void drawRail(net.minecraft.client.gui.GuiGraphicsExtractor graphics, boolean over) {
        BookChrome.arrow(graphics, getX() + BookChrome.RAIL_W / 2, getY() + 3, chevron < 0, over ? BookChrome.INK : BookChrome.INK_SOFT);
    }

    private void drawRow(net.minecraft.client.gui.GuiGraphicsExtractor graphics, Font font, boolean over) {
        int textY = getY() + (height - 8) / 2;
        if (over) {
            graphics.fill(getX(), getY(), getX() + width, getY() + height, BookChrome.ROW_HOVER);
            // The nib sits inside the row, so it is inside the wash and inside the hit box. Put it
            // in the gutter, as an earlier draft did, and the mouse over the mark does not light
            // the row the mark belongs to.
            graphics.fill(getX() + 1, getY() + 2, getX() + 4, getY() + height - 2, BookChrome.INK);
        }

        int numRight = panelLeft + BookChrome.TEXT_X + BookChrome.NUMBER_COL;
        graphics.text(font, number,
            numRight - font.width(number),
            textY,
            GuiText.argb(over ? BookChrome.INK : BookChrome.INK_SOFT), false);

        int titleX = numRight + BookChrome.TITLE_GAP;
        graphics.text(font, getMessage().getString(), titleX, textY, GuiText.argb(BookChrome.INK), false);

        int folioRight = panelLeft + BookChrome.TEXT_R;
        int folioW = font.width(folio);
        graphics.text(font, folio, folioRight - folioW, textY, GuiText.argb(over ? BookChrome.INK : BookChrome.INK_SOFT), false);

        // The leader starts on a fixed grid rather than wherever this row's title happened to end,
        // so the dots line up down the whole list instead of shivering from row to row. One
        // drawString of ". " repeated, not eighteen rectangles.
        int from = titleX + font.width(getMessage().getString()) + 5;
        int off = (from - panelLeft - BookChrome.TEXT_X) % BookChrome.LEADER_PITCH;
        if (off != 0) from += BookChrome.LEADER_PITCH - off;
        int to = folioRight - folioW - 5;
        int dots = (to - from) / BookChrome.LEADER_PITCH;
        if (dots > 0) {
            StringBuilder leader = new StringBuilder(dots * 2);
            for (int i = 0; i < dots; i++) leader.append(". ");
            graphics.text(font, leader.toString(), from, textY, GuiText.argb(over ? BookChrome.INK_SOFT : BookChrome.INK_FAINT), false);
        }
    }
}
