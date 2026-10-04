package com.trmtgtnh.network;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.erosion.ChunkErosionData;
import com.trmtgtnh.erosion.ErosionEntry;
import com.trmtgtnh.util.MainThread;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Server to client: every worn position in one chunk.
 *
 * <p>
 * Three bytes per position — a sixteen-bit chunk-local key and a packed appearance byte. A
 * heavily travelled chunk with three hundred worn blocks is under a kilobyte, which is why
 * this can be sent whole on chunk watch rather than reconstructed from a stream of deltas.
 * Positions with no visible stage are left out entirely; the client has no use for wear it
 * cannot see.
 */
public class PacketChunkErosion implements IMessage {

    private int chunkX;
    private int chunkZ;
    private int[] keys;
    private short[] states;

    public PacketChunkErosion() {}

    private PacketChunkErosion(int chunkX, int chunkZ, int[] keys, short[] states) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.keys = keys;
        this.states = states;
    }

    /** Builds a packet from a chunk's data, or null when nothing in it is visible yet. */
    public static PacketChunkErosion of(int chunkX, int chunkZ, ChunkErosionData data) {
        return of(chunkX, chunkZ, data, false);
    }

    /**
     * As above, and able to send a packet with nothing in it.
     *
     * <p>
     * An empty packet is a statement rather than a waste: it says nothing in this chunk is worn
     * any more, and it is the only thing that tells a client to lift the ghosts it is still
     * holding. A caller that has just healed the last of a chunk's wear away has to be able to
     * say that; a caller that is only reporting what it found should not, or every chunk load
     * over untouched ground would send one.
     */
    public static PacketChunkErosion of(int chunkX, int chunkZ, ChunkErosionData data, boolean evenIfEmpty) {
        int[] allKeys = data.keys();
        int visible = 0;
        for (int key : allKeys) {
            ErosionEntry entry = data.get(key);
            if (entry != null && entry.isVisible()) visible++;
        }
        if (visible == 0 && !evenIfEmpty) return null;

        int[] keys = new int[visible];
        short[] states = new short[visible];
        int index = 0;
        for (int key : allKeys) {
            ErosionEntry entry = data.get(key);
            if (entry == null || !entry.isVisible()) continue;
            keys[index] = key;
            states[index] = entry.packState();
            index++;
        }
        return new PacketChunkErosion(chunkX, chunkZ, keys, states);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        chunkX = buf.readInt();
        chunkZ = buf.readInt();
        int count = buf.readUnsignedShort();
        // A malformed or hostile length must not make us allocate an array we cannot fill.
        // Six bytes an entry: a four-byte key and a two-byte state. It was four when the key was
        // a short, and leaving it at four would have clamped a full chunk to two-thirds of itself and
        // dropped the rest without a word.
        int available = buf.readableBytes() / 6;
        if (count > available) count = available;
        keys = new int[count];
        states = new short[count];
        for (int i = 0; i < count; i++) {
            keys[i] = buf.readInt();
            states[i] = buf.readShort();
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(chunkX);
        buf.writeInt(chunkZ);
        int count = Math.min(keys.length, 65535);
        buf.writeShort(count);
        for (int i = 0; i < count; i++) {
            buf.writeInt(keys[i]);
            buf.writeShort(states[i]);
        }
    }

    public static class Handler implements IMessageHandler<PacketChunkErosion, IMessage> {

        @Override
        public IMessage onMessage(final PacketChunkErosion message, MessageContext ctx) {
            MainThread.onClient(new Runnable() {

                @Override
                public void run() {
                    Trmt.proxy.handleChunkErosion(message.chunkX, message.chunkZ, message.keys, message.states);
                }
            });
            return null;
        }
    }
}
