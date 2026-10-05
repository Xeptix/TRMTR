package com.trmtgtnh.network;

import net.minecraft.server.level.ServerPlayer;

import com.trmtgtnh.Client;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.surface.SurfaceFamily;
import com.trmtgtnh.util.MainThread;

import io.netty.buffer.ByteBuf;

/**
 * Server to client: the rules a client has to play by.
 *
 * <p>
 * Only sent when the server has ruts you can walk down into. In that mode the ground you stand
 * on is the server's business, and a client that had switched the overlay off would be tripping
 * over dips it never drew — so the overlay stops being optional. The wear geometry travels with
 * it, because both sides work the rut depth out from the stage rather than sending it per
 * block, and they can only agree on that if they agree on the numbers behind it.
 *
 * <p>
 * Nothing here is written to the client's config file. It applies for the session and is
 * dropped on disconnect, so a player's own settings survive visiting someone else's server.
 */
public class PacketServerRules implements Message {

    private boolean forceOverlay;
    private boolean mayEditFamilies;

    /** Pricing, for the Wear Table's diff: per-family thresholds and heal time, plus the globals. */
    private static final int PRICING_FORMAT = 1;

    /** Guards the geometry tail below, the same way PRICING_FORMAT guards the pricing one. */
    private static final int GEOMETRY_FORMAT = 1;

    /** Guards the chain tail, which is the last of the four and the largest. */
    private static final int CHAIN_FORMAT = 1;
    private float[] threshMin;
    private float[] threshMax;
    private float[] healDays;
    private float gMultiplierPlayer;
    private float gErosionSpeed;
    private float gGlobalSpeed;
    private float gHealingRate;
    private float gMaxWearFraction;
    /**
     * Which cost curve each family prices by, as an ordinal, or -1 where this server named none.
     *
     * <p>
     * Added after the rest of the pricing tail and guarded the same way, because a curve changes
     * what a run costs at every point except its end. Without it a client that priced grass on a
     * rising curve compared its own figures against a server's flat ones and showed a difference
     * on every family that has a curve - which is to say six of the ten - between two machines
     * configured identically. A quiet lie on the one screen whose whole job is telling the truth
     * about numbers.
     */
    private byte[] curves;

    private boolean hasPricing;
    private String decayMode;

    /**
     * The wear geometry: how many gradations a family has, how many pixels it sinks at its worst,
     * how far through the run the sinking begins, and how many gradations a pixel of depth is worth.
     *
     * <p>
     * Four tails carry a family's shape between them, and they are four rather than one because each
     * was added after the one before it shipped: this triple, then the pricing the wear table draws,
     * then depth and the ceiling, then the chain. Every one of them is guarded by a version byte and
     * skipped whole by a client that predates it, so the order they arrived in is now the order they
     * are read in and none of them may be reordered.
     */
    private byte[] stages;
    private byte[] maxSink;
    private byte[] sinkStart;

    /**
     * How many gradations each family gives a pixel of depth, on its own tail.
     *
     * <p>
     * It travels for the reason the three above it travel: the record on the wire says only which
     * family, which gradation and how many pixels down, and this is the number both sides divide by
     * to place that record on a run. Its own tail rather than a fourth byte in the triple above, so
     * a client from before this stops reading before it and a server from before it sends none.
     */
    private byte[] depths;

    /**
     * Each family's own wear ceiling, in hundredths, or null where this server named none.
     *
     * <p>
     * For the Wear Table rather than for the ground. The global ceiling has been on the wire since
     * the pricing tail existed and was read back and then dropped, so a server column could quote a
     * whole run where that server's own cap stops the ground a quarter of the way along - on the
     * one screen whose entire job is telling the truth about numbers. The per-family half was never
     * sent at all, and the two are compared as a minimum, so sending one without the other would
     * have closed half the gap and left the rest looking closed.
     */
    private byte[] caps;

