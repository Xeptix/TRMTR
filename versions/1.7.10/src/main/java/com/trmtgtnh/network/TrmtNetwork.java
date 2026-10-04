package com.trmtgtnh.network;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.erosion.ChunkErosionData;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

/**
 * The channel between the server's erosion data and each client's overlay.
 *
 * <p>
 * Nothing is sent to a player until their client announces itself with a
 * {@link PacketHello}, so a client that has not asked is sent nothing, and a server that has
 * not been asked sends nothing. It is not what makes the mod optional - a client without it
 * cannot join a server that has it at all, since Forge refuses a client missing a server's
 * blocks and items - but it doubles as the per-client opt-out: a player who has switched the overlay off simply
 * stops being a subscriber, and the server stops spending bandwidth on them while carrying
 * on tracking for everyone else.
 */
public final class TrmtNetwork {

    private static final String CHANNEL_NAME = "trmtgtnh";

    public static SimpleNetworkWrapper channel;

    /**
     * Players who want overlay updates. Keyed by UUID so it survives dimension changes and
     * respawns.
     *
     * <p>
     * Concurrent as insurance rather than of need. In 1.7.10 everything that reads or writes it runs on
     * the server thread: the answer to a hello and every reconciliation, the chunk watch, the sends, and a
     * logout, which the connection's tick announces. A concurrent set costs nothing over a plain one, and
     * stays sound for anything that reaches it from another thread, as packet handlers do from 1.8 to
     * 1.12.2.
     */
    private static final Set<UUID> subscribers = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());

    /**
     * What each player asked for when its client said hello, kept whether or not it was granted.
     *
     * <p>
     * The subscriber set above records the answer, and the answer rests on things that change while
     * players stay connected: whether the mod is enabled, and whether ruts are real. A player whose
     * hello arrived while the mod was off was refused, and with the question thrown away there was
     * nothing to ask again when it came back on - they saw no wear at all until they reconnected.
     * Present only for a client that has said hello, which is how a player still part way through
     * joining is told apart from one that said no.
     */
    private static final java.util.Map<UUID, Boolean> wanted = new ConcurrentHashMap<UUID, Boolean>();

    private TrmtNetwork() {}

    public static void init() {
        channel = NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL_NAME);
        channel.registerMessage(PacketHello.Handler.class, PacketHello.class, 0, Side.SERVER);
        channel.registerMessage(PacketChunkErosion.Handler.class, PacketChunkErosion.class, 1, Side.CLIENT);
        channel.registerMessage(PacketErosionDelta.Handler.class, PacketErosionDelta.class, 2, Side.CLIENT);
        channel.registerMessage(PacketClearAll.Handler.class, PacketClearAll.class, 3, Side.CLIENT);
        channel.registerMessage(PacketServerRules.Handler.class, PacketServerRules.class, 4, Side.CLIENT);
        channel.registerMessage(PacketInspect.Handler.class, PacketInspect.class, 5, Side.SERVER);
        channel.registerMessage(PacketInspectResult.Handler.class, PacketInspectResult.class, 6, Side.CLIENT);
        channel.registerMessage(PacketTamperSettings.Handler.class, PacketTamperSettings.class, 7, Side.SERVER);
        channel.registerMessage(PacketPushConfig.Handler.class, PacketPushConfig.class, 8, Side.SERVER);
        channel.registerMessage(PacketEditFamily.Handler.class, PacketEditFamily.class, 9, Side.SERVER);
        channel.registerMessage(PacketModifier.Handler.class, PacketModifier.class, 10, Side.SERVER);
        channel.registerMessage(PacketEditMob.Handler.class, PacketEditMob.class, 11, Side.SERVER);
        channel.registerMessage(PacketDevPreview.Handler.class, PacketDevPreview.class, 12, Side.CLIENT);
        channel.registerMessage(PacketSnapshotState.Handler.class, PacketSnapshotState.class, 13, Side.CLIENT);
        channel.registerMessage(PacketSnapshotAction.Handler.class, PacketSnapshotAction.class, 14, Side.SERVER);
        channel.registerMessage(PacketChunkLight.Handler.class, PacketChunkLight.class, 15, Side.CLIENT);
        channel.registerMessage(PacketLightDelta.Handler.class, PacketLightDelta.class, 16, Side.CLIENT);
        channel.registerMessage(PacketAirSwing.Handler.class, PacketAirSwing.class, 17, Side.SERVER);
        channel.registerMessage(PacketGolemHome.Handler.class, PacketGolemHome.class, 18, Side.SERVER);
        channel
            .registerMessage(PacketSurfaceTableRequest.Handler.class, PacketSurfaceTableRequest.class, 19, Side.SERVER);
        channel.registerMessage(PacketSurfaceTable.Handler.class, PacketSurfaceTable.class, 20, Side.CLIENT);
    }

    // ------------------------------------------------------------------
    // Subscriptions
    // ------------------------------------------------------------------

    /** Tells the server this client's settings modifier changed state. Client side only. */
    public static void sendModifier(boolean down) {
        if (channel != null) channel.sendToServer(new PacketModifier(down));
    }

    /**
     * Client side: a tool was swung, and if it was swung at nothing the server is told.
     *
     * <p>
     * The whole test lives here so both tools share one copy of it. {@code onEntitySwing} fires for
     * a swing at a block and at an entity as well, on both sides, and for every other player's
     * swing replayed on this client - so the checks are that this world is the client's, that the
     * swinger is the player at this keyboard, and that the crosshair is on nothing. A swing at a
     * block sends nothing, because {@code PlayerInteractEvent} is already carrying that one.
     */
    public static void sendAirSwingIfMissed(EntityPlayer swinger) {
        if (channel == null || swinger == null) return;
        if (swinger.worldObj == null || !swinger.worldObj.isRemote) return;
        if (!Trmt.proxy.swungAtNothing(swinger)) return;
        channel.sendToServer(new PacketAirSwing());
    }

    public static void setSubscribed(EntityPlayerMP player, boolean subscribed) {
        if (player == null) return;
        UUID id = player.getUniqueID();
        if (id == null) return;
        if (subscribed) {
            subscribers.add(id);
        } else {
            subscribers.remove(id);
        }
    }

    public static boolean isSubscribed(EntityPlayer player) {
        UUID id = player == null ? null : player.getUniqueID();
        return id != null && subscribers.contains(id);
    }

    public static void forget(EntityPlayerMP player) {
        if (player == null || player.getUniqueID() == null) return;
        subscribers.remove(player.getUniqueID());
        wanted.remove(player.getUniqueID());
        tableSent.remove(player.getUniqueID());
    }

    /**
     * Answers a player's hello, on the server thread, by the same rule a reconciliation uses.
     *
     * <p>
     * Handed to the end of the tick by the hello's handler rather than decided in it. In 1.7.10 that
     * handler already runs on the server thread, straight from the connection's queue, so on this version
     * the move buys no safety: whichever runs first, the answer or a reload, /trmt enable or /trmt
     * disable, the reconciliation that follows each of those brings the player into line. It stays for two
     * reasons. From 1.8 to 1.12.2 handlers run on the network thread, where deciding in the handler could
     * read the mod as off a moment before an enable switched it on and write that stale answer over the
     * enable's, leaving a player who had asked for wear unsubscribed while the mod was on. And sending the
     * surroundings walks the loaded chunks, where a throw costs a warning from the queue rather than the
     * player's connection. A refusal still sends the rules, which a client needs whether or not it is
     * shown any ground, and a clear, so nothing left from a previous server is drawn.
     */
    public static void answerHello(EntityPlayerMP player) {
        UUID id = player == null ? null : player.getUniqueID();
        Boolean asked = id == null ? null : wanted.get(id);
        // Gone again before the answer, and forgotten on the way out.
        if (asked == null) return;
        boolean should = shouldReceive(asked.booleanValue());
        setSubscribed(player, should);
        PacketServerRules.sendTo(player);
        if (should) {
            sendSurroundings(player);
        } else {
            sendClearAll(player);
        }
    }

    /**
     * Which surface table each player was last sent, by fingerprint, since the rules last named one to
     * them. So an ask that crossed with a table already on its way costs nothing.
     */
    private static final java.util.Map<UUID, Long> tableSent = new ConcurrentHashMap<UUID, Long>();

    /** Client side: asks the server for the surface table it named in its rules. */
    public static void requestSurfaceTable(long fingerprint) {
        if (channel != null) channel.sendToServer(new PacketSurfaceTableRequest(fingerprint));
    }

    /**
     * Forgets which table a player was sent, because the rules are about to name one to them again.
     *
     * <p>
     * A client stops using a server's table without a word to the server: it hands the table back the
     * moment the rules name one that matches its own file. A record kept past that point answered the
     * client's next ask for the table it had handed back - an operator undoing a family edit and then
     * making it again is all it took - with silence, and a client asks only once for each table, so it
     * wore its own for the rest of the visit while the server wore another. Forgotten on every
     * announcement rather than only a different one, so no record outlives the rules it answered: the
     * next ask is always for a table the client has just been told about.
     */
    public static void tableAnnounced(EntityPlayerMP player) {
        if (player != null && player.getUniqueID() != null) tableSent.remove(player.getUniqueID());
    }

    /**
     * Sends a player the surface table in use. Server thread.
     *
     * <p>
     * Skipped for a player already sent this very table since the rules last named one to them, which
     * is all a repeated ask can be. Refused,
     * and said once, for a table too large to send whole: the client then keeps its own, which is
     * the behaviour every version before this had.
     */
    public static void sendSurfaceTable(EntityPlayerMP player) {
        if (channel == null || player == null || player.getUniqueID() == null) return;
        long fingerprint = com.trmtgtnh.surface.SurfaceRegistry.fingerprint();
        Long already = tableSent.get(player.getUniqueID());
        if (already != null && already.longValue() == fingerprint) return;
        byte[] bytes;
        try {
            bytes = com.trmtgtnh.surface.SurfaceRegistry.encodedTable();
        } catch (RuntimeException unpackable) {
            Trmt.LOG.warn(
                "Could not pack the surface table for {}; they keep their own",
                player.getCommandSenderName(),
                unpackable);
            return;
        }
        if (bytes.length > com.trmtgtnh.surface.SurfaceTableCodec.MAX_COMPRESSED) {
            if (!tableTooLargeSaid) {
                tableTooLargeSaid = true;
                Trmt.LOG.warn(
                    "The surface table is {} bytes compressed, too large to send; clients keep their own",
                    Integer.valueOf(bytes.length));
            }
            return;
        }
        tableSent.put(player.getUniqueID(), Long.valueOf(fingerprint));
        channel.sendTo(
            new PacketSurfaceTable(fingerprint, com.trmtgtnh.surface.SurfaceRegistry.groundCoverHoldsOn(), bytes),
            player);
    }

    private static volatile boolean tableTooLargeSaid;

    /**
     * Records what a client asked for in its hello, from the hello's handler: the server thread in 1.7.10,
     * like every other touch of this map. Concurrent anyway, for the reason the subscriber set gives.
     */
    public static void rememberWanted(EntityPlayerMP player, boolean wants) {
        UUID id = player == null ? null : player.getUniqueID();
        if (id != null) wanted.put(id, Boolean.valueOf(wants));
    }

    /**
     * Whether a player who asked this should be receiving wear under the rules as they stand.
     *
     * <p>
     * The one rule, asked by a hello and by every later reconciliation alike, so the two cannot come
     * to disagree. Real ruts subscribe a client whatever it asked, because it will be walking down
     * into hollows and had better be able to see them; nothing is sent while the mod is off.
     */
    public static boolean shouldReceive(boolean wants) {
        return (wants || TrmtConfig.physicalDecayCollides()) && TrmtConfig.enabled;
    }

    /**
     * Brings every connected player's subscription into line with the rules as they now stand.
     *
     * <p>
     * A hello is answered once, when a player joins, and until this existed nothing asked the
     * question again. So the two changes that should start wear flowing to a player already
     * connected - the mod being enabled, and ruts becoming real - started nothing: whoever they newly
     * applied to saw no wear until they rejoined, and re-enabling after a disable left every player
     * looking at the empty overlay the disable had cleared. Anybody who should now receive wear is
     * subscribed and sent the rules and what is loaded around them, exactly as a join does; anybody
     * who should not is let go and sent a clear. The clear is not a courtesy: a client that stops
     * receiving updates but keeps its cache holds wear that goes on changing without it, and paints
     * every bit of that stale picture straight back the moment it is subscribed again.
     *
     * <p>
     * Server thread only, and a no-op without a running server - which is what makes it safe to call
     * from a reload that, on a client connected to somebody else's server, runs on the client thread.
     * Idempotent: a player whose subscription is already right is not touched, so calling it after
     * every reload costs a walk over the player list and nothing else.
     */
    public static void reconcileSubscriptions() {
        if (Trmt.runningServer() == null) return;
        // Every caller today is already on the server thread, or on a client with no server of its own and
        // so gone at the line above: CommandTrmt hands a command from any other thread over whole, and a
        // config screen's reload is queued. This walks the player list and the loaded chunks, which only
        // the server thread may touch, so anything that does arrive from elsewhere is queued there
        // instead, once.
        if (!Trmt.onServerThread()) {
            com.trmtgtnh.util.MainThread.onServer(new Runnable() {

                @Override
                public void run() {
                    reconcileNow();
                }
            });
            return;
        }
        reconcileNow();
    }

    private static void reconcileNow() {
        net.minecraft.server.MinecraftServer server = Trmt.runningServer();
        if (channel == null || server == null || server.getConfigurationManager() == null) return;
        int joined = 0;
        for (Object candidate : server.getConfigurationManager().playerEntityList) {
            if (!(candidate instanceof EntityPlayerMP)) continue;
            EntityPlayerMP player = (EntityPlayerMP) candidate;
            UUID id = player.getUniqueID();
            Boolean asked = id == null ? null : wanted.get(id);
            // No hello yet, so no client of this mod to send anything to.
            if (asked == null) continue;
            boolean should = shouldReceive(asked.booleanValue());
            if (should == isSubscribed(player)) continue;
            setSubscribed(player, should);
            if (!should) {
                sendClearAll(player);
                continue;
            }
            PacketServerRules.sendTo(player);
            sendSurroundings(player);
            joined++;
        }
        if (joined > 0) {
            Trmt.LOG.info(
                "Sent the surrounding wear to {} player(s) the changed rules now subscribe",
                Integer.valueOf(joined));
        }
    }

    /**
     * Sends every chunk a player is already watching. Chunks they start watching later arrive
     * through the normal chunk-watch path.
     *
     * <p>
     * Here rather than inside the hello's handler, because a join is no longer the only moment a
     * player starts receiving wear.
     */
    public static void sendSurroundings(EntityPlayerMP player) {
        if (player == null || player.worldObj == null) return;
        int dimension = player.worldObj.provider.dimensionId;
        int centreX = ((int) Math.floor(player.posX)) >> 4;
        int centreZ = ((int) Math.floor(player.posZ)) >> 4;
        // Only what this player has actually been sent: the same rule every later update follows, so the
        // two cannot disagree about which chunks a client should hold. Walked over a square a chunk wider
        // than the one the server watches, because that square is not quite the one its setting names:
        // the radius is held between three and twenty, and the square is centred where the player last
        // moved a whole chunk rather than where they stand now, which can be a chunk behind. Walked from
        // the setting and the standing position, a ring of watched chunks was sent no wear at all -
        // after /trmt enable, a reload, or a player switching wear back on - and nothing sent it later,
        // because each of those chunks had already had its watch event while the player was not
        // subscribed. The test inside trims the square to what was really sent.
        net.minecraft.server.MinecraftServer server = Trmt.runningServer();
        int range = server == null || server.getConfigurationManager() == null ? TrmtConfig.overlayDistanceChunks
            : net.minecraft.util.MathHelper.clamp_int(
                server.getConfigurationManager()
                    .getViewDistance(),
                3,
                20) + 1;

        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                if (!watching(player.worldObj, player, centreX + dx, centreZ + dz)) continue;
                ChunkErosionData data = com.trmtgtnh.erosion.ErosionStore.get()
                    .getChunk(dimension, centreX + dx, centreZ + dz);
                if (data != null && !data.isEmpty()) {
                    sendChunkTo(player, centreX + dx, centreZ + dz, data);
                }
            }
        }
    }

    public static int subscriberCount() {
        return subscribers.size();
    }

    // ------------------------------------------------------------------
    // Sending
    // ------------------------------------------------------------------

    /** Sends one position's new appearance to everyone near enough to see it. */
    public static void sendDelta(World world, int x, int y, int z, short state) {
        // Nothing goes out while the mod is disabled, here or in the three below. Switching it off
        // clears every client once, and a chunk loading afterwards used to send its wear straight
        // back - so paths reappeared under anybody who moved, while the server said disabled.
        if (world == null || world.isRemote || channel == null || !TrmtConfig.enabled) return;
        PacketErosionDelta packet = new PacketErosionDelta(x, y, z, state);
        for (Object candidate : world.playerEntities) {
            if (!(candidate instanceof EntityPlayerMP)) continue;
            EntityPlayerMP player = (EntityPlayerMP) candidate;
            if (!isSubscribed(player)) continue;
            if (!watching(world, player, x >> 4, z >> 4)) continue;
            channel.sendTo(packet, player);
        }
    }

    /** Sends one position's new glow to everyone near enough to see it. */
    public static void sendLightDelta(World world, int x, int y, int z, int packed) {
        if (world == null || world.isRemote || channel == null || !TrmtConfig.enabled) return;
        PacketLightDelta packet = new PacketLightDelta(x, y, z, packed);
        for (Object candidate : world.playerEntities) {
            if (!(candidate instanceof EntityPlayerMP)) continue;
            EntityPlayerMP player = (EntityPlayerMP) candidate;
            if (!isSubscribed(player)) continue;
            if (!watching(world, player, x >> 4, z >> 4)) continue;
            channel.sendTo(packet, player);
        }
    }

    /**
     * Sends a chunk's lit positions to one player, if any of them are lit.
     *
     * <p>
     * Always paired with the wear packet rather than sent on its own schedule, so a client cannot
     * end up believing in a glow on a chunk whose ground it has not been told about.
     */
    private static void sendLightTo(EntityPlayerMP player, int chunkX, int chunkZ, ChunkErosionData data) {
        if (player == null || channel == null) return;
        PacketChunkLight light = PacketChunkLight.of(chunkX, chunkZ, data);
        if (light != null) channel.sendTo(light, player);
    }

    /** Sends a whole chunk's worth of overlay to everyone watching it. */
    public static void sendChunkToWatchers(World world, int chunkX, int chunkZ, ChunkErosionData data) {
        sendChunkToWatchers(world, chunkX, chunkZ, data, false);
    }

    /**
     * As above, and willing to say that a chunk has nothing left in it.
     *
     * <p>
     * A gesture that removed the last of a chunk's wear has to send that, because the client
     * lifts a ghost when a packet stops listing its position and a packet that is never sent
     * lists nothing at all. Callers that are only reporting what they found pass false, so a
     * chunk load over untouched ground still costs nothing.
     */
    public static void sendChunkToWatchers(World world, int chunkX, int chunkZ, ChunkErosionData data,
        boolean evenIfEmpty) {
        if (world == null || world.isRemote || channel == null || !TrmtConfig.enabled) return;
        if (data == null) return;
        if (data.isEmpty() && !evenIfEmpty) return;

        PacketChunkErosion packet = PacketChunkErosion.of(chunkX, chunkZ, data, evenIfEmpty);
        if (packet == null) return;

        for (Object candidate : world.playerEntities) {
            if (!(candidate instanceof EntityPlayerMP)) continue;
            EntityPlayerMP player = (EntityPlayerMP) candidate;
            if (!isSubscribed(player)) continue;
            if (!watching(world, player, chunkX, chunkZ)) continue;
            channel.sendTo(packet, player);
            sendLightTo(player, chunkX, chunkZ, data);
        }
    }

    /** Sends one chunk to one player, used when that player starts watching it. */
    public static void sendChunkTo(EntityPlayerMP player, int chunkX, int chunkZ, ChunkErosionData data) {
        if (player == null || channel == null || data == null || data.isEmpty()) return;
        if (!TrmtConfig.enabled || !isSubscribed(player)) return;
        PacketChunkErosion packet = PacketChunkErosion.of(chunkX, chunkZ, data);
        if (packet != null) channel.sendTo(packet, player);
        sendLightTo(player, chunkX, chunkZ, data);
    }

    /** Tells every subscriber to drop its overlays, for the server-side kill switch. */
    public static void broadcastClearAll() {
        if (channel == null) return;
        // The running server rather than the last one seen. A reload that turns the mod off runs
        // on the client thread whenever this machine has no server of its own, and a client that
        // opened a single-player world earlier in the session would otherwise walk the stale
        // MinecraftServer that world left behind.
        net.minecraft.server.MinecraftServer server = Trmt.runningServer();
        if (server == null || server.getConfigurationManager() == null) return;
        for (Object candidate : server.getConfigurationManager().playerEntityList) {
            if (candidate instanceof EntityPlayerMP && isSubscribed((EntityPlayerMP) candidate)) {
                channel.sendTo(new PacketClearAll(), (EntityPlayerMP) candidate);
            }
        }
    }

    /** Re-sends the rules to everyone, after a config reload changes them. */
    public static void broadcastRules() {
        // The running server, for the reason broadcastClearAll gives: a reload on a client with no
        // server of its own would otherwise walk one a single-player world left behind.
        net.minecraft.server.MinecraftServer server = Trmt.runningServer();
        if (channel == null || server == null || server.getConfigurationManager() == null) return;
        for (Object candidate : server.getConfigurationManager().playerEntityList) {
            if (candidate instanceof EntityPlayerMP) PacketServerRules.sendTo((EntityPlayerMP) candidate);
        }
    }

    /** Tells one player to drop its overlays and re-request, e.g. after a purge. */
    public static void sendClearAll(EntityPlayerMP player) {
        if (channel != null && player != null) channel.sendTo(new PacketClearAll(), player);
    }

    /**
     * Whether a player has actually been sent this chunk, which is exactly when an update to it is any use.
     *
     * <p>
     * Asked of the server's own chunk bookkeeping rather than of a distance. The distance used here
     * was the server's copy of a client setting, the overlay distance, and it did not match the rule
     * the first send follows: a chunk arrives whole when a player starts watching it, at whatever
     * range that is, and every later change to it was then dropped for being too far away. A player
     * who stood beyond it while somebody wore or mended a road came back to the picture as it was
     * when the chunk was first sent - on real ruts, to a floor the server no longer agreed with. Vanilla
     * sends a chunk's block changes to exactly the players this answers yes for, and Forge's watch
     * event, which carries the whole chunk, fires at the moment it starts to.
     */
    private static boolean watching(World world, EntityPlayerMP player, int chunkX, int chunkZ) {
        if (!(world instanceof net.minecraft.world.WorldServer)) return false;
        return ((net.minecraft.world.WorldServer) world).getPlayerManager()
            .isPlayerWatchingChunk(player, chunkX, chunkZ);
    }

    /** Client side: ask the server for the exact wear numbers at one position. */
    /** Sends a tamper's two settings to the server, which applies them to the held item. */
    public static void sendTamperSettings(int reach, int steps, int mode, int reinforceLevel) {
        channel.sendToServer(new PacketTamperSettings(reach, steps, mode, reinforceLevel));
    }

    /**
     * Tells the server where a golem's home should be, from the golem's own screen.
     *
     * <p>
     * Its own message rather than one of the container's buttons because a button carries one small
     * number and a coordinate is three large ones. Everything the server needs to refuse it - whose
     * screen is open, and whether the place named is anywhere near the golem - is checked there.
     */
    public static void setGolemHome(int entityId, int x, int y, int z) {
        if (channel != null) channel.sendToServer(new PacketGolemHome(entityId, x, y, z));
    }

    /** Shows one player a wear-pattern preview. Their client only; nothing is written anywhere. */
    public static void sendDevPreview(net.minecraft.entity.player.EntityPlayerMP player, int mode) {
        if (channel != null && player != null) channel.sendTo(new PacketDevPreview(mode), player);
    }

    /** Tells one player's snapshot screen which slots hold something. */
    public static void sendSnapshotState(EntityPlayerMP player, int flags) {
        if (channel != null && player != null) channel.sendTo(new PacketSnapshotState(flags), player);
    }

    /** Client side: a button on the snapshot screen was pressed. */
    public static void sendSnapshotAction(int action) {
        if (channel != null) channel.sendToServer(new PacketSnapshotAction(action));
    }

    /** Offers the client's config to the server, which decides whether to take it. */
    public static void pushConfig(java.util.List<String> lines) {
        channel.sendToServer(new PacketPushConfig(lines));
    }

    /** Asks the server to change which entities wear the ground. The server decides whether to. */
    public static void editMob(int action, String entity) {
        if (channel != null) channel.sendToServer(new PacketEditMob(action, entity));
    }

    public static void editFamily(int action, String block, int family) {
        channel.sendToServer(new PacketEditFamily(action, block, family));
    }

    public static void requestInspection(int x, int y, int z) {
        if (channel != null) channel.sendToServer(new PacketInspect(x, y, z));
    }

    /** Client side: announce this client and whether it wants overlays at all. */
    public static void sendHello(boolean wantsOverlays) {
        if (channel != null) channel.sendToServer(new PacketHello(wantsOverlays));
    }

    /** Convenience for logging. */
    public static List<String> describe() {
        return Collections.singletonList("subscribers=" + subscribers.size());
    }
}
