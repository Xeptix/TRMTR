package com.trmtgtnh.client.model;

/**
 * The colors a plugin's own quads ask for, on a game whose quads hold none (W3 T8, carried in T20).
 *
 * <p>
 * Up to 1.21.1 {@code GhostQuads.freeQuad} wrote a plugin's color into the quad's vertices. From 26.1 a vertex is its
 * position and its packed texture coordinates, and the only way to color a quad is its tint slot, which is a place in
 * the block's list of tint sources ({@code ModelBlockRenderer.computeTintColor}: a slot past the list's end is white).
 * So the ghost's list has {@link #SLOTS} places after its three, and this says which color each stands for at the
 * square being drawn. The renderer asks a tint for a block on the thread that asked for its quads and before it asks
 * for the next block's, so a thread keeps the one square it drew last, as {@code GhostQuads.HOOKED} keeps its drawing.
 *
 * <p>
 * Sixteen colors a block is what the slots hold, each slot a longer list for every ghost block; a seventeenth color on
 * one block takes the sixteenth's slot, as API.md says. Nothing of this reaches the game, so it is tried without one.
 */
public final class HookTints {

    /** The slot of the first plugin color: after {@code GRASS_TINT}, {@code LIGHT_TINT} and {@code COVERED_TINT}. */
    public static final int FIRST_SLOT = 3;

    /** How many colors one block's quads can have between them. */
    public static final int SLOTS = 16;

    /** One thread's square: where it is, and the colors its quads have taken, in slot order. */
    private static final class Square {

        final long at;
        final int[] colors = new int[SLOTS];
        int held;

        Square(long at) {
            this.at = at;
        }
    }

    private static final ThreadLocal<Square> DRAWN = new ThreadLocal<Square>();

    private HookTints() {}

    /** A new drawing of this square: the colors of whatever this thread drew before it are forgotten. */
    public static void begin(long at) {
        DRAWN.set(new Square(at));
    }

    /** The tint slot of a quad of this color in the drawing begun last on this thread; one color, one slot. */
    public static int slotFor(int rgb) {
        Square square = DRAWN.get();
        if (square == null) return -1;
        int color = rgb & 0xFFFFFF;
        for (int i = 0; i < square.held; i++) {
            if (square.colors[i] == color) return FIRST_SLOT + i;
        }
        if (square.held == SLOTS) return FIRST_SLOT + SLOTS - 1;
        square.colors[square.held] = color;
        return FIRST_SLOT + square.held++;
    }

    /** What a plugin's slot stands for at this square, 0xRRGGBB; white where this thread drew no such quad here. */
    public static int colorOf(long at, int slot) {
        Square square = DRAWN.get();
        int place = slot - FIRST_SLOT;
        if (square == null || square.at != at || place < 0 || place >= square.held) return 0xFFFFFF;
        return square.colors[place];
    }
}
