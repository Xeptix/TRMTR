package com.trmtgtnh.network;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.netty.buffer.Unpooled;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.trmtgtnh.Trmt;

/**
 * One of this mod's messages, as 1.20.5's networking carries it.
 *
 * <p>
 * <strong>From 1.20.5 a custom packet is a typed payload</strong>: a record with a type naming its channel and a codec
 * that writes it, registered with the loader before it can be sent - where 1.20.1 handed each loader a channel name
 * and a buffer of bytes. Every message here already writes itself to a buffer ({@link Message#toBytes}) and is read
 * back by its {@link Packets.Entry}, and both ends of that are byte for byte the older editions' wire. So the payload
 * is the message's bytes and nothing else: one type per message, named by the message's own channel name, the codec
 * copying the bytes across untouched. What goes over the wire is exactly what 1.20.1 sent on the same channel, and a
 * message is still made and read by the code that made and read it there.
 *
 * <p>
 * Held as an array rather than a buffer, because the payload outlives the network thread's buffer: a loader hands the
 * work to the game thread after the buffer it was read from has been released, which every edition has had to copy
 * for, and an array is that copy.
 */
public record Payload(CustomPacketPayload.Type<Payload> type, byte[] bytes) implements CustomPacketPayload {

    private static final Map<String, CustomPacketPayload.Type<Payload>> TYPES = new ConcurrentHashMap<>();

    /** The type a message travels as: its channel, {@code trmtgtnh:<the message's name>}, as in 1.20.1. */
    public static CustomPacketPayload.Type<Payload> typeOf(Packets.Entry<?> entry) {
        return TYPES.computeIfAbsent(
            entry.name,
            name -> new CustomPacketPayload.Type<Payload>(ResourceLocation.fromNamespaceAndPath(Trmt.MODID, name)));
    }

    /** How a payload of this type is written and read: its bytes, as they are, to the end of the packet. */
    public static StreamCodec<FriendlyByteBuf, Payload> codec(CustomPacketPayload.Type<Payload> type) {
        return StreamCodec.of(
            (buf, payload) -> buf.writeBytes(payload.bytes()),
            buf -> {
                byte[] read = new byte[buf.readableBytes()];
                buf.readBytes(read);
                return new Payload(type, read);
            });
    }

    /** A message, written as its entry's payload. */
    public static Payload of(Packets.Entry<?> entry, Message message) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        message.toBytes(buf);
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        buf.release();
        return new Payload(typeOf(entry), bytes);
    }

    /** The bytes as a buffer to read the message back from, as its entry reads one. */
    public io.netty.buffer.ByteBuf buffer() {
        return Unpooled.wrappedBuffer(bytes);
    }
}
