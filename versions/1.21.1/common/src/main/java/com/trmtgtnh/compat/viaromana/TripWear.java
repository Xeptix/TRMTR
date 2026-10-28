package com.trmtgtnh.compat.viaromana;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.erosion.ErosionEngine;
import com.trmtgtnh.surface.SurfaceRegistry;
import com.trmtgtnh.util.Worlds;

/**
 * A fast trip along a charted road wears the road as if it had been walked: the second part of the support (0.9.228),
 * off unless integration.viaRomanaTripWear is on.
 *
 * <p>
 * Via Romana sends a trip as {@code network.packets.TeleportRequestC2S}, whose {@code handle(Level, Player)} hands it to
 * {@code teleport.ServerTeleportHandler.handleTeleportRequest}; that holds it twenty ticks while the screen fades, then
 * moves the player beside the destination node, or tells them it is unsafe and moves nobody. It keeps no route: the
 * trip is a jump from one node of a road to another. So {@code MixinViaRomanaTrip} notes each request here as it is
 * handled, and the server's tick waits for the player to arrive; a trip that never lands - an unsafe destination, a
 * player who died or left - wears nothing. On arrival the road is found again from Via Romana's own graph, as the
 * fewest nodes from the one departed from to the one arrived at, and each square of its line is given one ordinary
 * player step times integration.viaRomanaTripSteps, through the same step a footstep takes, with the squares beside the
 * line given what a step bleeds onto them sideways (general.bleedSide). The squares in chunks nobody has loaded are not
 * loaded for it and wear nothing, as a step would not.
 */
public final class TripWear {

    /** How long after its request a trip may take to land, in server ticks: Via Romana's own is twenty. */
    static final int LANDS_WITHIN = 100;

    /** How near the destination node a player must be to have arrived; Via Romana lands them within two. */
    static final double ARRIVED = 6.0D;

    /** How far from where a trip was asked for its departure node may be, where it was asked for away from a sign. */
    static final double DEPARTS_WITHIN = 8.0D;

    /** The most nodes a route is looked for among, so a vast network cannot hold a tick. */
    static final int ROUTE_LIMIT = 8192;

    /** Requests as they are handled, on whichever thread Via Romana handles them on. */
    private static final ConcurrentLinkedQueue<Trip> ASKED = new ConcurrentLinkedQueue<Trip>();

    /** Requests waiting for their player to land, on the server's thread alone. */
    private static final List<Trip> WAITING = new ArrayList<Trip>();

    private TripWear() {}

    private static final class Trip {

        final UUID player;

        final ServerLevel level;

        final BlockPos sign;

        final BlockPos from;

        final BlockPos to;

        int waited;

        Trip(UUID player, ServerLevel level, BlockPos sign, BlockPos from, BlockPos to) {
            this.player = player;
            this.level = level;
            this.sign = sign;
            this.from = from;
            this.to = to;
        }
    }

    /**
     * A trip was asked for. Called by the mixin at the head of Via Romana's own handling, with the request itself.
     * Notes it and nothing more: the wear waits for the landing.
     */
    public static void requested(Object request, Level level, Player player) {
        if (!TrmtConfig.viaRomanaTripWear || level == null || level.isClientSide()) return;
        if (!(level instanceof ServerLevel) || !(player instanceof ServerPlayer) || !ViaRomana.ready()) return;
        BlockPos to = ViaRomana.destinationOf(request);
        if (to == null) return;
        ASKED.add(new Trip(player.getUUID(), (ServerLevel) level, ViaRomana.originOf(request), player.blockPosition(), to));
    }

    /** Every server tick: lands the trips whose players have arrived, and forgets the ones that never will. */
    public static void tick() {
        Trip asked;
        while ((asked = ASKED.poll()) != null) WAITING.add(asked);
        if (WAITING.isEmpty()) return;
        MinecraftServer server = Trmt.runningServer();
        Iterator<Trip> each = WAITING.iterator();
        while (each.hasNext()) {
            Trip trip = each.next();
            ServerPlayer player = server == null ? null
                : server.getPlayerList()
                    .getPlayer(trip.player);
            if (player != null && player.level() == trip.level && arrived(player, trip.to)) {
                each.remove();
                land(trip, player);
            } else if (++trip.waited > LANDS_WITHIN) {
                each.remove();
            }
        }
    }

    /** Forgets every trip, as a server stops. */
    public static void forget() {
        ASKED.clear();
        WAITING.clear();
    }

    private static boolean arrived(ServerPlayer player, BlockPos to) {
        double dx = player.getX() - (to.getX() + 0.5D);
        double dz = player.getZ() - (to.getZ() + 0.5D);
        return dx * dx + dz * dz <= ARRIVED * ARRIVED;
    }

