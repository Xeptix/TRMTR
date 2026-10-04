package com.trmtgtnh.item;

import java.util.WeakHashMap;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import com.trmtgtnh.config.TrmtConfig;

/**
 * The tamper's left-click gestures, which have no item hook on either version.
 *
 * <p>
 * Registered on the Forge bus on <em>both</em> sides even though only one handler here is about
 * the server, because {@link #onBreakSpeed} has to reach the client to do its job. See its own
 * note for why.
 *
 * <p>
 * Its own class rather than another handful of methods on {@code ServerEvents}, because that
 * class is about wiring the erosion engine to the bus and this is about one item - and because
 * a client-side handler living in something called {@code ServerEvents} would be a lie a reader
 * has to discover.
 */
public final class TamperEvents {

    private static final TamperEvents INSTANCE = new TamperEvents();

    /**
     * When each player's last swing at nothing landed.
     *
     * <p>
     * Separate from the position-keyed cooldown and keyed on time alone, because the thing that
     * map keys on - a new square being a new gesture - has no meaning for a click that landed on
     * no square at all.
     */
    private final WeakHashMap<EntityPlayer, Integer> lastAirGesture = new WeakHashMap<EntityPlayer, Integer>();

    /**
     * Where and when each player's last left-click gesture landed: tick, x, y, z.
     *
     * <p>
     * Weak so a disconnected player drops out without any bookkeeping. Keyed on position as well
     * as time on purpose - a held button repeating on one square is the thing being throttled,
     * while a held button dragged across new squares is somebody painting a path, and that is
     * the gesture the tool exists for.
     */
    private final WeakHashMap<EntityPlayer, int[]> lastGesture = new WeakHashMap<EntityPlayer, int[]>();

    private TamperEvents() {}

    public static TamperEvents get() {
        return INSTANCE;
    }

    /**
     * Left-click, and sneak + left-click.
     *
     * <p>
     * Acted on server side and nowhere else, and here that guard is load-bearing in a way it was
     * only precautionary in the other edition. 1.7.10's client left-click path runs from
     * {@code Minecraft.func_147116_af} straight into {@code PlayerControllerMP.clickBlock} without
     * posting anything, so there this event has one fire site and it is the server's;
     * {@code ForgeHooks.onLeftClickBlock} is called from both sides here, so every gesture arrives
     * twice and the client's copy must be dropped rather than acted on. What the client does about
     * the click is {@link #onBreakSpeed}'s business.
     *
     * <p>
     * Cancelled whether or not anything happened, and on both sides. Cancelling is what makes the
     * server send the block straight back and never begin destroying it, and a gesture that found
     * nothing to do still must not be allowed to turn into a dig.
     *
     * <p>
     * Subscribed at the default priority so that every claim-protection mod in the pack vetoes
     * this for free: a cancelled cancelable event is not delivered onward, and the protection mods
     * all cancel at a higher priority than this.
     */
    @SubscribeEvent
    public void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        EntityPlayer player = event.getEntityPlayer();
        if (player == null) return;

        // The stack in the hand the event names, which for a left click is always the main one -
        // and that is the right stack to read under Backhand precisely because Backhand swaps
        // which slot the main hand holds.
        ItemStack held = event.getItemStack();
        if (held.isEmpty() || !(held.getItem() instanceof TamperTool)) return;

        event.setCanceled(true);
        if (event.getWorld() == null || event.getWorld().isRemote) return;
        // Robots and drones hold items and click blocks too. Only somebody with a connection has
        // a hotbar this means anything in.
        if (!(player instanceof EntityPlayerMP) || player instanceof FakePlayer) return;

        BlockPos pos = event.getPos();
        // The reinforce tool wants both taps of a deliberate double-click, so it is not put
        // through the repeat throttle - which exists to tell a held button from a press and
        // would otherwise swallow the second tap.
        boolean reinforcing = held.getItem() instanceof ItemChunkTamper && ItemChunkTamper.reinforceActive(held);
        if (!reinforcing && !offCooldown(player, pos.getX(), pos.getY(), pos.getZ())) return;

        // What the gesture means is the tool's business: a square for the hand tools, a cube
        // for the big ones. Tested against the interface rather than a class, because the two
        // hierarchies do not share one and the version that tested for ItemTamper quietly left
        // the chunk tamper and the Wayfarer's out of both halves of this wiring - no gesture,
        // and nothing stopping a left-click turning into a dig.
        ((TamperTool) held.getItem())
            .leftClick(event.getWorld(), pos.getX(), pos.getY(), pos.getZ(), player, held, player.isSneaking());
    }

    /**
     * Nothing is dug while a tamper is out.
     *
     * <p>
     * The client is the side that matters here. With the break speed at zero it never completes
     * a dig, so it never removes the block from its own copy of the world and never re-sends the
     * packet that would repeat the gesture - one press gives one gesture, and moving the
     * crosshair to the next square gives exactly one more, which is what laying out a path
     * wants. On the server the same answer stops a click being read as an instant harvest.
     *
     * <p>
     * Unconditional, and not gated on the mod being enabled. A tool that dug ground while the
     * mod was switched off and refused to while it was on would be the strangest thing about it.
     */
    @SubscribeEvent
    public void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (event.getEntityPlayer() == null) return;
        ItemStack held = event.getEntityPlayer()
            .getHeldItemMainhand();
        if (!held.isEmpty() && held.getItem() instanceof TamperTool) event.setCanceled(true);
    }

    /**
     * True for a new square straight away, and for the same square once the clock has run.
     *
     * <p>
     * Creative is what this is for. There the client re-sends the dig packet every five ticks
     * for as long as the button is held, whatever the block's hardness, and there is no packet
     * at all for letting go - so a clock is the only thing that can tell a press from a hold.
     */
    private boolean offCooldown(EntityPlayer player, int x, int y, int z) {
        int[] last = lastGesture.get(player);
        if (last == null) {
            lastGesture.put(player, new int[] { player.ticksExisted, x, y, z });
            return true;
        }
        boolean sameSquare = last[1] == x && last[2] == y && last[3] == z;
        if (sameSquare && player.ticksExisted - last[0] < TrmtConfig.tamperCooldownTicks) return false;
        last[0] = player.ticksExisted;
        last[1] = x;
        last[2] = y;
        last[3] = z;
        return true;
    }

    /**
     * True once the clock has run since this player's last swing at nothing.
     *
     * <p>
     * Creative is what this guards, because creative is who holds these tools: vanilla's own
     * defence against a mashed button is the ten-tick counter it sets on a dig, and it sets that
     * only for players who are not in creative.
     */
    public boolean airOffCooldown(EntityPlayer player) {
        if (player == null) return false;
        Integer last = lastAirGesture.get(player);
        if (last != null && player.ticksExisted - last.intValue() < TrmtConfig.tamperCooldownTicks) {
            return false;
        }
        lastAirGesture.put(player, Integer.valueOf(player.ticksExisted));
        return true;
    }
}
