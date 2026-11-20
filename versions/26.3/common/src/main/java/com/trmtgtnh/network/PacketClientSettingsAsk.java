package com.trmtgtnh.network;

import net.minecraft.server.level.ServerPlayer;

import com.trmtgtnh.util.MainThread;

import io.netty.buffer.ByteBuf;

/**
 * Server to client: an operator ran {@code /trmt clientrules copy}, and the server asks their game for its settings
 * (0.9.229). Sent only to that operator, and only when their hello said it reads client rules, so a client that could
 * not answer is never sent one: a client without this message's type would be disconnected for it. The number is the
 * ask's own, so an answer is taken only for the ask it answers.
 */
public class PacketClientSettingsAsk implements Message {

    int ask;

    public PacketClientSettingsAsk() {}

    public PacketClientSettingsAsk(int ask) {
        this.ask = ask;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        ask = buf.readableBytes() >= 4 ? buf.readInt() : 0;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(ask);
    }

    public static class Handler implements Receiver<PacketClientSettingsAsk> {

        @Override
        public void onMessage(final PacketClientSettingsAsk message, ServerPlayer from) {
            MainThread.onClient(new Runnable() {

                @Override
                public void run() {
                    TrmtNetwork.answerSettingsAsk(message.ask);
                }
            });
        }
    }
}
