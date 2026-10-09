package com.trmtgtnh.client.gui;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The book the guide screens are drawn on, and the one place its measurements live.
 *
 * <p>
 * Every measurement is an offset from the top left of the panel, so the page and the table of
 * contents are visibly the same object with two different things inside it, and so the number of
 * body lines and the number of contents rows are worked out from the room that actually exists
 * rather than assumed. The screen this replaces assumed, which is why a ten page book drew its
 * last two entries off the bottom of the panel and its eighth entry underneath the Done button.
 *
 * <p>
 * Nothing here ships a texture. It is all rectangles, which means there is no atlas to stitch, no
 * PNG for a resource pack to get wrong, and no texture bound at any point while the book draws -
 * so the leftover color that {@link Gui#drawRect} always leaves on the GL state cannot tint
 * anything.
 *
 * <p>
 * The worn page edge and the foxing are drawn from a hash of the coordinate rather than from a
 * random source, because a book that shimmered differently every frame would be worse than a book
 * with a ruler-straight edge. The hash is fed offsets from the panel, never screen coordinates, so
 * the pattern is also the same after the window is resized.
 */
@SideOnly(Side.CLIENT)
public final class BookChrome {

    private BookChrome() {}

    /**
     * The panel. Landscape and left bound, because a surveyor's ledger is the right object for a
     * mod about worn ground, and because the widest contents entry will not fit a portrait one.
     */
    public static final int PANEL_W = 250;

    public static final int PANEL_H = 192;

    /** The cover board showing round the leaf. */
    public static final int BORDER = 3;

    /** The stitched binding, just inside the left board. */
    public static final int SPINE_W = 10;

    /** Spine-side margin, narrower than the outer one as a bound page wants. */
    public static final int MARGIN_IN = 13;

    public static final int MARGIN_OUT = 18;

    public static final int TEXT_X = BORDER + SPINE_W + MARGIN_IN;

    public static final int TEXT_W = PANEL_W - TEXT_X - MARGIN_OUT - BORDER;

    public static final int TEXT_R = TEXT_X + TEXT_W;

    /**
     * Text is centred on the column rather than on the panel, so the ornaments line up with the
     * prose above them instead of with the binding.
     */
    public static final int TEXT_CX = TEXT_X + TEXT_W / 2;

    public static final int HEAD_Y = 10;

    public static final int HEADING_Y = 27;

    /**
     * The one rule on the leaf besides the foot. It closes the head block and opens the content
     * region, so the region a reader works in is bracketed top and bottom and nothing else has to
     * be divided. Two rules eighteen pixels apart with a single line of text between them, which
     * is what an earlier draft had, is belt and braces.
     */
    public static final int HEAD_RULE_Y = 38;

    public static final int CONTENT_Y = 45;

    /** Exclusive. Nothing in the content region may be drawn at or past this. */
    public static final int CONTENT_B = 155;

    public static final int FOOT_RULE_Y = 161;

    public static final int NAV_Y = 168;

    public static final int NAV_H = 14;

    /** The least air ever left between two controls on the navigation row. */
    public static final int NAV_GAP = 4;

    /** Vanilla's own line height, which is what a block of body text should be set at. */
    public static final int LINE = 9;

    /** A contents row, at about one and a third times the type size, as a printed list is set. */
    public static final int ROW = 11;

    /**
     * How much fits, worked out rather than guessed. Twelve body lines takes all but two of the
     * thirty-four pages in one leaf, and ten rows takes the longest book there is.
     */
    public static final int BODY_LINES = (CONTENT_B - CONTENT_Y) / LINE;

    public static final int LIST_ROWS = (CONTENT_B - CONTENT_Y) / ROW;

    /** Where the entry number is right aligned to, so a two digit number lines up under a one. */
    public static final int NUMBER_COL = 20;

    public static final int TITLE_GAP = 6;

    /** A leader on a fixed grid, so the dots line up down the list instead of shivering row to row. */
    public static final int LEADER_PITCH = 6;

    /** Air between the last line of content and the mark that closes it. */
    public static final int MARK_GAP = 3;

    public static final int MARK_H = 5;

    /** Air the mark must leave above the foot rule, or there is no room and none is drawn. */
    public static final int MARK_FOOT = 3;

    /** The scroll rail, in the outer margin. Only a book longer than the list is deep grows one. */
    public static final int RAIL_W = 9;

    public static final int RAIL_BTN_H = 10;

    public static final int INK = 0xFF2A2114;

    public static final int INK_SOFT = 0xFF5F4E36;

    public static final int INK_FAINT = 0xFF97845F;

    public static final int INK_PALE = 0xFFB6A582;

    public static final int PARCH = 0xFFE7D9B6;

    public static final int PARCH_EDGE = 0xFFB39F79;

    /**
     * The one color every shadow on the paper is mixed from.
     *
     * <p>
     * It has to be this much darker than the parchment to be worth drawing at all. An earlier
     * draft shaded the gutter with a tone twenty levels off the paper, which at half alpha moved
     * the darkest column of the fold by ten levels of red spread over fourteen pixels - about one
     * level a column, which is below anything an eye or a monitor resolves. The fall into the
     * binding was the whole claim that this object is bound, and it was invisible.
     */
    public static final int TONE = 0x8A7448;

    public static final int COVER = 0xFF41301D;

    public static final int COVER_HI = 0xFF634C31;

    public static final int COVER_LO = 0xFF1E150D;

    public static final int SPINE = 0xFF4C3823;

    public static final int STITCH = 0xFFD2BC8C;

    public static final int ROW_HOVER = 0x3A806040;

    public static final int KEY_FILL = 0x1E4A3A22;

    public static final int KEY_EDGE = 0x484A3A22;

    public static final int KEY_HOVER = 0x58806040;

    public static final int KEY_HOVER_EDGE = 0x99332211;

    private static final int SHADOW = 0x60000000;

    private static final int FOLD = 0x88221810;

    private static final int HILITE = 0x30FFFFFF;

    private static final int HILITE_WORN = 0x24FFFFFF;

    /** The three shading ramps, worked out once rather than raised to a power every frame. */
    private static final int[] GUTTER = new int[14];

    private static final int[] OUTER = new int[6];

    private static final int[] VIGNETTE = new int[5];

    static {
        for (int i = 0; i < GUTTER.length; i++) {
            GUTTER[i] = tint(TONE, (int) (0x60 * Math.pow(1.0 - i / 14.0, 1.6)));
        }
        // Index 0 is the darkest and is drawn hard against the page edge. An earlier draft had
        // this ramp the other way round, so the page block was darkest thirteen pixels inside the
        // leaf and faded to nothing exactly where the edge is - which is why it read as a smudge
        // rather than as the block of pages underneath.
        for (int i = 0; i < OUTER.length; i++) {
            OUTER[i] = tint(TONE, (int) (0x34 * Math.pow(1.0 - i / 6.0, 1.3)));
        }
        for (int i = 0; i < VIGNETTE.length; i++) {
            VIGNETTE[i] = tint(TONE, (int) (0x22 * Math.pow(1.0 - i / 5.0, 1.5)));
        }
    }

    private static int tint(int rgb, int alpha) {
        return ((alpha & 255) << 24) | (rgb & 0xFFFFFF);
    }

    /**
     * A settled value between nought and 255 for an integer.
     *
     * <p>
     * The page edge has to be irregular to look worn and identical every frame to look like paper
     * rather than static, so it is a pure function of where it is and never of chance.
     */
    public static int wear(int v) {
        v = v * 374761393 + 668265263;
        v ^= v >>> 13;
        v *= 1274126177;
        return (v >>> 16) & 255;
    }

    /**
     * As much of a string as will fit in the given width, with an ellipsis when it had to be cut.
     *
     * <p>
     * Every string this book draws goes through here. Nothing else stops a translated page title
     * from being drawn straight over the page number beside it, or a translated book title over
     * the folio - and the only reason the shipped English never does is that English happens to be
     * short, which is not a property of the code.
     */
    public static String fit(FontRenderer font, String text, int width) {
        if (width <= 0) return "";
        if (font.getStringWidth(text) <= width) return text;
        int dots = font.getStringWidth("...");
        if (width <= dots) return font.trimStringToWidth(text, width);
        return font.trimStringToWidth(text, width - dots) + "...";
    }

    /**
     * The top of the mark that closes a block of content, or -1 when there is no room for one.
     *
     * <p>
     * No room means no blank paper to explain, so no mark is wanted: a leaf whose text runs to the
     * bottom of the region does not look like a section that stopped early, because nothing has
     * stopped. What a continued leaf of that length has instead is the row of leaf marks in the
     * running head, which is drawn whatever the length.
     */
    public static int markTop(int contentBottom) {
        int top = contentBottom + MARK_GAP;
        return top + MARK_H > FOOT_RULE_Y - MARK_FOOT ? -1 : top;
    }

    /**
     * The whole book: boards, stitched spine, leaf, the shading of paper turning into its binding,
     * and a page block worn in clumps rather than evenly.
     */
    public static void frame(int left, int top) {
        int right = left + PANEL_W;
        int bottom = top + PANEL_H;

        Gui.drawRect(left + 4, top + 4, right + 4, bottom + 4, SHADOW);

        // Three plies, so the cover reads as a raised board with the leaf recessed into it.
        Gui.drawRect(left, top, right, bottom, COVER_LO);
        Gui.drawRect(left + 1, top + 1, right - 1, bottom - 1, COVER);
        Gui.drawRect(left + 1, top + 1, right - 1, top + 2, COVER_HI);
        Gui.drawRect(left + 1, top + 1, left + 2, bottom - 1, COVER_HI);
        Gui.drawRect(left + 2, bottom - 2, right - 1, bottom - 1, COVER_LO);
        Gui.drawRect(right - 2, top + 2, right - 1, bottom - 1, COVER_LO);

        int px0 = left + BORDER;
        int py0 = top + BORDER;
        int px1 = right - BORDER;
        int py1 = bottom - BORDER;
        Gui.drawRect(px0, py0, px1, py1, PARCH);

        int sx0 = px0;
        int sx1 = px0 + SPINE_W;
        Gui.drawRect(sx0, py0, sx1, py1, SPINE);
        Gui.drawRect(sx0, py0, sx0 + 1, py1, COVER_LO);

        // Two running threads, dashed and out of phase with each other, which is what a saddle
        // stitch looks like from outside the binding.
        for (int y = py0 + 1; y + 7 < py1; y += 12) {
            int far = Math.min(y + 13, py1 - 1);
            Gui.drawRect(sx0 + 3, y, sx0 + 4, y + 7, STITCH);
            Gui.drawRect(sx0 + 4, y, sx0 + 5, y + 7, COVER_LO);
            Gui.drawRect(sx0 + 7, y + 6, sx0 + 8, far, STITCH);
            Gui.drawRect(sx0 + 8, y + 6, sx0 + 9, far, COVER_LO);
        }
        Gui.drawRect(sx1, py0, sx1 + 1, py1, FOLD);

        for (int i = 0; i < GUTTER.length; i++) {
            Gui.drawRect(sx1 + 1 + i, py0, sx1 + 2 + i, py1, GUTTER[i]);
        }

        // The block of pages under this one, darkest against the edge and fading inwards.
        Gui.drawRect(px1 - 1, py0, px1, py1, PARCH_EDGE);
        Gui.drawRect(px0, py1 - 1, px1, py1, PARCH_EDGE);
        for (int i = 0; i < OUTER.length; i++) {
            Gui.drawRect(px1 - 2 - i, py0, px1 - 1 - i, py1, OUTER[i]);
        }
        for (int i = 0; i < 4; i++) {
            Gui.drawRect(px0, py1 - 2 - i, px1 - 1, py1 - 1 - i, OUTER[i]);
        }

        for (int i = 0; i < VIGNETTE.length; i++) {
            Gui.drawRect(sx1 + 1, py0 + i, px1, py0 + i + 1, VIGNETTE[i]);
        }

        // The edge thinned in clumps. Clumps rather than a bite on every row both because that is
        // how paper actually wears and because it costs a tenth as much.
        int spanY = py1 - py0 - 10;
        int spanX = px1 - sx1 - 12;
        for (int i = 0; i < 11; i++) {
            int y = py0 + 2 + wear(i * 3 + 11) % spanY;
            Gui.drawRect(
                px1 - 1 - (1 + wear(i * 3 + 12) % 3),
                y,
                px1 - 1,
                y + 3 + wear(i * 3 + 13) % 7,
                tint(TONE, 0x44));
            int x = sx1 + 3 + wear(i * 3 + 41) % spanX;
            Gui.drawRect(
                x,
                py1 - 1 - (1 + wear(i * 3 + 42) % 3),
                x + 3 + wear(i * 3 + 43) % 7,
                py1 - 1,
                tint(TONE, 0x38));
        }

        for (int i = 0; i < 14; i++) {
            int fx = px0 + 12 + wear(i * 3 + 1) % (PANEL_W - 2 * BORDER - 22);
            int fy = py0 + 3 + wear(i * 3 + 2) % (PANEL_H - 2 * BORDER - 6);
            Gui.drawRect(fx, fy, fx + 1, fy + 1, tint(TONE, 0x1E));
        }
    }

    /**
     * A cut line: a dark stroke with a light incision under it.
     *
     * <p>
     * The foot rule is worn through in a handful of places and the head rule is not, which is the
     * one ornament a mod about roads losing their edges has actually earned. The gaps are drawn in
     * clumps of three because a rule that lost every eighth pixel reads as dithering rather than
     * as wear - and because whole runs of kept pixels go out as one rectangle, so the worn rule
     * costs seven of them rather than the hundred and seventy-eight a pixel at a time would.
     */
    public static void rule(int x0, int x1, int y, boolean worn) {
        if (worn) {
            int run = -1;
            for (int x = x0; x <= x1; x++) {
                boolean ink = x < x1 && wear((x - x0) / 3 * 7 + 3) % 11 != 0;
                if (ink) {
                    if (run < 0) run = x;
                } else if (run >= 0) {
                    Gui.drawRect(run, y, x, y + 1, INK_FAINT);
                    run = -1;
                }
            }
            Gui.drawRect(x0, y + 1, x1, y + 2, HILITE_WORN);
        } else {
            Gui.drawRect(x0, y, x1, y + 1, INK_FAINT);
            Gui.drawRect(x0, y + 1, x1, y + 2, HILITE);
        }
    }

    /** A five row lozenge, centred on cx and occupying rows top to top + 4. */
    public static void diamond(int cx, int top, int color) {
        for (int i = 0; i < 5; i++) {
            int half = i < 3 ? i : 4 - i;
            Gui.drawRect(cx - half, top + i, cx + half + 1, top + i + 1, color);
        }
    }

    /**
     * A four by five arrow head occupying x to x + 3 and rows y to y + 4, whichever way it points.
     * Two pixels thick, or it vanishes against the parchment.
     */
    public static void chevron(int x, int y, boolean pointLeft, int color) {
        for (int i = 0; i < 5; i++) {
            int dx = i < 3 ? 2 - i : i - 2;
            if (!pointLeft) dx = 2 - dx;
            Gui.drawRect(x + dx, y + i, x + dx + 2, y + i + 1, color);
        }
    }

    /**
     * The mark that closes a block of content: a tailpiece when the section ends here, and the
     * same rule with an arrow head in it when it carries on to the next leaf.
     *
     * <p>
     * Without this the five line showcase page and a leaf that continues look identical, and both
     * look like the layout failed. The two share one slot, one height and one optical line, so
     * turning between them does not move anything.
     */
    public static void mark(int cx, int top, boolean continues, int color) {
        Gui.drawRect(cx - 22, top + 2, cx - 6, top + 3, color);
        Gui.drawRect(cx + 7, top + 2, cx + 23, top + 3, color);
        if (continues) chevron(cx - 2, top, false, color);
        else diamond(cx, top, color);
    }

    /** A five by three triangle, for the two marks that say the contents list has more to it. */
    public static void arrow(int cx, int y, boolean up, int color) {
        for (int i = 0; i < 3; i++) {
            int row = up ? y + i : y + 2 - i;
            Gui.drawRect(cx - i, row, cx + i + 1, row + 1, color);
        }
    }

    /**
     * A heading, with weight.
     *
     * <p>
     * The font has no bold, so this is the same trick vanilla's own bold formatting uses - the
     * string struck twice, one pixel apart.
     */
    public static void bold(FontRenderer font, String text, int x, int y, int color) {
        font.drawString(text, x, y, color);
        font.drawString(text, x + 1, y, color);
    }
}
