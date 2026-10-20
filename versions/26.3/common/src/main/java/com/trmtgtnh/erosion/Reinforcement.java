package com.trmtgtnh.erosion;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import com.trmtgtnh.config.TrmtConfig;

/**
 * How much harder reinforced ground is to wear.
 *
 * <p>
 * <strong>The arithmetic half of the older editions' class, and only that half.</strong> There this
 * also reads a level at a position and decides whether reinforced ground survives an explosion, and
 * both need the erosion store and a world. Neither has arrived.
 *
 * <p>
 * The arithmetic comes first because the portable core asks for it: {@code ErosionEntry}, one of the
 * twenty-eight classes held byte for byte identical across every edition, multiplies its threshold by
 * {@link #wearFactor(int)}. So this method's name and signature are fixed by a file nobody may edit,
 * which is a good reason to have carried it unchanged.
 *
 * <p>
 * The store has arrived, so {@link #levelAt} and {@link #survives} are here too, and this class is
 * whole.
 */
public final class Reinforcement {

    private Reinforcement() {}

    /** The reinforcement level at a position, 0 when none. Server-side. */
    public static int levelAt(Level level, int x, int y, int z) {
        if (level == null || level.isClientSide()) return 0;
        ErosionEntry entry = ErosionStore.get()
            .getEntry(level, x, y, z);
        return entry == null ? 0 : entry.getReinforce();
    }

    /**
     * Whether a position survives an explosion of this size.
     *
     * <p>
     * The top level always survives. Below it, the block lives when the blast is no larger than the
     * configured ceiling for its level plus the block's own explosion resistance - so a tougher block
     * reinforced once still outlasts a softer one reinforced once, which is the point of
     * "reinforcement adds to the base resistance" rather than replacing it.
     *
     * <p>
     * <strong>The resistance is the block's own rather than the state's at this position</strong>,
     * and that is a small loss against the 1.12.2 edition. Asking a block how much blast it resists
     * <em>here</em> is a Forge extension; vanilla only asks the block, and this module may not name a
     * loader. A block that answers differently in different places - and some do - is therefore
     * answered for in the abstract. The ceiling that reinforcement adds is the larger term in the sum
     * by some margin, so the difference is small, but it is a difference and this says so rather than
     * letting somebody find it.
     *
     * <p>
     * Asked defensively, as both older editions ask it: a block that throws rather than answer is one
     * to treat as having no resistance of its own, not one to let an explosion crash on.
     */
    public static boolean survives(Level level, int x, int y, int z, float explosionSize) {
        if (!TrmtConfig.reinforceEnabled) return false;
        int held = levelAt(level, x, y, z);
        if (held <= 0) return false;
        if (held >= TrmtConfig.reinforceMaxLevel) return true; // fully blast-proof

        float[] ceiling = TrmtConfig.reinforceCeiling;
        int index = held - 1;
        float allowance = index >= 0 && index < ceiling.length ? ceiling[index] : 0f;

        BlockState state = level.getBlockState(new BlockPos(x, y, z));
        float base = 0f;
        if (state != null) {
            try {
                base = state.getBlock()
                    .getExplosionResistance();
            } catch (RuntimeException awkwardBlock) {
                base = 0f;
            }
        }
        return explosionSize <= allowance + base;
    }

    /**
     * What a reinforcement level multiplies the wear threshold by.
     *
     * <p>
     * One whole extra lifetime per level, so a block reinforced once needs twice the traffic to move
     * a step and one reinforced three times needs four times - and because the multiplier is applied
     * at every one of the eighty steps rather than once at the end, a fully reinforced road takes four
     * times the walking to sink from new to bare.
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
     * block recovers a step in the same idle time as an unreinforced one - reinforcement resists being
     * worn down, not the ground's own recovery.
     */
    public static float wearFactor(int level) {
        if (level <= 0 || !TrmtConfig.reinforceEnabled) return 1f;
        double factor = 1d + level * TrmtConfig.reinforceWearFactor;
        return factor < 1d ? 1f : (float) factor;
    }
}
