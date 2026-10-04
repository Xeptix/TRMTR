package com.trmtgtnh.erosion;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.world.World;

import com.trmtgtnh.config.TrmtConfig;

/**
 * Snow lying on a road takes the traffic, until it has been trodden through.
 *
 * <p>
 * What this replaces is not nothing, which is the part worth stating plainly. Snow already changed
 * how a square wore, in two directions at once and neither of them chosen. One layer - the only
 * depth the game itself ever lays - has a collision box of no height whatever, so a walker's feet
 * land on the ground beneath and the ground wore at full speed with snow visibly lying on it. Two
 * layers or more and the feet land inside the snow's own cell, the step arrives addressed to a block
 * that is not a wearing surface, and it is dropped: total shelter, for ever, free, and not even a
 * debt banked against the thaw.
 *
 * <p>
 * So this makes shelter exist at the depth that exists, and makes the shelter at greater depths
 * finite. Snow takes the step instead of the ground; enough steps and a layer goes, and when the
 * last one goes the ground is bare and wears as it always did. Which is what a path through snow
 * actually looks like: the snow goes first, and then the ground underneath starts to show.
 *
 * <p>
 * Nothing here is written to disk, and that is deliberate rather than a shortcut. Vanilla lays snow
 * and melts it wholesale on its own schedule, so a half-trodden layer is a fact with a lifetime
 * measured in minutes; persisting it would mean saving state about a block the weather is about to
 * remove anyway. What is held is forgotten when a chunk unloads and when a server stops, and a
 * layer met again is met fresh - which costs, at worst, a few extra footfalls after a reload.
 *
 * <p>
 * A square under snow never reaches the erosion store at all, so a snowfield cannot spend the
 * per-chunk record budget that a road through it will want later.
 */
public final class SnowCover {

    /** In-game seconds a day, matching the engine's own figure. */
    private static final double SECONDS_PER_DAY = 1200d;

    /** How trodden each covered square is, by chunk and then by position within it. */
    private static final Map<Long, Map<Integer, Tread>> TRODDEN = new HashMap<Long, Map<Integer, Tread>>();

    private SnowCover() {}

    /** How much a square's snow has been walked on, and when that was last true. */
    private static final class Tread {

        float wear;

        int seconds;
    }

    /**
     * Whether this block is a layer of snow lying on something rather than snow in its own right.
     *
     * <p>
     * By material, because every snow layer in a pack is on snow's material whatever it is called,
     * and by refusing a full cube, because a snow block is a wearing surface of its own and wears
     * as ground already. Guarded, because both questions are answered by third-party code.
     */
    public static boolean isSnowLayer(Block block) {
        if (block == null) return false;
        try {
            return block.getDefaultState()
                .getMaterial() == Material.SNOW
                && !block.getDefaultState()
                    .isFullCube();
        } catch (RuntimeException awkwardBlock) {
            return false;
        }
    }

    /** Whether snow is lying on the square at this position. */
    public static boolean covers(World world, int x, int y, int z) {
        if (!TrmtConfig.snowCovers || world == null || y >= 255) return false;
        return isSnowLayer(com.trmtgtnh.util.Worlds.blockAt(world, x, y + 1, z));
    }

