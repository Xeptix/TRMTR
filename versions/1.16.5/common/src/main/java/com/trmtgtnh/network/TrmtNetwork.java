package com.trmtgtnh.network;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.erosion.ChunkErosionData;

/**
 * The channel between the server's erosion data and each client's overlay.
 *
 * <p>
 * Nothing is sent to a player until their client announces itself with a
 * {@link PacketHello}, so a client that has not asked is sent nothing, and a server that has
 * not been asked sends nothing. It is not what makes the mod optional - a client without it
 * cannot join a server that has it at all, since a loader refuses a client missing a server's
 * blocks and items - but it doubles as the per-client opt-out: a player who has switched the overlay off simply
 * stops being a subscriber, and the server stops spending bandwidth on them while carrying
 * on tracking for everyone else.
 *
 * <p>
 * Which messages exist, and in what order, is {@link Packets} rather than a list in this class.
 * Forge needs a number per message and Fabric needs a name per message, so the list is kept once in
 * terms this mod owns and each loader turns it into what it needs. <strong>That order is a wire
 * format</strong> - Forge's ids come from it - which is why it lives in a class of its own with a
 * test on it rather than in the middle of this one.
 *
 * <p>
 * <strong>Every send is here.</strong> {@code sendAirSwingIfMissed} was the last and waited on the
 * asking rather than on the answering: both older editions notice an air swing from an item method
 * Forge adds, and the other loader has nothing of the sort. What asks now is the client's own attack
 * handling, which is vanilla's and the same on both - see {@code AirSwing}.
 *
 */
public final class TrmtNetwork {

    /**
     * What a loader module supplies once it has a channel: two ways to send, and nothing else.
     *
     * <p>
     * Forge has one channel carrying numerically-identified messages and Fabric has a
     * {@code ResourceLocation} per message type with no channel object at all, so the two share no
     * shape worth pretending about. What they do share is these two verbs, and every one of the
     * twenty sends in this class is one of them.
     */
    public interface Channel {

        /** To one player, who is on the other end of a connection this server owns. */
        void sendTo(Message message, ServerPlayer player);

        /** From a client to the server it is connected to. */
        void sendToServer(Message message);
    }

    private static volatile Channel channel;

    private static volatile boolean complained;

    /** Tells this how to send. Called by each loader module once its channel exists. */
    public static void use(Channel loaders) {
        channel = loaders;
    }

    /** Forgets it again. For tests, which must not leak a channel into the next one. */
    public static void forget() {
        channel = null;
        complained = false;
    }

    /** Whether a loader has given this a way to send yet. */
    public static boolean wired() {
        return channel != null;
    }

    /**
     * Sends one message to one player.
     *
     * <p>
     * The argument order is 1.12.2's and is kept on purpose: twenty call sites in this file read
     * {@code channel.sendTo(packet, player)}, and keeping the order means none of them was rewritten
     * to get here. The same goes for the name.
     */
    public static void sendTo(Message message, ServerPlayer player) {
        Channel on = reaching();
        if (on != null && message != null && player != null) on.sendTo(message, player);
    }

    /** Sends one message from this client to its server. */
    public static void sendToServer(Message message) {
        Channel on = reaching();
        if (on != null && message != null) on.sendToServer(message);
    }

    /**
     * The channel, or null having said so once.
     *
     * <p>
     * Once, because the busiest caller is a delta sent as ground wears under somebody walking, and a
     * line per step would be the kind of log nobody reads.
     */
    private static Channel reaching() {
        Channel on = channel;
        if (on == null && !complained) {
            complained = true;
            Trmt.LOG.warn(
                "Nothing has given this edition a way to send, so no client will be told what the "
                    + "erosion engine changes. The ground still wears and heals; nobody is watching. "
                    + "The loader module should call TrmtNetwork.use() as it starts; this is said "
                    + "once.");
        }
        return on;
    }

    /**
     * Players who want overlay updates. Keyed by UUID so it survives dimension changes and
     * respawns.
     *
     * <p>
     * Concurrent of need here, where the other edition had it as insurance. It wrote that a concurrent set
     * costs nothing over a plain one and stays sound for anything reaching it from another thread, as
     * packet handlers do from 1.8 to 1.12.2. This is that version: a hello arrives on the network thread
     * and writes this set while the server thread is reading it to decide who to send a delta to.
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

    // ------------------------------------------------------------------
    /** Shows one player a wear-pattern preview. Their client only; nothing is written anywhere. */
    public static void sendDevPreview(ServerPlayer player, int mode) {
        if (wired() && player != null) sendTo(new PacketDevPreview(mode), player);
    }

