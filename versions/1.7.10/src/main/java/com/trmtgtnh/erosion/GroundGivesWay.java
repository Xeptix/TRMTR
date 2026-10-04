package com.trmtgtnh.erosion;

import net.minecraft.block.Block;
import net.minecraft.world.World;

import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.network.TrmtNetwork;
import com.trmtgtnh.surface.SurfaceFamily;

/**
 * What happens to ground that has been walked all the way through.
 *
 * <p>
 * A family's run ends in a rut half a block deep and then simply stops: the wear is pinned at the
 * last threshold, the position is as worn as the world lets it get, and any amount of further
 * traffic changes nothing at all. That is a reasonable place to stop and it is not the only one.
 * Ground that has had everything taken out of it can be gone.
 *
 * <p>
 * It is the sixteenth pixel of depth, which is the one depth the record cannot hold - four bits
 * count to fifteen - and the reason it cannot is the reason it should not be a depth. Fifteen
 * sixteenths of a block is still a block. Sixteen is a hole.
 *
 * <p>
 * Nothing drops. What wore away was carried off a grain at a time by whatever walked over it, and
 * there is nothing left at the end of that to pick up. It is also the honest reading of a rule
 * that destroys real blocks: a rule that paid you for it would be a mining technique.
 */
public final class GroundGivesWay {

    /**
     * How much further a finished square must be worn before it goes.
     *
     * <p>
     * One whole extra threshold past the end of the chain, and it is doing two jobs. It is the
     * grace a save written before this existed needs, because every square already parked at the
     * end of its run would otherwise become a hole on the first footstep after the update - an
     * irreversible edit to somebody's world that they did not ask for by installing a version.
     * And it is the margin that stops a single hard blow going from untouched ground to a hole in
     * one tick: a charge that carries the whole run still has to be followed by a full threshold's
     * more traffic before the ground actually gives.
     */
    private static final float PAST_THE_END = 2f;

    private GroundGivesWay() {}

    /**
     * Whether this position has been worn past the end of its chain and may be taken.
     *
     * <p>
     * Every refusal here is deliberate rather than defensive. A pin, a reinforcement, a spawn ward
     * and a wayfinding glow are all things somebody spent a tamper charge to put on this square,
     * and a charge somebody spent is a refusal rather than a delay. The dimension and height bounds
     * are tested here as well as by the caller for the same reason {@link GroundCover} tests them:
     * a rule whose whole cost is a real block being destroyed is not a rule to leave to whoever
     * calls next.
     */
    public static boolean dueToGo(World world, int x, int y, int z, SurfaceFamily base, ErosionEntry entry) {
        if (world == null || world.isRemote || entry == null || base == null) return false;
        if (!TrmtConfig.enabled || !TrmtConfig.groundWearsAway) return false;
        if (!TrmtConfig.dimensionAllowed(world.provider.dimensionId)) return false;
        if (y < TrmtConfig.minY || y > TrmtConfig.maxY) return false;

        if (entry.isFrozen() || entry.getReinforce() > 0 || entry.getWard() != 0 || entry.isLit()) return false;
        if (!entry.isVisible()) return false;

        // The end of the whole run, and only while a ceiling lets ground reach it. A ceiling below
        // the whole run promises ground stops where it is told to, and this used to read the capped
        // end as the end: at a quarter, a flat and barely darkened path went to air after one more
        // threshold of traffic.
        int full = ErosionChain.length(base);
        if (full <= 0 || ErosionChain.cappedLength(base) < full) return false;
        if (ErosionChain.indexOf(base, entry.getFamily(), entry.getStage(), entry.getSink()) != full - 1) {
            return false;
        }
        if (entry.getWear() < entry.effectiveThreshold() * PAST_THE_END) return false;

        // Last, because it reads the world. A plant holding this square keeps it. The hold is spared
        // being knocked down and held level on both sides, and this was the one place that never
        // asked - so the record walked on under the sapling to the end of its run and then the
        // ground went out from under it, taking the orchard the setting exists to keep.
        return !GroundCover.holdsAt(world, x, y, z);
    }

    /**
     * Takes the block, and the record with it.
     *
     * <p>
     * The block is checked against what the engine was working on rather than trusted, because the
     * plants standing on this square have already been knocked down by the time this runs and that
     * hands control to arbitrary mod code. Something else may be standing here now, and whatever it
     * is, it is not the ground this rule earned the right to remove.
     *
     * <p>
     * The record is cleared outright rather than handed to the grace period that holds a broken
     * block's wear in case the same block is put back. That window exists so replacing a block by
     * hand does not cost you the path it was carrying; this position had no path left to carry.
     */
    public static void take(World world, int x, int y, int z, Block expected, int expectedMeta, ChunkErosionData data,
        int key) {
        if (world.getBlock(x, y, z) != expected || world.getBlockMetadata(x, y, z) != expectedMeta) return;

        world.playAuxSFX(2001, x, y, z, Block.getIdFromBlock(expected) + (expectedMeta << 12));
        world.setBlockToAir(x, y, z);

        data.remove(key);
        data.markDirty();
        ErosionStore.get()
            .markModified(world, x >> 4, z >> 4);
        TrmtNetwork.sendDelta(world, x, y, z, ErosionState.NONE);
    }
}
