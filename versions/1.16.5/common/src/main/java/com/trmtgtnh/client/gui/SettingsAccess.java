package com.trmtgtnh.client.gui;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * The two ways to the settings besides the mod list's Config button (0.9.228; Xep, 2026-10-20: "/trmt config", "A key
 * binding", and the Options-screen button, {@link OptionsButton}): a key, unbound until a player binds it, and a client
 * command. Each loader registers the key and catches the command in its own way and hands its client tick here; what
 * they do is this class's, the same on both loaders.
 *
 * <p>
 * <strong>Opened on the next tick, never at once.</strong> A command is run while the chat screen is still up, and the
 * chat closes itself after its line is sent: a screen opened from the command would be shut by that close before it was
 * drawn. So the command and the key only ask, and the tick opens the screen once nothing is in the way.
 *
 * <p>
 * <strong>The command is caught as it is sent, not registered.</strong> Forge 36 has no client command API at all, so
 * the line is caught by its {@code ClientChatEvent}; and Fabric API 0.42's client commands would answer every
 * {@code /trmt} line: with {@code /trmt config} registered on the client, {@code /trmt reload} reads there as an
 * incorrect argument to a client command, is refused on the spot and never reaches the server, whose {@code /trmt}
 * answers everything else. So both loaders hand the typed line to {@link #typed}, which takes this one line and
 * nothing else, and every other line goes to the server as it always did.
 */
public final class SettingsAccess {

    /** The key's name in the controls screen and the language file. */
    public static final String KEY_NAME = "key.trmtgtnh.settings";

    /** The mod's own group in the controls screen, named in the language file. */
    public static final String KEY_CATEGORY = "key.category.trmtgtnh.keys";

    /** The command's words after the slash: {@code /trmt config}. */
    public static final String ROOT = "trmt";

    public static final String CONFIG = "config";

    private static KeyMapping key;

    private static volatile boolean asked;

    private SettingsAccess() {}

    /** The key, made once: unbound out of the box, in the mod's own group of the controls screen. */
    public static KeyMapping key() {
        if (key == null) {
            key = new KeyMapping(KEY_NAME, InputConstants.UNKNOWN.getValue(), KEY_CATEGORY);
        }
        return key;
    }

    /** What {@code /trmt config} does on the client: asks for the screen, which the next tick opens. */
    public static int askedByCommand() {
        asked = true;
        return 1;
    }

    /**
     * A line the player is sending, as each loader catches it: true when it is {@code /trmt config}, which this has then
     * answered and which must not be sent. Spaces either side and between the words are the player's and do not count;
     * anything more after it is another command, and goes.
     */
    public static boolean typed(String message) {
        if (!isTheCommand(message)) return false;
        askedByCommand();
        return true;
    }

    /** Whether a line sent from the chat is {@code /trmt config} and nothing more. Plain, so a test can ask it. */
    static boolean isTheCommand(String message) {
        if (message == null) return false;
        String[] words = message.trim()
            .split(" +");
        return words.length == 2 && ("/" + ROOT).equals(words[0]) && CONFIG.equals(words[1]);
    }

    /** The loader's client tick: a press of the key, or the command's ask, opens the settings once nothing is open. */
    public static void tick(Minecraft client) {
        if (key != null) {
            while (key.consumeClick()) {
                if (client.screen == null) asked = true;
            }
        }
        if (!asked) return;
        Screen open = client.screen;
        // The chat that ran the command closes itself first; the screen waits for it.
        if (open instanceof net.minecraft.client.gui.screens.ChatScreen) return;
        asked = false;
        // Anything else up is somebody's: a key press or a command never covers it.
        if (open != null) return;
        Screen settings = ConfigScreens.open(null);
        if (settings != null) client.setScreen(settings);
    }
}
