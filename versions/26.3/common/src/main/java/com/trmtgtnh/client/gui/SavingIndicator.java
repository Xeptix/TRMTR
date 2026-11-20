package com.trmtgtnh.client.gui;

import net.minecraft.client.gui.screens.Screen;

import com.trmtgtnh.client.settings.SettingsGeometry;

/**
 * The game's "Saving world" is left out while a screen of this mod's that pauses the game is open (1.0.0; Xep,
 * 2026-11-20, choosing to hide it over moving the settings screen's Done button).
 *
 * <p>
 * The game draws "Saving world" in the bottom right corner, 5 pixels in from the right and the bottom, for as long as
 * the world in this game is saving and a second after. 1.20.1 draws it in its HUD, beneath any screen; by 1.21.1 it is
 * drawn after the open screen, over it (Hud.extractSavingIndicator here, called by Gui once the screen is drawn).
 * Opening a screen that pauses
 * the game from play saves the world at once (IntegratedServer.tickServer, "Saving and pausing game..."), so the
 * settings screen opened by its key or by /trmt config came up with the words over its Done button, whose corner is
 * the one the game draws them in; the game's own pausing screens keep their buttons in the middle. 1.7.10, the
 * specification, has no such words, so its frame is the settings screen alone. The save itself is never touched.
 *
 * <p>
 * Only this mod's own screens are asked about, by their class's package, and only while they pause the game: a screen
 * of the game's own, or another mod's, shows the words as the game draws them.
 */
public final class SavingIndicator {

    /** The package every screen of this mod's lives under. */
    static final String OURS = "com.trmtgtnh.";

    /** How far above the bottom the game draws the words' top: their height, 9, and 5 under them. */
    static final int WORDS_TOP = 14;

    /** How far above the bottom the words end. */
    static final int WORDS_BOTTOM = 5;

    private SavingIndicator() {}

    /** Whether the game's "Saving world" is left out over this screen, the one open now; null is play itself. */
    public static boolean hiddenOver(Screen screen) {
        return screen != null && hides(screen.getClass()
            .getName(), screen.isPauseScreen());
    }

    /** Whether a screen of this class, pausing the game or not, hides the words: this mod's own, while it pauses. */
    static boolean hides(String screenClass, boolean pauses) {
        return pauses && screenClass != null && screenClass.startsWith(OURS);
    }

    /**
     * What the game's indicator lends the harness: the words lit at full strength, as a save that has run for a while
     * leaves them, so the screens part photographs the settings screen with them lit whatever the save's own length.
     */
    public interface Lit {

        void trmt$lightSavingIndicator();
    }

    /**
     * Where a photograph of the settings screen is read for the words, in its own pixels: the right quarter of Done,
     * inside its edge, on the rows the words are drawn on - clear of Done's own label in the middle - as {x0, y0, x1,
     * y1}, or null where that leaves nothing. Every pixel row there is Done's own fill unless something is drawn over
     * it.
     */
    static int[] corner(int guiWidth, int guiHeight, int pixelWidth, int pixelHeight) {
        if (guiWidth <= 0 || guiHeight <= 0) return null;
        SettingsGeometry.Rect done = SettingsGeometry.of(guiWidth, guiHeight, false, false).done;
        double scale = pixelWidth / (double) guiWidth;
        int x0 = (int) Math.ceil((done.x0 + (done.x1 - done.x0) * 3 / 4) * scale);
        int x1 = Math.min(pixelWidth, (int) Math.floor((done.x1 - 1) * scale));
        int y0 = (int) Math.ceil(Math.max(done.y0 + 1, guiHeight - WORDS_TOP) * scale);
        int y1 = Math.min(pixelHeight, (int) Math.floor(Math.min(done.y1 - 1, guiHeight - WORDS_BOTTOM) * scale));
        if (x1 - x0 < 2 || y1 <= y0) return null;
        return new int[] { x0, y0, x1, y1 };
    }

    /**
     * How many pixel rows of a settings screen photograph's corner (see corner) hold more than one color, so show
     * something drawn over Done where the game puts "Saving world"; -1 when the photograph has no such corner. The
     * pixels are the photograph's, row after row, pixelWidth to a row.
     */
    public static int rowsWrittenOver(int[] pixels, int pixelWidth, int pixelHeight, int guiWidth, int guiHeight) {
        int[] at = corner(guiWidth, guiHeight, pixelWidth, pixelHeight);
        if (at == null || pixels == null || pixels.length < pixelWidth * pixelHeight) return -1;
        int written = 0;
        for (int y = at[1]; y < at[3]; y++) {
            int ground = pixels[y * pixelWidth + at[0]];
            for (int x = at[0] + 1; x < at[2]; x++) {
                if (pixels[y * pixelWidth + x] != ground) {
                    written++;
                    break;
                }
            }
        }
        return written;
    }

    /** How many pixel rows the corner holds, read or not; 0 where there is none. */
    public static int cornerRows(int pixelWidth, int pixelHeight, int guiWidth, int guiHeight) {
        int[] at = corner(guiWidth, guiHeight, pixelWidth, pixelHeight);
        return at == null ? 0 : at[3] - at[1];
    }
}
