package com.trmtgtnh.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.trmtgtnh.item.ItemChunkTamper;
import com.trmtgtnh.util.MainThread;

import io.netty.buffer.ByteBuf;

/**
 * Two numbers a player changed on a tamper's screen.
 *
 * <p>
 * Two numbers, and deliberately not a stack. The client is asked what it wants set, never what
 * the item is: the server finds the item itself, in the hand it is actually held in, and
 * applies the values through the item's own setters, which clamp. A packet that carried an
 * ItemStack would be a packet that let a modified client hand the server any item it liked and
 * have it written into an inventory slot.
 *
 * <p>
 * Nothing here needs a permission check. The worst a forged one can do is resize the sender's
 * own tool, which is what the screen is for.
 */
public class PacketTamperSettings implements IMessage {

    private int reach;
    private int steps;
    private int mode;
    private int reinforceLevel;

    public PacketTamperSettings() {}

    public PacketTamperSettings(int reach, int steps, int mode, int reinforceLevel) {
        this.reach = reach;
        this.steps = steps;
        this.mode = mode;
        this.reinforceLevel = reinforceLevel;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        reach = buf.readUnsignedByte();
        steps = buf.readUnsignedByte();
        int flags = buf.readUnsignedByte();
        mode = flags & 0x3;
        reinforceLevel = (flags >> 2) & 0x3;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(reach & 0xFF);
        buf.writeByte(steps & 0xFF);
        buf.writeByte((mode & 0x3) | ((reinforceLevel & 0x3) << 2));
    }

    public static class Handler implements IMessageHandler<PacketTamperSettings, IMessage> {

        @Override
        public IMessage onMessage(final PacketTamperSettings message, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().player;
            if (player == null) return null;

            // Handed to the end of the tick before touching an inventory. In 1.7.10 this handler already
            // runs on the server thread, so here the hand-over only moves the write behind the rest of the
            // tick's packets. From 1.8 to 1.12.2 handlers run on the network thread, where a slot written
            // is a slot written while the tick that owns it is halfway through reading it.
            MainThread.onServer(new Runnable() {

                @Override
                public void run() {
                    // The hand the game tried first, and so the one whose screen this is: the main hand where it holds a
                    // chunk tamper, the off hand where only that does (0.9.222, spec TA50).
                    ItemStack held = player.getHeldItemMainhand();
                    if (held == null || !(held.getItem() instanceof ItemChunkTamper)) held = player.getHeldItemOffhand();
                    // The item the player is holding right now, not the one they were holding
                    // when the screen opened. If they have swapped, there is nothing to set.
                    if (held == null || !(held.getItem() instanceof ItemChunkTamper)) return;
                    ItemChunkTamper.setReach(held, message.reach);
                    ItemChunkTamper.setSteps(held, message.steps);
                    ItemChunkTamper.setMode(held, message.mode);
                    ItemChunkTamper.setReinforceLevel(held, message.reinforceLevel);
                }
            });
            return null;
        }
    }
}
