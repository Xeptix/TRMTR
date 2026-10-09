package com.trmtgtnh.client.gui;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.text.translation.I18n;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.trmtgtnh.item.GuideBook;

/**
 * The guide books, read.
 *
 * <p>
 * One screen for all four: a book is a key and a page count, and every word comes from the
 * language file, so this never needs to know what any particular book is about. It opens on the
 * table of contents - which is the point of having one - and a page is reached either from there
 * or by turning.
 *
 * <p>
 * No container and no server side. Everything shown is already in the client's own language file,
 * so opening a book asks nobody's permission and costs no packet.
 *
 * <p>
 * A page and a leaf are not the same thing. A page is what the contents lists and what the folio
 * counts; a leaf is as much of it as fits on the paper. Most pages are one leaf and the longest run
 * to several, and turning walks leaves so the reader never meets the seam. This matters
 * more than it sounds: the screen this replaces drew twelve lines and dropped whatever came after
 * them without saying so, which cost the two densest pages in the mod six lines between them - and
 * would have cost any translation rather more, since the prose is meant to be rewritten freely.
 *
 * <p>
 * Two invariants are kept by construction rather than by luck, because vanilla's click loop has no
 * break in it and fires every control the cursor is inside. Vertically, the list and the foot are
 * cut from one budget in {@link BookChrome} and cannot reach each other. Horizontally, the
 * navigation row is measured before it is placed and each key is cut to the room it was given, so
 * a long translation shortens a word instead of putting two controls on the same pixel.
 *
 * <p>
 * A vertical bar in a body value is a paragraph break. Nothing ships one today; it is here so that
 * prose can gain structure later by editing the language file, which is where the prose lives.
 */
@SideOnly(Side.CLIENT)
public class GuiGuideBook extends GuiScreen {

    private static final int ID_CONTENTS = 1;
    private static final int ID_PREV = 2;
    private static final int ID_NEXT = 3;
    private static final int ID_CLOSE = 4;
    private static final int ID_UP = 5;
    private static final int ID_DOWN = 6;
    private static final int ID_ENTRY = 100;

    private final GuideBook book;

    /** 0 is the table of contents; 1..pages is a page. */
    private int page;

    /** Which leaf of the current page is showing, counting from zero. */
    private int leaf;

    /** The first contents entry shown. Only ever moves for a book longer than the list is deep. */
    private int scroll;

    /** The current page's body, wrapped and already cut into leaves. */
    private final List<List<String>> leaves = new ArrayList<List<String>>();

    /**
     * Set when a control has changed what the screen shows, and acted on at the top of the next
     * frame.
     *
     * <p>
     * Vanilla's click loop walks the button list by index and does not stop at the first control
     * it hits, so it is still walking that list when a control fires. Rebuilding it there would be
     * pulling the list out from under the loop, and whether that mattered would come down to
     * whether the new controls happened to land where the old ones were. Waiting one frame costs
     * nothing anyone can see and makes the question not arise.
     */
    private boolean relayout;

    public GuiGuideBook(GuideBook book) {
        this.book = book;
    }

    private int left() {
        return (width - BookChrome.PANEL_W) / 2;
    }

    private int top() {
        return Math.max(4, (height - BookChrome.PANEL_H) / 2);
    }

    private boolean scrolls() {
        return book.pages > BookChrome.LIST_ROWS;
    }

    private String text(String key) {
        return I18n.translateToLocal(key);
    }

    /**
     * The word on the key that shuts the book.
     *
     * <p>
     * It is the one string this screen needs that the language file does not already have, and
     * I18n hands back the key itself when it is missing, so a forgotten line would put
     * the literal {@code trmtgtnh.guide.close} on the leaf. Falling back to vanilla's own Done -
     * which every language has - costs three lines and means the language edit is an improvement
     * rather than a prerequisite.
     */
    private String closeLabel() {
        return I18n.canTranslate("trmtgtnh.guide.close") ? text("trmtgtnh.guide.close") : text("gui.done");
    }

    @Override
    public void initGui() {
        layOutPage();
        buildControls();
    }

    // ------------------------------------------------------------------ the controls

