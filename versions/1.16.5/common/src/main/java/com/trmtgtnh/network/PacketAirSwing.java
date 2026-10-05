package com.trmtgtnh.network;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import com.trmtgtnh.item.AirSwingTool;
import com.trmtgtnh.item.TamperEvents;
import com.trmtgtnh.util.MainThread;

import io.netty.buffer.ByteBuf;

/**
 * Client to server: the tool in my hand was swung at nothing.
 *
 * <p>
 * The gesture no version of the game has an event for. Left-click reaches a tool through a block
 * interaction, which is posted only when a block was actually hit; a swing at sky reaches the server
 * as an animation and nothing more. So the client watches its own crosshair at the moment vanilla
 * decides what the click meant, and says so here when the answer was "nothing".
 *
 * <p>
 * Carries no payload on purpose. Everything that matters is re-read on the server from the server's
 * own player: which item is held, whether the sender is sneaking, and whether they are allowed to do
 * this at all - the last of those inside the tool's own {@code leftClick}, which is the same code
 * the block gesture goes through. The packet is trusted for exactly one thing, that a left-click
 * happened, and a client can already say that by swinging at a block.
 *
 * <p>
 * <strong>This was the one message this port deferred, and what it waited for was the asking rather
 * than the answering.</strong> Both older editions notice the swing from an item method that Forge
 * adds - {@code onEntitySwing} - and Fabric has nothing of the sort, so the question could not be
 * asked from a module both loaders share. What it is asked from now is the client's own attack
 * handling, which is vanilla's and is the same on both: see {@code AirSwing}.
 */
public class PacketAirSwing implements Message {

    @Override
    public void fromBytes(ByteBuf buf) {}

    @Override
    public void toBytes(ByteBuf buf) {}

    public static class Handler implements Receiver<PacketAirSwing> {

        @Override
        public void onMessage(PacketAirSwing message, final ServerPlayer from) {
            if (from == null) return;

            MainThread.onServer(new Runnable() {

                @Override
                public void run() {
                    // Re-read rather than trust: the stack may have changed since the swing, and
                    // only the two tools that opted in have anything to do without a square.
                    ItemStack held = from.getMainHandItem();
                    if (held == null || !(held.getItem() instanceof AirSwingTool)) return;
                    if (!TamperEvents.get()
                        .airOffCooldown(from)) return;

                    // The player's own feet. Both tools that get here ignore the position - it is
                    // passed because the contract takes one, and this is the only honest answer to
                    // "where" for a click that landed nowhere.
                    int x = net.minecraft.util.Mth.floor(from.getX());
                    int y = net.minecraft.util.Mth.floor(from.getY());
                    int z = net.minecraft.util.Mth.floor(from.getZ());

                    ((AirSwingTool) held.getItem())
                        .leftClick(from.level, x, y, z, from, held, from.isShiftKeyDown());
                }
            });
        }
    }
}
