package com.trmtgtnh.network;

import net.minecraft.entity.player.EntityPlayerMP;

import com.trmtgtnh.util.MainThread;

import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Client to server: "I have the mod, and here is whether I want to see the overlay."
 *
 * <p>
 * The server sends nothing to a player who has not said this, which is what makes the
 * per-client toggle cost the server nothing while it is off. Not what makes the mod optional
 * on a client, though: a client without it cannot join a server that has it at all, because
 * Forge refuses a client missing a server's blocks and items before any of this is sent.
 */
public class PacketHello implements IMessage {

    private boolean wantsOverlays;

    /**
     * The protocol the sender speaks (0.9.223): written after the switch, so an older server reads the switch and
     * leaves this unread, and read as 1 where a hello from an older client ends at the switch. See
     * TrmtNetwork.PROTOCOL.
     */
    private int protocol = 1;

    public PacketHello() {}

    public PacketHello(boolean wantsOverlays) {
        this.wantsOverlays = wantsOverlays;
        this.protocol = TrmtNetwork.PROTOCOL;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        wantsOverlays = buf.readBoolean();
        protocol = buf.readableBytes() >= 4 ? buf.readInt() : 1;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(wantsOverlays);
        buf.writeInt(protocol);
    }

    public static class Handler implements IMessageHandler<PacketHello, IMessage> {

        @Override
        public IMessage onMessage(PacketHello message, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().player;
            if (player == null) return null;
            final boolean wants = message.wantsOverlays;

            // Only what was asked is recorded here. In 1.7.10 this handler runs on the server thread,
            // straight from the connection's queue; the answer, and everything sent because of it, is
            // handed to the end of the tick, in the same line as a reload or /trmt enable handed over
            // from another thread. See TrmtNetwork.answerHello for why it is not decided here.
            TrmtNetwork.rememberWanted(player, wants);
            TrmtNetwork.rememberProtocol(player, message.protocol);
            MainThread.onServer(new Runnable() {

                @Override
                public void run() {
                    TrmtNetwork.answerHello(player);
                }
            });
            return null;
        }

    }
}
