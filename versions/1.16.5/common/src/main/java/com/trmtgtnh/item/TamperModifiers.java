package com.trmtgtnh.item;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.world.entity.player.Player;

import com.trmtgtnh.Client;

/**
 * Whether a player is holding the settings modifier, on the side that has to act on it.
 *
 * <p>
 * Control is not a thing the server knows. Sneaking is synced because vanilla syncs it; every
 * other key a client presses stays on the client, and a right-click packet says where the
 * crosshair was and nothing about the keyboard. So the client tells the server when the key
 * goes down and when it comes back up, and the server remembers.
 *
 * <p>
 * Sent on the transition rather than with the click, and that ordering is the whole reason it
 * works. FML's channels ride inside vanilla custom-payload packets on the same connection, so a
 * message sent a tick before a click arrives before that click - whereas a modifier sent
 * alongside a click would be racing the click it is meant to qualify, and would lose about half
 * the time.
 *
 * <p>
 * Weak keys, so a player who disconnects drops out with no bookkeeping.
 */
public final class TamperModifiers {

    private static final Map<Player, Boolean> HELD = Collections
        .synchronizedMap(new WeakHashMap<Player, Boolean>());

    private TamperModifiers() {}

    /** Called from the packet handler when a client's modifier key changes state. */
    public static void set(Player player, boolean down) {
        if (player == null) return;
        if (down) {
            HELD.put(player, Boolean.TRUE);
        } else {
            HELD.remove(player);
        }
    }

    /**
     * Whether this player's settings modifier is down.
     *
     * <p>
     * The client asks its own keyboard, which is both authoritative and current; the server asks
     * what it was last told. Neither side is trusted with anything but opening a screen.
     */
    public static boolean held(Player player) {
        if (player == null) return false;
        if (player.level != null && player.level.isClientSide()) return Client.modifierHeld();
        return Boolean.TRUE.equals(HELD.get(player));
    }
}
