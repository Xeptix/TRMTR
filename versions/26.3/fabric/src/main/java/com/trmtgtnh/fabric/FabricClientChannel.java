package com.trmtgtnh.fabric;

import io.netty.buffer.Unpooled;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.network.Packets;
import com.trmtgtnh.network.Payload;

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
            // The type was registered, both ways, by the common half (FabricChannel.open); the payload holds its own
            // copy of the bytes, so the work can wait for the client thread.
            ClientPlayNetworking.registerGlobalReceiver(Payload.typeOf(entry), (payload, context) -> {
                context.client()
                    .execute(() -> entry.accept(payload.buffer(), null));
            });
        }

        FabricChannel.useClientSender(message -> {
            Packets.Entry<?> entry = FabricChannel.entryFor(message.getClass());
            if (entry == null) {
                Trmt.LOG.warn("No address for {}, so it was not sent", message.getClass().getName());
                return;
            }
            ClientPlayNetworking.send(Payload.of(entry, message));
        });

        Trmt.LOG.info("Opened the Fabric client channel, listening for {} message(s)", listening);
    }
}
