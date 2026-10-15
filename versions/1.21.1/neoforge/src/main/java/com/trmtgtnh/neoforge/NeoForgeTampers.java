package com.trmtgtnh.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import com.trmtgtnh.item.ItemChunkTamper;
import com.trmtgtnh.item.ItemGradedTamper;
import com.trmtgtnh.item.ItemMagicTamper;

/**
 * The three tampers, with the one answer the loader asks of the item. The Forge module's ForgeTampers carried across.
 *
 * <p>
 * <strong>Whether a left-click may break the block.</strong> It may not; a left-click with one of these is a gesture,
 * and refusing the break here is one of the refusals that make that true (NeoForgeEvents holds the others).
 *
 * <p>
 * Forge asks this through its own {@code onBlockStartBreak}, which NeoForge removed. NeoForge asks vanilla's own
 * {@code canAttackBlock} instead - read from its {@code CommonHooks.fireBlockBreak}, which cancels the break of a
 * block whose breaker's held item answers false there, and from {@code MultiPlayerGameMode.destroyBlock}, which asks
 * the same on the client - so the same refusal is given through that, inverted: Forge's true, "stop", is false here.
 *
 * <p>
 * How much damage a stack may take, and whether the grid may repair it, are mixins in this module as in the Forge one
 * (MixinItemStack, MixinRepairItemRecipe). Neither loader decides anything here - what the answers are is the common
 * classes' - so the loaders cannot drift.
 */
public final class NeoForgeTampers {

    private NeoForgeTampers() {}

    /** The hand tamper. */
    public static final class Graded extends ItemGradedTamper {

        @Override
        public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
            return false;
        }
    }

    /** The chunk tamper. */
    public static class Chunk extends ItemChunkTamper {

        @Override
        public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
            return false;
        }
    }

    /** The Wayfarer, which never wears out and still must not dig. */
    public static final class Magic extends ItemMagicTamper {

        @Override
        public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
            return false;
        }
    }
}
