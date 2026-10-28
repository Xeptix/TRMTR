package com.trmtgtnh.compat.viaromana;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.trmtgtnh.ModsPresent;
import com.trmtgtnh.Trmt;

/**
 * Via Romana, asked by name: whether it is here, and what its road graph holds (0.9.228).
 *
 * <p>
 * Nothing of Via Romana is on the compile path and nothing of it is linked; every class and member below is named as
 * its own jars name it, read with javap from all four of 2.2.3's - 1.20.1 Forge and Fabric, 1.21.1 NeoForge and Fabric
 * - where each is spelled the same, being Via Romana's own and not the game's. What it hands back is the game's own
 * types (a block position, a level), which every loader's build of this mod names as that loader's game does:
 *
 * <ul>
 * <li>{@code net.rasanovum.viaromana.path.PathGraph}: static {@code getInstance(ServerLevel)}, the level's graph, kept
 * by Data Anchor as the level's tracked data; {@code queryNearby(BlockPos, double)}, the nodes within a radius;
 * {@code getNodeAt(BlockPos)}, {@code getNodeBySignPos(BlockPos)} and {@code getNearestNode(BlockPos, double)}, each an
 * {@code Optional} of a node.
 * <li>{@code net.rasanovum.viaromana.path.Node}: {@code getBlockPos()}, the block the charting player stood in, and
 * {@code getConnectedNodes()}, a fastutil {@code LongSet} of the positions it is joined to, packed as
 * {@code BlockPos.asLong} packs them.
 * <li>{@code net.rasanovum.viaromana.network.packets.TeleportRequestC2S}: a record of {@code originSignPos()}, null for
 * a trip asked for away from a sign, and {@code destinationPos()}, the node the trip goes to.
 * <li>{@code net.rasanovum.viaromana.tags.TagGenerator.PATH_BLOCK_TAG}: the block tag {@code via_romana:path_block}
 * that its {@code util.PathUtils.isBlockValidPath} asks of a block, filled from its own config as data loads.
 * </ul>
 *
 * <p>
 * Anything that is not there - a later Via Romana that renamed a member - is said once in the log and then answered
 * as "no road", so this mod goes on exactly as it does without Via Romana rather than failing a tick.
 */
public final class ViaRomana {

    /** Via Romana's mod id, in its mods.toml, neoforge.mods.toml and fabric.mod.json alike. */
    public static final String MOD_ID = "via_romana";

    static final String GRAPH = "net.rasanovum.viaromana.path.PathGraph";

    static final String NODE = "net.rasanovum.viaromana.path.Node";

    static final String TRIP = "net.rasanovum.viaromana.network.packets.TeleportRequestC2S";

    static final String TAGS = "net.rasanovum.viaromana.tags.TagGenerator";

    private static Method graphOf;

    private static Method nearby;

    private static Method nodeAt;

    private static Method nodeBySign;

    private static Method nearest;

    private static Method nodePos;

    private static Method links;

    private static Method originSign;

    private static Method destination;

    private static TagKey<Block> pathTag;

    /** Nought until asked, one once every name is found, minus one once one was not. */
    private static volatile int resolved;

    private ViaRomana() {}

    /** Whether Via Romana is installed. Everything this support does asks this first. */
    public static boolean present() {
        return ModsPresent.has(MOD_ID);
    }

    /** Whether Via Romana is installed and every name this support asks it by was found. */
    static boolean ready() {
        if (!present()) return false;
        if (resolved == 0) resolve();
        return resolved > 0;
    }

    @SuppressWarnings("unchecked")
    private static synchronized void resolve() {
        if (resolved != 0) return;
        try {
            Class<?> graph = Class.forName(GRAPH);
            Class<?> node = Class.forName(NODE);
            Class<?> trip = Class.forName(TRIP);
            graphOf = graph.getMethod("getInstance", ServerLevel.class);
            nearby = graph.getMethod("queryNearby", BlockPos.class, double.class);
            nodeAt = graph.getMethod("getNodeAt", BlockPos.class);
            nodeBySign = graph.getMethod("getNodeBySignPos", BlockPos.class);
            nearest = graph.getMethod("getNearestNode", BlockPos.class, double.class);
            nodePos = node.getMethod("getBlockPos");
            links = node.getMethod("getConnectedNodes");
            originSign = trip.getMethod("originSignPos");
            destination = trip.getMethod("destinationPos");
            pathTag = (TagKey<Block>) Class.forName(TAGS)
                .getField("PATH_BLOCK_TAG")
                .get(null);
            resolved = 1;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError gone) {
            resolved = -1;
            Trmt.LOG.warn(
                "Via Romana is installed, but this mod could not find what it asks of it ({}), so worn ground is not "
                    + "offered to it as road, a fast trip wears nothing and a charted road heals as any other ground. "
                    + "Via Romana 2.2.3 is the version this was written against. This is said once.",
                gone.toString());
        }
    }

