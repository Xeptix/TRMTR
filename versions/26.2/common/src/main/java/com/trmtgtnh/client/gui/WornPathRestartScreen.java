package com.trmtgtnh.client.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import com.trmtgtnh.client.settings.SettingsKeys;

/**
 * What the settings screen says after Save when a change waits for a restart or a resource reload (0.9.228, spec M13.3:
 * restart, honestly). A restart: its line and note, then "Quit the game" - the game's own shutdown - and "Later". A
 * resource reload alone: its line, "Reload resources now" - the game's own reload, as F3+T does it - and "Later". Both:
 * both lines and all three buttons. "Later", and Escape, go back where Save was going.
 *
 * <p>
 * Until 0.9.228 a restart was a note with one button, and a setting that waited for a resource reload said nothing at
 * all: the player was left to find F3+T, or to believe the change had not taken.
 */
public class WornPathRestartScreen extends SettingsQuestion {

    /**
     * @param parent  where Save was going: "Later" goes there, and so does the screen after a reload
     * @param restart whether a saved change waits for the game to be started again
     * @param reload  whether a saved change waits for the next resource reload
     */
    public WornPathRestartScreen(final Screen parent, boolean restart, boolean reload) {
        super(Offer.lines(restart, reload), Offer.headlines(restart, reload), answers(parent, restart, reload));
    }

    private static List<Answer> answers(final Screen parent, boolean restart, boolean reload) {
        List<Answer> out = new ArrayList<Answer>();
        for (String key : Offer.buttons(restart, reload)) {
            if (SettingsKeys.RESTART_QUIT.equals(key)) {
                out.add(new Answer(key, () -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.stop();
                }));
            } else if (SettingsKeys.RELOAD_NOW.equals(key)) {
                out.add(new Answer(key, () -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.reloadResourcePacks();
                    mc.gui.setScreen(parent);
                }));
            } else {
                out.add(
                    new Answer(
                        key,
                        () -> Minecraft.getInstance()
                            .gui.setScreen(parent)));
            }
        }
        return out;
    }

    /** The screen as the rig photographs it (spec Part 4, {@code settings-restart}): nothing it offers is pressed. */
    public static Screen forPhotograph(Screen parent, boolean restart, boolean reload) {
        return new WornPathRestartScreen(parent, restart, reload);
    }

    /**
     * What the screen says and offers, by what waits (spec M13.3). Plain, apart from the screen, so a test can ask it
     * without building a screen.
     */
    static final class Offer {

        private Offer() {}

        /** Its paragraphs, by word key: the restart's line and note, then the reload's line. */
        static List<String> lines(boolean restart, boolean reload) {
            List<String> out = new ArrayList<String>();
            if (restart) {
                out.add(SettingsKeys.RESTART);
                out.add(SettingsKeys.RESTART_NOTE);
            }
            if (reload) out.add(SettingsKeys.RELOAD);
            return out;
        }

        /** The lines drawn gold: what waits, as against the note under it. */
        static List<String> headlines(boolean restart, boolean reload) {
            List<String> out = new ArrayList<String>();
            if (restart) out.add(SettingsKeys.RESTART);
            if (reload) out.add(SettingsKeys.RELOAD);
            return out;
        }

        /**
         * Its buttons, left to right, by word key: Quit where a restart waits, Reload now where a reload does, Later.
         */
        static List<String> buttons(boolean restart, boolean reload) {
            List<String> out = new ArrayList<String>();
            if (restart) out.add(SettingsKeys.RESTART_QUIT);
            if (reload) out.add(SettingsKeys.RELOAD_NOW);
            out.add(SettingsKeys.RESTART_LATER);
            return out;
        }
    }
}