    /**
     * What each family wears through into, as family ordinals, on its own tail.
     *
     * <p>
     * The last part of a family's shape to travel, and the one that decided the most. A chain is
     * built by walking these lists, so two machines with different ones build different runs out of
     * identical records - and the failure is not a wrong picture but no picture at all. The painter
     * asks whether a record's appearance could ever have come from that ground before it draws
     * anything, so a client whose stone does not name cobble declines every cobble record a server's
     * stone road is made of and leaves the rut unworn; and where the list does match but the
     * wholesale switch does not, the ghost goes down and the atlas has no worn cobble in stone's
     * fallback set to draw it with, because that set is planned by walking the same chain.
     *
     * <p>
     * The lists as they are <em>written</em> rather than as they are switched on, with the switches
     * carried beside them in {@link #chainSwitches}. Sending the gated lists would have been fewer
     * bytes and would have thrown away the one question the ungated list exists to answer: whether a
     * record could ever have come from this ground. A record made while a run was switched on is
     * still that surface's own record after somebody turns the run off, and must not be mistaken for
     * ground that has been dug up and replaced.
     */
    private byte[][] successors;

    /** Whether each family wears at all, since a family switched off has no chain to place anything on. */
    private boolean[] familyEnabled;

    /**
     * The two switches gating the lists, packed, or -1 where this server sent no chain tail.
     *
     * <p>
     * Bit 0 general.wearThroughToOtherSurfaces, bit 1 general.grassWearsThroughToDirt. Minus one
     * rather than nought, because both being off is a real thing for a server to say and is in fact
     * how the wholesale one ships.
     */
    private int chainSwitches = -1;

    /** Guards the table tail, the fifth and last. */
    private static final int TABLE_FORMAT = 1;

    /**
     * The fingerprint of the surface table this server is using, and whether it sent one at all.
     *
     * <p>
     * Only the fingerprint travels with the rules, because the rules go to every player on every
     * reload and nearly every client's table already matches. A client whose does not asks for the
     * table itself. An older server sends no tail and its clients keep their own tables, which is
     * exactly what they did before any of this; an older client stops reading before the tail.
     */
    private long tableFingerprint;

    private boolean hasTable;

    public PacketServerRules() {}

    /** Captures the server's current rules. */
    public static PacketServerRules current() {
        PacketServerRules packet = new PacketServerRules();
        SurfaceFamily[] families = SurfaceFamily.values();
        packet.forceOverlay = com.trmtgtnh.config.TrmtConfig.physicalDecayCollides();
        packet.decayMode = com.trmtgtnh.config.TrmtConfig.physicalDecay;
        packet.stages = new byte[families.length];
        packet.maxSink = new byte[families.length];
        packet.sinkStart = new byte[families.length];
        packet.depths = new byte[families.length];
        packet.caps = new byte[families.length];
        packet.successors = new byte[families.length][];
        packet.familyEnabled = new boolean[families.length];
        packet.chainSwitches = (com.trmtgtnh.config.TrmtConfig.wearThroughToOtherSurfaces ? 1 : 0)
            | (com.trmtgtnh.config.TrmtConfig.grassWearsThroughToDirt ? 2 : 0);

        packet.threshMin = new float[families.length];
        packet.threshMax = new float[families.length];
        packet.healDays = new float[families.length];
        packet.curves = new byte[families.length];
        for (int i = 0; i < families.length; i++) {
            com.trmtgtnh.config.FamilySettings settings = com.trmtgtnh.config.TrmtConfig.family(families[i]);
            if (settings == null) continue;
            packet.stages[i] = (byte) settings.stages;
            packet.maxSink[i] = (byte) settings.maxSinkPixels;
            packet.sinkStart[i] = (byte) Math.round(settings.sinkStartFraction * 100f);
            packet.depths[i] = (byte) settings.layersPerDepth;
            packet.caps[i] = (byte) Math.round(Math.max(0f, Math.min(1f, settings.maxWear)) * 100f);
            packet.familyEnabled[i] = settings.enabled;
            // Asked of the resolver rather than read off the settings, because the field there is
            // raw config text: it can name a family this build has never heard of, name the same
            // one twice, or name the family itself. The resolver drops all three, and it is the
            // same call the chain builder makes, so what travels is exactly what this server
            // walks. Ungated - the switches travel beside it.
            java.util.List<SurfaceFamily> after = com.trmtgtnh.config.TrmtConfig.wearsThroughTo(families[i], false);
            byte[] ordinals = new byte[after.size()];
            for (int j = 0; j < ordinals.length; j++) {
                ordinals[j] = (byte) after.get(j)
                    .ordinal();
            }
            packet.successors[i] = ordinals;
            packet.threshMin[i] = settings.thresholdMin;
            packet.threshMax[i] = settings.thresholdMax;
            packet.healDays[i] = (float) settings.healDaysPerStage;
            packet.curves[i] = (byte) (settings.costCurve == null ? -1 : settings.costCurve.ordinal());
        }
        packet.gMultiplierPlayer = com.trmtgtnh.config.TrmtConfig.multiplierPlayer;
        packet.gErosionSpeed = (float) com.trmtgtnh.config.TrmtConfig.erosionSpeed;
        packet.gGlobalSpeed = (float) com.trmtgtnh.config.TrmtConfig.globalSpeed;
        packet.gHealingRate = (float) com.trmtgtnh.config.TrmtConfig.healingRate;
        packet.gMaxWearFraction = com.trmtgtnh.config.TrmtConfig.maxWearFraction;
        packet.hasPricing = true;
        packet.tableFingerprint = com.trmtgtnh.surface.SurfaceRegistry.fingerprint();
        packet.hasTable = true;
        return packet;
    }

