package com.trmtgtnh.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import com.trmtgtnh.item.ItemChunkTamper;
import com.trmtgtnh.item.ItemGradedTamper;
import com.trmtgtnh.item.ItemMagicTamper;

/**
 * The three tampers, with the three answers only Forge can hear.
 *
 * <p>
 * Each subclass adds nothing but overrides, and every one of them is a method Forge put on an item
 * that vanilla has no counterpart for:
 *
 * <ul>
 * <li><strong>How much damage this stack may take.</strong> Vanilla asks the item, not the stack, so
 * without this every grade of chunk tamper would last exactly as long as every other. The number is
 * the common class's - see {@code ItemChunkTamper.maxDamageOf} - and is only fetched from here.
 * <li><strong>Whether the grid may repair it.</strong> It may not: the grid is the one place a
 * per-stack maximum is ignored, and it launders the grade off the stack on its way past.
 * <li><strong>Whether a left-click begins a dig.</strong> It does not; a left-click with one of
 * these is a gesture, and refusing the dig here is one of the two refusals that make that true.
 * </ul>
 *
 * <p>
 * Fabric answers the first two with mixins and the third with a callback. Neither loader decides
 * anything here - what the answers are is the common classes' - so the two cannot drift.
 */
public final class ForgeTampers {

    private ForgeTampers() {}

    /** The hand tamper. */
    public static final class Graded extends ItemGradedTamper {

        @Override
        public int getMaxDamage(ItemStack stack) {
            return maxDamageOf(stack);
        }

        @Override
        public boolean isRepairable(ItemStack stack) {
            return false;
        }

        @Override
        public boolean onBlockStartBreak(ItemStack stack, BlockPos pos, Player player) {
            return true;
        }
    }

    /** The chunk tamper. */
    public static class Chunk extends ItemChunkTamper {

        @Override
        public int getMaxDamage(ItemStack stack) {
            return maxDamageOf(stack);
        }

        @Override
        public boolean isRepairable(ItemStack stack) {
            return false;
        }

        @Override
        public boolean onBlockStartBreak(ItemStack stack, BlockPos pos, Player player) {
            return true;
        }
    }

    /** The Wayfarer, which never wears out and still must not dig. */
    public static final class Magic extends ItemMagicTamper {

        @Override
        public int getMaxDamage(ItemStack stack) {
            return maxDamageOf(stack);
        }

        @Override
        public boolean isRepairable(ItemStack stack) {
            return false;
        }

        @Override
        public boolean onBlockStartBreak(ItemStack stack, BlockPos pos, Player player) {
            return true;
        }
    }
}
