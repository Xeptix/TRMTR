package com.trmtgtnh.client.gui;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.erosion.WearMath;
import com.trmtgtnh.surface.SurfaceFamily;
import com.trmtgtnh.surface.SurfaceRegistry;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * A read-only page that works the wear config out into plain numbers: how many phases each family
 * wears through, how much traffic reaches each quarter of that, and how long it takes to grow
 * back. Where a server has told this client its own numbers, the difference is shown beside each
 * value in green or red, so tuning a setting shows at a glance how it lands against the server.
 *
 * <p>
 * All arithmetic lives in {@link WearMath}; this only lays it out. Two lines per family - crossings
 * on top, heal days below - is what buys room for eight figures and their diffs without the table
 * running off the side of the screen.
 */
@SideOnly(Side.CLIENT)
public class GuiWearTable extends GuiScreen {

    private static final int ID_BACK = 1;
    private static final int ID_EDIT = 2;

    private static final int ROW_H = 26;
    private static final int PANEL_MAX = 420;

    /** Right edges of the four quarter columns, measured from the content left. */
    private static final int[] QX = { 206, 268, 330, 392 };

    private final GuiScreen parent;
    private final Map<SurfaceFamily, List<ItemStack>> icons = new EnumMap<SurfaceFamily, List<ItemStack>>(
        SurfaceFamily.class);
    private SurfaceFamily[] rows;

    private int cursor; // which block in each family's cycle is showing
    private int cycleTicks;
    private int scroll; // pixels the body is scrolled down

    public GuiWearTable(GuiScreen parent) {
        this.parent = parent;
    }

    private int panelWidth() {
        return Math.min(width - 8, PANEL_MAX);
    }

    private int panelLeft() {
        return width / 2 - panelWidth() / 2;
    }

    private int panelTop() {
        return 18;
    }

    private int panelBottom() {
        return height - 40;
    }

    private int bodyTop() {
        return panelTop() + 45;
    }

    private int bodyBottom() {
        return panelBottom() - 4;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        buttonList
            .add(new GuiButton(ID_BACK, panelLeft(), height - 28, 70, 20, StatCollector.translateToLocal("gui.back")));
        buttonList.add(
            new GuiButton(
                ID_EDIT,
                panelLeft() + panelWidth() - 90,
                height - 28,
                90,
                20,
                StatCollector.translateToLocal("trmtgtnh.weartable.edit")));

        // The staged families, in the order the enum declares them, which reads grass-first.
        List<SurfaceFamily> staged = new ArrayList<SurfaceFamily>();
        for (SurfaceFamily family : SurfaceFamily.values()) {
            if (family.staged) staged.add(family);
        }
        rows = staged.toArray(new SurfaceFamily[staged.size()]);

        // One list of example blocks per family, resolved once. A family with nothing detected
        // falls back to its vanilla stand-in so the row is never iconless.
        icons.clear();
        for (SurfaceFamily family : rows) {
            icons.put(family, new ArrayList<ItemStack>());
        }
        for (SurfaceRegistry.SurfaceState state : SurfaceRegistry.texturableStates()) {
            List<ItemStack> list = icons.get(state.family);
            if (list == null) continue;
            Item item = Item.getItemFromBlock(state.block);
            if (item != null) list.add(new ItemStack(item, 1, 0));
        }
        for (SurfaceFamily family : rows) {
            List<ItemStack> list = icons.get(family);
            if (list.isEmpty()) {
                Block fallback = vanillaFor(family);
                Item item = fallback == null ? null : Item.getItemFromBlock(fallback);
                if (item != null) list.add(new ItemStack(item, 1, 0));
            }
        }
    }

