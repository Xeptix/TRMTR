package com.trmtgtnh.fabric;

import java.util.HashMap;
import java.util.Map;

import io.netty.buffer.Unpooled;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.network.Message;
import com.trmtgtnh.network.Packets;
import com.trmtgtnh.network.Payload;
import com.trmtgtnh.network.TrmtNetwork;

/**
 * This mod's channel, under Fabric. The server half.
 *
 * <p>
 * Fabric has no channel object: each message type is addressed by an {@code Identifier} of its
 * own, built here from the name {@link Packets} gives it. The ids that list also carries are Forge's
 * business and are ignored on this side - which is the whole reason one list holds both.
 *
 * <p>
 * <strong>Split in two, which Forge does not need.</strong> Receiving a client-bound message, and
 * sending one to the server, both go through {@code ClientPlayNetworking} - a class that does not
 * exist on a dedicated server, so naming it from a file the server loads would be a crash on
 * startup. So this file does the half a server does, and {@link FabricClientChannel} does the half a
 * client does and hands its sender back here.
 */
public final class FabricChannel {

    /** How a client sends to its server. Supplied by the client half; absent on a server. */
    public interface ToServer {

        void send(Message message);
    }

    private static volatile ToServer toServer;

    private static volatile boolean complained;

    /**
     * Which entry carries a given message, by its class.
     *
     * <p>
     * Built once rather than walked per send. The busiest caller is a delta sent as ground wears
     * under somebody walking, and {@code Packets.all()} builds a fresh list each time it is asked.
     */
    private static final Map<Class<?>, Packets.Entry<?>> BY_TYPE = new HashMap<Class<?>, Packets.Entry<?>>();

    private FabricChannel() {}

    /** The address this message type is sent to, which is what Fabric carries instead of a number. */
    public static Identifier addressOf(Packets.Entry<?> entry) {
        return Identifier.fromNamespaceAndPath(Trmt.MODID, entry.name);
    }

    /** Opens the server half: every message a server receives, and the way it sends. */
    public static void open() {
        int listening = 0;
        for (Packets.Entry<?> each : Packets.all()) {
            BY_TYPE.put(each.type, each);
            // From 1.20.5 every payload's type is registered before anything is sent, on both sides, in the direction
            // it travels: each message is its own type on its own 1.20.1 channel name (see Payload).
            net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<Payload> type = Payload.typeOf(each);
            // playC2S and playS2C are serverboundPlay and clientboundPlay from Fabric API 0.155 (26.1).
            if (each.to == Packets.To.SERVER) {
                net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.serverboundPlay()
                    .register(type, Payload.codec(type));
            } else {
                net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay()
                    .register(type, Payload.codec(type));
            }
            if (each.to != Packets.To.SERVER) continue;
            listening++;
            final Packets.Entry<?> entry = each;
            ServerPlayNetworking.registerGlobalReceiver(type, (payload, context) -> {
                // The payload holds its own copy of the bytes, so the work can wait for the server thread as
                // every edition's does; already on it, execute runs it at once.
                context.server()
                    .execute(() -> entry.accept(payload.buffer(), context.player()));
            });
        }

        TrmtNetwork.use(new TrmtNetwork.Channel() {

            @Override
            public void sendTo(Message message, ServerPlayer player) {
                Packets.Entry<?> entry = BY_TYPE.get(message.getClass());
                if (entry == null) {
                    Trmt.LOG.warn("No address for {}, so it was not sent", message.getClass().getName());
                    return;
                }
                ServerPlayNetworking.send(player, Payload.of(entry, message));
            }

            @Override
            public void sendToServer(Message message) {
                ToServer out = toServer;
                if (out == null) {
                    if (!complained) {
                        complained = true;
                        Trmt.LOG.warn(
                            "Something tried to send to the server from a side that has no client "
                                + "half. On a dedicated server nothing should ever ask; this is said "
                                + "once.");
                    }
                    return;
                }
                out.send(message);
            }
        });

        Trmt.LOG.info("Opened the Fabric channel, listening for {} message(s)", listening);
    }

    /** The client half telling this how a client sends. Called by {@link FabricClientChannel}. */
    public static void useClientSender(ToServer out) {
        toServer = out;
    }

    /** Every entry by payload type, for the client half, which addresses messages the same way. */
    public static Packets.Entry<?> entryFor(Class<?> type) {
        return BY_TYPE.get(type);
    }
}
