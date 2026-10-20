package com.trmtgtnh.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.GrassColor;

import com.trmtgtnh.Trmt;

/**
 * The pictures the mod's own settings screen is drawn with: a lawn with a path worn across it, a heading laid on a
 * strip of path, and a bar that wears from grass to dirt.
 *
 * <p>
 * Xep's brief for 26.x's screen without Cloth Config (0.9.227): "a little more themed towards my mod" - worn dirt and
 * grass behind it, headers on a path strip, sliders that wear grass to dirt, the mod's icon in the title. Every
 * picture is one this jar already carries for the worn ground itself: {@code grass_top_<n>_r<k>} is grass at wear
 * {@code n} of four (its pixels thin as {@code n} rises, so the dirt beneath shows through) in four turns, and
 * {@code dirt_all_0_r<k>} the earth under it. The grass is grey in the file, as the game's own is, and is drawn
 * tinted with the game's default grass color.
 */
final class WornPathArt {

    /** The most worn picture there is: {@code grass_top_4}. */
    static final int MOST_WORN = 4;

    /** The size a square of lawn is drawn at behind the screen: twice the picture's own sixteen. */
    static final int LAWN_SQUARE = 32;

    /** Every picture is sixteen by sixteen. */
    private static final int PICTURE = 16;

    /** What the screen's title is drawn beside: the tamper, the first tool the mod gives a player. */
    static final Identifier ICON = Identifier.fromNamespaceAndPath(Trmt.MODID, "textures/item/tamper.png");

    private WornPathArt() {}

    /** Grass at a wear of nought (untouched) to four (nearly gone), in one of its four turns. */
    static Identifier grass(int wear, int turn) {
        return Identifier.fromNamespaceAndPath(Trmt.MODID, "textures/block/grass_top_" + wear + "_r" + (turn & 3) + ".png");
    }

    /** The earth beneath, in one of its four turns. */
    static Identifier dirt(int turn) {
        return Identifier.fromNamespaceAndPath(Trmt.MODID, "textures/block/dirt_all_0_r" + (turn & 3) + ".png");
    }

    /** The game's default grass color, opaque. */
    static int grassColor() {
        return 0xFF000000 | GrassColor.getDefaultColor();
    }

    /**
     * One square of ground: earth, and grass over it worn as far as {@code wear} says. Drawn at any size and cut to
     * {@code width} from the left, so a row of squares can end part way through one.
     */
    static void square(GuiGraphicsExtractor graphics, int x, int y, int size, int width, int wear, int turn) {
        if (width <= 0 || size <= 0) return;
        int shown = Math.min(width, size);
        int source = Math.max(1, Math.round(PICTURE * (shown / (float) size)));
        graphics.blit(RenderPipelines.GUI_TEXTURED, dirt(turn), x, y, 0F, 0F, shown, size, source, PICTURE, PICTURE, PICTURE);
        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            grass(clampWear(wear), turn),
            x,
            y,
            0F,
            0F,
            shown,
            size,
            source,
            PICTURE,
            PICTURE,
            PICTURE,
            grassColor());
    }

    /**
     * The whole screen's lawn, with one path worn across it from left to right.
     *
     * <p>
     * The path is a gentle curve, worn deepest along its middle and less the further a square lies from it, the way
     * a real path wears: four in the middle, then three, two and one, and untouched grass beyond. Each square's turn
     * is fixed by where it is, so the lawn does not shimmer from frame to frame.
     */
    static void lawn(GuiGraphicsExtractor graphics, int width, int height) {
        for (int top = 0, row = 0; top < height; top += LAWN_SQUARE, row++) {
            for (int left = 0, column = 0; left < width; left += LAWN_SQUARE, column++) {
                int middleX = left + LAWN_SQUARE / 2;
                int middleY = top + LAWN_SQUARE / 2;
                square(graphics, left, top, LAWN_SQUARE, LAWN_SQUARE, wearOnThePath(middleX, middleY, width, height),
                    turnAt(column, row));
            }
        }
    }

    /** How worn the lawn is at a point: four on the path's middle line, nought two squares and more from it. */
    static int wearOnThePath(int x, int y, int width, int height) {
        double across = width <= 0 ? 0D : x / (double) width;
        double line = height * 0.62D + Math.sin(across * Math.PI * 1.6D + 0.6D) * height * 0.14D;
        double squares = Math.abs(y - line) / LAWN_SQUARE;
        if (squares < 0.5D) return 4;
        if (squares < 1.0D) return 3;
        if (squares < 1.5D) return 2;
        if (squares < 2.0D) return 1;
        return 0;
    }

    /**
     * A heading on a strip of path: worn deepest under the words and less towards each end, the strip's own height
     * square by square.
     */
    static void pathStrip(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        int squares = Math.max(1, (width + height - 1) / height);
        for (int at = 0; at < squares; at++) {
            int left = x + at * height;
            int fromEnd = Math.min(at, squares - 1 - at);
            int wear = fromEnd == 0 ? 2 : fromEnd == 1 ? 3 : MOST_WORN;
            square(graphics, left, y, height, x + width - left, wear, turnAt(at, y));
        }
        graphics.outline(x - 1, y - 1, width + 2, height + 2, 0x80000000);
    }

    /**
     * A bar worn from grass to dirt as far as {@code fraction} of it, for a slider: the part behind the handle is
     * path, the part ahead is lawn, and the square the handle stands on is half worn.
     */
    static void wornBar(GuiGraphicsExtractor graphics, int x, int y, int width, int height, double fraction) {
        int squares = Math.max(1, (width + height - 1) / height);
        double handle = Math.max(0D, Math.min(1D, fraction)) * width;
        for (int at = 0; at < squares; at++) {
            int left = at * height;
            int right = left + height;
            int wear = right <= handle ? MOST_WORN : left <= handle ? 2 : 0;
            square(graphics, x + left, y, height, width - left, wear, at);
        }
    }

    /** A fixed turn for a square, from where it is. */
    static int turnAt(int column, int row) {
        int mixed = column * 73856093 ^ row * 19349663;
        return (mixed >>> 7) & 3;
    }

    private static int clampWear(int wear) {
        return wear < 0 ? 0 : wear > MOST_WORN ? MOST_WORN : wear;
    }
}
