package com.trmtgtnh.network;

import com.trmtgtnh.Client;
import com.trmtgtnh.util.MainThread;

import net.minecraft.server.level.ServerPlayer;

import io.netty.buffer.ByteBuf;

/**
 * Server to one client: which snapshot slots hold something, and whether there is a way back.
 *
 * <p>
 * Three bits rather than the snapshots themselves. The screen only needs to know what to offer -
 * a whole config each way would be tens of kilobytes sent so a button could read "Commit" instead
 * of being greyed out, and the snapshots are the server's anyway.
 */
public class PacketSnapshotState implements Message {

    /** bit 0: left holds something. bit 1: right does. bit 2: an undo is available. */
    private int flags;

    public PacketSnapshotState() {}

    public PacketSnapshotState(int flags) {
        this.flags = flags;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        flags = buf.readUnsignedByte();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(flags & 0xFF);
    }

    public static class Handler implements Receiver<PacketSnapshotState> {

        @Override
        public void onMessage(final PacketSnapshotState message, ServerPlayer from) {
            MainThread.onClient(new Runnable() {

                @Override
                public void run() {
                    Client.openSnapshotScreen(message.flags);
                }
            });
            return;
        }
    }
}
