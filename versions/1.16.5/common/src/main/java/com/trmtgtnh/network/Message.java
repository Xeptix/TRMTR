package com.trmtgtnh.network;

import io.netty.buffer.ByteBuf;

/**
 * One thing this mod sends over the wire.
 *
 * <p>
 * Both older editions use Forge's {@code IMessage} for this, which has exactly these two methods
 * under exactly these names. There is no counterpart on Fabric and no vanilla interface that means
 * the same thing, so the mod declares its own and a packet's declaration changes by one word.
 *
 * <p>
 * <strong>{@code ByteBuf} rather than {@code FriendlyByteBuf}, deliberately.</strong> Both loaders
 * hand a packet a {@code FriendlyByteBuf} at this version, and that extends netty's {@code ByteBuf},
 * so declaring the narrower type costs nothing and buys the thing this port is for: every buffer
 * call in all twenty-one packets is already plain netty - {@code writeByte}, {@code readInt},
 * {@code readableBytes} - with not one use of Forge's {@code ByteBufUtils} anywhere in the layer. So
 * the bodies cross from 1.12.2 unchanged, and serialisation, where a mistake is silent and shows up
 * as a corrupted overlay three hours later, is not rewritten at all.
 *
 * <p>
 * An implementation needs a public no-argument constructor, because that is how the decoder makes
 * one before handing it the bytes. Both older editions require the same and for the same reason.
 */
public interface Message {

    /** Reads this message's own fields back out, in the order {@link #toBytes} wrote them. */
    void fromBytes(ByteBuf buf);

    /** Writes this message's own fields. */
    void toBytes(ByteBuf buf);
}
