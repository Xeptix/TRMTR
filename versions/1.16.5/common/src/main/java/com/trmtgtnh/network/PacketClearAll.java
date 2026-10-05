package com.trmtgtnh.network;

import com.trmtgtnh.Client;
import com.trmtgtnh.util.MainThread;

import net.minecraft.server.level.ServerPlayer;

import io.netty.buffer.ByteBuf;

/**
 * Server to client: drop every overlay you are holding.
 *
 * <p>
 * The server-side kill switch. Because nothing was ever written into the world, switching
 * the mod off is exactly this packet plus the server no longer accumulating wear — there is
 * nothing to undo, and the stored data stays put so switching it back on restores every
 * path as it was.
 */
public class PacketClearAll implements Message {

    @Override
    public void fromBytes(ByteBuf buf) {}

    @Override
    public void toBytes(ByteBuf buf) {}

    public static class Handler implements Receiver<PacketClearAll> {

        @Override
        public void onMessage(PacketClearAll message, ServerPlayer from) {
            MainThread.onClient(new Runnable() {

                @Override
                public void run() {
                    Client.handleClearAll();
                }
            });
            return;
        }
    }
}
