package com.trmtgtnh.client.gui;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.lwjgl.opengl.GL11;

import com.trmtgtnh.client.settings.Canvas;
import com.trmtgtnh.client.settings.HoverText;
import com.trmtgtnh.client.settings.Slider;

/**
 * This edition's {@link Canvas}: how the shared settings widgets reach a 1.7.10 screen (spec Part 3.1).
 *
 * <p>
 * A fill is {@code Gui.drawRect}; a gradient a {@code Tessellator} quad with a color at each corner, smooth shading on
 * and texture off ({@code drawGradientRect} is vertical only); text the game's own font with its shadow, scaled through
 * the matrix; a clip the scissor test in window pixels, flipped, kept as a stack that intersects as it grows; the icons
 * and the wordmark from the atlases this jar ships; and a picture an item drawn the way the list page draws one, with
 * the whole GL state put back after it. <strong>Drawn with the alpha test on</strong>: every color the shared code
 * hands
 * over is opaque but the hover's, so nothing it draws is thrown away.
 */
final class SettingsCanvas implements Canvas {

    /** The tamper, the "All surfaces" sub-tab's picture. */
    static final String TAMPER = "tamper";

    private final Minecraft mc;

    private final FontRenderer font;

    private final int screenWidth;

    private final int screenHeight;

    private final Deque<int[]> clips = new ArrayDeque<int[]>();

    /** The hover to draw at the end of the frame, placed by {@link HoverText}. */
    private List<String> hoverLines;

    private int hoverX;

    private int hoverY;

    SettingsCanvas(Minecraft mc, int screenWidth, int screenHeight) {
        this.mc = mc;
        this.font = mc.fontRenderer;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
    }

    @Override
    public void fill(int x0, int y0, int x1, int y1, int argb) {
        if (x1 <= x0 || y1 <= y0) return;
        Gui.drawRect(x0, y0, x1, y1, argb);
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    @Override
    public void panel(int x0, int y0, int x1, int y1) {
        WornPathArt.dirt(x0, y0, x1, y1, true);
    }

    /** The screen's own dark dirt, behind the panel. */
    void background(int x0, int y0, int x1, int y1) {
        WornPathArt.dirt(x0, y0, x1, y1, false);
    }

    @Override
    public void gradient(int x0, int y0, int x1, int y1, int topLeft, int topRight, int bottomLeft, int bottomRight) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.setColorRGBA_I(bottomLeft & 0xFFFFFF, bottomLeft >>> 24);
        tessellator.addVertex(x0, y1, 0D);
        tessellator.setColorRGBA_I(bottomRight & 0xFFFFFF, bottomRight >>> 24);
        tessellator.addVertex(x1, y1, 0D);
        tessellator.setColorRGBA_I(topRight & 0xFFFFFF, topRight >>> 24);
        tessellator.addVertex(x1, y0, 0D);
        tessellator.setColorRGBA_I(topLeft & 0xFFFFFF, topLeft >>> 24);
        tessellator.addVertex(x0, y0, 0D);
        tessellator.draw();
        GL11.glShadeModel(GL11.GL_FLAT);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    @Override
    public void text(String text, int x, int y, int argb, boolean shadow) {
        font.drawString(text, x, y, argb, shadow);
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    @Override
    public void text(String text, int x, int y, int argb, float scale) {
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0F);
        GL11.glScalef(scale, scale, 1F);
        font.drawString(text, 0, 0, argb, true);
        GL11.glPopMatrix();
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    @Override
    public int width(String text) {
        return font.getStringWidth(text);
    }

    @Override
    public int lineHeight() {
        return font.FONT_HEIGHT;
    }

    @Override
    public String trim(String text, int width) {
        if (text == null) return "";
        if (font.getStringWidth(text) <= width) return text;
        return font.trimStringToWidth(text, Math.max(0, width - font.getStringWidth("..."))) + "...";
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
        if (outer == null) GL11.glDisable(GL11.GL_SCISSOR_TEST);
        else scissor(outer);
    }

    /** The scissor test over a box of GUI pixels: window pixels, counted from the bottom. */
    private void scissor(int[] clip) {
        int factor = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight).getScaleFactor();
        int w = Math.max(0, clip[2] - clip[0]);
        int h = Math.max(0, clip[3] - clip[1]);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(clip[0] * factor, (screenHeight - clip[3]) * factor, w * factor, h * factor);
    }

    @Override
    public void icon(String name, int x, int y) {
        WornPathArt.settingsIcon(Canvas.iconCell(name), x, y);
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
            GL11.glPushMatrix();
            GL11.glTranslatef(x, y, 0F);
            GL11.glScalef(by, by, 1F);
            picture(scaled.ref, 0, 0);
            GL11.glPopMatrix();
            return;
        }
        if (ref instanceof Slider.Piece) {
            Slider.Piece piece = (Slider.Piece) ref;
            if (Slider.Piece.BAR.equals(piece.name)) {
                WornPathArt.wornBar(x, y, piece.width, piece.height, piece.fraction, piece.stages);
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
            Object block = com.trmtgtnh.command.CommandTrmt.roadBlock((com.trmtgtnh.surface.SurfaceFamily) ref);
            if (block != null) picture(block, x, y);
            return;
        }
        if (ref instanceof ChooserPictures.Picture) {
            // A chooser value's picture (spec M23.2): a wear look, a cost curve, a preset's depth or steps.
            ((ChooserPictures.Picture) ref).draw(x, y);
            GL11.glColor4f(1F, 1F, 1F, 1F);
            return;
        }
        if (TAMPER.equals(ref)) {
            WornPathArt.tamper(x, y);
            return;
        }
        if (SettingsPictures.draws(ref)) {
            // A list's picture: a block, its texture, an item, a fluid, a sprite, a mob (spec M3), within the frame's
            // budget of first tries (Part 6).
            SettingsPictures.draw(ref, x, y);
            return;
        }
        ItemStack stack = null;
        if (ref instanceof ItemStack) stack = (ItemStack) ref;
        else if (ref instanceof Block && Item.getItemFromBlock((Block) ref) != null) stack = new ItemStack((Block) ref);
        else if (ref instanceof Item) stack = new ItemStack((Item) ref);
        if (stack == null || stack.getItem() == null) return;
        String key = String.valueOf(Item.itemRegistry.getNameForObject(stack.getItem())) + ":" + stack.getItemDamage();
        if (BlockIconArrayEntry.broken(key)) return;
        BlockIconArrayEntry.drawIcon(key, stack, x, y);
        // The game's item lighting is left on by the item; the screen's own drawing wants it off.
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glColor4f(1F, 1F, 1F, 1F);
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
        return I18n.format(key, args);
    }
}