    public static void sendTo(ServerPlayer player) {
        if (!TrmtNetwork.wired() || player == null) return;
        PacketServerRules packet = current();
        // Worked out per player rather than broadcast, because it is a fact about them. The
        // client is told only so it knows whether to draw the controls; the server asks again
        // before it acts on anything, because a client can be told anything.
        packet.mayEditFamilies = !TrmtConfig.familyEditorOperatorsOnly || player.hasPermissions(2);
        // Before the send, so the ask these rules may prompt finds nothing left from an earlier table.
        TrmtNetwork.tableAnnounced(player);
        TrmtNetwork.sendTo(packet, player);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        forceOverlay = buf.readBoolean();
        mayEditFamilies = buf.readBoolean();
        int modeLength = buf.readUnsignedByte();
        StringBuilder mode = new StringBuilder(modeLength);
        for (int i = 0; i < modeLength && buf.readableBytes() > 0; i++) {
            mode.append((char) buf.readByte());
        }
        decayMode = mode.toString();

        int count = buf.readUnsignedByte();
        if (count > buf.readableBytes() / 3) count = buf.readableBytes() / 3;
        stages = new byte[count];
        maxSink = new byte[count];
        sinkStart = new byte[count];
        for (int i = 0; i < count; i++) {
            stages[i] = buf.readByte();
            maxSink[i] = buf.readByte();
            sinkStart[i] = buf.readByte();
        }

        // The pricing tail is optional: an older server sends nothing past here, and the Wear
        // Table simply shows no server column rather than the read desyncing. A version byte
        // guards each new addition.
        if (buf.readableBytes() < 1) return;
        int format = buf.readUnsignedByte();
        if (format < PRICING_FORMAT || buf.readableBytes() < 1) return;
        int priced = buf.readUnsignedByte();
        if (priced > buf.readableBytes() / 12) priced = buf.readableBytes() / 12;
        threshMin = new float[priced];
        threshMax = new float[priced];
        healDays = new float[priced];
        for (int i = 0; i < priced; i++) {
            threshMin[i] = buf.readFloat();
            threshMax[i] = buf.readFloat();
            healDays[i] = buf.readFloat();
        }
        if (buf.readableBytes() < 20) return;
        gMultiplierPlayer = buf.readFloat();
        gErosionSpeed = buf.readFloat();
        gGlobalSpeed = buf.readFloat();
        gHealingRate = buf.readFloat();
        gMaxWearFraction = buf.readFloat();
        hasPricing = true;

        // The curves, optional in their turn. A server from before they existed stops at the line
        // above and its families are read as flat, which is exactly what that server was running.
        if (buf.readableBytes() < 1) return;
        int shaped = buf.readUnsignedByte();
        if (shaped > buf.readableBytes()) shaped = buf.readableBytes();
        curves = new byte[shaped];
        for (int i = 0; i < shaped; i++) {
            curves[i] = buf.readByte();
        }

        // The geometry tail, optional in its turn and guarded by a version of its own. A server
        // from before it stops at the line above, and both arrays stay null - which is read as
        // "this server said nothing" rather than as a figure it never sent.
        if (buf.readableBytes() < 2) return;
        int geometry = buf.readUnsignedByte();
        if (geometry < GEOMETRY_FORMAT || buf.readableBytes() < 1) return;
        int shaped2 = buf.readUnsignedByte();
        if (shaped2 > buf.readableBytes() / 2) shaped2 = buf.readableBytes() / 2;
        depths = new byte[shaped2];
        caps = new byte[shaped2];
        for (int i = 0; i < shaped2; i++) {
            depths[i] = buf.readByte();
            caps[i] = buf.readByte();
        }

        // The chain tail, and the only one that is not a fixed width a family, so every read
        // below is guarded against the buffer running out rather than against a count worked
        // out in advance. A truncated tail leaves the families it did reach applied and the
        // rest on this client's own lists, which is the same answer an older server gives.
        if (buf.readableBytes() < 3) return;
        int chainFormat = buf.readUnsignedByte();
        if (chainFormat < CHAIN_FORMAT || buf.readableBytes() < 2) return;
        int flags = buf.readUnsignedByte();
        int chained = buf.readUnsignedByte();
        if (chained > buf.readableBytes()) chained = buf.readableBytes();
        successors = new byte[chained][];
        familyEnabled = new boolean[chained];
        for (int i = 0; i < chained; i++) {
            if (buf.readableBytes() < 1) return;
            int header = buf.readUnsignedByte();
            familyEnabled[i] = (header & 0x80) != 0;
            int listed = header & 0x7F;
            if (listed > buf.readableBytes()) listed = buf.readableBytes();
            byte[] ordinals = new byte[listed];
            for (int j = 0; j < listed; j++) {
                ordinals[j] = buf.readByte();
            }
            successors[i] = ordinals;
        }
        // Last, so a tail that ran out part way leaves the switches unset and the lists it did
        // read gated by this client's own - which is what it was doing before any of this
        // travelled, rather than a third behaviour nobody has thought about.
        chainSwitches = flags;

        // The table tail. A fingerprint only; see tableFingerprint.
        if (buf.readableBytes() < 9) return;
        int tableFormat = buf.readUnsignedByte();
        if (tableFormat < TABLE_FORMAT || buf.readableBytes() < 8) return;
        tableFingerprint = buf.readLong();
        hasTable = true;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(forceOverlay);
        buf.writeBoolean(mayEditFamilies);
        String mode = decayMode == null ? "" : decayMode;
        buf.writeByte(mode.length());
        for (int i = 0; i < mode.length(); i++) {
            buf.writeByte(mode.charAt(i));
        }
        buf.writeByte(stages.length);
        for (int i = 0; i < stages.length; i++) {
            buf.writeByte(stages[i]);
            buf.writeByte(maxSink[i]);
            buf.writeByte(sinkStart[i]);
        }

        buf.writeByte(PRICING_FORMAT);
        int priced = threshMin == null ? 0 : threshMin.length;
        buf.writeByte(priced);
        for (int i = 0; i < priced; i++) {
            buf.writeFloat(threshMin[i]);
            buf.writeFloat(threshMax[i]);
            buf.writeFloat(healDays[i]);
        }
        buf.writeFloat(gMultiplierPlayer);
        buf.writeFloat(gErosionSpeed);
        buf.writeFloat(gGlobalSpeed);
        buf.writeFloat(gHealingRate);
        buf.writeFloat(gMaxWearFraction);

        int shaped = curves == null ? 0 : curves.length;
        buf.writeByte(shaped);
        for (int i = 0; i < shaped; i++) {
            buf.writeByte(curves[i]);
        }

        buf.writeByte(GEOMETRY_FORMAT);
        int shaped2 = depths == null ? 0 : depths.length;
        buf.writeByte(shaped2);
        for (int i = 0; i < shaped2; i++) {
            buf.writeByte(depths[i]);
            buf.writeByte(caps == null || i >= caps.length ? 100 : caps[i]);
        }

        buf.writeByte(CHAIN_FORMAT);
        buf.writeByte(chainSwitches < 0 ? 0 : chainSwitches);
        int chained = successors == null ? 0 : successors.length;
        buf.writeByte(chained);
        for (int i = 0; i < chained; i++) {
            byte[] ordinals = successors[i] == null ? new byte[0] : successors[i];
            // Seven bits for the count and the top one for whether the family wears at all.
            // Seven is more room than the list can use: a family may not name itself and the
            // set it can name is the twelve families, so the longest honest list is eleven.
            int listed = Math.min(ordinals.length, 0x7F);
            boolean on = familyEnabled == null || i >= familyEnabled.length || familyEnabled[i];
            buf.writeByte((on ? 0x80 : 0) | listed);
            for (int j = 0; j < listed; j++) {
                buf.writeByte(ordinals[j]);
            }
        }

        buf.writeByte(TABLE_FORMAT);
        buf.writeLong(tableFingerprint);
    }