    /** Tells one player's snapshot screen which slots hold something. */
    public static void sendSnapshotState(ServerPlayer player, int flags) {
        if (wired() && player != null) sendTo(new PacketSnapshotState(flags), player);
    }

    /** Client side: ask the server for the exact wear numbers at one position. */
    public static void requestInspection(int x, int y, int z) {
        if (wired()) sendToServer(new PacketInspect(x, y, z));
    }

    /** Client side: the modifier key went down or came up, which the tools read. */
    public static void sendModifier(boolean down) {
        if (wired()) sendToServer(new PacketModifier(down));
    }

    /** Sends a tamper's settings to the server, which applies them to the held item. */
    public static void sendTamperSettings(int reach, int steps, int mode, int reinforceLevel) {
        if (wired()) sendToServer(new PacketTamperSettings(reach, steps, mode, reinforceLevel));
    }

    /** Client side: a button on the snapshot screen. */
    public static void sendSnapshotAction(int action) {
        if (wired()) sendToServer(new PacketSnapshotAction(action));
    }

    /** Client side: a whole config, pushed from the editor to the server that will keep it. */
    public static void pushConfig(java.util.List<String> lines) {
        if (wired()) sendToServer(new PacketPushConfig(lines));
    }

    /** Asks the server to change what a mob does to the ground. Refused there unless allowed. */
    public static void editMob(int action, String entity) {
        if (wired()) sendToServer(new PacketEditMob(action, entity));
    }

    /**
     * Client side: a tool was swung, and if it was swung at nothing the server is told.
     *
     * <p>
     * The checks that decide whether there is anything to tell live in {@code AirSwing}, which is
     * where the question is asked from; this is only the sending. Both older editions put the whole
     * test in this method because the question is asked from an item method there and the item has
     * nowhere better to put it.
     */
    public static void sendAirSwingIfMissed(net.minecraft.world.entity.player.Player swinger) {
        if (wired() && swinger != null) sendToServer(new PacketAirSwing());
    }

    /** Asks the server to file a block under a surface family, or to stop it wearing at all. */
    public static void editFamily(int action, String block, int family) {
        if (wired()) sendToServer(new PacketEditFamily(action, block, family));
    }

    /**
     * Client side: move a golem's home to a place somebody typed rather than walked to.
     *
     * <p>
     * The screen's "here" button goes through the container's own button channel, because that is a
     * press on a window the server already has open. A typed coordinate cannot go that way - a
     * button press carries one number and this carries three - so it is a message of its own, and
     * the only one the golem needs.
     *
     * <p>
     * Unchecked here on purpose. The screen bounds-checks before sending, so a place the server
     * would refuse is one it never claims to have sent; and {@link PacketGolemHome} checks again on
     * arrival, because the first check is on the far side of a connection.
     */
    public static void setGolemHome(int entityId, int x, int y, int z) {
        if (wired()) sendToServer(new PacketGolemHome(entityId, x, y, z));
    }

    // ------------------------------------------------------------------
    // Subscriptions
    // ------------------------------------------------------------------

    public static void setSubscribed(ServerPlayer player, boolean subscribed) {
        if (player == null) return;
        UUID id = player.getUUID();
        if (id == null) return;
        if (subscribed) {
            subscribers.add(id);
        } else {
            subscribers.remove(id);
        }
    }

    public static boolean isSubscribed(Player player) {
        UUID id = player == null ? null : player.getUUID();
        return id != null && subscribers.contains(id);
    }

    public static void forget(ServerPlayer player) {
        if (player == null || player.getUUID() == null) return;
        subscribers.remove(player.getUUID());
        wanted.remove(player.getUUID());
        tableSent.remove(player.getUUID());
    }

