package com.trmtgtnh.client.gui;

import java.util.function.IntBinaryOperator;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

/**
 * What this mod's screens draw behind themselves over the world, and how the harness reads a frame for it (1.0.0).
 *
 * <p>
 * <strong>The 1.7.10 edition dims the world</strong> behind a panel screen with {@code drawDefaultBackground}, a
 * gradient from {@code 0xC0101010} to {@code 0xD0101010}, and Forge's settings screen draws the game's dirt, opaque.
 * 1.20.1 does both the same way. From 1.20.5 a screen's own {@code renderBackground} draws the game's blur and then
 * {@code inworld_menu_background.png}, a texture a quarter dark (alpha 64, read in 1.21.1's jar): with the blur off,
 * as the rig keeps it, the world showed through the guide, the snapshot screen, the tamper, the wear editor and the
 * discard question almost undimmed, the sky's blue at 191 where 1.7.10 has it at 74. The game still draws 1.7.10's gradient,
 * the same two colors, as {@code Screen.renderTransparentBackground}, and its container screens draw that over the
 * world and nothing else ({@code AbstractContainerScreen.renderBackground}, read in the same jar) - so this mod's panel
 * screens draw it too, and the golem's screen, a container, already did. Away from a world they draw the game's own.
 *
 * <p>
 * The harness's screens part reads each photograph's frame where a screen draws nothing but its background: a panel
 * screen's corner must be no brighter than the gradient lets anything be ({@link #rowsUndimmed}), and Cloth Config's
 * header must be its tiled dirt ({@link #rowsNotOpaque}).
 */
public final class ScreenBackdrop {

    /**
     * The brightest any channel can be under the gradient: white under its lighter end, {@code 0xC0} of 16 over 255
     * times what is left, 12 + 63 = 75, the most every 1.7.10 and 1.20.1 photograph of these screens holds. Five more
     * for rounding; the quarter-dark texture over the rig's sky leaves 191.
     */
    public static final int DIM_CEILING = 80;

    /**
     * The width Cloth Config's background repeats at, in GUI pixels: it draws its sixteen-pixel dirt with each corner's
     * texture coordinate its position over 32, so the tile is 32 wide (ClothConfigScreen.overlayBackground, 15.0.140).
     */
    public static final int CLOTH_TILE = 32;

    /** The corner a panel screen leaves bare, read for the dim: eight GUI pixels square from the top left. */
    public static final int CORNER = 8;

    private ScreenBackdrop() {}

    /**
     * The background behind a panel screen: over the world, the game's dimming gradient, as the 1.7.10 edition's
     * {@code drawDefaultBackground} draws it; away from one, what the screen's own {@code renderBackground} would draw,
     * handed in because a screen that answers that call with nothing reaches the game's only through {@code super}.
     */
    public static void behind(Screen screen, GuiGraphics graphics, Runnable away) {
        if (Minecraft.getInstance().level != null) screen.renderTransparentBackground(graphics);
        else away.run();
    }

    /**
     * How many pixel rows of a frame's rectangle hold a pixel brighter, in any of its three colors, than the dimming
     * gradient lets through. The pixel is read packed as the game's frame holds it; only its low three bytes are
     * colors, in whichever order, and the brightest of them is what is asked.
     */
    public static int rowsUndimmed(IntBinaryOperator pixel, int x0, int y0, int x1, int y1) {
        int rows = 0;
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                int p = pixel.applyAsInt(x, y);
                if (Math.max(p & 0xFF, Math.max(p >> 8 & 0xFF, p >> 16 & 0xFF)) > DIM_CEILING) {
                    rows++;
                    break;
                }
            }
        }
        return rows;
    }

    /**
     * How many pixel rows of a frame's rectangle are not an opaque tiled texture: a row of one color (the sky, or a
     * flat veil over it), or one that does not repeat itself {@code period} pixels along (the world, blurred or not).
     * A rectangle narrower than two periods cannot be read, and every row of it is counted.
     */
    public static int rowsNotOpaque(IntBinaryOperator pixel, int x0, int y0, int x1, int y1, int period) {
        int rows = 0;
        for (int y = y0; y < y1; y++) {
            if (period < 1 || x1 - x0 < period * 2) {
                rows++;
                continue;
            }
            int first = pixel.applyAsInt(x0, y);
            boolean flat = true;
            boolean repeats = true;
            for (int x = x0; x < x1; x++) {
                int p = pixel.applyAsInt(x, y);
                if (p != first) flat = false;
                if (x + period < x1 && p != pixel.applyAsInt(x + period, y)) repeats = false;
            }
            if (flat || !repeats) rows++;
        }
        return rows;
    }
}
