package com.trmtgtnh.item;

import java.util.WeakHashMap;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import com.trmtgtnh.config.TrmtConfig;

/**
 * What a left-click with a tamper means, and what stops it being a dig.
 *
 * <p>
 * Carried from the 1.12.2 edition's class of the same name with its two Forge events taken off the
 * front, for the reason {@code ServerEvents} gives: not one argument here names an event class,
 * because the two loaders agree on almost nothing about how a click reaches a mod and agree exactly
 * on what the mod wants to know about it. Each loader's module notices; this decides.
 *
 * <h2>Why the dig has to be refused</h2>
 *
 * <p>
 * Refusing it is what makes the server send the block straight back and never begin destroying it,
 * and a gesture that found nothing to do still must not be allowed to turn into a dig. Both older
 * editions refuse it twice over - once by cancelling the click and once by refusing to let the tool
 * break anything - and so does each loader here, because the two refusals catch different paths.
 *
 * <p>
 * The refusal is asked of the interface rather than of a class. The two tamper hierarchies do not
 * share one, and the version that tested for {@code ItemTamper} quietly left the chunk tamper and
 * the Wayfarer out of both halves of this wiring: no gesture, and nothing stopping a left-click
 * turning into a dig.
 */
public final class TamperEvents {

    private static final TamperEvents INSTANCE = new TamperEvents();

    /**
     * When each player's last swing at nothing landed.
     *
     * <p>
     * Separate from the position-keyed cooldown and keyed on time alone, because the thing that map
     * keys on - a new square being a new gesture - has no meaning for a click that landed on no
     * square at all.
     */
    private final WeakHashMap<Player, Integer> lastAirGesture = new WeakHashMap<Player, Integer>();

    /**
     * Where and when each player's last left-click gesture landed: tick, x, y, z.
     *
     * <p>
     * Weak so a disconnected player drops out without any bookkeeping. Keyed on position as well as
     * time on purpose - a held button repeating on one square is the thing being throttled, while a
     * held button dragged across new squares is somebody painting a path, and that is the gesture
     * the tool exists for.
     */
    private final WeakHashMap<Player, int[]> lastGesture = new WeakHashMap<Player, int[]>();

    private TamperEvents() {}

    public static TamperEvents get() {
        return INSTANCE;
    }

    /**
     * Whether holding this means a left-click is a gesture rather than a dig.
     *
     * <p>
     * Asked by each loader twice: once where a click arrives, and once where the game asks how fast
     * this tool breaks the block it is pointed at.
     */
    public static boolean holdsTamper(ItemStack held) {
        return held != null && !held.isEmpty() && held.getItem() instanceof TamperTool;
    }

    /**
     * A left-click on a block, already refused as a dig by whoever is calling.
     *
     * <p>
     * Server side and nowhere else. Both loaders deliver this on both sides - the client's copy is
     * what swings the arm - and only one of them may act.
     *
     * <p>
     * Robots and drones hold items and click blocks too. Only somebody with a connection has a
     * hotbar this means anything in, which is the question asked here without naming a loader's
     * class for a stand-in; see {@code Notices}, which asks it the same way.
     */
    public void leftClicked(Level world, Player player, int x, int y, int z, ItemStack held) {
        if (world == null || world.isClientSide()) return;
        if (player == null || !holdsTamper(held)) return;
        if (!(player instanceof ServerPlayer) || ((ServerPlayer) player).connection == null) return;

        // The reinforce tool wants both taps of a deliberate double-click, so it is not put through
        // the repeat throttle - which exists to tell a held button from a press and would otherwise
        // swallow the second tap.
        boolean reinforcing = held.getItem() instanceof ItemChunkTamper && ItemChunkTamper.reinforceActive(held);
        if (!reinforcing && !offCooldown(player, x, y, z)) return;

        // What the gesture means is the tool's business: a square for the hand tools, a cube for the
        // big ones.
        ((TamperTool) held.getItem()).leftClick(world, x, y, z, player, held, player.isShiftKeyDown());
    }

    private boolean offCooldown(Player player, int x, int y, int z) {
        int[] last = lastGesture.get(player);
        if (last == null) {
            lastGesture.put(player, new int[] { player.tickCount, x, y, z });
            return true;
        }
        boolean sameSquare = last[1] == x && last[2] == y && last[3] == z;
        if (sameSquare && player.tickCount - last[0] < TrmtConfig.tamperCooldownTicks) return false;
        last[0] = player.tickCount;
        last[1] = x;
        last[2] = y;
        last[3] = z;
        return true;
    }

    public boolean airOffCooldown(Player player) {
        if (player == null) return false;
        Integer last = lastAirGesture.get(player);
        if (last != null && player.tickCount - last.intValue() < TrmtConfig.tamperCooldownTicks) {
            return false;
        }
        lastAirGesture.put(player, Integer.valueOf(player.tickCount));
        return true;
    }
}