    @Override
    public void updateScreen() {
        if (++cycleTicks >= WearIcons.CYCLE_TICKS) {
            cycleTicks = 0;
            cursor++;
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;
        int content = rows.length * ROW_H;
        int viewport = bodyBottom() - bodyTop();
        int max = Math.max(0, content - viewport);
        scroll -= wheel > 0 ? ROW_H : -ROW_H;
        if (scroll < 0) scroll = 0;
        if (scroll > max) scroll = max;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == ID_BACK) mc.displayGuiScreen(parent);
        if (button.id == ID_EDIT) mc.displayGuiScreen(new GuiWearEditor(this));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partial) {
        drawDefaultBackground();
        int left = panelLeft();
        int right = left + panelWidth();
        int top = panelTop();
        int bottom = panelBottom();
        int cx = width / 2;
        int cl = left + 6;

        // Panel chrome, the same vocabulary the tamper screen uses so the two read as one mod.
        drawRect(left - 1, top - 1, right + 1, bottom + 1, 0xFF6A6A7A);
        drawRect(left, top, right, bottom, 0xE6141420);
        drawGradientRect(left, top, right, top + 18, 0xFF32324A, 0xFF20202C);
        drawRect(left, top + 18, right, top + 19, 0xFF6A6A7A);
        drawCenteredString(
            fontRendererObj,
            StatCollector.translateToLocal("trmtgtnh.weartable.title"),
            cx,
            top + 5,
            0xFFFFFF);

        WearMath.Pricing server = Trmt.proxy.serverPricing();
        String where = server == null ? StatCollector.translateToLocal("trmtgtnh.weartable.local")
            : StatCollector.translateToLocal("trmtgtnh.weartable.server");
        drawString(fontRendererObj, where, right - 6 - fontRendererObj.getStringWidth(where), top + 5, 0xB8B8C4);

        // Fixed header band. The two legend lines are trimmed to the space actually in front of
        // the first quarter column rather than run at their natural width - "Crossings - player
        // passes to wear" is a hundred and eighty pixels and the gap is a hundred and sixty, so it
        // used to run straight through the 1/4 heading.
        int legendRoom = QX[0] - fontRendererObj.getStringWidth("1/4") - 26;
        drawString(
            fontRendererObj,
            fit(StatCollector.translateToLocal("trmtgtnh.weartable.head.cross"), legendRoom),
            cl + 20,
            top + 24,
            0xC9C9D6);
        drawString(
            fontRendererObj,
            fit(StatCollector.translateToLocal("trmtgtnh.weartable.head.heal"), legendRoom),
            cl + 20,
            top + 34,
            0xC9C9D6);
        String[] quarters = { "1/4", "1/2", "3/4", "Full" };
        for (int c = 0; c < 4; c++) {
            int w = fontRendererObj.getStringWidth(quarters[c]);
            drawString(fontRendererObj, quarters[c], cl + QX[c] - w, top + 30, 0x8C8CA0);
        }
        drawRect(cl - 2, top + 42, cl + QX[3] + 2, top + 43, 0x30FFFFFF);

        // Scrolled body, clipped to its viewport so rows never spill into the chrome.
        int vTop = bodyTop();
        int vBottom = bodyBottom();
        // The real display, not this screen's already-divided size. ScaledResolution derives the
        // factor from whatever it is handed, so giving it the scaled width made it solve for a
        // screen a quarter the size and answer 1 where the truth was 4 - and every number below is
        // multiplied by that. The scissor box then landed at a quarter of its intended position and
        // a quarter of its intended size, which is why most of the table was missing and the values
        // that did show were cut off mid-digit.
        ScaledResolution res = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int sf = res.getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(left * sf, (height - vBottom) * sf, panelWidth() * sf, (vBottom - vTop) * sf);

        for (int i = 0; i < rows.length; i++) {
            int rowY = vTop - scroll + i * ROW_H;
            if (rowY + ROW_H < vTop || rowY > vBottom) continue; // off-screen, skip
            drawRow(rows[i], cl, rowY, server, (i & 1) == 1);
        }

        GL11.glDisable(GL11.GL_SCISSOR_TEST);

        // One line, in the strip between the panel border and the buttons, which is empty. Full
        // panel width rather than the cramped space the two legends above share between Back and
        // Edit - this one has a picture to explain and needs the room.
        drawString(
            fontRendererObj,
            fit(StatCollector.translateToLocal("trmtgtnh.weartable.legend.shape"), panelWidth() - 12),
            left + 6,
            height - 38,
            0x8C8CA0);

        // Footer legend below the panel, between the two buttons and trimmed to the gap between
        // them, because it sits on their row and a sentence that overruns it lands on top of Edit.
        int legendLeft = left + 74;
        int legendWidth = right - 94 - legendLeft;
        drawString(
            fontRendererObj,
            fit(StatCollector.translateToLocal("trmtgtnh.weartable.legend"), legendWidth),
            legendLeft,
            height - 22,
            0x8C8CA0);
        if (server != null) {
            drawString(
                fontRendererObj,
                fit(StatCollector.translateToLocal("trmtgtnh.weartable.legend.diff"), legendWidth),
                legendLeft,
                height - 12,
                0x8C8CA0);
        }

        super.drawScreen(mouseX, mouseY, partial);
    }

