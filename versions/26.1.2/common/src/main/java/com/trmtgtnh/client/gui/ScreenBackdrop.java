package com.trmtgtnh.client.gui;

import java.util.function.IntBinaryOperator;

import net.minecraft.client.Minecraft;

/**
 * What this mod's screens draw behind themselves over the world, and how the harness reads a frame for it (1.0.0).
 *
 * <p>
 * <strong>The 1.7.10 edition dims the world</strong> behind a panel screen with {@code drawDefaultBackground}, a
 * gradient from {@code 0xC0101010} to {@code 0xD0101010}, and Forge's settings screen draws the game's dirt, opaque.
 * 1.20.1 does both the same way. From 26.1 the game draws a screen's background itself, before the screen
 * ({@code Screen.extractBackground}, read in 26.1.2's and 26.3's jars): a screen that says it is in-game UI gets
 * {@code extractTransparentBackground}, 1.7.10's gradient in the same two colors, as the game's container screens do;
 * any other gets the game's blur and then {@code inworld_menu_background.png}, a texture a quarter dark. With the blur
 * off, as the rig keeps it, the world showed through the guide, the snapshot screen, the tamper, the wear editor and
 * the discard question almost undimmed, the sky's blue at 191 where 1.7.10 has it at 74. So each of those says it is
 * in-game UI while there is a world ({@link #overWorld}), as the game's own book and sign screens do; away from one
 * they draw the game's own. Saying so changes nothing else: {@code Screen.isInGameUi} is asked by
 * {@code extractBackground} and by the HUD, for the order it draws its subtitles in, and by nothing else on the tree's
 * class path, game, loaders or mods (the 1.7.10 tree's {@code tools/member_users.py}, 26.1.2, 26.2 and 26.3).
 *
 * <p>
 * The harness's screens part reads each photograph where a screen draws nothing but its background: a panel screen's
 * corner must be no brighter than the gradient lets anything be ({@link #rowsUndimmed}), and Cloth Config's header,
 * where Cloth is installed, must be its tiled dirt ({@link #rowsNotOpaque}).
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
     * texture coordinate its position over 32, so the tile is 32 wide (DynamicEntryListWidget, read in 26.3.159).
     */
    public static final int CLOTH_TILE = 32;

    /** The corner a panel screen leaves bare, read for the dim: eight GUI pixels square from the top left. */
    public static final int CORNER = 8;

    private ScreenBackdrop() {}

    /**
     * Whether a panel screen draws the game's dimming gradient behind itself, as the 1.7.10 edition's
     * {@code drawDefaultBackground} draws it: while there is a world to dim. The answer each panel screen gives
     * {@code isInGameUi}.
     */
    public static boolean overWorld() {
        return Minecraft.getInstance().level != null;
    }

    /**
     * How many pixel rows of a frame's rectangle hold a pixel brighter, in any of its three colors, than the dimming
     * gradient lets through. The pixel is read packed; only its low three bytes are colors, in whichever order, and
     * the brightest of them is what is asked.
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
