package com.trmtgtnh.network;

import com.trmtgtnh.Client;
import com.trmtgtnh.util.MainThread;

import net.minecraft.server.level.ServerPlayer;

import io.netty.buffer.ByteBuf;

/**
 * Server to one client: show this player a wear-pattern preview.
 *
 * <p>
 * The dev tool's gestures land on the server, because a left-click has no item hook on the client
 * in 1.7.10 - but what they change is a purely local picture, so the answer comes back to that one
 * player and nobody else's view moves. Nothing here touches the config on disk: the preview is an
 * in-memory swap the next resource reload would undo anyway, which is exactly what a preview
 * should be.
 */
public class PacketDevPreview implements Message {

    private int mode;

    public PacketDevPreview() {}

    public PacketDevPreview(int mode) {
        this.mode = mode;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        mode = buf.readUnsignedByte();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(mode & 0xFF);
    }

    public static class Handler implements Receiver<PacketDevPreview> {

        @Override
        public void onMessage(final PacketDevPreview message, ServerPlayer from) {
            MainThread.onClient(new Runnable() {

                @Override
                public void run() {
                    // Through the proxy, so nothing client-only is named in a class the server loads.
                    Client.applyDevPreview(message.mode);
                }
            });
            return;
        }
    }
}