    /** The server's pricing as the Wear Table wants it, or null when this server sent none. */
    private com.trmtgtnh.erosion.WearMath.Pricing pricing() {
        if (!hasPricing || threshMin == null) return null;
        double[] avg = new double[threshMin.length];
        double[] heal = new double[threshMin.length];
        for (int i = 0; i < threshMin.length; i++) {
            avg[i] = (threshMin[i] + threshMax[i]) / 2d;
            heal[i] = healDays[i];
        }
        return new com.trmtgtnh.erosion.WearMath.Pricing(
            gMultiplierPlayer,
            gErosionSpeed,
            gGlobalSpeed,
            gHealingRate,
            gMaxWearFraction,
            ceilings(),
            avg,
            heal,
            shapes());
    }

    /**
     * The curves as this packet carried them, or null where it carried none.
     *
     * <p>
     * Null rather than an array of flats, and the difference matters: null means a server that
     * predates curves, whose families really were flat, and an array means a server that named
     * them. Both end up pricing the same way, but only one of them is an assumption.
     */
    private com.trmtgtnh.erosion.CostCurve[] shapes() {
        if (curves == null) return null;
        com.trmtgtnh.erosion.CostCurve[] all = com.trmtgtnh.erosion.CostCurve.values();
        com.trmtgtnh.erosion.CostCurve[] out = new com.trmtgtnh.erosion.CostCurve[curves.length];
        for (int i = 0; i < curves.length; i++) {
            int ordinal = curves[i];
            out[i] = ordinal >= 0 && ordinal < all.length ? all[ordinal] : com.trmtgtnh.erosion.CostCurve.FLAT;
        }
        return out;
    }

