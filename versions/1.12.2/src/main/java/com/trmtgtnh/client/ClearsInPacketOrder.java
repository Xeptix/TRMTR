package com.trmtgtnh.client;

import com.trmtgtnh.util.MainThread;

/**
 * The client's clears of its own wear and lights, handed to the client thread in the order their packets arrived
 * (spec PN33, PN34).
 *
 * <p>
 * A chunk unloading drops that chunk's records, and the world being replaced - a join, or a respawn into another
 * dimension - drops all of them. Until this, both ran where vanilla handles the packet: Forge's chunk and world unload
 * events, from Minecraft's own scheduled tasks at the start of a frame. This mod's own packets - a chunk's wear, a
 * single change, a chunk's lights, a light change - reach {@link MainThread} from the network thread the moment they
 * arrive and run at the end of the client tick, and the two queues keep no order between them. Wear sent straight
 * after a respawn could be applied first and then wiped by the respawn's clear, leaving the paths round the arrival
 * point bare until those chunks were watched again; a backlog of the old world's could be applied after the clear,
 * into the new world; and a chunk unloaded and sent again quickly could have its new wear applied before its unload's
 * clear. So each clear now goes into the mod's own queue, from the network thread, as its packet arrives, and keeps
 * its place among the mod's packets (0.9.222).
 *
 * <p>
 * Each vanilla handler runs twice: first on the network thread, where vanilla hands the packet on to the client thread
 * and returns by throwing, and again on the client thread. Only the first queues a clear - the second would queue it
 * again, behind whatever had arrived since. A single-player world's packets arrive on a network thread as well: the
 * integrated server's connection is a local channel read on its own event loop ({@code Netty Local Client IO}), not on
 * the client thread, and this mod's packets come in on that same thread, so the order holds there too.
 *
 * <p>
 * Whether a respawn replaces the world is vanilla's own test - a dimension other than the player's - asked here of the
 * last join or respawn to arrive rather than of the player: on the network thread the player belongs to the client
 * thread, and may not yet have taken a respawn that arrived before this one. A join always makes a new world. A
 * disconnect needs nothing: the hand-back on leaving empties the caches, and the next join clears in order.
 *
 * <p>
 * Nothing here names the game, so the order is tested with plain tasks; the hooks are {@code MixinClearsInPacketOrder}
 * and the clears themselves {@link ClientProxy#forgetChunk} and {@link ClientProxy#forgetWorld}.
 */
public final class ClearsInPacketOrder {

    /** A chunk's clear, by its position. */
    public interface ChunkClear {

        void forget(int chunkX, int chunkZ);
    }

    /** The dimension the last join or respawn named, in the order they arrived; null before the first join. */
    private static volatile Integer arrived;

    private ClearsInPacketOrder() {}

    /** A join arrived: the client's world is always a new one, so everything it holds goes, in order. */
    public static void joined(boolean onClientThread, int dimension, Runnable forgetWorld) {
        if (onClientThread) return;
        arrived = dimension;
        MainThread.onClient(forgetWorld);
    }

    /** A respawn arrived: everything goes, in order, when it takes the player to another dimension. */
    public static void respawned(boolean onClientThread, int dimension, Runnable forgetWorld) {
        if (onClientThread) return;
        boolean replaced = replacesWorld(arrived, dimension);
        arrived = dimension;
        if (replaced) MainThread.onClient(forgetWorld);
    }

    /** A chunk unload arrived: that chunk's wear and lights go, in order. */
    public static void chunkUnloaded(boolean onClientThread, final int chunkX, final int chunkZ, final ChunkClear clear) {
        if (onClientThread) return;
        MainThread.onClient(new Runnable() {

            @Override
            public void run() {
                clear.forget(chunkX, chunkZ);
            }
        });
    }

    /** Vanilla's test for a respawn building a new world, asked of the dimension the last arrival named. */
    static boolean replacesWorld(Integer last, int next) {
        return last == null || last.intValue() != next;
    }
}
