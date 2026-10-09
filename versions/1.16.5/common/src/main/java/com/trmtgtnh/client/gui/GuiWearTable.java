package com.trmtgtnh.client.gui;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.minecraft.world.level.block.Block;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.resources.language.I18n;

import org.lwjgl.opengl.GL11;

import com.trmtgtnh.Client;
import com.trmtgtnh.Trmt;
import com.trmtgtnh.erosion.WearMath;
import com.trmtgtnh.surface.SurfaceFamily;
import com.trmtgtnh.surface.SurfaceRegistry;

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
public class GuiWearTable extends Screen {

    private static final int ID_BACK = 1;
    private static final int ID_EDIT = 2;

    private static final int ROW_H = 26;
    private static final int PANEL_MAX = 420;

    /** Right edges of the four quarter columns, measured from the content left. */
    private static final int[] QX = { 206, 268, 330, 392 };

    private final Screen parent;
    private final Map<SurfaceFamily, List<ItemStack>> icons = new EnumMap<SurfaceFamily, List<ItemStack>>(
        SurfaceFamily.class);
    /**
     * The pose stack for the frame being drawn, held for the length of that frame.
     *
     * <p>
     * Every draw call takes one at this version and none did in either older edition. The rows, the
     * cells and the cost bar are drawn by private helpers reached from {@link #render} and from
     * nothing else, so the frame's pose is held rather than threaded through all of them - which
     * keeps the layout they carry readable beside the other edition's. {@code GuiGolem} threads the
     * parameter and is the shape to copy where a screen has two or three helpers rather than ten.
     */
    private com.mojang.blaze3d.vertex.PoseStack pose;

    private SurfaceFamily[] rows;

    private int cursor; // which block in each family's cycle is showing
    private int cycleTicks;
    private int scroll; // pixels the body is scrolled down

    public GuiWearTable(Screen parent) {
        // A screen carries its own name at this version; the heading's key is handed over so the
        // two cannot disagree.
        super(new net.minecraft.network.chat.TranslatableComponent("trmtgtnh.weartable.title"));
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
    public void init() {
        addButton(new Tagged(this, ID_BACK, panelLeft(), height - 28, 70, 20, com.trmtgtnh.util.Translate.get("gui.back")));
        addButton(
            new Tagged(this, ID_EDIT,
                panelLeft() + panelWidth() - 90,
                height - 28,
                90,
                20,
                com.trmtgtnh.util.Translate.get("trmtgtnh.weartable.edit")));

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
            Item item = heldForm(state.block);
            if (item != null) list.add(new ItemStack(item));
        }
        for (SurfaceFamily family : rows) {
            List<ItemStack> list = icons.get(family);
            if (list.isEmpty()) {
                Block fallback = vanillaFor(family);
                Item item = heldForm(fallback);
                if (item != null) list.add(new ItemStack(item));
            }
        }
    }

    @Override
    public void tick() {
        if (++cycleTicks >= WearIcons.CYCLE_TICKS) {
            cycleTicks = 0;
            cursor++;
        }
    }

