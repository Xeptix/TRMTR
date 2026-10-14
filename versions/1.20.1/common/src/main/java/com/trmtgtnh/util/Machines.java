package com.trmtgtnh.util;

import java.util.function.Predicate;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Whether a player is a machine standing in for one - a dispenser, a block breaker, an automated tamper.
 *
 * <p>
 * Both older editions ask whether the player is Forge's {@code FakePlayer}, which the shared module cannot name. A
 * machine's player usually has no connection, which is what this edition asked alone until 0.9.222 - in five places,
 * each its own copy - and a machine whose player is given a connection anyway passed as a person: it earned experience
 * for mending, which its mending enchantment then spent on the tool (spec WD59), and was told things. So the connection
 * is asked here, once, and the loader's own word as well: Forge hands over its {@code FakePlayer} test; Fabric has no
 * fake player of its own, and its mods' stand-ins are caught by the connection.
 */
public final class Machines {

    /** The loader's own word, beyond the connection; none until a loader says. */
    private static volatile Predicate<Player> loaderSays = player -> false;

    private Machines() {}

    /** Tells this how the loader names a machine's player. Called once by the loader module that has one. */
    public static void use(Predicate<Player> test) {
        loaderSays = test == null ? player -> false : test;
    }

    /** True for anything that is not a person playing: no player at all, a client one, or a machine's. */
    public static boolean is(Player player) {
        if (!(player instanceof ServerPlayer) || ((ServerPlayer) player).connection == null) return true;
        try {
            return loaderSays.test(player);
        } catch (RuntimeException awkward) {
            // Asked defensively: an answer that throws is no reason to treat a person as a machine.
            return false;
        }
    }
}
