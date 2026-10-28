package com.trmtgtnh.client.gui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.client.event.GuiScreenEvent;

import org.lwjgl.opengl.GL11;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.config.TrmtConfig;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * A small button for this mod's settings on the game's own Options screen (0.9.228; Xep, 2026-10-20: "Can we make it
 * like Distant Horizons button? Just a small one off to the side?"). In GT New Horizons Distant Horizons puts a square
 * icon button just left of the top row - the field of view slider and the difficulty button; this one is its mirror,
 * just right of the top row's right-hand button, the row's own height, four pixels off. On by default,
 * {@code client.optionsButton} hides it, and it is never on the pause menu, which is a screen of its own.
 *
 * <p>
 * <strong>It never covers another button.</strong> Where something already stands in its spot it steps aside: one
 * button further right, and below the spot when the screen's edge comes first ({@link #place}, which a test asks every
 * way). The placement is 26.3's, word for word.
 *
 * <p>
 * Its picture is composed from the game's own pictures - its grass top, tinted, with the most worn picture across the
 * middle ({@link WornPathArt#icon}) - so it follows whatever resource pack is loaded (Xep: "A custom icon, showing
 * a worn grass path from in-game own textures").
 */
public final class OptionsButton {

    /** The gap between it and the button beside it. */
    static final int GAP = 4;

    /** High enough not to collide with the ids the Options screen and Forge hand out; the screen ignores it. */
    static final int ID = 9023;

    /** The one instance Forge's event bus is given. */
    public static final OptionsButton EVENTS = new OptionsButton();

    /** The button laid last and the screen it was laid on, so its hover is drawn on that screen alone. */
    private Icon laid;

    private GuiScreen laidOn;

    private OptionsButton() {}

    /**
     * Where the button goes, as {x, y, size}, given every widget already on the screen as {x, y, width, height} - or
     * null where the screen has no widget to stand beside, or no room. The top row is the widgets at the least height;
     * the spot is just right of the row's right-hand one, the row's height square. Taken, it steps one button right at
     * a time; off the screen's edge, it goes below the spot instead, stepping down while that is taken.
     */
    static int[] place(List<int[]> widgets, int screenWidth, int screenHeight) {
        if (widgets.isEmpty()) return null;
        int top = Integer.MAX_VALUE;
        for (int[] w : widgets) top = Math.min(top, w[1]);
        int right = Integer.MIN_VALUE;
        int size = 0;
        for (int[] w : widgets) {
            if (w[1] != top) continue;
            right = Math.max(right, w[0] + w[2]);
            size = Math.max(size, w[3]);
        }
        int x = right + GAP;
        int y = top;
        while (x + size <= screenWidth && taken(widgets, x, y, size)) x += size + GAP;
        if (x + size <= screenWidth) return new int[] { x, y, size };
        // The edge comes first: below the first spot, kept on the screen.
        x = Math.min(right + GAP, screenWidth - size);
        y = top + size + GAP;
        while (y + size <= screenHeight && taken(widgets, x, y, size)) y += size + GAP;
        return y + size <= screenHeight ? new int[] { x, y, size } : null;
    }

    /** Whether a square there would touch a widget already on the screen. */
    static boolean taken(List<int[]> widgets, int x, int y, int size) {
        for (int[] w : widgets) {
            if (x < w[0] + w[2] && x + size > w[0] && y < w[1] + w[3] && y + size > w[1]) return true;
        }
        return false;
    }

    /** Laid on the game's Options screen once it has laid itself out, and every mod before this one has added to it. */
    @SubscribeEvent
    public void onInit(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!(event.gui instanceof GuiOptions) || !TrmtConfig.optionsButton) return;
        List<int[]> widgets = new ArrayList<int[]>();
        for (Object each : event.buttonList) {
            if (each instanceof GuiButton && ((GuiButton) each).visible) {
                GuiButton b = (GuiButton) each;
                widgets.add(new int[] { b.xPosition, b.yPosition, b.width, b.height });
            }
        }
        int[] at = place(widgets, event.gui.width, event.gui.height);
        if (at == null) return;
        @SuppressWarnings("unchecked")
        List<GuiButton> buttons = event.buttonList;
        laid = new Icon(at[0], at[1], at[2]);
        laidOn = event.gui;
        buttons.add(laid);
    }

    /** Pressed: the settings, over the Options screen, so Done comes back to it. */
    @SubscribeEvent
    public void onPress(GuiScreenEvent.ActionPerformedEvent.Post event) {
        if (!(event.button instanceof Icon)) return;
        GuiScreen settings = ConfigScreens.open(event.gui);
        Minecraft.getMinecraft()
            .displayGuiScreen(settings);
    }

    /** Its hover, the mod's name and what it opens, drawn over everything once the screen has drawn. */
    @SubscribeEvent
    public void onDraw(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (laid == null || event.gui != laidOn || !laid.hovered(event.mouseX, event.mouseY)) return;
        Hover.SHOW.draw(
            Arrays.asList(Trmt.NAME, I18n.format("trmtgtnh.config.own.settings")),
            event.mouseX,
            event.mouseY,
            event.gui.width,
            event.gui.height);
    }

    /** The button: the game's own face with the worn-path picture on it. */
    static final class Icon extends GuiButton {

        Icon(int x, int y, int size) {
            super(ID, x, y, size, size, "");
        }

        boolean hovered(int mouseX, int mouseY) {
            return visible && mouseX >= xPosition
                && mouseY >= yPosition
                && mouseX < xPosition + width
                && mouseY < yPosition + height;
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY) {
            super.drawButton(mc, mouseX, mouseY);
            if (!visible) return;
            WornPathArt.icon(xPosition + (width - 16) / 2, yPosition + (height - 16) / 2);
            GL11.glColor4f(1F, 1F, 1F, 1F);
        }
    }

    /**
     * The game's own hover, borrowed: {@code drawHoveringText} is a screen's to itself, so a screen of this mod's that
     * is
     * never shown draws it.
     */
    static final class Hover extends GuiScreen {

        static final Hover SHOW = new Hover();

        void draw(List<String> lines, int x, int y, int screenWidth, int screenHeight) {
            Minecraft mc = Minecraft.getMinecraft();
            this.mc = mc;
            this.width = screenWidth;
            this.height = screenHeight;
            drawHoveringText(lines, x, y, mc.fontRenderer);
            // drawHoveringText leaves the lighting on, as the screens of this edition expect after it; the Options
            // screen draws nothing after this, but the color goes back to white for whatever does.
            GL11.glColor4f(1F, 1F, 1F, 1F);
        }
    }
}
