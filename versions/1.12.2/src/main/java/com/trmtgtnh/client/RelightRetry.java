package com.trmtgtnh.client;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The squares whose re-light the game turned down, kept until it can be done.
 *
 * <p>
 * One ghost stands in for every stage here, and its answer to the light engine is read per position from the
 * record ({@code BlockGhost.getLightOpacity}): all of it until the ground sinks, none once it has. So a square that
 * sinks is never a block that changes, and its cell is lit right only if a re-light runs after this client learns of
 * it ({@code ClientProxy.relightIfMoved}, and the paint's own block write). But this version's {@code
 * World.checkLightFor} does nothing at all unless every chunk within {@link #LIGHT_REACH} blocks is loaded and not
 * empty - Forge's own {@code isAreaLoaded(pos, 16, false)}, read from the patched 1.12.2 jar - and nothing in the game
 * asks again. Under load, with the chunks round a road still arriving, the cell kept the darkness it had as a whole
 * block. This was written for the black sides beside the sunk squares of the second demonstrate yard's window row (dev
 * 1.12.2 and the Chisel target, 2026-11-02); the harness's light count then found every one of those faces fully lit,
 * and they were the blending pass drawn in another camera's order ({@code client.render.TranslucentOrder}). Refusals
 * do happen - a demo2 run keeps and re-lights thousands - so the retry stays.
 *
 * <p>
 * The 1.7.10 edition is not hit: sinking swaps its ghost for the separate sunken one, a real block write, and the
 * chunk re-lights the square's own column as part of that write with no test of the chunks round it. Nor are the
 * modern editions: their light engine queues a check rather than refusing it.
 *
 * <p>
 * So a re-light refused is remembered here, tried again on later client ticks once the area is loaded, and the block
 * round it then marked to be drawn again, because the mesh was built against the light it had. Bounded, oldest let go
 * first, and emptied with the world: a square kept from a world that is gone would be re-lit in the next one. Client
 * thread only. Nothing here names the game, so the rule is tested with a plain stand-in for the world.
 */
public final class RelightRetry {

    /**
     * How far round a square every chunk must be loaded before this version re-lights it: {@code
     * World.checkLightFor}'s {@code isAreaLoaded(pos, 16, false)} on Forge 1.12.2 (vanilla asks 17; Forge's patch
     * asks 16 and widens the reach of the update to 17 only when 18 is loaded).
     */
    public static final int LIGHT_REACH = 16;

    /** The most squares kept at once. A road crossing a whole chunk is 256; this is sixteen of them. */
    static final int MOST = 4096;

    /** The most squares re-lit in one tick, so a backlog arriving at once is spread over a few. */
    static final int PER_TICK = 512;

    /** What this asks of the world: kept apart so the rule can be tested without one. */
    public interface Light {

        /** Whether the game would re-light this square now: every chunk within {@link #LIGHT_REACH} loaded. */
        boolean loadedRound(int x, int y, int z);

        /** Re-lights the square. */
        void relight(int x, int y, int z);

        /** Marks the blocks in the box to be drawn again. */
        void redraw(int x0, int y0, int z0, int x1, int y1, int z1);
    }

    private static final RelightRetry INSTANCE = new RelightRetry();

    private final Set<Long> waiting = new LinkedHashSet<Long>();

    /**
     * How many re-lights were kept, and how many of those were done late, over the session: the harness says both
     * beside its light counts, so a dark square under a retry that never ran reads as that and not as a retry that
     * failed (0.9.229, G17). Not reset with the world; a session's are what a run asks about.
     */
    private long kept;

    private long doneLate;

    RelightRetry() {}

    public static RelightRetry get() {
        return INSTANCE;
    }

    /**
     * Re-lights a square now if the game will, and remembers it if it will not.
     *
     * @return whether it was re-lit now
     */
    public boolean relight(Light light, int x, int y, int z) {
        if (light.loadedRound(x, y, z)) {
            light.relight(x, y, z);
            return true;
        }
        remember(x, y, z);
        return false;
    }

    /**
     * Remembers a square whose re-light the game has just been asked for, if it will have refused it. For a block
     * write, which asks for the re-light itself and says nothing of whether it ran.
     */
    public void rememberIfRefused(Light light, int x, int y, int z) {
        if (!light.loadedRound(x, y, z)) remember(x, y, z);
    }

    /** Keeps a square for later, letting the oldest go when full. */
    void remember(int x, int y, int z) {
        if (y < 0 || y > 255) return;
        Long key = Long.valueOf(pack(x, y, z));
        // Moved to the newest end, so a square asked about again is the last to be let go.
        waiting.remove(key);
        if (waiting.size() >= MOST) {
            Iterator<Long> oldest = waiting.iterator();
            oldest.next();
            oldest.remove();
        }
        waiting.add(key);
        kept++;
    }

    /**
     * Once a tick: re-lights every kept square the game will now re-light, and has the block round each drawn again.
     */
    public void tick(Light light) {
        if (waiting.isEmpty()) return;
        int done = 0;
        for (Iterator<Long> each = waiting.iterator(); each.hasNext() && done < PER_TICK;) {
            long key = each.next()
                .longValue();
            int x = unpackX(key);
            int y = unpackY(key);
            int z = unpackZ(key);
            if (!light.loadedRound(x, y, z)) continue;
            each.remove();
            light.relight(x, y, z);
            light.redraw(x - 1, Math.max(0, y - 1), z - 1, x + 1, Math.min(255, y + 1), z + 1);
            done++;
        }
        doneLate += done;
    }

    /** How many re-lights the game refused and this kept, over the session. */
    public long kept() {
        return kept;
    }

    /** How many kept re-lights were done once their area loaded, over the session. */
    public long doneLate() {
        return doneLate;
    }

    /** Lets go of the squares in one chunk, which is gone; a chunk sent again is painted, and re-lit, afresh. */
    public void forgetChunk(int chunkX, int chunkZ) {
        if (waiting.isEmpty()) return;
        for (Iterator<Long> each = waiting.iterator(); each.hasNext();) {
            long key = each.next()
                .longValue();
            if (unpackX(key) >> 4 == chunkX && unpackZ(key) >> 4 == chunkZ) each.remove();
        }
    }

    /** Lets go of everything: the world is being left or replaced. */
    public void clear() {
        waiting.clear();
    }

    /** How many squares are kept. */
    int waiting() {
        return waiting.size();
    }

    /** A position in one long: 26 bits of x, 8 of y, 26 of z, as far as a world reaches. */
    static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 34) | ((long) (y & 0xFF) << 26) | (z & 0x3FFFFFF);
    }

    static int unpackX(long key) {
        return (int) (key >> 34) << 6 >> 6;
    }

    static int unpackY(long key) {
        return (int) (key >> 26) & 0xFF;
    }

    static int unpackZ(long key) {
        return (int) key << 6 >> 6;
    }
}
