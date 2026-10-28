package com.trmtgtnh.client.gui;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import com.trmtgtnh.client.settings.Canvas;
import com.trmtgtnh.client.settings.ColorMath;
import com.trmtgtnh.client.settings.HoverText;
import com.trmtgtnh.client.settings.Slider;

/**
 * This edition's {@link Canvas}: how the shared settings widgets reach a 26.x screen (spec Part 3.4).
 *
 * <p>
 * A fill is {@code GuiGraphicsExtractor.fill}; a gradient a row of {@code fillGradient} columns a GUI pixel wide, each
 * running from its column's top color to its bottom one - 26.x's GUI is a list of elements the game draws later, and
 * the one gradient it offers is vertical, with no public way to lay a quad of four colors (spec 3.4, read first:
 * {@code GuiGraphicsExtractor} has {@code fillGradient} and no per-vertex quad), so the picker's square is as many
 * columns as it is wide, each exact at its own pixel; text the game's own font with its shadow, scaled through the
 * frame's pose, a JOML {@code Matrix3x2fStack}; a clip {@code enableScissor}, whose own stack intersects as it grows,
 * kept in step with a stack here; the icons and the wordmark from the atlases this jar ships; and a picture an item
 * drawn as an inventory draws one.
 *
 * <p>
 * <strong>Painted in order, nearest last</strong>, as the 1.7.10 edition's screen is. 26.x draws a stratum's text after
 * its boxes and pictures, so a box drawn after text in the same stratum would lie under it - the fault 1.20.1 and 1.21.1
 * found through their depth (0.9.228: "Changed only", "Sod" and "OFF" showing through an open dropdown), which 26.x
 * would give by the order it draws in. So once text or a picture is drawn, the next box or picture begins a stratum of
 * its own ({@code nextStratum}), which the game draws over everything before it. The frame's
 * {@code GuiGraphicsExtractor} is handed in at the start of every frame ({@link #frame}).
 */
final class SettingsCanvas implements Canvas {

    /** The tamper, the "All surfaces" sub-tab's picture. */
    static final String TAMPER = "tamper";

    private final Minecraft mc;

    private final Font font;

    private final int screenWidth;

    private final int screenHeight;

    private GuiGraphicsExtractor graphics;

    private final Deque<int[]> clips = new ArrayDeque<int[]>();

    /** The hover to draw at the end of the frame, placed by {@link HoverText}. */
    private List<String> hoverLines;

    private int hoverX;

    private int hoverY;

    SettingsCanvas(Minecraft mc, int screenWidth, int screenHeight) {
        this.mc = mc;
        this.font = mc.font;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
    }

    /** The frame's drawing, handed in as each frame is drawn. */
    SettingsCanvas frame(GuiGraphicsExtractor frame) {
        this.graphics = frame;
        clips.clear();
        // Whatever the world and the frame before drew, the screen paints over it.
        textDrawn = true;
        return this;
    }

    /**
     * Whether text or a picture has been drawn since the last stratum began. The game draws a stratum's text after
     * its boxes, so a box filled later in the same stratum lies under text drawn before it - an open dropdown over the
     * rows, a hover over a field (0.9.228, seen in every 1.20.1 and 1.21.1 dev run's settings-dropdown photograph). The
     * 1.7.10 edition's GUI draws nearest last; so here a stratum is begun before the next box or picture
     * ({@link #over}).
     */
    private boolean textDrawn;

    /** Text was drawn, by this canvas or by a field the screen draws itself: the next box begins a stratum first. */
    void drewText() {
        textDrawn = true;
    }

    /** Before a box or a picture: a stratum of its own, so it is painted over the text before it. */
    private void over() {
        if (!textDrawn || graphics == null) return;
        textDrawn = false;
        graphics.nextStratum();
    }

    GuiGraphicsExtractor graphics() {
        return graphics;
    }

    @Override
    public void fill(int x0, int y0, int x1, int y1, int argb) {
        if (x1 <= x0 || y1 <= y0) return;
        over();
        graphics.fill(x0, y0, x1, y1, argb);
    }

