package com.trmtgtnh.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.trmtgtnh.item.ReinforceGestures;
import com.trmtgtnh.util.MainThread;

import io.netty.buffer.ByteBuf;

/**
 * Client to server: a new press of the attack key began on this block, with a chunk tamper in reinforce mode (RL19,
 * 0.9.229).
 *
 * <p>
 * 1.7.10's client sends one left-click a press, so its server takes each click it hears as a press. This edition's
 * client sends one every tick the key is held, and only the client can tell a held key from a new press: in its own
 * ticks a held key is a click on every one, and a release is a tick with none, however the server hears them. A
 * client that stalls while the server ticks on sends one press's clicks several server ticks apart, and a gap the
 * server measures cannot tell that from two deliberate clicks. So the client says where each press begins, once a
 * press, and the server takes a press only where a click it heard meets one of these.
 *
 * <p>
 * Trusted for that alone: what the press does is decided on the server from a left-click the server heard, which
 * has passed every check a click passes, claim protection included. A client can already make as many presses as it
 * likes by clicking.
 */
public class PacketTamperPress implements IMessage {

    /**
     * The first protocol that carries this message, 3 (0.9.229). A client sends it only to a server whose rules named
     * this protocol or a later one ({@link TrmtNetwork#sendTamperPress}); it stays 3 when the protocol steps on.
     */
    public static final int SINCE_PROTOCOL = 3;

    private int x;
    private int y;
    private int z;

    public PacketTamperPress() {}

    public PacketTamperPress(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        x = buf.readInt();
        y = buf.readInt();
        z = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
    }

    public static class Handler implements IMessageHandler<PacketTamperPress, IMessage> {

        @Override
        public IMessage onMessage(final PacketTamperPress message, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().player;
            if (player == null) return null;

            // Handed to the end of the tick as the other handlers are. The press does not mind arriving behind its
            // click: ReinforceGestures pairs the two in whichever order they come.
            MainThread.onServer(new Runnable() {

                @Override
                public void run() {
                    ReinforceGestures.pressBegan(player, message.x, message.y, message.z);
                }
            });
            return null;
        }
    }
}
