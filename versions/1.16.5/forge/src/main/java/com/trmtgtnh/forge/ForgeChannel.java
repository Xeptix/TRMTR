package com.trmtgtnh.forge;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.network.NetworkEvent;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.PacketDistributor;
import net.minecraftforge.fml.network.simple.SimpleChannel;

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
 * The version string is compared both ways and both comparisons are {@code equals}, which is Forge's
 * way of saying this mod is required on both ends. It already is - a client missing the server's
 * blocks cannot join - so this only makes the refusal happen earlier and with a clearer reason.
 */
public final class ForgeChannel {

    private static final String VERSION = "1";

    private static SimpleChannel channel;

    private ForgeChannel() {}

    /** Opens the channel and registers every message on it. Called once as the mod starts. */
    public static void open() {
        channel = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Trmt.MODID, "main"),
            () -> VERSION,
            VERSION::equals,
            VERSION::equals);

        for (Packets.Entry<?> each : Packets.all()) {
            register(each);
        }

        TrmtNetwork.use(new TrmtNetwork.Channel() {

            @Override
            public void sendTo(Message message, ServerPlayer player) {
                channel.send(PacketDistributor.PLAYER.with(() -> player), message);
            }

            @Override
            public void sendToServer(Message message) {
                channel.sendToServer(message);
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
        channel.registerMessage(
            entry.id,
            entry.type,
            (message, buf) -> message.toBytes(buf),
            buf -> decode(entry, buf),
            (message, context) -> {
                NetworkEvent.Context at = context.get();
                // onto the server or client thread. Both older editions wrap their handlers in
                // MainThread for the same reason, and those wrappers are still in the packets - this
                // is simply the loader's own way of saying it, and a handler already on the right
                // thread pays nothing for being told so twice.
                at.enqueueWork(() -> entry.receiver.onMessage(message, at.getSender()));
                at.setPacketHandled(true);
            });
    }

    private static <T extends Message> T decode(Packets.Entry<T> entry, FriendlyByteBuf buf) {
        T message = entry.make.get();
        message.fromBytes(buf);
        return message;
    }
}
