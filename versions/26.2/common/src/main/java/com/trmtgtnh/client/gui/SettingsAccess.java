package com.trmtgtnh.client.gui;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;

import com.mojang.blaze3d.platform.InputConstants;

import com.trmtgtnh.Trmt;

/**
 * The two ways to the settings besides the mod list's Config button (0.9.228; Xep, 2026-10-20: "/trmt config", "A key
 * binding", and the Options-screen button, {@link OptionsButton}): a key, unbound until a player binds it, and a client
 * command. Each loader registers the key and the command in its own way and hands its client tick here; what they do is
 * this class's, the same on every loader.
 *
 * <p>
 * <strong>Opened on the next tick, never at once.</strong> A command is run while the chat screen is still up, and the
 * chat closes itself after its line is sent: a screen opened from the command would be shut by that close before it was
 * drawn. So the command and the key only ask, and the tick opens the screen once nothing is in the way.
 */
public final class SettingsAccess {

    /** The key's name in the controls screen and the language file. */
    public static final String KEY_NAME = "key.trmtgtnh.settings";

    /** The command's words after the slash: {@code /trmt config}. */
    public static final String ROOT = "trmt";

    public static final String CONFIG = "config";

    private static KeyMapping key;

    private static volatile boolean asked;

    private SettingsAccess() {}

    /** The key, made once: unbound out of the box, in the mod's own group of the controls screen. */
    public static KeyMapping key() {
        if (key == null) {
            key = new KeyMapping(
                KEY_NAME,
                InputConstants.UNKNOWN.getValue(),
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath(Trmt.MODID, "keys")));
        }
        return key;
    }

    /** What {@code /trmt config} does on the client: asks for the screen, which the next tick opens. */
    public static int askedByCommand() {
        asked = true;
        return 1;
    }

    /**
     * Whether a command the player is sending is {@code /trmt config}, asking for the screen if it is (0.9.228) - how the
     * Fabric jar answers it, as the line leaves the client. Not a client command there: Fabric API hands a typed line to
     * the server only where the client's own tree has no such command or the line will not parse
     * (ClientCommandInternals.isIgnoredException), so a client {@code /trmt} holding {@code config} alone answered
     * {@code /trmt reload} itself, with an error, and the server never saw it. Forge and NeoForge hand an unknown argument
     * on to the server as well, so they keep the client command. A command reaches Fabric's event without its slash.
     */
    public static boolean sentCommand(String command) {
        if (!isTheCommand(command)) return false;
        askedByCommand();
        return true;
    }

    /** Exactly {@code trmt config}, with or without its slash; spaces either side and between the words are the player's. */
    static boolean isTheCommand(String command) {
        if (command == null) return false;
        String bare = command.trim();
        if (bare.startsWith("/")) bare = bare.substring(1)
            .trim();
        String[] words = bare.split("\\s+");
        return words.length == 2 && ROOT.equals(words[0]) && CONFIG.equals(words[1]);
    }

    /** The loader's client tick: a press of the key, or the command's ask, opens the settings once nothing is open. */
    public static void tick(Minecraft client) {
        if (key != null) {
            while (key.consumeClick()) {
                if (client.gui.screen() == null) asked = true;
            }
        }
        if (!asked) return;
        Screen open = client.gui.screen();
        // The chat that ran the command closes itself first; the screen waits for it.
        if (open instanceof net.minecraft.client.gui.screens.ChatScreen) return;
        asked = false;
        // Anything else up is somebody's: a key press or a command never covers it.
        if (open != null) return;
        Screen settings = ConfigScreens.open(null);
        if (settings != null) client.gui.setScreen(settings);
    }
}
