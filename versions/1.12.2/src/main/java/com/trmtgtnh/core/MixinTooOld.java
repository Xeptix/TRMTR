package com.trmtgtnh.core;

import java.util.List;

import net.minecraftforge.fml.common.DummyModContainer;
import net.minecraftforge.fml.common.LoadController;
import net.minecraftforge.fml.common.ModMetadata;

import com.google.common.eventbus.EventBus;

/**
 * Stops a game whose Mixin is older than this mod's mixins apply under, and says why (G17, Codex's review of R5,
 * 2026-11-11).
 *
 * <p>
 * A pack whose first Mixin on the class path is of the 0.7 line - a mod carrying its own copy, loading ahead of every
 * newer one - cannot load this mod's mixin config at all: its config plugin takes ASM's own tree classes, which 0.7 had
 * relocated. Carrying on would start a game with every mixin of this mod missing, which looks like a broken mod rather
 * than a refusal. So where {@link TrmtLoadingPlugin} finds such a Mixin it hands FML this container, and FML's first
 * look at it - {@link #registerBus}, while it builds the mod list, before any mod is constructed - stops the game with
 * {@link TrmtLoadingPlugin#tooOldLines()}: on a client on Forge's own error screen, the screen a missing mod is shown
 * on, and on a server as the reason the server would not start.
 */
public final class MixinTooOld extends DummyModContainer {

    /** The client's screen, named rather than loaded, so this common class reaches for no client class. */
    static final String SCREEN = "com.trmtgtnh.client.MixinTooOldScreen";

    public MixinTooOld() {
        super(metadata());
    }

    private static ModMetadata metadata() {
        ModMetadata md = new ModMetadata();
        md.modId = MixinBooterWanted.ID;
        md.name = "TRMT: Reimagined";
        md.version = com.trmtgtnh.Tags.VERSION;
        md.description = "Stops a game whose Mixin is older than this mod's mixins apply under.";
        return md;
    }

    @Override
    public boolean registerBus(EventBus bus, LoadController controller) {
        throw refusal(client(), TrmtLoadingPlugin.tooOldLines());
    }

    /** Nothing to construct. */
    @Override
    public boolean isImmutable() {
        return true;
    }

    private static boolean client() {
        try {
            return net.minecraftforge.fml.relauncher.FMLLaunchHandler.side()
                .isClient();
        } catch (Throwable noSide) {
            return false;
        }
    }

    /**
     * What stops the game: on a client, Forge's own error screen with the lines on it; on a server, or where the screen
     * cannot be made, the lines as the reason.
     */
    static RuntimeException refusal(boolean client, List<String> lines) {
        String message = String.join(" ", lines);
        if (client) {
            try {
                return (RuntimeException) Class.forName(SCREEN)
                    .getConstructor(String.class, List.class)
                    .newInstance(message, lines);
            } catch (Throwable unmade) {
                // The reason, still, as the server is given it.
            }
        }
        return new IllegalStateException(message);
    }
}
