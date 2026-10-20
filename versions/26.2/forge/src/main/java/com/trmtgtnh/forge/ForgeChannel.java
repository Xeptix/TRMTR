package com.trmtgtnh.forge;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.network.Message;
import com.trmtgtnh.network.Packets;
import com.trmtgtnh.network.TrmtNetwork;

/**
 * This mod's channel, under Forge.
 *
 * <p>
 * Forge carries every message on one channel and tells them apart by a number, so this walks
 * {@link Packets} and hands each entry over with the id that list gives it. The ids are a wire
 * format, which is why they are written out there rather than taken from a position - see that
 * class.
 *
 * <p>
 * One class for both directions, which Fabric cannot manage: {@code SimpleChannel} is safe to name
 * from either side, so a dedicated server loads this file and simply never calls
 * {@code sendToServer}. The Fabric module needs its client half kept separately because the API it
 * would have to name there does not exist on a server.
 *
 * <p>
 * <strong>A server must find the channel on its clients; a client need not find it on its server.</strong>
 * A client missing this mod cannot join a server that has it whatever the channel says - Forge's
 * registry handshake refuses a client without the server's blocks - so the server's side stays
 * {@code equals}, which only makes that refusal earlier and clearer. A client carrying the mod onto a
 * server without it is another matter, and the 1.7.10 edition lets it in: nothing is sent to it and
 * nothing is drawn. Until 0.9.219 both comparisons here were {@code equals}, and such a client was
 * turned away at the door, while the manual said it was fine.
 */
public final class ForgeChannel {

    private static final int VERSION = 1;

    private static SimpleChannel channel;

    private ForgeChannel() {}

    /** Opens the channel and registers every message on it. Called once as the mod starts. */
    public static void open() {
        // From 1.20.2 Forge builds a channel from a builder and numbers its version; the same two rules as 1.20.1's.
        channel = ChannelBuilder.named(Identifier.fromNamespaceAndPath(Trmt.MODID, "main"))
            .networkProtocolVersion(VERSION)
            // What a client accepts of its server: this version, or a server without the channel.
            .clientAcceptedVersions(Channel.VersionTest.exact(VERSION)
                .or(Channel.VersionTest.ACCEPT_MISSING)
                .or(Channel.VersionTest.ACCEPT_VANILLA))
            // What a server accepts of its clients: this version only.
            .serverAcceptedVersions(Channel.VersionTest.exact(VERSION))
            .simpleChannel();

        for (Packets.Entry<?> each : Packets.all()) {
            register(each);
        }
        // A simple channel is built once every message is on it.
        channel.build();

        TrmtNetwork.use(new TrmtNetwork.Channel() {

            @Override
            public void sendTo(Message message, ServerPlayer player) {
                channel.send(message, PacketDistributor.PLAYER.with(player));
            }

            @Override
            public void sendToServer(Message message) {
                channel.send(message, PacketDistributor.SERVER.noArg());
            }
        });

        Trmt.LOG.info("Opened the Forge channel with {} message(s)", Packets.all().size());
    }

    /**
     * One message type.
     *
     * <p>
     * Its own method so the type variable has somewhere to live: the list is of entries with
     * different payload types, and {@code registerMessage} wants one concrete type per call.
     */
    private static <T extends Message> void register(Packets.Entry<T> entry) {
        // The same number as 1.20.1's registerMessage gave it, so the discriminator on the wire is unchanged.
        channel.messageBuilder(entry.type, entry.id)
            .encoder((message, buf) -> message.toBytes(buf))
            .decoder(buf -> decode(entry, buf))
            .consumerNetworkThread((message, at) -> {
                // onto the server or client thread. Both older editions wrap their handlers in
                // MainThread for the same reason, and those wrappers are still in the packets - this
                // is simply the loader's own way of saying it, and a handler already on the right
                // thread pays nothing for being told so twice.
                at.enqueueWork(() -> entry.receiver.onMessage(message, at.getSender()));
                at.setPacketHandled(true);
            })
            .add();
    }

    private static <T extends Message> T decode(Packets.Entry<T> entry, FriendlyByteBuf buf) {
        T message = entry.make.get();
        message.fromBytes(buf);
        return message;
    }
}
