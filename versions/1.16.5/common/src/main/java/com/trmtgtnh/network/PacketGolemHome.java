package com.trmtgtnh.network;

import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;

import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.entity.EntityGolemOfWays;
import com.trmtgtnh.util.MainThread;

import io.netty.buffer.ByteBuf;

/**
 * A golem told where its home is, typed rather than stepped.
 *
 * <p>
 * The rest of that screen rides the container's own button channel, which needs no packet of its
 * own and is the right thing for a stepper. It is the wrong thing for a coordinate: that channel
 * carries a single number and puts it through a byte, so it can say "one more" and cannot say
 * "minus one thousand and forty-two".
 *
 * <p>
 * Bounded twice on the way in. The player has to be the one whose screen is open, so a packet
 * cannot move somebody else's golem; and the point named has to be within reach of where the golem
 * already is, so it cannot be teleported across the world by a packet - the anchor is what the
 * golem walks back to, and one set a thousand blocks away is a golem walking for the rest of the
 * session.
 */
public class PacketGolemHome implements Message {

    /**
     * How far a typed home may be from where the golem stands.
     *
     * <p>
     * Generous, because moving a golem's round along a road it is keeping is exactly what this is
     * for, and stingy against the whole world. A player who wants it somewhere else can walk it
     * there, which is what the leash is for.
     */
    public static final int MAX_MOVE = 256;

    private int entityId;
    private int x;
    private int y;
    private int z;

    public PacketGolemHome() {}

    public PacketGolemHome(int entityId, int x, int y, int z) {
        this.entityId = entityId;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        entityId = buf.readInt();
        x = buf.readInt();
        y = buf.readInt();
        z = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
    }

    public static class Handler implements Receiver<PacketGolemHome> {

        @Override
        public void onMessage(final PacketGolemHome message, ServerPlayer from) {
            final ServerPlayer player = from;
            if (player == null) return;

            MainThread.onServer(new Runnable() {

                @Override
                public void run() {
                    if (!TrmtConfig.golemEnabled) return;
                    Entity found = player.level.getEntity(message.entityId);
                    if (!(found instanceof EntityGolemOfWays)) return;
                    EntityGolemOfWays golem = (EntityGolemOfWays) found;

                    // The screen that sent this has to be the screen that is open, which is the
                    // same test every button on it already passes through the container.
                    if (!(player.containerMenu instanceof com.trmtgtnh.entity.ContainerGolem)) return;
                    if (((com.trmtgtnh.entity.ContainerGolem) player.containerMenu).golem() != golem) return;

                    if (Math.abs(message.x - Math.floor(golem.getX())) > MAX_MOVE) return;
                    if (Math.abs(message.z - Math.floor(golem.getZ())) > MAX_MOVE) return;
                    if (message.y < 0 || message.y > 255) return;

                    golem.setAnchor(message.x, message.y, message.z);
                    golem.setConfiguredBy(
                        player.getGameProfile()
                            .getName());
                }
            });
            return;
        }
    }
}
