package com.trmtgtnh.fabric;

import io.netty.buffer.Unpooled;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.network.Packets;

/**
 * This mod's channel, under Fabric. The client half.
 *
 * <p>
 * Kept apart from {@link FabricChannel} because everything in it names
 * {@code ClientPlayNetworking}, which does not exist on a dedicated server - so a server that loaded
 * this file would fail on startup over a class it has no use for. Forge needs no such split, because
 * its {@code SimpleChannel} is safe to name from either side.
 *
 * <p>
 * Two jobs: receive the messages a client receives, and give the server half a way to send to the
 * server, which it cannot build for itself.
 *
 * <p>
 * A message arriving here has no sender to name - the server is not a player - so the handler is
 * passed null, which is what {@code Receiver} says that means.
 */
public final class FabricClientChannel {

    private FabricClientChannel() {}

    /** Opens the client half. Called once from the client entry point. */
    public static void open() {
        int listening = 0;
        for (Packets.Entry<?> each : Packets.all()) {
            if (each.to != Packets.To.CLIENT) continue;
            listening++;
            final Packets.Entry<?> entry = each;
            ClientPlayNetworking.registerGlobalReceiver(FabricChannel.addressOf(entry), (client, handler, buf, responder) -> {
                // Copied for the same reason the server half copies: the buffer is the network
                // thread's and is released when this returns, and the work below waits for the
                // client thread.
                final io.netty.buffer.ByteBuf held = buf.copy();
                client.execute(() -> entry.accept(held, null));
            });
        }

        FabricChannel.useClientSender(message -> {
            Packets.Entry<?> entry = FabricChannel.entryFor(message.getClass());
            if (entry == null) {
                Trmt.LOG.warn("No address for {}, so it was not sent", message.getClass().getName());
                return;
            }
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            message.toBytes(buf);
            ClientPlayNetworking.send(FabricChannel.addressOf(entry), buf);
        });

        Trmt.LOG.info("Opened the Fabric client channel, listening for {} message(s)", listening);
    }
}
