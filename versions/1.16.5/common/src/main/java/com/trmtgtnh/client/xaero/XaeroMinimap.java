package com.trmtgtnh.client.xaero;

import java.lang.reflect.Field;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

import com.trmtgtnh.Trmt;

/**
 * Telling Xaero's Minimap that a chunk it has already drawn is no longer what it drew.
 *
 * <p>
 * Xaero keeps every tile it writes and reuses it whole unless the chunk is marked dirty, and the
 * only thing that marks one is a block packet arriving from the server - a chunk load, a block
 * change, a multi-block change. This mod never sends one. Wear advances by painting a cosmetic block
 * into the client's own copy of the world, which is the whole architecture, so as far as Xaero is
 * concerned nothing has happened and the square keeps the colour it had when the chunk was first
 * mapped. Stand on a path and wear it forty steps down and the minimap does not move.
 *
 * <p>
 * So the flag is cleared here instead. It is a field Xaero injects into {@code LevelChunk}
 * itself - {@code xaero_chunkClean}, put there by a mixin on Fabric and by its own coremod on Forge,
 * and named identically on both loaders and on every version this mod targets - which is why this is reflection and why
 * it is
 * written to survive the field not being there at all: looked up once, remembered as absent, never
 * asked for again. That is the same bargain every other integration in this mod makes - detected and
 * enhanced against, never required, never on the compile classpath - and the failure when Xaero is
 * absent or has moved its internals is that minimaps go back to behaving exactly as they did before
 * any of this existed.
 *
 * <p>
 * <strong>Carried from the 1.7.10 edition, where the problem is identical and so is the field.</strong>
 * It is not made unnecessary by this edition answering {@code getMapColor} per position, which is
 * what made the two JourneyMap fixes unnecessary: that changes what colour a square is drawn, and
 * this is about whether it is drawn again at all.
 *
 * <p>
 * Deliberately not hung on whether a block actually changed. A position can move along its chain and
 * want the very same ghost at the very same record - the sub-steps between one drawn gradation and
 * the next - and the map still wants redrawing, because the wear picture it carries has moved on.
 */
public final class XaeroMinimap {

    private XaeroMinimap() {}

    /** The field, once looked for. Null when Xaero is absent or has moved it. */
    private static volatile Field dirtyFlag;

    /** Whether the look has happened, so an absent mod is not searched for every tick. */
    private static volatile boolean looked;

    /** The last chunk poked, so a run of positions inside one chunk pokes it once. */
    private static volatile long lastChunk = Long.MIN_VALUE;

    /** Marks the chunk holding one position, coalescing a run of positions inside one chunk. */
    public static void chunkChanged(Level world, int x, int z) {
        if (world == null || !world.isClientSide()) return;
        long key = ((long) (x >> 4) << 32) ^ (z >> 4) & 0xFFFFFFFFL;
        if (key == lastChunk) return;
        lastChunk = key;
        poke(world, x >> 4, z >> 4);
    }

    /** Marks a whole chunk, for the paths that already know which chunk they are working on. */
    public static void chunkChangedAt(Level world, int chunkX, int chunkZ) {
        if (world == null || !world.isClientSide()) return;
        lastChunk = Long.MIN_VALUE;
        poke(world, chunkX, chunkZ);
    }

    /** Forgets the coalescing, so the next position is poked whatever the last one was. */
    public static void reset() {
        lastChunk = Long.MIN_VALUE;
    }

    private static void poke(Level world, int chunkX, int chunkZ) {
        Field flag = field();
        if (flag == null) return;
        try {
            // Only where the chunk is actually held. The client's provider answers yes to every
            // chunk asked about and hands back one shared blank for any it does not have, so the
            // honest question is whether what came back is that blank: setting the flag on it would
            // do no harm, and would tell the map nothing either.
            LevelChunk chunk = world.getChunk(chunkX, chunkZ);
            if (chunk == null || chunk.isEmpty()) return;
            flag.set(chunk, Boolean.FALSE);
        } catch (Throwable movedOrGone) {
            // Xaero changed its internals, or something else objected. Give up on the whole idea
            // rather than throwing once a tick for the rest of the session.
            dirtyFlag = null;
            Trmt.LOG.warn(
                "Could not tell Xaero's Minimap that a chunk changed; worn ground will redraw on its "
                    + "map only when something else dirties the chunk",
                movedOrGone);
        }
    }

    private static Field field() {
        if (looked) return dirtyFlag;
        looked = true;
        if (Minecraft.getInstance() == null) return null;
        try {
            Field found = LevelChunk.class.getDeclaredField("xaero_chunkClean");
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
