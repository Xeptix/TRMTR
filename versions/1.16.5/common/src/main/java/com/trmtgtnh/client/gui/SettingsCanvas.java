package com.trmtgtnh.client.gui;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.math.Matrix4f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Registry;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import com.trmtgtnh.client.settings.Canvas;
import com.trmtgtnh.client.settings.HoverText;
import com.trmtgtnh.client.settings.Slider;

/**
 * This edition's {@link Canvas}: how the shared settings widgets reach a 1.16.5 screen (spec Part 3.2).
 *
 * <p>
 * A fill is {@code GuiComponent.fill}; a gradient a quad through the {@code Tesselator} with
 * {@code DefaultVertexFormat.POSITION_COLOR} and a color at each corner, smooth shading on and texture off, as
 * {@code GuiComponent.fillGradient} does it inside (that one is vertical only); text the game's own font with its
 * shadow, scaled through the frame's {@code PoseStack}; a clip the scissor test in window pixels, counted from the
 * bottom, kept as a stack that intersects as it grows ({@code RenderSystem.enableScissor} keeps none); the icons and the
 * wordmark from the atlases this jar ships; and a picture an item drawn as an inventory draws one.
 *
 * <p>
 * <strong>Painted in order, nearest last</strong>, as the 1.7.10 edition's screen is: an item or a mob is drawn with
 * the depth test, as the game draws them, so the depth buffer is cleared after each picture and the depth test left
 * off - a hover or a dropdown drawn over a picture is then never hidden behind it. The frame's {@code PoseStack} is
 * handed in at the start of every frame ({@link #frame}).
 */
final class SettingsCanvas implements Canvas {

    /** The tamper, the "All surfaces" sub-tab's picture. */
    static final String TAMPER = "tamper";

    private final Minecraft mc;

    private final Font font;

    private final int screenWidth;

    private final int screenHeight;

    private PoseStack pose = new PoseStack();

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

    /** The frame's pose, handed in as each frame is drawn. */
    SettingsCanvas frame(PoseStack frame) {
        this.pose = frame == null ? new PoseStack() : frame;
        return this;
    }

    PoseStack pose() {
        return pose;
    }

    @Override
    public void fill(int x0, int y0, int x1, int y1, int argb) {
        if (x1 <= x0 || y1 <= y0) return;
        GuiComponent.fill(pose, x0, y0, x1, y1, argb);
        RenderSystem.color4f(1F, 1F, 1F, 1F);
    }

    @Override
    public void panel(int x0, int y0, int x1, int y1) {
        WornPathArt.dirt(pose, x0, y0, x1, y1, true);
    }

    /** The screen's own dark dirt, behind the panel. */
    void background(int x0, int y0, int x1, int y1) {
        WornPathArt.dirt(pose, x0, y0, x1, y1, false);
    }

