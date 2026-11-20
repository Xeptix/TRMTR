package com.trmtgtnh.client.gui;

/**
 * Which rows of the wear table's look picker are in view, when it holds more looks than its card has room for.
 *
 * <p>
 * The picker drew as many rows as cleared the card's bottom and left the rest out, which was a fair failure while it
 * was one row; with the looks made for single materials it is most of a column at 1920 by 1080's automatic scale. So
 * it scrolls, on the same terms as the mod's own settings list ({@code WornPathListScreen}) and its choosers
 * ({@code client.settings.Dropdown}): a row a wheel notch, and a bar at its right edge that a press or a drag moves and
 * that never chooses anything. Pure arithmetic, so the window's every edge is tested without a screen.
 */
final class LookWindow {

    /** How wide the bar's column is, the strip a press scrolls in rather than chooses: the choosers' own. */
    static final int BAR_HIT = 5;

    private LookWindow() {}

    /** The first row's index held inside the list: never before the first look, never past the last full window. */
    static int clamp(int top, int count, int shown) {
        int last = Math.max(0, count - Math.max(1, shown));
        return top < 0 ? 0 : top > last ? last : top;
    }

    /** The window moved as little as it takes to show {@code index}: a picker opens with its family's look in view. */
    static int showing(int index, int top, int count, int shown) {
        if (index < top) top = index;
        if (index >= top + shown) top = index - shown + 1;
        return clamp(top, count, shown);
    }

    /** A wheel turn: a row a notch, up for a positive turn, as every list in the mod scrolls. */
    static int wheel(int top, int turn, int count, int shown) {
        if (turn == 0) return clamp(top, count, shown);
        return clamp(top + (turn > 0 ? -1 : 1), count, shown);
    }

    /** Whether the list scrolls at all: only while it holds more looks than it shows. */
    static boolean scrolls(int count, int shown) {
        return count > shown;
    }

    /** Where a press or a drag on the bar puts the window, as the choosers' bar does ({@code Dropdown.barTo}). */
    static int barTo(int y, int trackTop, int trackHeight, int count, int shown) {
        int last = Math.max(0, count - shown);
        int track = Math.max(1, trackHeight);
        return clamp(Math.round((y - trackTop) * (float) last / track), count, shown);
    }

    /** The bar's thumb, as its top and its height within the track, as the choosers draw theirs. */
    static int[] thumb(int trackTop, int trackHeight, int top, int count, int shown) {
        int size = Math.max(4, trackHeight * shown / Math.max(1, count));
        int at = trackTop + (trackHeight - size) * top / Math.max(1, count - shown);
        return new int[] { at, size };
    }
}
