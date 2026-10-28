package com.trmtgtnh.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.config.TrmtConfig;

/**
 * A small button for this mod's settings on the game's own Options screen (0.9.228; Xep, 2026-10-20: "Can we make it like
 * Distant Horizons button? Just a small one off to the side?"). Distant Horizons puts a square icon button just left of
 * the top row; this one is its mirror, just right of the top row's right-hand button, the row's own height, four pixels
 * off. On by default, {@code client.optionsButton} hides it, and it is never on the pause menu.
 *
 * <p>
 * <strong>It never covers another button.</strong> Where something already stands in its spot - Distant Horizons on its
 * own side, another mod's button on this one - it steps aside: one button further right, and below the spot when the
 * screen's edge comes first ({@link #place}, which a test asks every way).
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
    static final Identifier GRASS = Identifier.withDefaultNamespace("textures/block/grass_block_top.png");

    static final Identifier PATH = Identifier.withDefaultNamespace("textures/block/dirt_path_top.png");

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
     */
    public static void addTo(Screen screen, List<?> children, Consumer<AbstractWidget> add) {
        if (!(screen instanceof OptionsScreen) || !TrmtConfig.optionsButton) return;
        List<int[]> widgets = new ArrayList<int[]>();
        for (Object each : children) {
            // A string widget is the screen's title, not a button of its top row: from 1.19 the Options screen's
            // title is a StringWidget above every button, and counted it put this button beside the word.
            if (each instanceof AbstractWidget
                && !(each instanceof net.minecraft.client.gui.components.AbstractStringWidget)
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
                if (settings != null) Minecraft.getInstance().gui.setScreen(settings);
            }, DEFAULT_NARRATION);
            setTooltip(Tooltip.create(Component.literal(Trmt.NAME + " - " + com.trmtgtnh.util.Translate.get(
                "trmtgtnh.config.own.settings"))));
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
            // The button's own face first: from 26.1 a button's contents are all it draws, its sprite included, so
            // without this the icon stood on nothing (0.9.228; Xep saw it in compare-screens-options-buttons.png).
            extractDefaultSprite(graphics);
            int picture = 16;
            int x = getX() + (getWidth() - picture) / 2;
            int y = getY() + (getHeight() - picture) / 2;
            graphics.blit(RenderPipelines.GUI_TEXTURED, GRASS, x, y, 0F, 0F, picture, picture, 16, 16, 16, 16,
                WornPathArt.grassColor());
            graphics.blit(RenderPipelines.GUI_TEXTURED, PATH, x, y + PATH_FROM, 0F, PATH_FROM, picture, PATH_ROWS, 16,
                PATH_ROWS, 16, 16);
        }
    }
}
