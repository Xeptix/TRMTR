package com.trmtgtnh.network;

import com.trmtgtnh.Client;
import com.trmtgtnh.util.MainThread;

import net.minecraft.server.level.ServerPlayer;

import io.netty.buffer.ByteBuf;

/**
 * Server to client: one position was lit, recolored, or put out.
 *
 * <p>
 * A packed byte whose level nibble is zero means "no longer lit", which needs no separate sentinel:
 * a light of level zero is not a light, so the value can never collide with a real one.
 */
public class PacketLightDelta implements Message {

    private int x;
    private int y;
    private int z;
    private byte packed;

    public PacketLightDelta() {}

    public PacketLightDelta(int x, int y, int z, int packed) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.packed = (byte) packed;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        x = buf.readInt();
        // A short from 1.18, whose worlds hold -64 to 319: 1.16.5's one unsigned byte held 0 to 255.
        y = buf.readShort();
        z = buf.readInt();
        packed = buf.readByte();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x);
        buf.writeShort(y);
        buf.writeInt(z);
        buf.writeByte(packed);
    }

    public static class Handler implements Receiver<PacketLightDelta> {

        @Override
        public void onMessage(final PacketLightDelta message, ServerPlayer from) {
            MainThread.onClient(new Runnable() {

                @Override
                public void run() {
                    Client.handleLightDelta(message.x, message.y, message.z, message.packed & 0xFF);
                }
            });
            return;
        }
    }
}