    private static void land(Trip trip, ServerPlayer player) {
        float amount = RoadRules.tripAmount(
            TrmtConfig.viaRomanaTripWear,
            TrmtConfig.viaRomanaTripSteps,
            TrmtConfig.multiplierPlayer * ErosionEngine.multiplierFor(player));
        if (amount <= 0f) return;
        Object graph = ViaRomana.graph(trip.level);
        Object start = trip.sign == null ? null : ViaRomana.nodeBySign(graph, trip.sign);
        if (start == null) start = ViaRomana.nearestNode(graph, trip.from, DEPARTS_WITHIN);
        Object end = ViaRomana.nodeAt(graph, trip.to);
        List<BlockPos> route = route(graph, start, end);
        if (route.size() < 2) return;
        RoadSquares road = new RoadSquares();
        for (int i = 1; i < route.size(); i++) {
            BlockPos a = route.get(i - 1);
            BlockPos b = route.get(i);
            road.addSegment(a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ());
        }
        int worn = wear(trip.level, road.line(), amount);
        if (TrmtConfig.bleedSide > 0f) wear(trip.level, road.besideLine(), amount * TrmtConfig.bleedSide);
        Trmt.LOG.info(
            "Via Romana: {}'s fast trip from {} to {} went along {} nodes of road and wore {} of its {} squares, each by "
                + "{} of a step (integration.viaRomanaTripSteps {})",
            player.getGameProfile()
                .getName(),
            route.get(0),
            route.get(route.size() - 1),
            Integer.valueOf(route.size()),
            Integer.valueOf(worn),
            Integer.valueOf(road.line()
                .size()),
            Float.valueOf(amount),
            Double.valueOf(TrmtConfig.viaRomanaTripSteps));
    }

    /** The fewest nodes from one to the other along the graph's joins, as positions, or none where they are not joined. */
    static List<BlockPos> route(Object graph, Object start, Object end) {
        BlockPos from = ViaRomana.posOf(start);
        BlockPos to = ViaRomana.posOf(end);
        if (graph == null || from == null || to == null) return Collections.emptyList();
        Map<Long, Long> cameFrom = new HashMap<Long, Long>();
        ArrayDeque<Long> queue = new ArrayDeque<Long>();
        long goal = to.asLong();
        cameFrom.put(Long.valueOf(from.asLong()), null);
        queue.add(Long.valueOf(from.asLong()));
        while (!queue.isEmpty() && cameFrom.size() <= ROUTE_LIMIT) {
            long here = queue.poll()
                .longValue();
            if (here == goal) break;
            Object node = here == from.asLong() ? start : ViaRomana.nodeAt(graph, BlockPos.of(here));
            for (long next : ViaRomana.linksOf(node)) {
                Long boxed = Long.valueOf(next);
                if (cameFrom.containsKey(boxed)) continue;
                cameFrom.put(boxed, Long.valueOf(here));
                queue.add(boxed);
            }
        }
        if (!cameFrom.containsKey(Long.valueOf(goal))) return Collections.emptyList();
        List<BlockPos> out = new ArrayList<BlockPos>();
        for (Long at = Long.valueOf(goal); at != null; at = cameFrom.get(at)) out.add(BlockPos.of(at.longValue()));
        Collections.reverse(out);
        return out;
    }

    /** Steps on each square's ground, and says how many squares had ground that wears. */
    private static int wear(ServerLevel level, List<int[]> squares, float amount) {
        int worn = 0;
        for (int[] square : squares) {
            int y = groundAt(level, square[0], square[1], square[2]);
            if (y == Integer.MIN_VALUE) continue;
            ErosionEngine.get()
                .step(level, square[0], y, square[2], amount);
            worn++;
        }
        return worn;
    }

    /**
     * The wearing ground nearest the road's height at this column, open to the sky above it, or MIN_VALUE where there
     * is none within the road's slack or the chunk is not loaded.
     */
    private static int groundAt(ServerLevel level, int x, int y, int z) {
        for (int off = 0; off <= RoadSquares.SLACK; off++) {
            for (int sign = 1; sign >= -1; sign -= 2) {
                int at = y + off * sign;
                if (!Worlds.loaded(level, x, at, z)) return Integer.MIN_VALUE;
                BlockState state = Worlds.stateAt(level, x, at, z);
                if (SurfaceRegistry.isStaged(state) && !Worlds.isOpaque(level, x, at + 1, z)) return at;
                if (off == 0) break;
            }
        }
        return Integer.MIN_VALUE;
    }
}