    /**
     * Rebuilds this screen's own controls.
     *
     * <p>
     * Only its own: anything another mod appended to the button list from Forge's post-init event
     * is left where it is. Emptying the list, which is what this screen used to do on every press,
     * threw those away for good, since the event that added them only fires when the screen is
     * sized.
     */
    private void buildControls() {
        relayout = false;
        for (Iterator<GuiButton> it = buttonList.iterator(); it.hasNext();) {
            if (it.next() instanceof GuiBookButton) it.remove();
        }
        int left = left();
        int top = top();

        if (page == 0) {
            int rows = Math.min(book.pages, BookChrome.LIST_ROWS);
            scroll = Math.max(0, Math.min(scroll, book.pages - rows));
            // A row stops at the text column when there is a rail beside it and reaches six pixels
            // into the outer margin when there is not, so the rail and the rows never share a pixel.
            int rowWidth = BookChrome.TEXT_W + (scrolls() ? 0 : 6);
            for (int r = 0; r < rows; r++) {
                int n = scroll + r + 1;
                buttonList.add(
                    GuiBookButton.row(
                        ID_ENTRY + n,
                        left,
                        top + BookChrome.CONTENT_Y + r * BookChrome.ROW,
                        rowWidth,
                        n + ".",
                        text(book.pageTitleKey(n)),
                        String.valueOf(n),
                        fontRenderer));
            }
            if (scroll > 0) {
                buttonList.add(GuiBookButton.rail(ID_UP, left, top + BookChrome.CONTENT_Y, true));
            }
            if (scroll + rows < book.pages) {
                buttonList
                    .add(GuiBookButton.rail(ID_DOWN, left, top + BookChrome.CONTENT_B - BookChrome.RAIL_BTN_H, false));
            }
            buildNav(
                left,
                top,
                new String[] { closeLabel() },
                new int[] { 0 },
                new int[] { ID_CLOSE },
                new boolean[] { true });
            return;
        }

        buildNav(
            left,
            top,
            new String[] { text("trmtgtnh.guide.prev"), text("trmtgtnh.guide.contents"), text("trmtgtnh.guide.next"),
                closeLabel() },
            new int[] { -1, 0, 1, 0 },
            new int[] { ID_PREV, ID_CONTENTS, ID_NEXT, ID_CLOSE },
            new boolean[] { page > 1 || leaf > 0, true, page < book.pages || leaf < leaves.size() - 1, true });
    }

    /**
     * Measures the navigation row, then places it.
     *
     * <p>
     * The old screen gave each key a width in pixels chosen by eye for English. This one asks the
     * font how wide each label really is, and if the four of them will not fit the column it takes
     * the words off the two arrow keys first - an arrow needs no word - and only then starts
     * cutting. What is cut is shared out smallest first, so a short word survives whole and only
     * the long one loses its tail, which is the opposite of what a flat proportion does.
     */
    private void buildNav(int left, int top, String[] labels, int[] chevrons, int[] ids, boolean[] on) {
        int n = labels.length;
        int[] w = new int[n];
        int total = 0;
        for (int i = 0; i < n; i++) {
            w[i] = GuiBookButton.navWidth(labels[i], chevrons[i], fontRenderer);
            total += w[i];
        }
        int avail = BookChrome.TEXT_W - BookChrome.NAV_GAP * (n - 1);

        if (total > avail) {
            total = 0;
            for (int i = 0; i < n; i++) {
                if (chevrons[i] != 0) labels[i] = "";
                w[i] = GuiBookButton.navWidth(labels[i], chevrons[i], fontRenderer);
                total += w[i];
            }
        }

        if (total > avail) {
            int fixed = 0;
            int[] need = new int[n];
            for (int i = 0; i < n; i++) {
                need[i] = fontRenderer.getStringWidth(labels[i]);
                fixed += w[i] - need[i];
            }
            int room = Math.max(0, avail - fixed);
            boolean[] done = new boolean[n];
            for (int k = n; k > 0; k--) {
                int pick = -1;
                for (int i = 0; i < n; i++) {
                    if (!done[i] && (pick < 0 || need[i] < need[pick])) pick = i;
                }
                labels[pick] = BookChrome.fit(fontRenderer, labels[pick], Math.min(need[pick], room / k));
                w[pick] = GuiBookButton.navWidth(labels[pick], chevrons[pick], fontRenderer);
                room -= fontRenderer.getStringWidth(labels[pick]);
                done[pick] = true;
            }
        }

        int used = 0;
        for (int i = 0; i < n; i++) used += w[i];
        int gap = n > 1 ? Math.max(0, BookChrome.TEXT_W - used) / (n - 1) : 0;

        int x = left + BookChrome.TEXT_X;
        for (int i = 0; i < n; i++) {
            // The last key is pulled out to the margin so integer division cannot leave a ragged
            // right edge. Math.max only ever moves it further right, so it cannot walk back into
            // the key before it.
            if (i == n - 1) x = Math.max(x, left + BookChrome.TEXT_R - w[i]);
            GuiBookButton key = GuiBookButton.nav(ids[i], x, top + BookChrome.NAV_Y, w[i], labels[i], chevrons[i]);
            key.enabled = on[i];
            buttonList.add(key);
            x += w[i] + gap;
        }
    }

    // ------------------------------------------------------------------ the text

