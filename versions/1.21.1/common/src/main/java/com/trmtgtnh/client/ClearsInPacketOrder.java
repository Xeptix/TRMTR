package com.trmtgtnh.client;

import com.trmtgtnh.util.MainThread;

/**
 * The client's clears of its own wear and lights, handed to this mod's queue in the order their packets arrived
 * (spec PN33, PN34).
 *
 * <p>
 * A chunk forgotten drops that chunk's records, and the world being replaced - a respawn into another dimension, or a
 * login over a world still held - drops all of them. Until this, each loader's hook ran the clear on the spot. This
 * mod's own packets - a chunk's wear, a single change, a chunk's lights, a light change - run from {@link MainThread} at
 * the end of the client tick, so a backlog of the old world's wear still waiting there when the world was replaced was
 * applied after the clear, into the new world, and a chunk's wear still waiting when the chunk was forgotten survived
 * its forgetting and painted the chunk when it came back. So the clear now waits in that same queue, behind what
 * arrived before it and ahead of what arrives after (0.9.222).
 *
 * <p>
 * <strong>Queued from the client thread, where vanilla handles the packet, and not from the network thread</strong>:
 * on this version that is where this mod's packets join the queue too. Forge hands a play payload to the mod's channel
 * only once the packet has been handed to the client thread ({@code ClientPacketListener.handleCustomPayload} calls
 * {@code NetworkHooks.onCustomPayload} after {@code ensureRunningOnSameThread}), and its {@code enqueueWork} then runs
 * the handler at once; Fabric's networking hears the payload on the network thread and this mod's receiver hands it to
 * {@code Minecraft.execute}, the same queue vanilla's packets wait in. Either way a payload reaches {@link MainThread}
 * from the client thread, in arrival order with vanilla's packets - so a clear queued from the network thread would
 * jump ahead of mod packets that arrived before it and are still waiting in vanilla's queue, which is the backlog this
 * is here to keep behind it. A single-player world is the same: the integrated server's connection is a local channel
 * read on its own network thread, and everything it delivers goes through the client thread's queue in the same way.
 *
 * <p>
 * Nothing here names the game, so the order is tested with plain tasks; the hooks are each loader's
 * {@code MixinChunkForgotten} and {@code MixinLevelReplaced}, and the clears themselves {@link ClientSide#forgetChunk}
 * and {@link ClientSide#forgetLevel}.
 */
public final class ClearsInPacketOrder {

    /** A chunk's clear, by its position. */
    public interface ChunkClear {

        void forget(int chunkX, int chunkZ);
    }

    private ClearsInPacketOrder() {}

    /** The client's world is being replaced: everything it holds goes, behind what arrived before. */
    public static void worldReplaced(Runnable forgetWorld) {
        MainThread.onClient(forgetWorld);
    }

    /** The server told the client to forget a chunk: that chunk's wear and lights go, behind what arrived before. */
    public static void chunkForgotten(final int chunkX, final int chunkZ, final ChunkClear clear) {
        MainThread.onClient(() -> clear.forget(chunkX, chunkZ));
    }
}
