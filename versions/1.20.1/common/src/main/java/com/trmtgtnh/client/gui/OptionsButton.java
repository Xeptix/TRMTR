package com.trmtgtnh.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractStringWidget;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.config.TrmtConfig;

/**
 * A small button for this mod's settings on the game's own Options screen (0.9.228; Xep, 2026-10-20: "Can we make it like
 * Distant Horizons button? Just a small one off to the side?"). Distant Horizons puts a square icon button just left of
 * the top row; this one is its mirror, just right of the top row's right-hand button - the difficulty button in a world
 * (its lock beside it in single player), the online button at the title screen - the row's own height, four pixels
 * off. On by default, {@code client.optionsButton} hides it, and it is never on the pause menu ("Options screen only").
 * Carried from 26.3.
 *
 * <p>
 * <strong>It never covers another button.</strong> Where something already stands in its spot - another mod's button
 * on this side - it steps aside: one button further right, and below the spot when the screen's edge comes first
 * ({@link #place}, which a test asks every way).
 *
 * <p>
 * Its picture is composed as it is drawn from the game's own pictures - its grass top, tinted, with a band of its path
 * worn across the middle - so it follows whatever resource pack is loaded (Xep: "A custom icon, showing a worn grass path
 * from in-game own textures").
 */
public final class OptionsButton {

    /** The gap between it and the button beside it. */
    static final int GAP = 4;

    /** The game's own grass top and path top, from whatever resource pack is loaded. */
    static final ResourceLocation GRASS = new ResourceLocation("textures/block/grass_block_top.png");

    static final ResourceLocation PATH = new ResourceLocation("textures/block/dirt_path_top.png");

    /** The rows of the icon the path is worn across: the middle six of sixteen. */
    static final int PATH_FROM = 5;

    static final int PATH_ROWS = 6;

    private OptionsButton() {}

    /**
     * Where the button goes, as {x, y, size}, given every widget already on the screen as {x, y, width, height} - or null
     * where the screen has no widget to stand beside. The top row is the widgets at the least height; the spot is just
     * right of the row's right-hand one, the row's height square. Taken, it steps one button right at a time; off the
     * screen's edge, it goes below the spot instead, stepping down while that is taken.
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

    /**
     * Adds the button to the game's Options screen, where the setting has it on: each loader calls this once the screen
     * has laid itself out, with the screen's widgets and a way to add one.
     *
     * <p>
     * A string widget is left out of the widgets the row is found among. 1.20.1's Options screen draws its title
     * itself rather than as a widget, but from 1.20.2 the title is a string widget at the top of the header, above the
     * row, and counted it would be the top row: the button would stand beside the title's words rather than beside the
     * difficulty button. Left out here too, so a mod that adds one cannot do the same.
     */
    public static void addTo(Screen screen, List<?> children, Consumer<AbstractWidget> add) {
        if (!(screen instanceof OptionsScreen) || !TrmtConfig.optionsButton) return;
        List<int[]> widgets = new ArrayList<int[]>();
        for (Object each : children) {
            if (each instanceof AbstractWidget && !(each instanceof AbstractStringWidget) && !(each instanceof Icon)
                && ((AbstractWidget) each).visible) {
                AbstractWidget w = (AbstractWidget) each;
                widgets.add(new int[] { w.getX(), w.getY(), w.getWidth(), w.getHeight() });
            }
        }
        int[] at = place(widgets, screen.width, screen.height);
        if (at == null) return;
        add.accept(new Icon(screen, at[0], at[1], at[2]));
    }

    /** The button: the game's own face with the worn-path picture on it. */
    static final class Icon extends Button {

        Icon(Screen options, int x, int y, int size) {
            super(x, y, size, size, Component.literal(Trmt.NAME), pressed -> {
                Screen settings = ConfigScreens.open(options);
                if (settings != null) Minecraft.getInstance()
                    .setScreen(settings);
            }, DEFAULT_NARRATION);
            setTooltip(
                Tooltip.create(
                    Component.literal(Trmt.NAME + " - " + com.trmtgtnh.util.Translate.get("trmtgtnh.config.own.settings"))));
        }

        /** The picture where the button's words would be: the game draws the button's face, this its middle. */
        @Override
        public void renderString(GuiGraphics graphics, Font font, int color) {
            int picture = 16;
            int x = getX() + (getWidth() - picture) / 2;
            int y = getY() + (getHeight() - picture) / 2;
            int grass = WornPathArt.grassColor();
            graphics.setColor(((grass >> 16) & 0xFF) / 255F, ((grass >> 8) & 0xFF) / 255F, (grass & 0xFF) / 255F, 1F);
            graphics.blit(GRASS, x, y, 0F, 0F, picture, picture, 16, 16);
            graphics.setColor(1F, 1F, 1F, 1F);
            // The path's own picture over the middle rows, the most worn ground the game has - blended, so a resource
            // pack's see-through path pixels show the grass beneath them.
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            graphics.blit(PATH, x, y + PATH_FROM, picture, PATH_ROWS, 0F, PATH_FROM, picture, PATH_ROWS, 16, 16);
            RenderSystem.disableBlend();
        }
    }
}