    /**
     * Wraps the current page's body and cuts it into leaves.
     *
     * <p>
     * The cut is balanced rather than greedy - a page of seventeen lines becomes nine and eight,
     * not twelve and five - because two even leaves look typeset and a full one followed by a stub
     * looks like something went wrong.
     */
    private void layOutPage() {
        leaves.clear();
        if (page < 1) {
            leaf = 0;
            return;
        }

        List<String> lines = new ArrayList<String>();
        for (String part : text(book.pageBodyKey(page)).split("\\|")) {
            String para = part.trim();
            if (para.isEmpty()) continue;
            if (!lines.isEmpty()) lines.add("");
            lines.addAll(fontRenderer.listFormattedStringToWidth(para, BookChrome.TEXT_W));
        }

        int cap = BookChrome.BODY_LINES;
        int count = Math.max(1, (lines.size() + cap - 1) / cap);
        int per = Math.max(1, (lines.size() + count - 1) / count);
        for (int i = 0; i < lines.size(); i += per) {
            List<String> chunk = new ArrayList<String>(lines.subList(i, Math.min(lines.size(), i + per)));
            while (!chunk.isEmpty() && chunk.get(0)
                .isEmpty()) chunk.remove(0);
            while (!chunk.isEmpty() && chunk.get(chunk.size() - 1)
                .isEmpty()) chunk.remove(chunk.size() - 1);
            leaves.add(chunk);
        }
        if (leaves.isEmpty()) leaves.add(new ArrayList<String>());
        leaf = Math.max(0, Math.min(leaf, leaves.size() - 1));
    }

    /**
     * One step through the book.
     *
     * <p>
     * Counted in leaves rather than pages, so a page that runs to two turns like any other and
     * arriving backwards at a long page lands on its last leaf rather than its first.
     */
    private void turn(int direction) {
        if (direction > 0) {
            if (leaf < leaves.size() - 1) leaf++;
            else if (page < book.pages) {
                page++;
                leaf = 0;
            } else return;
        } else {
            if (leaf > 0) leaf--;
            else if (page > 1) {
                page--;
                leaf = Integer.MAX_VALUE;
            } else return;
        }
        // The leaves are worked out at once and only the controls wait, so a second turn arriving
        // in the same frame steps from where the first one left off rather than from stale text.
        layOutPage();
        relayout = true;
    }

    private void scrollBy(int rows) {
        int wanted = Math.max(0, Math.min(scroll + rows, book.pages - BookChrome.LIST_ROWS));
        if (wanted != scroll) {
            scroll = wanted;
            relayout = true;
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id > ID_ENTRY) {
            page = button.id - ID_ENTRY;
            leaf = 0;
            layOutPage();
            relayout = true;
            return;
        }
        switch (button.id) {
            case ID_CONTENTS:
                page = 0;
                leaf = 0;
                layOutPage();
                relayout = true;
                break;
            case ID_PREV:
                turn(-1);
                break;
            case ID_NEXT:
                turn(1);
                break;
            case ID_UP:
                scrollBy(-1);
                break;
            case ID_DOWN:
                scrollBy(1);
                break;
            case ID_CLOSE:
                mc.displayGuiScreen(null);
                break;
            default:
                break;
        }
    }