    /**
     * Puts a step into the snow rather than into the ground.
     *
     * <p>
     * Always absorbs once {@link #covers} has said yes, including the very pass that breaks the last
     * layer - so bare ground takes its first step on the next crossing rather than on the one that
     * uncovered it. That is the same one-tick-later rule the ground-gives-way path is held to, and
     * it is what stops a single footfall both clearing the snow and marking what was under it.
     *
     * @return true when the step was taken by the snow and the ground should be left alone
     */
    public static boolean takes(World world, int x, int y, int z, float amount) {
        if (world == null || world.isRemote) return false;

        long chunkKey = ErosionStore.chunkKey(world.provider.getDimension(), x >> 4, z >> 4);
        Map<Integer, Tread> page = TRODDEN.get(Long.valueOf(chunkKey));
        if (page == null) {
            page = new HashMap<Integer, Tread>();
            TRODDEN.put(Long.valueOf(chunkKey), page);
        }
        Integer key = Integer.valueOf(ErosionKey.packWorld(x, y, z));
        Tread tread = page.get(key);
        if (tread == null) {
            tread = new Tread();
            page.put(key, tread);
        }

        int now = ErosionEngine.nowSeconds(world);
        if (TrmtConfig.snowCoverFadeDays > 0f && tread.seconds != 0) {
            // Fresh snow settles over a half-made track. Faded rather than held, so a route
            // crossed once a week never gets through, which is the same argument the partial wear
            // decay makes for ground.
            double perDay = TrmtConfig.snowCoverWear / TrmtConfig.snowCoverFadeDays;
            tread.wear -= (float) (perDay * ((now - tread.seconds) / SECONDS_PER_DAY));
            if (tread.wear < 0f) tread.wear = 0f;
        }
        tread.wear += amount;
        tread.seconds = now;

        if (tread.wear >= TrmtConfig.snowCoverWear) {
            page.remove(key);
            breakLayer(world, x, y + 1, z);
        }
        return true;
    }

    /**
     * Takes one layer off, or the last one away entirely.
     *
     * <p>
     * Re-read and checked before anything is written, because the layer may have melted between the
     * question and the answer. Nothing is dropped: what carried the snow off was somebody's boots.
     *
     * <p>
     * Told to clients but not to neighbours, and that is specific rather than cautious. A stacked
     * column of snow only stands because the layer below it reads as full, so notifying a drop from
     * full to nearly-full would bring down everything above it. It is also the standing rule that
     * arbitrary neighbour code must never be run from inside a wear tick.
     */
    private static void breakLayer(World world, int x, int ys, int z) {
        Block block = com.trmtgtnh.util.Worlds.blockAt(world, x, ys, z);
        if (!isSnowLayer(block)) return;
        int meta = com.trmtgtnh.util.Worlds.metaAt(world, x, ys, z);
        com.trmtgtnh.util.Worlds.playBreakEffect(world, x, ys, z, block, meta);
        if ((meta & 7) > 0) {
            com.trmtgtnh.util.Worlds.setMeta(world, x, ys, z, meta - 1, 2);
        } else {
            com.trmtgtnh.util.Worlds.setToAir(world, x, ys, z);
        }
    }

    /**
     * How trodden this square's snow is, without changing it.
     *
     * <p>
     * Non-consuming on purpose, unlike the weather meter it otherwise resembles: that one advances
     * a clock as it is read and may therefore be asked only once a pass, and a figure anything may
     * ask for should not carry that trap.
     */
    public static float trodden(World world, int x, int y, int z) {
        if (world == null) return 0f;
        Map<Integer, Tread> page = TRODDEN
            .get(Long.valueOf(ErosionStore.chunkKey(world.provider.getDimension(), x >> 4, z >> 4)));
        if (page == null) return 0f;
        Tread tread = page.get(Integer.valueOf(ErosionKey.packWorld(x, y, z)));
        if (tread == null) return 0f;
        if (TrmtConfig.snowCoverFadeDays <= 0f || tread.seconds == 0) return tread.wear;
        double perDay = TrmtConfig.snowCoverWear / TrmtConfig.snowCoverFadeDays;
        float faded = tread.wear
            - (float) (perDay * ((ErosionEngine.nowSeconds(world) - tread.seconds) / SECONDS_PER_DAY));
        return faded < 0f ? 0f : faded;
    }

    /** Drops a chunk's page when it unloads, so nothing is held for ground nobody is standing on. */
    public static void forget(long chunkKey) {
        TRODDEN.remove(Long.valueOf(chunkKey));
    }

    /** Forgets everything, when a server stops. */
    public static void reset() {
        TRODDEN.clear();
    }
}
