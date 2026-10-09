package com.trmtgtnh.client.xaero;

import java.lang.reflect.Field;

import net.minecraft.client.Minecraft;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import com.trmtgtnh.Trmt;

/**
 * Telling Xaero's Minimap that a chunk it has already drawn is no longer what it drew.
 *
 * <p>
 * Xaero keeps every tile it writes and reuses it whole unless the chunk is marked dirty, and the
 * only thing that marks one is a block packet arriving from the server - a chunk load, a block
 * change, a multi-block change. This mod never sends one. Wear advances by painting a cosmetic
 * block into the client's own copy of the world, which is the whole architecture, so as far as
 * Xaero is concerned nothing has happened and the square keeps the color it had when the chunk
 * was first mapped. Stand on a path and wear it forty steps down and the minimap does not move.
 *
 * <p>
 * So the flag is cleared here instead. It is a field Xaero injects into {@code Chunk} itself, which
 * is why this is reflection and why it is written to survive the field not being there at all:
 * looked up once, remembered as absent, never asked for again. That is the same bargain every
 * other integration in this mod makes - detected and enhanced against, never required, never on
 * the compile classpath - and the failure when Xaero is absent or has moved its internals is that
 * minimaps go back to behaving exactly as they did before any of this existed.
 *
 * <p>
 * Deliberately not hung on whether a block actually changed. A position can move along its chain
 * and want the very same ghost at the very same metadata - the sub-steps between one drawn
 * gradation and the next - and those are precisely the steps this exists to show, because the
 * color they produce comes from the wear record rather than from the block. Nudging only where a
 * block changed would drop them and leave the feature working in the coarsest case alone.
 */
public final class XaeroMinimap {

    /** The injected field, or null once it is known not to be there. */
    private static Field dirtyFlag;

    /** Whether the lookup has been done, successfully or not. */
    private static boolean looked;

    /** The last chunk nudged, packed, so a sweep over one chunk pokes it once rather than per block. */
    private static long lastChunk = Long.MIN_VALUE;

    private XaeroMinimap() {}

    /**
     * Marks the chunk holding this position as worth drawing again.
     *
     * <p>
     * Coalesced on the last chunk asked for, which costs one long comparison and takes the common
     * shape out of the cost: a repaint walks a chunk's positions in order, so the run collapses to
     * one poke. It is idempotent in any case - the flag is already false - and Xaero's own writer
     * takes one tile per run and only on one run in five, so nothing here can outpace it.
     */
    public static void chunkChanged(World world, int x, int z) {
        if (world == null || !world.isRemote) return;
        long key = ((long) (x >> 4) << 32) ^ (z >> 4) & 0xFFFFFFFFL;
        if (key == lastChunk) return;
        lastChunk = key;
        poke(world, x >> 4, z >> 4);
    }

    /** Marks a whole chunk, for the paths that already know which chunk they are working on. */
    public static void chunkChangedAt(World world, int chunkX, int chunkZ) {
        if (world == null || !world.isRemote) return;
        lastChunk = Long.MIN_VALUE;
        poke(world, chunkX, chunkZ);
    }

    /** Forgets the coalescing, so the next position is poked whatever the last one was. */
    public static void reset() {
        lastChunk = Long.MIN_VALUE;
    }

    private static void poke(World world, int chunkX, int chunkZ) {
        Field flag = field();
        if (flag == null) return;
        try {
            // Only where the chunk is actually held. The client's provider answers yes to every
            // chunk asked about and hands back one shared blank for any it does not have, so the
            // honest question is whether what came back is that blank: setting the flag on it
            // would do no harm, and would tell the map nothing either.
            Chunk chunk = world.getChunkFromChunkCoords(chunkX, chunkZ);
            if (chunk == null || chunk.isEmpty()) return;
            flag.set(chunk, Boolean.FALSE);
        } catch (Throwable movedOrGone) {
            // Xaero changed its internals, or something else objected. Give up on the whole idea
            // rather than throwing once a tick for the rest of the session.
            dirtyFlag = null;
            Trmt.LOG.warn(
                "Could not tell Xaero's Minimap that a chunk changed; worn ground will "
                    + "redraw on its map only when something else dirties the chunk",
                movedOrGone);
        }
    }

    private static Field field() {
        if (looked) return dirtyFlag;
        looked = true;
        if (Minecraft.getMinecraft() == null) return null;
        try {
            Field found = Chunk.class.getDeclaredField("xaero_chunkClean");
            found.setAccessible(true);
            dirtyFlag = found;
            Trmt.LOG.info("Xaero's Minimap found; worn ground will redraw on it as it wears");
        } catch (NoSuchFieldException notInstalled) {
            // The ordinary case on a pack without it. Said at debug rather than info, because an
            // absent optional mod is not news.
            Trmt.LOG.debug("Xaero's Minimap is not installed; nothing to tell it about worn ground");
        } catch (Throwable awkward) {
            Trmt.LOG.debug("Xaero's Minimap chunk flag could not be reached", awkward);
        }
        return dirtyFlag;
    }
}