    @Override
    public void handleMouseInput() throws java.io.IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;
        if (page == 0) scrollBy(wheel > 0 ? -1 : 1);
        else turn(wheel > 0 ? -1 : 1);
    }

    @Override
    protected void keyTyped(char typed, int key) throws java.io.IOException {
        if (page > 0 && (key == Keyboard.KEY_LEFT || key == Keyboard.KEY_RIGHT)) {
            turn(key == Keyboard.KEY_RIGHT ? 1 : -1);
        } else if (page == 0 && (key == Keyboard.KEY_UP || key == Keyboard.KEY_DOWN)) {
            scrollBy(key == Keyboard.KEY_DOWN ? 1 : -1);
        } else {
            super.keyTyped(typed, key);
        }
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void drawScreen(int mouseX, int mouseY, float partial) {
        if (relayout) buildControls();
        drawDefaultBackground();
        int left = left();
        int top = top();
        BookChrome.frame(left, top);

        if (page == 0) drawContents(left, top);
        else drawLeaf(left, top);

        super.drawScreen(mouseX, mouseY, partial);

        // Gui.drawRect leaves the last color it used on the GL state, and this screen ends on a
        // great many of them. Nothing here binds a texture so nothing here suffers, but whatever
        // draws next might, and putting it back costs one call.
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void drawLeaf(int left, int top) {
        List<String> lines = leaves.get(leaf);
        runningHead(left, top, page + " / " + book.pages);

        // A continued leaf repeats the heading quietly rather than shouting it twice, which says
        // "still this section" without needing a word for it in every language.
        heading(left, top, text(book.pageTitleKey(page)), leaf > 0);

        for (int i = 0; i < lines.size(); i++) {
            fontRenderer.drawString(
                lines.get(i),
                left + BookChrome.TEXT_X,
                top + BookChrome.CONTENT_Y + i * BookChrome.LINE,
                BookChrome.INK);
        }

        contentMark(left, top, lines.size() * BookChrome.LINE, leaf < leaves.size() - 1);
        BookChrome.rule(left + BookChrome.TEXT_X, left + BookChrome.TEXT_R, top + BookChrome.FOOT_RULE_Y, true);
    }

    private void drawContents(int left, int top) {
        runningHead(left, top, null);
        heading(left, top, text("trmtgtnh.guide.contents"), false);

        int rows = Math.min(book.pages, BookChrome.LIST_ROWS);
        if (scrolls()) drawRail(left, top);
        contentMark(left, top, rows * BookChrome.ROW, false);
        BookChrome.rule(left + BookChrome.TEXT_X, left + BookChrome.TEXT_R, top + BookChrome.FOOT_RULE_Y, true);
    }

    /**
     * The track the two rail marks are the ends of, with a block on it showing how much of the
     * list is in view.
     *
     * <p>
     * No book the mod ships is long enough to grow one; it is here so that the next one cannot
     * break the screen the way a tenth entry broke the old one.
     */
    private void drawRail(int left, int top) {
        int cx = left + BookChrome.TEXT_R + BookChrome.RAIL_W / 2;
        int trackTop = top + BookChrome.CONTENT_Y + BookChrome.RAIL_BTN_H + 2;
        int trackBottom = top + BookChrome.CONTENT_B - BookChrome.RAIL_BTN_H - 2;
        drawRect(cx, trackTop, cx + 1, trackBottom, BookChrome.INK_PALE);

        int span = trackBottom - trackTop;
        int block = Math.max(8, span * BookChrome.LIST_ROWS / book.pages);
        int at = trackTop + (span - block) * scroll / Math.max(1, book.pages - BookChrome.LIST_ROWS);
        drawRect(cx - 1, at, cx + 2, at + block, BookChrome.INK_FAINT);
    }

    /**
     * The mark that closes the content, when there is blank paper under it to explain.
     *
     * <p>
     * A leaf whose text reaches the bottom of the region gets none, and wants none: there is no
     * gap for a reader to wonder about. What tells them a full leaf carries on is the row of leaf
     * marks in the running head, which does not depend on how long the text is.
     */
    private void contentMark(int left, int top, int height, boolean continues) {
        int markTop = BookChrome.markTop(BookChrome.CONTENT_Y + height);
        if (markTop < 0) return;
        BookChrome.mark(left + BookChrome.TEXT_CX, top + markTop, continues, BookChrome.INK_FAINT);
    }

    /**
     * The book's title at the head of the leaf, and the folio opposite it, which is where a book
     * puts its page number and why the number never has to float on its own above the navigation.
     */
    private void runningHead(int left, int top, String folio) {
        int block = left + BookChrome.TEXT_R;
        int pips = 0;
        if (folio != null) {
            block -= fontRenderer.getStringWidth(folio);
            if (leaves.size() > 1) {
                pips = leaves.size() * 5 - 2;
                block -= 6 + pips;
            }
        }
        fontRenderer.drawString(
            BookChrome.fit(fontRenderer, text(book.titleKey()), block - 6 - left - BookChrome.TEXT_X),
            left + BookChrome.TEXT_X,
            top + BookChrome.HEAD_Y,
            BookChrome.INK_SOFT);

        if (folio == null) return;
        fontRenderer.drawString(
            folio,
            left + BookChrome.TEXT_R - fontRenderer.getStringWidth(folio),
            top + BookChrome.HEAD_Y,
            BookChrome.INK_SOFT);

        // One mark per leaf when a page runs to more than one, with the current one struck in full
        // ink. It is the only signal that does not depend on how much text the leaf happens to
        // carry, so it is the one a full leaf that continues has to rely on.
        if (pips == 0) return;
        for (int i = 0; i < leaves.size(); i++) {
            drawRect(
                block + i * 5,
                top + BookChrome.HEAD_Y + 2,
                block + i * 5 + 3,
                top + BookChrome.HEAD_Y + 5,
                i == leaf ? BookChrome.INK : BookChrome.INK_PALE);
        }
    }

    private void heading(int left, int top, String title, boolean muted) {
        String shown = BookChrome.fit(fontRenderer, title, BookChrome.TEXT_W - 1);
        if (muted) {
            fontRenderer.drawString(shown, left + BookChrome.TEXT_X, top + BookChrome.HEADING_Y, BookChrome.INK_SOFT);
        } else {
            BookChrome.bold(fontRenderer, shown, left + BookChrome.TEXT_X, top + BookChrome.HEADING_Y, BookChrome.INK);
        }
        BookChrome.rule(left + BookChrome.TEXT_X, left + BookChrome.TEXT_R, top + BookChrome.HEAD_RULE_Y, false);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