    /**
     * The wheel, which is handed over rather than read out of a queue.
     *
     * <p>
     * Both older editions override the whole of mouse handling and ask LWJGL what the wheel did
     * since last time. There is a method for exactly this here and the amount arrives with it;
     * positive is up, as it was. The arithmetic below is unchanged.
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double wheel) {
        if (wheel == 0) return super.mouseScrolled(mouseX, mouseY, wheel);
        int content = rows.length * ROW_H;
        int viewport = bodyBottom() - bodyTop();
        int max = Math.max(0, content - viewport);
        scroll -= wheel > 0 ? ROW_H : -ROW_H;
        if (scroll < 0) scroll = 0;
        if (scroll > max) scroll = max;
        return true;
    }

    void actionPerformed(Button button, int id) {
        if (id == ID_BACK) minecraft.setScreen(parent);
        if (id == ID_EDIT) minecraft.setScreen(new GuiWearEditor(this));
    }

    @Override
    public void render(com.mojang.blaze3d.vertex.PoseStack pose, int mouseX, int mouseY, float partial) {
        // Held for the length of this frame; see the field.
        this.pose = pose;
        renderBackground(pose);
        int left = panelLeft();
        int right = left + panelWidth();
        int top = panelTop();
        int bottom = panelBottom();
        int cx = width / 2;
        int cl = left + 6;

        // Panel chrome, the same vocabulary the tamper screen uses so the two read as one mod.
        fill(pose, left - 1, top - 1, right + 1, bottom + 1, 0xFF6A6A7A);
        fill(pose, left, top, right, bottom, 0xE6141420);
        fillGradient(pose, left, top, right, top + 18, 0xFF32324A, 0xFF20202C);
        fill(pose, left, top + 18, right, top + 19, 0xFF6A6A7A);
        drawCenteredString(pose, font, com.trmtgtnh.util.Translate.get("trmtgtnh.weartable.title"), cx, top + 5, 0xFFFFFF);

        WearMath.Pricing server = Client.serverPricing();
        String where = server == null ? com.trmtgtnh.util.Translate.get("trmtgtnh.weartable.local")
            : com.trmtgtnh.util.Translate.get("trmtgtnh.weartable.server");
        drawString(pose, font, where, right - 6 - font.width(where), top + 5, 0xB8B8C4);

        // Fixed header band. The two legend lines are trimmed to the space actually in front of
        // the first quarter column rather than run at their natural width - "Crossings - player
        // passes to wear" is a hundred and eighty pixels and the gap is a hundred and sixty, so it
        // used to run straight through the 1/4 heading.
        int legendRoom = QX[0] - font.width("1/4") - 26;
        drawString(pose, font,
            fit(com.trmtgtnh.util.Translate.get("trmtgtnh.weartable.head.cross"), legendRoom),
            cl + 20,
            top + 24,
            0xC9C9D6);
        drawString(pose, font,
            fit(com.trmtgtnh.util.Translate.get("trmtgtnh.weartable.head.heal"), legendRoom),
            cl + 20,
            top + 34,
            0xC9C9D6);
        String[] quarters = { "1/4", "1/2", "3/4", "Full" };
        for (int c = 0; c < 4; c++) {
            int w = font.width(quarters[c]);
            drawString(pose, font, quarters[c], cl + QX[c] - w, top + 30, 0x8C8CA0);
        }
        fill(pose, cl - 2, top + 42, cl + QX[3] + 2, top + 43, 0x30FFFFFF);

        // Scrolled body, clipped to its viewport so rows never spill into the chrome.
        int vTop = bodyTop();
        int vBottom = bodyBottom();
        // In real pixels, not in this screen's already-divided ones: a scissor box is given to the
        // driver and the driver has never heard of a GUI scale, so every number below is multiplied
        // by it. The other edition derives that factor from a ScaledResolution and has a paragraph
        // here about having handed it the scaled width once - which made it solve for a screen a
        // quarter the size and answer 1 where the truth was 4, so the box landed at a quarter of its
        // intended position and most of the table was missing. The window answers the number
        // directly here, so there is nothing to hand it wrongly.
        int sf = (int) Math.max(1.0D, minecraft.getWindow()
            .getGuiScale());
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
        drawString(pose, font,
            fit(com.trmtgtnh.util.Translate.get("trmtgtnh.weartable.legend.shape"), panelWidth() - 12),
            left + 6,
            height - 38,
            0x8C8CA0);

        // Footer legend below the panel, between the two buttons and trimmed to the gap between
        // them, because it sits on their row and a sentence that overruns it lands on top of Edit.
        int legendLeft = left + 74;
        int legendWidth = right - 94 - legendLeft;
        drawString(pose, font,
            fit(com.trmtgtnh.util.Translate.get("trmtgtnh.weartable.legend"), legendWidth),
            legendLeft,
            height - 22,
            0x8C8CA0);
        if (server != null) {
            drawString(pose, font,
                fit(com.trmtgtnh.util.Translate.get("trmtgtnh.weartable.legend.diff"), legendWidth),
                legendLeft,
                height - 12,
                0x8C8CA0);
        }

        super.render(pose, mouseX, mouseY, partial);
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
        if (room <= 0 || font.width(text) <= room) return text;
        return font.plainSubstrByWidth(text, Math.max(0, room - 8)) + "...";
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

        fill(pose, x0, y, x0 + w, y + 7, 0x18FFFFFF);
        final int[] shade = { 0xFF4A4A5A, 0xFF6A6A7A, 0xFF8C8CA0, 0xFFC9C9D6 };
        int prev = 0;
        for (int k = 0; k < 4; k++) {
            int edge = k == 3 ? w : (int) Math.round(w * live.crossings[k] / total);
            if (edge < prev) edge = prev;
            if (edge > w) edge = w;
            if (edge > prev) fill(pose, x0 + prev, y, x0 + edge, y + 7, shade[k]);
            prev = edge;
        }
    }

    private void drawRow(SurfaceFamily family, int cl, int rowY, WearMath.Pricing server, boolean shade) {
        int y1 = rowY + 4;
        int y2 = rowY + 15;

        if (shade) fill(pose, cl - 4, rowY, cl + QX[3] + 2, rowY + ROW_H - 1, 0x14FFFFFF);
        fill(pose, cl - 4, rowY + ROW_H - 1, cl + QX[3] + 2, rowY + ROW_H, 0x18FFFFFF);

        // Cycling block icon.
        List<ItemStack> group = icons.get(family);
        if (group != null && !group.isEmpty()) {
            ItemStack stack = group.get(cursor % group.size());
            WearIcons.drawIcon(family.key(), stack, cl, rowY + 4);
        }

        WearMath.Row live = WearMath.rowFor(family);
        int nameColor = live.wears ? 0xFFFFFF : 0xFF808080;
        drawString(pose, font, fit(family.key(), 36), cl + 20, y1, nameColor);

        if (!live.wears) {
            drawString(pose, font, com.trmtgtnh.util.Translate.get("trmtgtnh.weartable.inert"), cl + 20, y2, 0xFF808080);
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
        drawString(pose, font, meta, cl + 20, y2, 0xB8B8C4);

        // In the gap between the family's name and the labels, which is empty on every row: the
        // longest key runs out by cl+54 and the labels start at cl+118.
        drawCostBar(live, cl, rowY + 5);

        drawString(pose, font, "cross", cl + 118, y1, 0x8C8CA0);
        drawString(pose, font, "days", cl + 118, y2, 0x8C8CA0);

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
        int vw = font.width(value);
        if (!Double.isNaN(delta) && Math.abs(delta) >= 0.5d) {
            String tag = (delta > 0 ? "+" : "-") + compact(Math.abs(delta));
            int color = delta > 0 ? 0xFF6AD46A : 0xFFE0605A;
            int tw = font.width(tag);
            drawString(pose, font, tag, rightX - vw - 3 - tw, y, color);
        }
        drawString(pose, font, value, rightX - vw, y, 0xFFFFFF);
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
                return Blocks.GRASS_BLOCK;
            case DIRT:
                return Blocks.DIRT;
            case SAND:
                return Blocks.SAND;
            case GRAVEL:
                return Blocks.GRAVEL;
            case STONE:
                return Blocks.STONE;
            case COBBLE:
                return Blocks.COBBLESTONE;
            case NETHER:
                return Blocks.NETHERRACK;
            case END:
                return Blocks.END_STONE;
            case SNOW:
                return Blocks.SNOW_BLOCK;
            case ICE:
                return Blocks.ICE;
            default:
                return Blocks.DIRT;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * The item form of a block, or null where it has none.
     *
     * <p>
     * The other edition asks {@link Item#getItemFromBlock} and tests the answer for null. 1.12.2
     * answers {@code Items.AIR} for a block nobody can hold and never null, so that test stops
     * firing: a ghost block, or anything else registered without an item, would be offered to a
     * picture as an empty stack and drawn as a gap. Named here so that the six places which ask have
     * one answer between them.
     */
    private static Item heldForm(Block block) {
        if (block == null) return null;
        Item item = block.asItem();
        return item == null || item == net.minecraft.world.item.Items.AIR ? null : item;
    }

    /**
     * A button that remembers which one it is. See {@code GuiTamper.Tagged}: vanilla's id is gone
     * and the dispatch below tests one, so the button keeps it and hands it back.
     */
    private static final class Tagged extends Button {

        Tagged(GuiWearTable screen, int id, int x, int y, int width, int height, String label) {
            super(
                x,
                y,
                width,
                height,
                new net.minecraft.network.chat.TextComponent(label),
                pressed -> screen.actionPerformed((Button) pressed, id));
        }
    }
}
