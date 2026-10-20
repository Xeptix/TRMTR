package com.trmtgtnh.neoforge;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.network.Message;
import com.trmtgtnh.network.Packets;
import com.trmtgtnh.network.Payload;
import com.trmtgtnh.network.TrmtNetwork;

/**
 * This mod's channel, under NeoForge.
 *
 * <p>
 * <strong>Not the Forge module's one channel with a number per message.</strong> NeoForge has no simple channel at
 * this version: from 1.20.5 a custom packet is a typed payload registered with the loader, as on Fabric, so this does
 * what FabricChannel does - each message in {@link Packets} is a {@link Payload} type of its own, named by the
 * message's own 1.20.1 channel name ({@code trmtgtnh:<name>}), its bytes written and read by the message exactly as
 * there. The ids {@link Packets} also carries are the Forge jar's wire format and are not used here.
 *
 * <p>
 * One class for both directions, as the Forge module's is: NeoForge registers both directions in one event that fires
 * on either side, and its distributor is safe to name from either. Only the client's way of asking whether its server
 * has the channel names a client class, and that is kept in a class of its own ({@link ToServer}) which a dedicated
 * server never loads.
 *
 * <p>
 * <strong>A client need not find the channel on its server, and a server need not find it on its client.</strong>
 * Every type is registered {@code optional}, so a client carrying the mod may join a server without it, as 1.20.1 let
 * it: nothing is sent to it and nothing is drawn. A client missing this mod cannot join a server that has it whatever
 * the channel says - NeoForge's registry sync refuses a client without the server's blocks - so the Forge jar's rule,
 * that a server requires the channel of its clients, holds here without being said. NeoForge refuses to send a payload
 * down a connection that has not got its channel, by exception, so every send asks first and a send the other end
 * could not read is simply not made.
 */
public final class NeoForgeChannel {

    /**
     * Which entry carries a given message, by its class.
     *
     * <p>
     * Built once rather than walked per send. The busiest caller is a delta sent as ground wears under somebody
     * walking, and {@code Packets.all()} builds a fresh list each time it is asked.
     */
    private static final Map<Class<?>, Packets.Entry<?>> BY_TYPE = new HashMap<Class<?>, Packets.Entry<?>>();

    /** The version every payload type is registered under, the Forge channel's number, as text. */
    private static final String VERSION = "1";

    private NeoForgeChannel() {}

    /**
     * Opens the channel: every payload type when NeoForge asks for them, and the way out now. Called once as the mod
     * starts, with the mod's own bus, on which the registration event is posted.
     */
    public static void open(IEventBus modBus) {
        for (Packets.Entry<?> each : Packets.all()) {
            BY_TYPE.put(each.type, each);
        }
        modBus.addListener(NeoForgeChannel::register);

        TrmtNetwork.use(new TrmtNetwork.Channel() {

            @Override
            public void sendTo(Message message, ServerPlayer player) {
                Payload payload = payloadOf(message);
                if (payload == null || player.connection == null) return;
                // A player whose client has not got this mod's channel - a vanilla client, or one without the mod -
                // is sent nothing, where NeoForge would otherwise throw.
                if (!player.connection.hasChannel(payload.type())) return;
                PacketDistributor.sendToPlayer(player, payload);
            }

            @Override
            public void sendToServer(Message message) {
                Payload payload = payloadOf(message);
                if (payload != null) ToServer.send(payload);
            }
        });
    }

    /**
     * Every message, as a payload type in the direction it travels, each with its handler.
     *
     * <p>
     * The handler runs on the network thread, so the work is enqueued for the game's thread, as the Forge module's
     * consumer does and as every edition's handlers expect; the payload holds its own copy of the bytes, so it can
     * wait. A server-bound message is handed its sender; a client-bound one is handed no one, as on Fabric.
     */
    private static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(Trmt.MODID)
            .versioned(VERSION)
            .optional();
        int count = 0;
        for (Packets.Entry<?> each : Packets.all()) {
            final Packets.Entry<?> entry = each;
            CustomPacketPayload.Type<Payload> type = Payload.typeOf(entry);
            if (entry.to == Packets.To.SERVER) {
                registrar.playToServer(
                    type,
                    Payload.codec(type),
                    (payload, context) -> context.enqueueWork(
                        () -> entry.accept(
                            payload.buffer(),
                            context.player() instanceof ServerPlayer ? (ServerPlayer) context.player() : null)));
            } else {
                registrar.playToClient(
                    type,
                    Payload.codec(type),
                    (payload, context) -> context.enqueueWork(() -> entry.accept(payload.buffer(), null)));
            }
            count++;
        }
        Trmt.LOG.info("Opened the NeoForge channel with {} message(s)", Integer.valueOf(count));
    }

    /** The payload a message travels as, or null, said, for a message no entry carries. */
    private static Payload payloadOf(Message message) {
        Packets.Entry<?> entry = BY_TYPE.get(message.getClass());
        if (entry == null) {
            Trmt.LOG.warn("No address for {}, so it was not sent", message.getClass().getName());
            return null;
        }
        return Payload.of(entry, message);
    }

    /**
     * How a client sends to its server, and the one place this class names the client: a class of its own so a
     * dedicated server, which never sends to a server, never loads it.
     */
    private static final class ToServer {

        private ToServer() {}

        static void send(Payload payload) {
            net.minecraft.client.multiplayer.ClientPacketListener connection = net.minecraft.client.Minecraft
                .getInstance()
                .getConnection();
            // No connection, or a server without this mod's channel: nothing is sent, where NeoForge would throw.
            if (connection == null || !connection.hasChannel(payload.type())) return;
            net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(payload);
        }
    }
}
