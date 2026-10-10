package com.trmtgtnh.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.trmtgtnh.config.FamilySettings;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.erosion.ErosionChain;
import com.trmtgtnh.erosion.ErosionEngine;
import com.trmtgtnh.erosion.ErosionEntry;
import com.trmtgtnh.erosion.ErosionStore;
import com.trmtgtnh.erosion.RecordReadout;
import com.trmtgtnh.erosion.Weather;
import com.trmtgtnh.surface.SurfaceFamily;
import com.trmtgtnh.util.MainThread;

import io.netty.buffer.ByteBuf;

/**
 * Client to server: "how worn is the block I am looking at, exactly?"
 *
 * <p>
 * The client already knows which surface and stage a position wears - that is what it is drawing. What it
 * cannot know is the progress toward the next stage, how long the ground has been left alone, and whether
 * the block carries a reinforcement or a spawn ward, because all of those live only in the server's record,
 * and the last two can sit on any block, worn or not. Rather than pay for those on every position in every
 * chunk packet, they are asked for one position at a time, and only while somebody is actually looking at it.
 *
 * <p>
 * A record with nothing drawn is answered too, because a reinforcement or a ward can sit on one, but only
 * with what it carries: see {@link RecordReadout} for why a tally's wear, and ground's wear short of its
 * first gradation, never leave the server.
 */
public class PacketInspect implements IMessage {

    private int x;
    private int y;
    private int z;

    public PacketInspect() {}

    public PacketInspect(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        x = buf.readInt();
        y = buf.readUnsignedByte();
        z = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x);
        buf.writeByte(y & 0xFF);
        buf.writeInt(z);
    }

    /** In-game seconds in a Minecraft day, matching {@code ErosionEngine.SECONDS_PER_DAY}. */
    private static final double SECONDS_PER_DAY = 1200.0D;

    /**
     * How long until this position next recovers a stage, or -1 when it never will.
     *
     * <p>
     * Worked out here rather than on the client for two reasons. Healing happens in two steps -
     * partial wear bleeds off first, and only then does a whole stage cost its healing time - so
     * an estimate that counts just the second one is short by however much wear has built up.
     * And the rates come from the server's config, which is the one that governs; a client on
     * someone else's server has its own copy and no reason to think it matches.
     */
    private static int recoverySeconds(ErosionEntry entry, SurfaceFamily base, int untouchedSeconds, World world) {
        if (!TrmtConfig.healingEnabled || !entry.isVisible()) return -1;
        FamilySettings settings = TrmtConfig.family(entry.getFamily());
        if (settings == null) return -1;

        // Ground waiting on the weather has no answer to give, so it is given none. What is owed
        // here is banked days, and the sum below counts them honestly enough - but what turns a
        // banked day into a gradation is precipitation falling on this very square, handed over at
        // whatever weather.wetHealSecondsPerStage says, and neither the storms to come nor the roof
        // overhead is a length of time anybody knows now. Asked of the ground rather than of what
        // it currently draws as, the same way the engine asks it, because a turf path showing the
        // earth beneath is still turf recovering under turf's rules. Saying nothing is the honest
        // reading: a countdown that runs down to nought over ground the rain cannot reach is worse
        // than no countdown at all, because it reads as a promise, and under a roof or in a dry
        // biome it is one that will never be kept.
        if (Weather.waitsForWeather(base, world)) return -1;

        double decayPerDay = TrmtConfig.wearDecayPerDay * entry.effectiveThreshold();
        double bleedDays = decayPerDay > 0 ? entry.getWear() / decayPerDay : 0.0D;
        double days = bleedDays + settings.scaledHealDays() - untouchedSeconds / SECONDS_PER_DAY;
        return days <= 0 ? 0 : (int) (days * SECONDS_PER_DAY);
    }

    /**
     * What the server tells a client about one position, or null when it holds no record there.
     *
     * <p>
     * Null rather than an empty reply, and the client leans on it: a position with nothing on it costs a
     * request and no answer, and an answer that stops coming is how the client learns a reinforcement or a
     * ward has been taken off - see {@link com.trmtgtnh.util.InspectionSlot}. Worked out on the server thread
     * from the world and the record alone, apart from the handler, the one caller with a player to measure the
     * reach against, so it can be asked for with nobody connected.
     */
    public static RecordReadout readout(World world, int x, int y, int z) {
        ErosionEntry entry = ErosionStore.get()
            .getEntry(world, x, y, z);
        if (entry == null) return null;
        int untouched = ErosionEngine.nowSeconds(world) - entry.getLastTouchedSeconds();
        // Where this sits on the whole run from pristine to fully sunk, which is the
        // only number that means anything across a chain that changes surface part
        // way. The stage on its own restarts counting when grass gives way to earth.
        SurfaceFamily base = com.trmtgtnh.surface.SurfaceRegistry.familyOf(
            com.trmtgtnh.util.Worlds.blockAt(world, x, y, z),
            com.trmtgtnh.util.Worlds.metaAt(world, x, y, z));
        int chainLength = base == null ? 0 : ErosionChain.length(base);
        int chainIndex = base == null ? -1
            : ErosionChain.indexOf(base, entry.getFamily(), entry.getStage(), entry.getSink());
        return RecordReadout
            .of(entry, untouched, recoverySeconds(entry, base, untouched, world), chainIndex, chainLength);
    }

    public static class Handler implements IMessageHandler<PacketInspect, IMessage> {

        @Override
        public IMessage onMessage(final PacketInspect message, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().player;
            if (player == null) return null;

            MainThread.onServer(new Runnable() {

                @Override
                public void run() {
                    World world = player.world;
                    if (world == null) return;
                    // Only answer for somewhere the player could plausibly be looking.
                    if (player.getDistanceSq(message.x + 0.5D, message.y + 0.5D, message.z + 0.5D) > 64 * 64) return;

                    RecordReadout readout = readout(world, message.x, message.y, message.z);
                    if (readout == null) return;
                    TrmtNetwork.channel.sendTo(
                        new PacketInspectResult(
                            message.x,
                            message.y,
                            message.z,
                            readout.wear,
                            readout.threshold,
                            readout.untouchedSeconds,
                            readout.recoverySeconds,
                            readout.chainIndex,
                            readout.chainLength,
                            readout.reinforce,
                            readout.ward),
                        player);
                }
            });
            return null;
        }
    }

}
