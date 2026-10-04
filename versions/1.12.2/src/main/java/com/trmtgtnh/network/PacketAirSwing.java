package com.trmtgtnh.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.trmtgtnh.item.AirSwingTool;
import com.trmtgtnh.item.TamperEvents;
import com.trmtgtnh.util.MainThread;

import io.netty.buffer.ByteBuf;

/**
 * Client to server: the tool in my hand was swung at nothing.
 *
 * <p>
 * The gesture 1.7.10 does not have an event for. {@code PlayerInteractEvent.LEFT_CLICK_BLOCK} is
 * posted from {@code ItemInWorldManager.onBlockClicked} and therefore only ever when a block was
 * hit; a swing at sky reaches the server as an animation and nothing more. So the client watches
 * its own crosshair at the moment vanilla decides what the click meant, and says so here when the
 * answer was "nothing".
 *
 * <p>
 * Carries no payload on purpose. Everything that matters is re-read on the server from the server's
 * own player: which item is held, whether the sender is sneaking, and whether they are allowed to
 * do this at all - the last of those inside the tool's own {@code leftClick}, which is the same
 * code the block gesture goes through. The packet is trusted for exactly one thing, that a
 * left-click happened, and a client can already say that by swinging at a block.
 */
public class PacketAirSwing implements IMessage {

    @Override
    public void fromBytes(ByteBuf buf) {}

    @Override
    public void toBytes(ByteBuf buf) {}

    public static class Handler implements IMessageHandler<PacketAirSwing, IMessage> {

        @Override
        public IMessage onMessage(PacketAirSwing message, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().player;
            if (player == null) return null;

            MainThread.onServer(new Runnable() {

                @Override
                public void run() {
                    // Re-read rather than trust: the stack may have changed since the swing, and
                    // only the two tools that opted in have anything to do without a square.
                    ItemStack held = player.getHeldItemMainhand();
                    if (held == null || !(held.getItem() instanceof AirSwingTool)) return;
                    if (!TamperEvents.get()
                        .airOffCooldown(player)) return;

                    // The player's own feet. Both tools that get here ignore the position - it is
                    // passed because the contract takes one, and this is the only honest answer to
                    // "where" for a click that landed nowhere.
                    int x = MathHelper.floor(player.posX);
                    int y = MathHelper.floor(player.posY);
                    int z = MathHelper.floor(player.posZ);

                    ((AirSwingTool) held.getItem()).leftClick(player.world, x, y, z, player, held, player.isSneaking());
                }
            });
            return null;
        }
    }
}