    /**
     * A string cut to fit, with an ellipsis when it had to be cut.
     *
     * <p>
     * Every label on this screen sits beside something else, so a long one does not simply look
     * untidy - it lands on a number. Trimming is the honest failure here: a legend that stops
     * short still reads, where a legend drawn over the 1/4 column makes both unreadable.
     */
    private String fit(String text, int room) {
        if (room <= 0 || fontRendererObj.getStringWidth(text) <= room) return text;
        return fontRendererObj.trimStringToWidth(text, Math.max(0, room - 8)) + "...";
    }

    /**
     * Where a run's cost actually sits, drawn from the four figures beside it and nothing else.
     *
     * <p>
     * Four blocks whose widths are the crossings at a quarter, a half, three quarters and the whole
     * of the run, so the picture is of those four columns and can make no claim they do not. A flat
     * family draws four equal blocks; one that opens cheap and ends dear draws a narrow first block
     * and a wide last one. Stone, cobble, netherrack and end stone are all flat, so the reference is
     * always somewhere on screen.
     *
     * <p>
     * Deliberately not a plot of the curve itself. At the height a row has to spare, the three
     * middle curves sit about one pixel apart at both ends of their ramp - a picture that cannot be
     * read is worse than none, because it looks like information. This separates by two to four
     * pixels at every boundary and stays that way from a run of eighty down to sixteen.
     */
    private void drawCostBar(WearMath.Row live, int cl, int y) {
        final int x0 = cl + 58;
        final int w = 56;
        double total = live.crossings[3];
        if (!(total > 0d)) return;

        drawRect(x0, y, x0 + w, y + 7, 0x18FFFFFF);
        final int[] shade = { 0xFF4A4A5A, 0xFF6A6A7A, 0xFF8C8CA0, 0xFFC9C9D6 };
        int prev = 0;
        for (int k = 0; k < 4; k++) {
            int edge = k == 3 ? w : (int) Math.round(w * live.crossings[k] / total);
            if (edge < prev) edge = prev;
            if (edge > w) edge = w;
            if (edge > prev) drawRect(x0 + prev, y, x0 + edge, y + 7, shade[k]);
            prev = edge;
        }
    }

