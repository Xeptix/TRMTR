package com.trmtgtnh.network;

import net.minecraft.entity.player.EntityPlayerMP;

import com.trmtgtnh.util.MainThread;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Client to server: "the surface table you are using is not the one I built, so send it."
 *
 * <p>
 * Sent at most once for each fingerprint a client meets, and only by a client that has seen the
 * server name one. The fingerprint it carries is the one it was asking about, which lets the server
 * skip a player it has already sent that very table to.
 */
public class PacketSurfaceTableRequest implements IMessage {

    private long fingerprint;

    public PacketSurfaceTableRequest() {}

    public PacketSurfaceTableRequest(long fingerprint) {
        this.fingerprint = fingerprint;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        fingerprint = buf.readableBytes() >= 8 ? buf.readLong() : 0L;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(fingerprint);
    }

    public static class Handler implements IMessageHandler<PacketSurfaceTableRequest, IMessage> {

        @Override
        public IMessage onMessage(final PacketSurfaceTableRequest message, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;
            // On the server thread, because the table is built and published there and encoding it
            // reads the published fields as one piece.
            MainThread.onServer(new Runnable() {

                @Override
                public void run() {
                    TrmtNetwork.sendSurfaceTable(player);
                }
            });
            return null;
        }
    }
}
