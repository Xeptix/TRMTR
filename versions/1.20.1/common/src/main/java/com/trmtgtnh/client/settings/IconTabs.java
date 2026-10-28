package com.trmtgtnh.client.settings;

import com.trmtgtnh.client.settings.SettingsGeometry.Rect;

/**
 * The Surfaces tab's second row: one square tab per family, each its family's picture, the row centred (0.9.228; spec
 * M1.2).
 *
 * <p>
 * "All surfaces" first, then each family in {@code SurfaceFamily} order; a family whose category the file does not
 * hold has none. Each square is 20 by 20, 2 apart, the picture 16 by 16 in its middle - 18 by 18 on a compact screen,
 * whose band is only 18 high. The open one is filled {@link Canvas#TAB_OPEN} with the gold underline the tabs above
 * use, one under the pointer is framed in gold, and each carries its own dot.
 */
public final class IconTabs {

    public static final int SIZE = 20;

    public static final int GAP = 2;

    private Rect[] placed = new Rect[0];

    /** Places so many squares, centred across the band. */
    public void layout(int count, Rect band) {
        int size = Math.min(SIZE, band.height());
        int total = count * size + Math.max(0, count - 1) * GAP;
        int x = band.x0 + (band.width() - total) / 2;
        int y = band.y0 + (band.height() - size) / 2;
        placed = new Rect[count];
        for (int i = 0; i < count; i++) {
            placed[i] = Rect.sized(x + i * (size + GAP), y, size, size);
        }
    }

    public int count() {
        return placed.length;
    }

    public Rect tab(int index) {
        return index >= 0 && index < placed.length ? placed[index] : null;
    }

    /** The square at a point, or -1. */
    public int hit(int x, int y) {
        for (int i = 0; i < placed.length; i++) {
            if (placed[i].contains(x, y)) return i;
        }
        return -1;
    }

    /**
     * Draws the squares.
     *
     * @param pictures each square's picture ref, as {@link Canvas#picture} takes it
     * @param dots     each square's dot, as {@link TabStrip#draw} takes them; may be null
     */
    public void draw(Canvas canvas, Object[] pictures, int open, int[] dots, int mouseX, int mouseY) {
        for (int i = 0; i < placed.length; i++) {
            Rect square = placed[i];
            boolean isOpen = i == open;
            canvas.fill(square.x0, square.y0, square.x1, square.y1, isOpen ? Canvas.TAB_OPEN : Canvas.TAB_IDLE);
            if (pictures != null && i < pictures.length && pictures[i] != null) {
                canvas.picture(
                    pictures[i],
                    square.x0 + (square.width() - 16) / 2,
                    square.y0 + (square.height() - 16) / 2);
            }
            if (isOpen) canvas.fill(square.x0, square.y1 - 2, square.x1, square.y1, Canvas.GOLD);
            if (square.contains(mouseX, mouseY)) {
                Canvas.frame(canvas, square.x0, square.y0, square.x1, square.y1, Canvas.GOLD);
            }
            TabStrip.dot(canvas, square, dots == null || i >= dots.length ? TabStrip.DOT_NONE : dots[i]);
        }
    }
}