    private void drawRow(SurfaceFamily family, int cl, int rowY, WearMath.Pricing server, boolean shade) {
        int y1 = rowY + 4;
        int y2 = rowY + 15;

        if (shade) drawRect(cl - 4, rowY, cl + QX[3] + 2, rowY + ROW_H - 1, 0x14FFFFFF);
        drawRect(cl - 4, rowY + ROW_H - 1, cl + QX[3] + 2, rowY + ROW_H, 0x18FFFFFF);

        // Cycling block icon.
        List<ItemStack> group = icons.get(family);
        if (group != null && !group.isEmpty()) {
            ItemStack stack = group.get(cursor % group.size());
            BlockIconArrayEntry.drawIcon(family.key(), stack, cl, rowY + 4);
        }

        WearMath.Row live = WearMath.rowFor(family);
        int nameColor = live.wears ? 0xFFFFFF : 0xFF808080;
        drawString(fontRendererObj, fit(family.key(), 36), cl + 20, y1, nameColor);

        if (!live.wears) {
            drawString(
                fontRendererObj,
                StatCollector.translateToLocal("trmtgtnh.weartable.inert"),
                cl + 20,
                y2,
                0xFF808080);
            return;
        }

        String meta = live.phases + (live.phases < live.fullPhases ? "/" + live.fullPhases : "")
            + " ph"
            + "  "
            + live.sinkPixels
            + "px"
            + "  "
            + Math.round(live.cap * 100f)
            + "%";
        drawString(fontRendererObj, meta, cl + 20, y2, 0xB8B8C4);

        // In the gap between the family's name and the labels, which is empty on every row: the
        // longest key runs out by cl+54 and the labels start at cl+118.
        drawCostBar(live, cl, rowY + 5);

        drawString(fontRendererObj, "cross", cl + 118, y1, 0x8C8CA0);
        drawString(fontRendererObj, "days", cl + 118, y2, 0x8C8CA0);

        WearMath.Row base = server == null ? null : WearMath.rowFrom(family, server);
        for (int c = 0; c < 4; c++) {
            drawCell(
                compact(live.crossings[c]),
                base == null ? Double.NaN : live.crossings[c] - base.crossings[c],
                cl + QX[c],
                y1);
            drawCell(
                compact(live.healDays[c]),
                base == null ? Double.NaN : live.healDays[c] - base.healDays[c],
                cl + QX[c],
                y2);
        }
    }

    /** One value, right-aligned to {@code rightX}, with its server delta just left of it when there is one. */
    private void drawCell(String value, double delta, int rightX, int y) {
        int vw = fontRendererObj.getStringWidth(value);
        if (!Double.isNaN(delta) && Math.abs(delta) >= 0.5d) {
            String tag = (delta > 0 ? "+" : "-") + compact(Math.abs(delta));
            int color = delta > 0 ? 0xFF6AD46A : 0xFFE0605A;
            int tw = fontRendererObj.getStringWidth(tag);
            drawString(fontRendererObj, tag, rightX - vw - 3 - tw, y, color);
        }
        drawString(fontRendererObj, value, rightX - vw, y, 0xFFFFFF);
    }

    /** Four glyphs at most: 42, 380, 1.2k, 14k, 2.3M. */
    static String compact(double v) {
        double a = Math.abs(v);
        if (a < 10d) return trim(v);
        if (a < 1000d) return String.valueOf(Math.round(v));
        if (a < 1_000_000d) return oneDp(v / 1000d) + "k";
        return oneDp(v / 1_000_000d) + "M";
    }

    private static String trim(double v) {
        String s = String.format(java.util.Locale.ROOT, "%.1f", v);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    private static String oneDp(double v) {
        if (v >= 100d) return String.valueOf(Math.round(v));
        String s = String.format(java.util.Locale.ROOT, "%.1f", v);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    private static Block vanillaFor(SurfaceFamily family) {
        switch (family) {
            case GRASS:
                return Blocks.grass;
            case DIRT:
                return Blocks.dirt;
            case SAND:
                return Blocks.sand;
            case GRAVEL:
                return Blocks.gravel;
            case STONE:
                return Blocks.stone;
            case COBBLE:
                return Blocks.cobblestone;
            case NETHER:
                return Blocks.netherrack;
            case END:
                return Blocks.end_stone;
            case SNOW:
                return Blocks.snow;
            case ICE:
                return Blocks.ice;
            default:
                return Blocks.dirt;
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
