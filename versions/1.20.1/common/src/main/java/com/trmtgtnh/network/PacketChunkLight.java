package com.trmtgtnh.network;

import com.trmtgtnh.Client;
import com.trmtgtnh.erosion.ChunkErosionData;
import com.trmtgtnh.erosion.ErosionEntry;
import com.trmtgtnh.util.MainThread;

import net.minecraft.server.level.ServerPlayer;

import io.netty.buffer.ByteBuf;

/**
 * Server to client: every lit position in one chunk.
 *
 * <p>
 * Three bytes each - two of key, one of packed level and color - and only for positions that are
 * actually lit, which in most chunks is none at all. A chunk with nothing lit produces no packet,
 * so the common case costs nothing on the wire.
 */
public class PacketChunkLight implements Message {

    private int chunkX;
    private int chunkZ;
    private int[] keys = new int[0];
    private byte[] values = new byte[0];

    public PacketChunkLight() {}

    private PacketChunkLight(int chunkX, int chunkZ, int[] keys, byte[] values) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.keys = keys;
        this.values = values;
    }

    /**
     * Builds the packet for a chunk, or null when nothing in it is lit.
     *
     * <p>
     * Returning null rather than an empty packet is the point: a world where nobody has lit
     * anything sends no light packets at all.
     */
    public static PacketChunkLight of(int chunkX, int chunkZ, ChunkErosionData data) {
        if (data == null || data.isEmpty()) return null;

        int[] allKeys = data.keys();
        int count = 0;
        for (int key : allKeys) {
            ErosionEntry entry = data.get(key);
            if (entry != null && entry.isLit()) count++;
        }
        if (count == 0) return null;

        int[] keys = new int[count];
        byte[] values = new byte[count];
        int index = 0;
        for (int key : allKeys) {
            ErosionEntry entry = data.get(key);
            if (entry == null || !entry.isLit()) continue;
            keys[index] = key;
            values[index] = (byte) entry.getLight();
            index++;
            if (index == count) break;
        }
        return new PacketChunkLight(chunkX, chunkZ, keys, values);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        chunkX = buf.readInt();
        chunkZ = buf.readInt();
        int count = buf.readUnsignedShort();
        // Never trust the stated count past what actually arrived.
        // Five bytes an entry: a four-byte key and one byte of light. Three when the key was a
        // short.
        count = Math.min(count, buf.readableBytes() / 5);
        keys = new int[count];
        values = new byte[count];
        for (int index = 0; index < count; index++) {
            keys[index] = buf.readInt();
            values[index] = buf.readByte();
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        int count = Math.min(keys.length, 65535);
        buf.writeInt(chunkX);
        buf.writeInt(chunkZ);
        buf.writeShort(count);
        for (int index = 0; index < count; index++) {
            buf.writeInt(keys[index]);
            buf.writeByte(values[index]);
        }
    }

    public static class Handler implements Receiver<PacketChunkLight> {

        @Override
        public void onMessage(final PacketChunkLight message, ServerPlayer from) {
            MainThread.onClient(new Runnable() {

                @Override
                public void run() {
                    Client.handleChunkLight(message.chunkX, message.chunkZ, message.keys, message.values);
                }
            });
            return;
        }
    }
}