    /** Whether a block is one Via Romana itself counts as road, by its own tag. False where it cannot be asked. */
    static boolean isPathBlock(BlockState state) {
        return state != null && ready() && pathTag != null && state.is(pathTag);
    }

    /** The level's road graph, or null. */
    static Object graph(ServerLevel level) {
        if (level == null || !ready()) return null;
        try {
            return graphOf.invoke(null, level);
        } catch (ReflectiveOperationException | RuntimeException noGraph) {
            // Data Anchor answers a level it has no container for with an IllegalStateException: no graph, no road.
            return null;
        }
    }

    /** The nodes within a radius of a block, or none. */
    static List<Object> nearby(Object graph, BlockPos centre, double radius) {
        if (graph == null) return Collections.emptyList();
        try {
            Object found = nearby.invoke(graph, centre, Double.valueOf(radius));
            return found instanceof Collection ? new ArrayList<Object>((Collection<?>) found) : Collections.emptyList();
        } catch (ReflectiveOperationException | RuntimeException racing) {
            return Collections.emptyList();
        }
    }

    static Object nodeAt(Object graph, BlockPos pos) {
        return optional(graph, nodeAt, pos);
    }

    static Object nodeBySign(Object graph, BlockPos sign) {
        return optional(graph, nodeBySign, sign);
    }

    static Object nearestNode(Object graph, BlockPos pos, double within) {
        if (graph == null || pos == null) return null;
        try {
            return unwrap(nearest.invoke(graph, pos, Double.valueOf(within)));
        } catch (ReflectiveOperationException | RuntimeException racing) {
            return null;
        }
    }

    private static Object optional(Object graph, Method method, BlockPos pos) {
        if (graph == null || pos == null) return null;
        try {
            return unwrap(method.invoke(graph, pos));
        } catch (ReflectiveOperationException | RuntimeException racing) {
            return null;
        }
    }

    private static Object unwrap(Object answer) {
        return answer instanceof Optional ? ((Optional<?>) answer).orElse(null) : null;
    }

    /** The block a node's charting player stood in, or null. */
    static BlockPos posOf(Object node) {
        if (node == null) return null;
        try {
            Object pos = nodePos.invoke(node);
            return pos instanceof BlockPos ? (BlockPos) pos : null;
        } catch (ReflectiveOperationException | RuntimeException racing) {
            return null;
        }
    }

    /** The packed positions a node is joined to, or none. */
    static long[] linksOf(Object node) {
        if (node == null) return new long[0];
        try {
            Object set = links.invoke(node);
            if (!(set instanceof Collection)) return new long[0];
            Collection<?> each = (Collection<?>) set;
            long[] out = new long[each.size()];
            int at = 0;
            for (Object packed : each) {
                if (at < out.length && packed instanceof Long) out[at++] = ((Long) packed).longValue();
            }
            return at == out.length ? out : java.util.Arrays.copyOf(out, at);
        } catch (ReflectiveOperationException | RuntimeException racing) {
            return new long[0];
        }
    }

    /** A trip request's sign of departure, or null - for a trip asked for away from a sign, as well. */
    static BlockPos originOf(Object request) {
        return position(request, originSign);
    }

    /** A trip request's destination node, or null. */
    static BlockPos destinationOf(Object request) {
        return position(request, destination);
    }

    private static BlockPos position(Object request, Method method) {
        if (request == null || !ready()) return null;
        try {
            Object pos = method.invoke(request);
            return pos instanceof BlockPos ? (BlockPos) pos : null;
        } catch (ReflectiveOperationException | RuntimeException wrongType) {
            return null;
        }
    }
}
