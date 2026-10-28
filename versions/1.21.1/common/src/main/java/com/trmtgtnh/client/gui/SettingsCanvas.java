package com.trmtgtnh.client.gui;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import com.trmtgtnh.client.settings.Canvas;
import com.trmtgtnh.client.settings.HoverText;
import com.trmtgtnh.client.settings.Slider;

/**
 * This edition's {@link Canvas}: how the shared settings widgets reach a 1.21.1 screen (spec Part 3.3).
 *
 * <p>
 * A fill is {@code GuiGraphics.fill}; a gradient a quad through the frame's own buffers with {@code RenderType.gui()}
 * and a color at each corner, as {@code GuiGraphics.fillGradient} does it inside (that one is vertical only, and its
 * vertex helper private); text the game's own font with its shadow, scaled through the frame's pose; a clip
 * {@code GuiGraphics.enableScissor}, whose own stack intersects as it grows, kept in step with a stack here; the icons
 * and the wordmark from the atlases this jar ships; and a picture an item drawn as an inventory draws one.
 *
 * <p>
 * <strong>Painted in order, nearest last</strong>, as the 1.7.10 edition's screen is: an item or a mob is drawn with
 * the depth test, as the game draws them, so the depth buffer is cleared after each picture and the depth test left
 * off - a hover or a dropdown drawn over a picture is then never hidden behind it. The frame's {@code GuiGraphics} is
 * handed in at the start of every frame ({@link #frame}).
 */
final class SettingsCanvas implements Canvas {

    /** The tamper, the "All surfaces" sub-tab's picture. */
    static final String TAMPER = "tamper";

    private final Minecraft mc;

    private final Font font;

    private final int screenWidth;

    private final int screenHeight;

    private GuiGraphics graphics;

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
    SettingsCanvas frame(GuiGraphics frame) {
        this.graphics = frame;
        clips.clear();
        // Whatever the world and the frame before drew, the screen paints over it.
        textDrawn = true;
        return this;
    }

    /**
     * Whether text has been drawn since the depth was last cleared. The game draws a GUI's text with the depth test and
     * its shadow a little nearer than the text, so a box filled later at the same height fails the test where text
     * stands and the words show through it - an open dropdown over the rows, a hover over a field (0.9.228, seen in
     * every 1.20.1 and 1.21.1 dev run's settings-dropdown photograph). The 1.7.10 edition's GUI draws with no depth at
     * all, nearest last; so here the depth text wrote is cleared before the next box or picture ({@link #over}).
     */
    private boolean textDrawn;

    /** Text was drawn, by this canvas or by a field the screen draws itself: the next box clears its depth first. */
    void drewText() {
        textDrawn = true;
    }

    /** Before a box or a picture: the depth any text before it wrote is cleared, so it is painted over that text. */
    private void over() {
        if (!textDrawn || graphics == null) return;
        textDrawn = false;
        graphics.flush();
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
    }

    GuiGraphics graphics() {
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
        Matrix4f matrix = graphics.pose()
            .last()
            .pose();
        // The order fillGradient lays its corners in, one color each; the GUI shader blends between them.
        VertexConsumer buffer = graphics.bufferSource()
            .getBuffer(RenderType.gui());
        vertex(buffer, matrix, x0, y0, topLeft);
        vertex(buffer, matrix, x0, y1, bottomLeft);
        vertex(buffer, matrix, x1, y1, bottomRight);
        vertex(buffer, matrix, x1, y0, topRight);
        graphics.flush();
    }

    private static void vertex(VertexConsumer buffer, Matrix4f matrix, int x, int y, int argb) {
        // 1.21's vertex is added and colored, and ends itself.
        buffer.addVertex(matrix, x, y, 0F)
            .setColor((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF);
    }

    @Override
    public void text(String text, int x, int y, int argb, boolean shadow) {
        graphics.drawString(font, text, x, y, argb, shadow);
        drewText();
    }

    @Override
    public void text(String text, int x, int y, int argb, float scale) {
        graphics.pose()
            .pushPose();
        graphics.pose()
            .translate(x, y, 0D);
        graphics.pose()
            .scale(scale, scale, 1F);
        graphics.drawString(font, text, 0, 0, argb, true);
        graphics.pose()
            .popPose();
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
        // GuiGraphics keeps its own stack, which intersects as this one does and takes GUI pixels; each push here is
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
            // Through the frame's own pose, which the item renderer draws under as well as everything else.
            graphics.pose()
                .pushPose();
            graphics.pose()
                .translate(x, y, 0D);
            graphics.pose()
                .scale(by, by, 1F);
            picture(scaled.ref, 0, 0);
            graphics.pose()
                .popPose();
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
            RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
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
            afterPicture(graphics);
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
        afterPicture(graphics);
    }

    /**
     * After an item or a mob: the depth it wrote cleared and the depth test left off, so whatever is drawn after it is
     * drawn over it, as the 1.7.10 edition's screen paints. What the frame has batched is drawn first, so the clear
     * reaches only what is already on the screen; 1.20.1 has no fixed lighting or alpha test to set back.
     */
    static void afterPicture(GuiGraphics graphics) {
        if (graphics != null) graphics.flush();
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.disableDepthTest();
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
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
        HoverText.draw(this, box);
        hoverLines = null;
    }

    @Override
    public String words(String key, Object... args) {
        return I18n.get(key, args);
    }
}
