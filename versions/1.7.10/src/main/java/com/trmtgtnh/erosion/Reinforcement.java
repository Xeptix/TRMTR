package com.trmtgtnh.erosion;

import net.minecraft.block.Block;
import net.minecraft.world.World;

import com.trmtgtnh.config.TrmtConfig;

/**
 * What a reinforced block does when something tries to blow it up.
 *
 * <p>
 * Reinforcement is a level, 0-3, kept per position in the erosion store, and it answers two
 * questions. The one it was built for is blast: the top level is fully blast-proof and the levels
 * below raise how large a blast the block will survive, on top of its own resistance, which is
 * consulted where an explosion decides what it destroys - see {@code ServerEvents.onExplosion}.
 * The other is traffic: a reinforced block also takes proportionally more crossings to wear, which
 * is {@link #wearFactor(int)} and is applied wherever a threshold is measured against.
 *
 * <p>
 * The server holds the real block at every position, never a ghost, so the block's own resistance
 * read here is the resistance of whatever is actually there - a worn path is dirt to the server,
 * and its resistance is dirt's. Reinforcement adds to that, which is exactly the rule asked for:
 * a worn block inherits the unworn block's resistance and reinforcement is laid on top.
 */
public final class Reinforcement {

    private Reinforcement() {}

    /**
     * How much longer a reinforced block takes to wear, as a multiplier on its threshold.
     *
     * <p>
     * One whole extra lifetime per level, so a block reinforced once needs twice the traffic to
     * move a step and one reinforced three times needs four times - and because the multiplier is
     * applied at every one of the eighty steps rather than once at the end, a fully reinforced road
     * takes four times the walking to sink from new to bare.
     *
     * <p>
     * It multiplies the threshold rather than dividing the step, and the difference shows on ground
     * that is already part worn. Dividing the step would leave the wear already banked untouched and
     * only slow what came after; multiplying the threshold moves the finish line, so reinforcing a
     * half-worn path visibly sets it back towards new. That is the reading that matches what
     * reinforcing is for.
     *
     * <p>
     * Healing is left alone by design. The decay rate is a share of the threshold, so a reinforced
     * block recovers a step in the same idle time as an unreinforced one - reinforcement resists
     * being worn down, not the ground's own recovery.
     */
    public static float wearFactor(int level) {
        if (level <= 0 || !TrmtConfig.reinforceEnabled) return 1f;
        double factor = 1d + level * TrmtConfig.reinforceWearFactor;
        return factor < 1d ? 1f : (float) factor;
    }

    /** The reinforcement level at a position, 0 when none. Server-side. */
    public static int levelAt(World world, int x, int y, int z) {
        if (world == null || world.isRemote) return 0;
        ErosionEntry entry = ErosionStore.get()
            .getEntry(world, x, y, z);
        return entry == null ? 0 : entry.getReinforce();
    }

    /**
     * Whether a position survives an explosion of this size.
     *
     * <p>
     * The top level always survives. Below it, the block lives when the blast is no larger than
     * the configured ceiling for its level plus the block's own explosion resistance - so a
     * tougher block reinforced once still outlasts a softer one reinforced once, which is the
     * point of "reinforcement adds to the base resistance" rather than replacing it.
     */
    public static boolean survives(World world, int x, int y, int z, float explosionSize) {
        if (!TrmtConfig.reinforceEnabled) return false;
        int level = levelAt(world, x, y, z);
        if (level <= 0) return false;
        if (level >= TrmtConfig.reinforceMaxLevel) return true; // fully blast-proof

        float[] ceiling = TrmtConfig.reinforceCeiling;
        int index = level - 1;
        float allowance = index >= 0 && index < ceiling.length ? ceiling[index] : 0f;

        Block block = world.getBlock(x, y, z);
        float base = 0f;
        if (block != null) {
            try {
                base = block.getExplosionResistance(null);
            } catch (RuntimeException awkwardBlock) {
                base = 0f;
            }
        }
        return explosionSize <= allowance + base;
    }
}
