package com.trmtgtnh.network;

import net.minecraft.server.level.ServerPlayer;

import com.trmtgtnh.util.MainThread;

import io.netty.buffer.ByteBuf;

/**
 * Client to server: "I have the mod, and here is whether I want to see the overlay."
 *
 * <p>
 * The server sends nothing to a player who has not said this, which is what makes the
 * per-client toggle cost the server nothing while it is off. Not what makes the mod optional
 * on a client, though: a client without it cannot join a server that has it at all, because
 * Forge refuses a client missing a server's blocks and items before any of this is sent.
 */
public class PacketHello implements Message {

    private boolean wantsOverlays;

    public PacketHello() {}

    public PacketHello(boolean wantsOverlays) {
        this.wantsOverlays = wantsOverlays;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        wantsOverlays = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(wantsOverlays);
    }

    public static class Handler implements Receiver<PacketHello> {

        @Override
        public void onMessage(PacketHello message, ServerPlayer from) {
            final ServerPlayer player = from;
            if (player == null) return;
            final boolean wants = message.wantsOverlays;

            // Only what was asked is recorded here. In 1.7.10 this handler runs on the server thread,
            // straight from the connection's queue; the answer, and everything sent because of it, is
            // handed to the end of the tick, in the same line as a reload or /trmt enable handed over
            // from another thread. See TrmtNetwork.answerHello for why it is not decided here.
            TrmtNetwork.rememberWanted(player, wants);
            MainThread.onServer(new Runnable() {

                @Override
                public void run() {
                    TrmtNetwork.answerHello(player);
                }
            });
            return;
        }

    }
}
