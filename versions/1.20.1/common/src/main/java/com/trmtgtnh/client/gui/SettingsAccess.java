package com.trmtgtnh.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * The two ways to the settings besides the mod list's Config button (0.9.228; Xep, 2026-10-20: "/trmt config", "A key
 * binding", and the Options-screen button, {@link OptionsButton}): a key, unbound until a player binds it, and a client
 * command. Each loader registers the key and the command in its own way and hands its client tick here; what they do is
 * this class's, the same on every loader. Carried from 26.3.
 *
 * <p>
 * <strong>The command is the client's own and never reaches a server</strong>, so it opens the screen on a server
 * without this mod as well as on one with it. Each loader's client-command API (NeoForge's and Forge's
 * {@code RegisterClientCommandsEvent}, Fabric's {@code ClientCommandRegistrationCallback}) runs a typed command against
 * the client's own tree first and hands it to the server only where that tree answers that it has no such command or
 * argument. The tree here is {@code trmt} with the one branch {@code config}, so {@code /trmt config} is answered on
 * the client and every other {@code /trmt} line - {@code reload}, {@code version} - goes to the server as before
 * ({@link #register}, which a test runs against Brigadier's own dispatcher).
 *
 * <p>
 * <strong>Opened on the next tick, never at once.</strong> A command is run while the chat screen is still up, and the
 * chat closes itself after its line is sent: a screen opened from the command would be shut by that close before it was
 * drawn on an older game. So the command and the key only ask, and the tick opens the screen once nothing is in the
 * way.
 */
public final class SettingsAccess {

    /** The key's name in the controls screen and the language file. */
    public static final String KEY_NAME = "key.trmtgtnh.settings";

    /** The heading the key is listed under on the controls screen: this mod's own. */
    public static final String KEY_CATEGORY = "key.category.trmtgtnh.keys";

    /** The command's words after the slash: {@code /trmt config}. */
    public static final String ROOT = "trmt";

    public static final String CONFIG = "config";

    private static KeyMapping key;

    private static volatile boolean asked;

    private SettingsAccess() {}

    /**
     * The key, made once: unbound out of the box, in the mod's own group of the controls screen. Made when first asked
     * rather than as the class loads, so a test that asks the command's tree does not stand up the game's key table.
     */
    public static synchronized KeyMapping key() {
        if (key == null) key = new KeyMapping(KEY_NAME, InputConstants.UNKNOWN.getValue(), KEY_CATEGORY);
        return key;
    }

    /**
     * {@code /trmt config} on a client's command tree, of whatever source type its loader's tree holds. NeoForge and
     * Forge hand a tree of the game's own source, Fabric one of its own; the branch is the same on each.
     */
    public static <S> void register(CommandDispatcher<S> dispatcher) {
        dispatcher.register(
            LiteralArgumentBuilder.<S>literal(ROOT)
                .then(
                    LiteralArgumentBuilder.<S>literal(CONFIG)
                        .executes(context -> com.trmtgtnh.client.gui.SettingsAccess.askedByCommand())));
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

    /** Whether the screen has been asked for and not yet opened - for the test that runs the command. */
    static boolean asked() {
        return asked;
    }

    /** The ask taken back without opening anything - for the same test, so it leaves nothing behind. */
    static void forgetAsk() {
        asked = false;
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