    /**
     * Each family's ceiling as a fraction, or null where this server sent none.
     *
     * <p>
     * Null rather than an array of ones, on the same distinction the curves keep: a server that
     * predates the tail could not have said, and one that filled it did.
     */
    private double[] ceilings() {
        if (caps == null) return null;
        double[] out = new double[caps.length];
        for (int i = 0; i < caps.length; i++) {
            int hundredths = caps[i] & 0xFF;
            out[i] = hundredths >= 100 ? 1d : hundredths / 100d;
        }
        return out;
    }

    public static class Handler implements Receiver<PacketServerRules> {

        @Override
        public void onMessage(final PacketServerRules message, ServerPlayer from) {
            MainThread.onClient(new Runnable() {

                @Override
                public void run() {
                    Client.setMayEditFamilies(message.mayEditFamilies);
                    Client.setServerPricing(message.pricing());
                    Client.applyServerRules(
                        message.forceOverlay,
                        message.decayMode,
                        new com.trmtgtnh.config.ServerRules.Geometry(
                            message.stages,
                            message.maxSink,
                            message.sinkStart,
                            message.depths,
                            message.successors,
                            message.familyEnabled,
                            message.chainSwitches));
                    // After the rules, so a table that arrives lands on the geometry it belongs to.
                    Client.considerServerTable(message.hasTable, message.tableFingerprint);
                }
            });
            return;
        }
    }
}
