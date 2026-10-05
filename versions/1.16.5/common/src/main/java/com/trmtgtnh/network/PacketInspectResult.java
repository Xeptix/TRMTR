package com.trmtgtnh.network;

import com.trmtgtnh.Client;
import com.trmtgtnh.util.MainThread;

import net.minecraft.server.level.ServerPlayer;

import io.netty.buffer.ByteBuf;

/**
 * Server to client: the wear numbers for one position, in reply to {@link PacketInspect}.
 *
 * <p>
 * For a record with nothing drawn only the reinforcement, the ward and the run length mean anything: the wear
 * and threshold arrive as nought, and the clock, recovery and place on the run as minus one. The place on the
 * run is one signed byte, and any negative but minus one is a place past 127 (RecordReadout.indexFromWire).
 * The layout is unchanged from 0.9.212, so either side can be a version behind the other.
 */
public class PacketInspectResult implements Message {

    private int x;
    private int y;
    private int z;
    private float wear;
    private float threshold;
    private int untouchedSeconds;
    private int recoverySeconds;
    private int chainIndex;
    private int chainLength;
    private int reinforce;
    private int ward;

    public PacketInspectResult() {}

    public PacketInspectResult(int x, int y, int z, float wear, float threshold, int untouchedSeconds,
        int recoverySeconds, int chainIndex, int chainLength, int reinforce, int ward) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.wear = wear;
        this.threshold = threshold;
        this.untouchedSeconds = untouchedSeconds;
        this.recoverySeconds = recoverySeconds;
        this.chainIndex = chainIndex;
        this.chainLength = chainLength;
        this.reinforce = reinforce;
        this.ward = ward;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        x = buf.readInt();
        y = buf.readUnsignedByte();
        z = buf.readInt();
        wear = buf.readFloat();
        threshold = buf.readFloat();
        untouchedSeconds = buf.readInt();
        recoverySeconds = buf.readInt();
        chainIndex = buf.readByte();
        chainLength = buf.readUnsignedByte();
        reinforce = buf.readUnsignedByte();
        ward = buf.readUnsignedByte();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x);
        buf.writeByte(y & 0xFF);
        buf.writeInt(z);
        buf.writeFloat(wear);
        buf.writeFloat(threshold);
        buf.writeInt(untouchedSeconds);
        buf.writeInt(recoverySeconds);
        buf.writeByte(chainIndex);
        buf.writeByte(chainLength & 0xFF);
        buf.writeByte(reinforce & 0xFF);
        buf.writeByte(ward & 0xFF);
    }

    public static class Handler implements Receiver<PacketInspectResult> {

        @Override
        public void onMessage(final PacketInspectResult message, ServerPlayer from) {
            MainThread.onClient(new Runnable() {

                @Override
                public void run() {
                    Client.acceptInspection(
                        message.x,
                        message.y,
                        message.z,
                        message.wear,
                        message.threshold,
                        message.untouchedSeconds,
                        message.recoverySeconds,
                        message.chainIndex,
                        message.chainLength,
                        message.reinforce,
                        message.ward);
                }
            });
            return;
        }
    }
}