    /**
     * Answers a player's hello, on the server thread, by the same rule a reconciliation uses.
     *
     * <p>
     * Handed to the end of the tick by the hello's handler rather than decided in it, and here that is
     * load-bearing rather than tidy. In 1.7.10 the handler already runs on the server thread and the move
     * bought no safety; the other edition kept it anyway, against the version this one is. Deciding in the
     * handler on 1.12.2 means deciding on the network thread, which could read the mod as off a moment
     * before an enable switched it on and write that stale answer over the enable's, leaving a player who
     * had asked for wear unsubscribed while the mod was on. And sending the surroundings walks the loaded
     * chunks, which only the server thread may do at all - so on this version the queue is not a
     * precaution against a throw but the only correct place for this work. A refusal still sends the rules, which a
     * client needs whether or not it is
     * shown any ground, and a clear, so nothing left from a previous server is drawn.
     */
    public static void answerHello(ServerPlayer player) {
        UUID id = player == null ? null : player.getUUID();
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
        if (wired()) sendToServer(new PacketSurfaceTableRequest(fingerprint));
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
    public static void tableAnnounced(ServerPlayer player) {
        if (player != null && player.getUUID() != null) tableSent.remove(player.getUUID());
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
    public static void sendSurfaceTable(ServerPlayer player) {
        if (!wired() || player == null || player.getUUID() == null) return;
        long fingerprint = com.trmtgtnh.surface.SurfaceRegistry.fingerprint();
        Long already = tableSent.get(player.getUUID());
        if (already != null && already.longValue() == fingerprint) return;
        byte[] bytes;
        try {
            bytes = com.trmtgtnh.surface.SurfaceRegistry.encodedTable();
        } catch (RuntimeException unpackable) {
            Trmt.LOG.warn("Could not pack the surface table for {}; they keep their own", player.getName(), unpackable);
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
        tableSent.put(player.getUUID(), Long.valueOf(fingerprint));
        sendTo(
            new PacketSurfaceTable(fingerprint, com.trmtgtnh.surface.SurfaceRegistry.groundCoverHoldsOn(), bytes),
            player);
    }

    private static volatile boolean tableTooLargeSaid;

    /**
     * Records what a client asked for in its hello, from the hello's handler: the server thread in 1.7.10,
     * like every other touch of this map. Concurrent anyway, for the reason the subscriber set gives.
     */
    public static void rememberWanted(ServerPlayer player, boolean wants) {
        UUID id = player == null ? null : player.getUUID();
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
        if (!wired() || server == null || server.getPlayerList() == null) return;
        int joined = 0;
        for (Object candidate : server.getPlayerList()
            .getPlayers()) {
            if (!(candidate instanceof ServerPlayer)) continue;
            ServerPlayer player = (ServerPlayer) candidate;
            UUID id = player.getUUID();
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
    public static void sendSurroundings(ServerPlayer player) {
        if (player == null || player.level == null) return;
        int dimension = com.trmtgtnh.erosion.ErosionStore.get()
            .indexOf(player.level);
        int centreX = ((int) Math.floor(player.getX())) >> 4;
        int centreZ = ((int) Math.floor(player.getZ())) >> 4;
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
        int range = server == null || server.getPlayerList() == null ? TrmtConfig.overlayDistanceChunks
            : net.minecraft.util.Mth.clamp(
                server.getPlayerList()
                    .getViewDistance(),
                3,
                20) + 1;

        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                if (!watching(player.level, player, centreX + dx, centreZ + dz)) continue;
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
    public static void sendDelta(Level world, int x, int y, int z, short state) {
        // Nothing goes out while the mod is disabled, here or in the three below. Switching it off
        // clears every client once, and a chunk loading afterwards used to send its wear straight
        // back - so paths reappeared under anybody who moved, while the server said disabled.
        if (world == null || world.isClientSide() || !wired() || !TrmtConfig.enabled) return;
        PacketErosionDelta packet = new PacketErosionDelta(x, y, z, state);
        for (Object candidate : world.players()) {
            if (!(candidate instanceof ServerPlayer)) continue;
            ServerPlayer player = (ServerPlayer) candidate;
            if (!isSubscribed(player)) continue;
            if (!watching(world, player, x >> 4, z >> 4)) continue;
            sendTo(packet, player);
        }
    }

    /** Sends one position's new glow to everyone near enough to see it. */
    public static void sendLightDelta(Level world, int x, int y, int z, int packed) {
        if (world == null || world.isClientSide() || !wired() || !TrmtConfig.enabled) return;
        PacketLightDelta packet = new PacketLightDelta(x, y, z, packed);
        for (Object candidate : world.players()) {
            if (!(candidate instanceof ServerPlayer)) continue;
            ServerPlayer player = (ServerPlayer) candidate;
            if (!isSubscribed(player)) continue;
            if (!watching(world, player, x >> 4, z >> 4)) continue;
            sendTo(packet, player);
        }
    }

    /**
     * Sends a chunk's lit positions to one player, if any of them are lit.
     *
     * <p>
     * Always paired with the wear packet rather than sent on its own schedule, so a client cannot
     * end up believing in a glow on a chunk whose ground it has not been told about.
     */
    private static void sendLightTo(ServerPlayer player, int chunkX, int chunkZ, ChunkErosionData data) {
        if (player == null || !wired()) return;
        PacketChunkLight light = PacketChunkLight.of(chunkX, chunkZ, data);
        if (light != null) sendTo(light, player);
    }

    /** Sends a whole chunk's worth of overlay to everyone watching it. */
    public static void sendChunkToWatchers(Level world, int chunkX, int chunkZ, ChunkErosionData data) {
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
    public static void sendChunkToWatchers(Level world, int chunkX, int chunkZ, ChunkErosionData data,
        boolean evenIfEmpty) {
        if (world == null || world.isClientSide() || !wired() || !TrmtConfig.enabled) return;
        if (data == null) return;
        if (data.isEmpty() && !evenIfEmpty) return;

        PacketChunkErosion packet = PacketChunkErosion.of(chunkX, chunkZ, data, evenIfEmpty);
        if (packet == null) return;

        for (Object candidate : world.players()) {
            if (!(candidate instanceof ServerPlayer)) continue;
            ServerPlayer player = (ServerPlayer) candidate;
            if (!isSubscribed(player)) continue;
            if (!watching(world, player, chunkX, chunkZ)) continue;
            sendTo(packet, player);
            sendLightTo(player, chunkX, chunkZ, data);
        }
    }

    /** Sends one chunk to one player, used when that player starts watching it. */
    public static void sendChunkTo(ServerPlayer player, int chunkX, int chunkZ, ChunkErosionData data) {
        if (player == null || !wired() || data == null || data.isEmpty()) return;
        if (!TrmtConfig.enabled || !isSubscribed(player)) return;
        PacketChunkErosion packet = PacketChunkErosion.of(chunkX, chunkZ, data);
        if (packet != null) sendTo(packet, player);
        sendLightTo(player, chunkX, chunkZ, data);
    }

    /** Tells every subscriber to drop its overlays, for the server-side kill switch. */
    public static void broadcastClearAll() {
        if (!wired()) return;
        // The running server rather than the last one seen. A reload that turns the mod off runs
        // on the client thread whenever this machine has no server of its own, and a client that
        // opened a single-player world earlier in the session would otherwise walk the stale
        // MinecraftServer that world left behind.
        net.minecraft.server.MinecraftServer server = Trmt.runningServer();
        if (server == null || server.getPlayerList() == null) return;
        for (Object candidate : server.getPlayerList()
            .getPlayers()) {
            if (candidate instanceof ServerPlayer && isSubscribed((ServerPlayer) candidate)) {
                sendTo(new PacketClearAll(), (ServerPlayer) candidate);
            }
        }
    }

    /** Re-sends the rules to everyone, after a config reload changes them. */
    public static void broadcastRules() {
        // The running server, for the reason broadcastClearAll gives: a reload on a client with no
        // server of its own would otherwise walk one a single-player world left behind.
        net.minecraft.server.MinecraftServer server = Trmt.runningServer();
        if (!wired() || server == null || server.getPlayerList() == null) return;
        for (Object candidate : server.getPlayerList()
            .getPlayers()) {
            if (candidate instanceof ServerPlayer) PacketServerRules.sendTo((ServerPlayer) candidate);
        }
    }

    /** Tells one player to drop its overlays and re-request, e.g. after a purge. */
    public static void sendClearAll(ServerPlayer player) {
        if (wired() && player != null) sendTo(new PacketClearAll(), player);
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
    private static boolean watching(Level world, ServerPlayer player, int chunkX, int chunkZ) {
        if (!(world instanceof net.minecraft.server.level.ServerLevel)) return false;
        // getPlayers rather than isPlayerWatchingChunk, which is no longer a question anything can
        // ask. The boolean asks for the chunk's boundary only; false means everyone it is sent to.
        return ((net.minecraft.server.level.ServerLevel) world).getChunkSource().chunkMap
            .getPlayers(new net.minecraft.world.level.ChunkPos(chunkX, chunkZ), false)
            .anyMatch(watcher -> watcher == player);
    }

    /** Client side: announce this client and whether it wants overlays at all. */
    public static void sendHello(boolean wantsOverlays) {
        if (wired()) sendToServer(new PacketHello(wantsOverlays));
    }

    /** Convenience for logging. */
    public static List<String> describe() {
        return Collections.singletonList("subscribers=" + subscribers.size());
    }
}