    @Override
    public void panel(int x0, int y0, int x1, int y1) {
        over();
        WornPathArt.dirt(graphics, x0, y0, x1, y1, true);
    }

    /** The screen's own dark dirt, behind the panel. */
    void background(int x0, int y0, int x1, int y1) {
        over();
        WornPathArt.dirt(graphics, x0, y0, x1, y1, false);
    }

    @Override
    public void gradient(int x0, int y0, int x1, int y1, int topLeft, int topRight, int bottomLeft, int bottomRight) {
        over();
        if (x1 <= x0 || y1 <= y0) return;
        if (topLeft == topRight && bottomLeft == bottomRight) {
            graphics.fillGradient(x0, y0, x1, y1, topLeft, bottomLeft);
            return;
        }
        // A column a pixel wide, each from its own top color to its own bottom one, mixed across as the corners give.
        int width = x1 - x0;
        for (int column = 0; column < width; column++) {
            float across = width == 1 ? 0F : column / (float) (width - 1);
            graphics.fillGradient(
                x0 + column,
                y0,
                x0 + column + 1,
                y1,
                mixArgb(topLeft, topRight, across),
                mixArgb(bottomLeft, bottomRight, across));
        }
    }

    /** Two ARGB colors mixed with their alpha: the shared {@code ColorMath.mix} for the color, the same for the alpha. */
    static int mixArgb(int from, int to, float amount) {
        float clamped = Math.max(0F, Math.min(1F, amount));
        int alphaFrom = from >>> 24;
        int alphaTo = to >>> 24;
        int alpha = Math.round(alphaFrom + (alphaTo - alphaFrom) * clamped);
        return alpha << 24 | ColorMath.mix(from & 0xFFFFFF, to & 0xFFFFFF, clamped) & 0xFFFFFF;
    }

    @Override
    public void text(String text, int x, int y, int argb, boolean shadow) {
        graphics.text(font, text, x, y, argb, shadow);
        drewText();
    }

    @Override
    public void text(String text, int x, int y, int argb, float scale) {
        graphics.pose()
            .pushMatrix();
        graphics.pose()
            .translate(x, y);
        graphics.pose()
            .scale(scale, scale);
        graphics.text(font, text, 0, 0, argb, true);
        graphics.pose()
            .popMatrix();
        drewText();
    }

    @Override
    public int width(String text) {
        return font.width(text);
    }

    @Override
    public int lineHeight() {
        return font.lineHeight;
    }

