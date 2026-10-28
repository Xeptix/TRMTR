package com.trmtgtnh.client.gui;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.CommandEvent;

import org.lwjgl.input.Keyboard;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * The two ways to the settings besides the mod list's Config button and the Options-screen button (0.9.228; Xep,
 * 2026-10-20: "/trmt config", "A key binding"): a key, unbound until a player binds it, in the mod's own group of the
 * controls screen, and {@code /trmt config}, answered on the client and never sent to the server.
 *
 * <p>
 * <strong>The command is Forge's own kind of client command</strong> ({@code ClientCommandHandler}), which 1.7.10 has
 * and the newer editions replace with their loaders' own. A client command takes its whole name, and the server's
 * {@code /trmt} has a dozen more words than this one: so every {@code /trmt} the client is given is looked at here,
 * and anything but {@code config} is handed straight back - Forge's handler sends a command on to the server when the
 * event it posts is cancelled with nothing thrown, exactly as typed. {@link #answeredHere} is the one rule, and a test
 * asks it.
 *
 * <p>
 * <strong>Opened on the next tick, never at once.</strong> A command is run while the chat screen is still up, and the
 * chat closes itself once its line is sent: a screen opened from the command would be shut by that close before it was
 * drawn. So the command and the key only ask, and the tick opens the screen once nothing is in the way.
 */
public final class SettingsAccess {

    /** The key's name in the controls screen and the language file. */
    public static final String KEY_NAME = "key.trmtgtnh.settings";

    /** The key's group in the controls screen: the mod's own. */
    public static final String KEY_CATEGORY = "key.category.trmtgtnh.keys";

    /** The command's words after the slash: {@code /trmt config}. */
    public static final String ROOT = "trmt";

    public static final String CONFIG = "config";

    /** The one instance Forge's event bus is given. */
    public static final SettingsAccess EVENTS = new SettingsAccess();

    private static KeyBinding key;

    private static volatile boolean asked;

    private SettingsAccess() {}

    /** The key, the command and the handler that hands every other {@code /trmt} on, once, as the client starts. */
    public static void register() {
        key = new KeyBinding(KEY_NAME, Keyboard.KEY_NONE, KEY_CATEGORY);
        ClientRegistry.registerKeyBinding(key);
        ClientCommandHandler.instance.registerCommand(new Command());
        MinecraftForge.EVENT_BUS.register(EVENTS);
    }

    /**
     * Whether the words after {@code /trmt} are answered on this client: {@code config}, and nothing else. Every other
     * {@code /trmt} - with no words, or with the server's - goes on to the server as it was typed.
     */
    static boolean answeredHere(String[] words) {
        return words != null && words.length >= 1 && CONFIG.equalsIgnoreCase(words[0].trim());
    }

    /** Every {@code /trmt} the client is given passes here first; anything not answered here is handed back. */
    @SubscribeEvent
    public void onCommand(CommandEvent event) {
        if (event.command instanceof Command && !answeredHere(event.parameters)) event.setCanceled(true);
    }

    /** The client tick: a press of the key, or the command's ask, opens the settings once nothing is open. */
    public static void tick(Minecraft client) {
        if (key != null) {
            while (key.isPressed()) {
                if (client.currentScreen == null) asked = true;
            }
        }
        if (!asked) return;
        GuiScreen open = client.currentScreen;
        // The chat that ran the command closes itself first; the screen waits for it.
        if (open instanceof GuiChat) return;
        asked = false;
        // Anything else up is somebody's: a key press or a command never covers it.
        if (open != null) return;
        client.displayGuiScreen(ConfigScreens.open(null));
    }

    /** {@code /trmt config}, on the client. */
    static final class Command extends CommandBase {

        @Override
        public String getCommandName() {
            return ROOT;
        }

        @Override
        public String getCommandUsage(ICommandSender sender) {
            return "/" + ROOT + " " + CONFIG;
        }

        @Override
        public int getRequiredPermissionLevel() {
            return 0;
        }

        /** Every player opens their own settings; the server's words are the server's to refuse. */
        @Override
        public boolean canCommandSenderUseCommand(ICommandSender sender) {
            return true;
        }

        @Override
        public void processCommand(ICommandSender sender, String[] words) {
            // Only reached for config: every other /trmt was handed back in onCommand.
            asked = true;
        }

        /** {@code config} offered beside the server's own words, which the chat asks the server for as well. */
        @SuppressWarnings("rawtypes")
        @Override
        public List addTabCompletionOptions(ICommandSender sender, String[] words) {
            return words.length == 1 ? getListOfStringsMatchingLastWord(words, CONFIG) : null;
        }
    }
}
