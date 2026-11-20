package com.trmtgtnh.network;

import net.minecraft.server.level.ServerPlayer;

import com.trmtgtnh.util.MainThread;

import io.netty.buffer.ByteBuf;

/**
 * Client to server: an operator's game answering {@link PacketClientSettingsAsk} with its current client settings
 * (0.9.229), encoded as ClientSettingRules encodes rules. Sent only in answer to an ask. The server takes it only from
 * the player it asked, for the ask it sent, and only while they are still an operator, and checks every setting
 * against its own before any of it becomes a rule: a client can be made to say anything.
 */
public class PacketClientSettingsCopy implements Message {

    int ask;

    byte[] settings = new byte[0];

    public PacketClientSettingsCopy() {}

    public PacketClientSettingsCopy(int ask, byte[] settings) {
        this.ask = ask;
        this.settings = settings;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        if (buf.readableBytes() < 8) return;
        ask = buf.readInt();
        int length = buf.readInt();
        if (length < 0 || length > buf.readableBytes() || length > com.trmtgtnh.util.ClientSettingRules.MAX_BYTES * 2) {
            return;
        }
        settings = new byte[length];
        buf.readBytes(settings);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(ask);
        buf.writeInt(settings.length);
        buf.writeBytes(settings);
    }

    public static class Handler implements Receiver<PacketClientSettingsCopy> {

        @Override
        public void onMessage(final PacketClientSettingsCopy message, ServerPlayer from) {
            final ServerPlayer player = from;
            if (player == null) return;
            MainThread.onServer(new Runnable() {

                @Override
                public void run() {
                    TrmtNetwork.takeSettingsCopy(player, message.ask, message.settings);
                }
            });
        }
    }
}
