package com.trmtgtnh.network;

import com.trmtgtnh.Client;
import com.trmtgtnh.surface.SurfaceTableCodec;
import com.trmtgtnh.util.MainThread;

import net.minecraft.server.level.ServerPlayer;

import io.netty.buffer.ByteBuf;

/**
 * Server to client: the surface table this server is using, for a client whose own differs.
 *
 * <p>
 * The table, compressed, with the fingerprint it answers to and the one switch that travels beside
 * it. Anything that does not read as a whole table arrives as nothing, and the client keeps its own,
 * which is the answer it had before it asked.
 */
public class PacketSurfaceTable implements Message {

    private long fingerprint;

    private boolean holdsSwitch;

    private byte[] bytes;

    public PacketSurfaceTable() {}

    public PacketSurfaceTable(long fingerprint, boolean holdsSwitch, byte[] bytes) {
        this.fingerprint = fingerprint;
        this.holdsSwitch = holdsSwitch;
        this.bytes = bytes;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        if (buf.readableBytes() < 13) return;
        fingerprint = buf.readLong();
        holdsSwitch = buf.readBoolean();
        int length = buf.readInt();
        if (length <= 0 || length > SurfaceTableCodec.MAX_COMPRESSED || length > buf.readableBytes()) return;
        bytes = new byte[length];
        buf.readBytes(bytes);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(fingerprint);
        buf.writeBoolean(holdsSwitch);
        byte[] out = bytes == null ? new byte[0] : bytes;
        buf.writeInt(out.length);
        buf.writeBytes(out);
    }

    public static class Handler implements Receiver<PacketSurfaceTable> {

        @Override
        public void onMessage(final PacketSurfaceTable message, ServerPlayer from) {
            if (message.bytes == null) return;
            MainThread.onClient(new Runnable() {

                @Override
                public void run() {
                    Client.installServerTable(message.fingerprint, message.holdsSwitch, message.bytes);
                }
            });
            return;
        }
    }
}
