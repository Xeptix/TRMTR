package com.trmtgtnh.erosion;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
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
 *
 * <p>
 * Worth knowing about this class, because it caught somebody once already. {@code ErosionEntry} is a
 * portable class, held byte for byte identical across both editions, and it calls {@link #wearFactor}
 * by name without importing this class - which is how a class that reads the world came to sit inside
 * a core that is meant to name nothing from the game. The test that guards that core checks import
 * lines, and a same-package call has none.
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
     *
     * <p>
     * The resistance is asked of the block at the position rather than of the block in the
     * abstract, because 1.12.2 lets a block answer differently in different places and some do.
     * Asked defensively, as the 1.7.10 edition asks it: a block that throws rather than answer is
     * one to treat as having no resistance of its own, not one to let an explosion crash on.
     */
    public static boolean survives(World world, int x, int y, int z, float explosionSize) {
        if (!TrmtConfig.reinforceEnabled) return false;
        int level = levelAt(world, x, y, z);
        if (level <= 0) return false;
        if (level >= TrmtConfig.reinforceMaxLevel) return true; // fully blast-proof

        float[] ceiling = TrmtConfig.reinforceCeiling;
        int index = level - 1;
        float allowance = index >= 0 && index < ceiling.length ? ceiling[index] : 0f;

        BlockPos pos = new BlockPos(x, y, z);
        IBlockState state = world.getBlockState(pos);
        float base = 0f;
        if (state != null && state.getBlock() != null) {
            try {
                base = state.getBlock()
                    .getExplosionResistance(world, pos, null, null);
            } catch (RuntimeException awkwardBlock) {
                base = 0f;
            }
        }
        return explosionSize <= allowance + base;
    }
}