    @Override
    public void gradient(int x0, int y0, int x1, int y1, int topLeft, int topRight, int bottomLeft, int bottomRight) {
        Matrix4f matrix = pose.last()
            .pose();
        RenderSystem.disableTexture();
        RenderSystem.enableBlend();
        RenderSystem.disableAlphaTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.shadeModel(GL11.GL_SMOOTH);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormat.POSITION_COLOR);
        vertex(buffer, matrix, x1, y0, topRight);
        vertex(buffer, matrix, x0, y0, topLeft);
        vertex(buffer, matrix, x0, y1, bottomLeft);
        vertex(buffer, matrix, x1, y1, bottomRight);
        tesselator.end();
        RenderSystem.shadeModel(GL11.GL_FLAT);
        RenderSystem.disableBlend();
        RenderSystem.enableAlphaTest();
        RenderSystem.enableTexture();
        RenderSystem.color4f(1F, 1F, 1F, 1F);
    }

    private static void vertex(BufferBuilder buffer, Matrix4f matrix, int x, int y, int argb) {
        buffer.vertex(matrix, x, y, 0F)
            .color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF)
            .endVertex();
    }

    @Override
    public void text(String text, int x, int y, int argb, boolean shadow) {
        if (shadow) font.drawShadow(pose, text, x, y, argb);
        else font.draw(pose, text, x, y, argb);
        RenderSystem.color4f(1F, 1F, 1F, 1F);
    }

    @Override
    public void text(String text, int x, int y, int argb, float scale) {
        pose.pushPose();
        pose.translate(x, y, 0D);
        pose.scale(scale, scale, 1F);
        font.drawShadow(pose, text, 0F, 0F, argb);
        pose.popPose();
        RenderSystem.color4f(1F, 1F, 1F, 1F);
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
        scissor(clip);
    }

    @Override
    public void popClip() {
        clips.pop();
        int[] outer = clips.peek();
        if (outer == null) RenderSystem.disableScissor();
        else scissor(outer);
    }

    /** The scissor test over a box of GUI pixels: window pixels, counted from the bottom. */
    private void scissor(int[] clip) {
        double factor = mc.getWindow()
            .getGuiScale();
        int w = Math.max(0, clip[2] - clip[0]);
        int h = Math.max(0, clip[3] - clip[1]);
        RenderSystem.enableScissor(
            (int) (clip[0] * factor),
            (int) ((screenHeight - clip[3]) * factor),
            (int) (w * factor),
            (int) (h * factor));
    }

    @Override
    public void icon(String name, int x, int y) {
        WornPathArt.settingsIcon(pose, Canvas.iconCell(name), x, y);
        RenderSystem.color4f(1F, 1F, 1F, 1F);
    }

    /**
     * A picture: the slider's worn bar and its handle, the tamper, a block or an item drawn as the game draws one in
     * an inventory, at 16 by 16 or scaled. Anything else draws nothing.
     */
    @Override
    public void picture(Object ref, int x, int y) {
        if (ref instanceof Canvas.Scaled) {
            Canvas.Scaled scaled = (Canvas.Scaled) ref;
            float by = scaled.size / 16F;
            // Through the game's own matrix, which the item renderer draws under as well as everything else.
            RenderSystem.pushMatrix();
            RenderSystem.translatef(x, y, 0F);
            RenderSystem.scalef(by, by, 1F);
            picture(scaled.ref, 0, 0);
            RenderSystem.popMatrix();
            return;
        }
        if (ref instanceof Slider.Piece) {
            Slider.Piece piece = (Slider.Piece) ref;
            if (Slider.Piece.BAR.equals(piece.name)) {
                WornPathArt.wornBar(pose, x, y, piece.width, piece.height, piece.fraction, piece.stages);
            } else {
                // The game's own slider handle, as a button's face: light above, dark below.
                fill(x, y, x + piece.width, y + piece.height, 0xFF000000);
                fill(x + 1, y + 1, x + piece.width - 1, y + piece.height - 1, 0xFFA0A0A0);
                fill(x + 1, y + piece.height - 2, x + piece.width - 1, y + piece.height - 1, 0xFF555555);
            }
            RenderSystem.color4f(1F, 1F, 1F, 1F);
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
            ((ChooserPictures.Picture) ref).draw(pose, x, y);
            RenderSystem.color4f(1F, 1F, 1F, 1F);
            return;
        }
        if (TAMPER.equals(ref)) {
            WornPathArt.tamper(pose, x, y);
            RenderSystem.color4f(1F, 1F, 1F, 1F);
            return;
        }
        if (SettingsPictures.draws(ref)) {
            // A list's picture: a block, its texture, an item, a fluid, a sprite, a mob (spec M3), within the frame's
            // budget of first tries (Part 6).
            SettingsPictures.draw(pose, ref, x, y);
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
        WearIcons.drawIcon(String.valueOf(Registry.ITEM.getKey(stack.getItem())), stack, x, y);
        afterPicture();
    }

    /**
     * After an item or a mob: the depth it wrote cleared and the depth test left off, so whatever is drawn after it is
     * drawn over it, as the 1.7.10 edition's screen paints; the lighting the item left set back, the alpha test on.
     */
    static void afterPicture() {
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.disableDepthTest();
        RenderSystem.disableLighting();
        RenderSystem.enableAlphaTest();
        RenderSystem.defaultAlphaFunc();
        RenderSystem.color4f(1F, 1F, 1F, 1F);
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
