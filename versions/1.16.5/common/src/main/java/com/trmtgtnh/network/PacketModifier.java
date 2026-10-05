package com.trmtgtnh.network;

import net.minecraft.server.level.ServerPlayer;

import com.trmtgtnh.item.TamperModifiers;

import io.netty.buffer.ByteBuf;

/**
 * Client to server: the settings modifier went down, or came back up.
 *
 * <p>
 * One byte, sent only when the state actually changes and only while a tamper is in hand, so a
 * player who never holds one never sends it. See {@link TamperModifiers} for why the server
 * needs telling at all and why it has to be told in advance rather than alongside the click.
 *
 * <p>
 * Handled where it arrives, on purpose, without the hand-over every other handler here makes. In 1.7.10
 * that is the server thread, straight from the connection's queue, which hands a client's packets over
 * in the order they were sent, so a flag sent before a click is set before that click is handled; see
 * {@link TamperModifiers} for why it is sent a tick ahead. Handed to the end of the tick instead, it
 * would land behind a click handled in the same tick, and a settings click would reach the server as a
 * mend. It stays right from 1.8 to 1.12.2, where it is handled on the network thread and the click waits
 * for the main thread. The map it writes is synchronized, though on this version only the server thread
 * uses it.
 */
public class PacketModifier implements Message {

    private boolean down;

    public PacketModifier() {}

    public PacketModifier(boolean down) {
        this.down = down;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        down = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(down);
    }

    public static class Handler implements Receiver<PacketModifier> {

        @Override
        public void onMessage(PacketModifier message, ServerPlayer from) {
            ServerPlayer player = from;
            if (player != null) TamperModifiers.set(player, message.down);
            return;
        }
    }
}