    @Override
    public String trim(String text, int width) {
        if (text == null) return "";
        if (font.width(text) <= width) return text;
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width("..."))) + "...";
    }

    @Override
    public void pushClip(int x0, int y0, int x1, int y1) {
        int[] clip = { x0, y0, x1, y1 };
        int[] outer = clips.peek();
        if (outer != null) {
            clip = new int[] { Math.max(x0, outer[0]), Math.max(y0, outer[1]), Math.min(x1, outer[2]),
                Math.min(y1, outer[3]) };
        }
        clips.push(clip);
        // The extractor keeps its own stack, which intersects as this one does and takes GUI pixels; each push here is
        // one push there, so a pop takes off exactly what its push put on.
        graphics.enableScissor(clip[0], clip[1], Math.max(clip[0], clip[2]), Math.max(clip[1], clip[3]));
    }

    @Override
    public void popClip() {
        if (clips.isEmpty()) return;
        clips.pop();
        graphics.disableScissor();
    }

    @Override
    public void icon(String name, int x, int y) {
        over();
        WornPathArt.settingsIcon(graphics, Canvas.iconCell(name), x, y);
    }

    /**
     * A picture: the slider's worn bar and its handle, the tamper, a block or an item drawn as the game draws one in
     * an inventory, at 16 by 16 or scaled. Anything else draws nothing.
     */
    @Override
    public void picture(Object ref, int x, int y) {
        over();
        if (ref instanceof Canvas.Scaled) {
            Canvas.Scaled scaled = (Canvas.Scaled) ref;
            float by = scaled.size / 16F;
            // Through the frame's own pose, which an item is drawn under as well as everything else.
            graphics.pose()
                .pushMatrix();
            graphics.pose()
                .translate(x, y);
            graphics.pose()
                .scale(by, by);
            picture(scaled.ref, 0, 0);
            graphics.pose()
                .popMatrix();
            return;
        }
        if (ref instanceof Slider.Piece) {
            Slider.Piece piece = (Slider.Piece) ref;
            if (Slider.Piece.BAR.equals(piece.name)) {
                WornPathArt.wornBar(graphics, x, y, piece.width, piece.height, piece.fraction, piece.stages);
            } else {
                // The game's own slider handle, as a button's face: light above, dark below.
                fill(x, y, x + piece.width, y + piece.height, 0xFF000000);
                fill(x + 1, y + 1, x + piece.width - 1, y + piece.height - 1, 0xFFA0A0A0);
                fill(x + 1, y + piece.height - 2, x + piece.width - 1, y + piece.height - 1, 0xFF555555);
            }
            return;
        }
        if (ref instanceof com.trmtgtnh.surface.SurfaceFamily) {
            // A surface, as its sub-tab draws it: its road block (spec M1.2), for the Advanced fold's rows (M16.2).
            Block block = com.trmtgtnh.command.CommandTrmt.roadBlock((com.trmtgtnh.surface.SurfaceFamily) ref);
            if (block != null) picture(block, x, y);
            return;
        }
        if (ref instanceof ChooserPictures.Picture) {
            // A chooser value's picture (spec M23.2): a wear look, a cost curve, a preset's depth or steps.
            ((ChooserPictures.Picture) ref).draw(graphics, x, y);
            afterPicture();
            return;
        }
        if (TAMPER.equals(ref)) {
            WornPathArt.tamper(graphics, x, y);
            return;
        }
        if (SettingsPictures.draws(ref)) {
            // A list's picture: a block, its texture, an item, a fluid, a sprite, a mob (spec M3), within the frame's
            // budget of first tries (Part 6).
            SettingsPictures.draw(graphics, ref, x, y);
            afterPicture();
            return;
        }
        if (ref instanceof Block) {
            // A block, by its item where it has one, else its face (survey 2.3).
            Object shown = SettingsPictures.blockPicture((Block) ref);
            if (shown != null) picture(shown, x, y);
            return;
        }
        ItemStack stack = null;
        if (ref instanceof ItemStack) stack = (ItemStack) ref;
        else if (ref instanceof Item) stack = new ItemStack((Item) ref);
        if (stack == null || stack.isEmpty() || stack.getItem() == Items.AIR) return;
        WearIcons.drawIcon(graphics, String.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem())), stack, x, y);
        afterPicture();
    }

    /**
     * After an item or a mob: whatever is drawn next begins a stratum of its own, so it is drawn over the picture, as
     * the 1.7.10 edition's screen paints - 1.21.1 cleared the depth the picture wrote. 26.x keeps no shader color or
     * depth test for a picture to leave behind.
     */
    private void afterPicture() {
        textDrawn = true;
    }

    @Override
    public void hover(List<String> lines, int x, int y) {
        hoverLines = new ArrayList<String>(lines);
        hoverX = x;
        hoverY = y;
    }

    /** The hover asked for this frame, drawn last, over everything. */
    void drawHover() {
        if (hoverLines == null || hoverLines.isEmpty()) return;
        HoverText.Box box = HoverText.box(this, hoverLines, hoverX, hoverY, screenWidth, screenHeight);
        // Its own stratum, over every row and picture the frame drew, whatever came last.
        textDrawn = true;
        HoverText.draw(this, box);
        hoverLines = null;
    }

    @Override
    public String words(String key, Object... args) {
        return I18n.get(key, args);
    }
}
