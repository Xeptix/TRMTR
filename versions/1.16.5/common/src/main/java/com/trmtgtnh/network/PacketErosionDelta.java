package com.trmtgtnh.network;

import com.trmtgtnh.Client;
import com.trmtgtnh.util.MainThread;

import net.minecraft.server.level.ServerPlayer;

import io.netty.buffer.ByteBuf;

/**
 * Server to client: one position changed appearance.
 *
 * <p>
 * A flags byte of zero means "no longer worn" - a valid appearance always carries a stage
 * of at least one in its biased field, so zero can never collide with a real value and is
 * free to act as the removal sentinel.
 */
public class PacketErosionDelta implements Message {

    private int x;
    private int y;
    private int z;
    private short state;

    public PacketErosionDelta() {}

    public PacketErosionDelta(int x, int y, int z, short state) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.state = state;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        x = buf.readInt();
        y = buf.readUnsignedByte();
        z = buf.readInt();
        state = buf.readShort();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x);
        buf.writeByte(y & 0xFF);
        buf.writeInt(z);
        buf.writeShort(state);
    }

    public static class Handler implements Receiver<PacketErosionDelta> {

        @Override
        public void onMessage(final PacketErosionDelta message, ServerPlayer from) {
            MainThread.onClient(new Runnable() {

                @Override
                public void run() {
                    Client.handleDelta(message.x, message.y, message.z, message.state);
                }
            });
            return;
        }
    }
}
