package com.trmtgtnh.item;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.World;

import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.erosion.ErosionEngine;
import com.trmtgtnh.erosion.ErosionState;
import com.trmtgtnh.erosion.MendLedger;
import com.trmtgtnh.server.HealingXp;
import com.trmtgtnh.server.MendPurse;
import com.trmtgtnh.surface.SurfaceFamily;
import com.trmtgtnh.surface.SurfaceRegistry;

/**
 * What the chunk tamper does, kept out of the item so both halves cannot drift apart.
 *
 * <p>
 * Mending is paid for as it goes, never up front. Each square in the cube is paid for from what
 * matches it - a block of the ground itself, or whatever its family accepts in its place, by bone
 * meal's rule - and a block is taken only once the gradation it pays for is back. That ordering is
 * the whole of the honesty: nothing is spent on work that did not happen, and nothing bought for
 * one kind of ground is spent mending another. Twelve cobble mends forty-eight gradations of cobble
 * at the default rate, not the cube, and the grass beside it waits for earth.
 */
public final class ChunkTamperActions {

    /**
     * The most squares in one area mend that get their own puff of bone-meal particles.
     *
     * <p>
     * A nine-block reach is a nineteen-cube, and effect 2005 is a packet to every client in
     * range plus fifteen particles each. Enough of them to read as "that worked", and no more.
     */
    private static final int SPARKLE_BUDGET = 48;

    private ChunkTamperActions() {}

    static void say(EntityPlayer player, String key, Object value) {
        if (player == null) return;
        player.sendMessage(
            new TextComponentString(
                TextFormatting.AQUA + I18n.translateToLocal(key)
                    + " "
                    + TextFormatting.WHITE
                    + value));
    }

    private static void tell(EntityPlayer player, TextFormatting colour, String message) {
        if (player != null) player.sendMessage(new TextComponentString(colour + message));
    }

    /**
     * Mends everything in the cube, as far as the blocks carried will pay for it.
     *
     * <p>
     * Priced per gradation put back: one block for every chunkTamperGradationsPerBlock, rounded up
     * for each kind of ground on its own, so filling a deep rut costs what it is deep and a cube
     * holding grass and cobble spends a block of each - as does one holding grass and bare earth,
     * although both are mended with earth. Ground whose material runs out is left as it
     * is from there on, and a line in chat says how many squares were.
     *
     * <p>
     * Creative pays nothing and earns nothing, for the reason it pays nothing for bone meal: the
     * game puts back only the stack in the hand after a right click, so a block taken from any other
     * slot would simply be destroyed. With no player at all nothing is mended, even while the cost
     * is switched off, because there is nobody to pay and nobody the work was done for.
     */
    public static boolean mendArea(World world, int x, int y, int z, EntityPlayer player, ItemStack stack) {
        if (!TrmtConfig.enabled || player == null) return false;
        SurfaceFamily family = SurfaceRegistry.familyOf(com.trmtgtnh.util.Worlds.blockAt(world, x, y, z), com.trmtgtnh.util.Worlds.metaAt(world, x, y, z));
        if (family == null || !family.staged) return false;

        int reach = ItemChunkTamper.reachOf(stack);
        final int steps = ItemChunkTamper.stepsOf(stack);

        // The rule for what pays is bone meal's, and so is the switch: a pack that turns bone
        // meal's block cost off has turned this one off with it.
        boolean free = player.capabilities.isCreativeMode || freeTool(stack) || !TrmtConfig.bonemealCostsABlock;
        MendLedger ledger = free ? MendLedger.free()
            : MendLedger.perGradations(TrmtConfig.chunkTamperGradationsPerBlock);
        final MendPurse purse = new MendPurse(player, ledger, MendPurse.byOwnBlock(player));

        final int[] sparkles = new int[1];
        final World shown = world;
        // No budget. Every square pays for itself inside the work as the sweep reaches it, so there
        // is no purse bought up front for the sweep to keep within.
        int mended = ErosionEngine.get()
            .sweepArea(world, x, y, z, reach, new ErosionEngine.AreaWork() {

                @Override
                public boolean apply(World at, int px, int py, int pz) {
                    // Quiet per square: the sweep tells the clients once per chunk when it is done.
                    boolean any = purse.mendSquare(at, px, py, pz, steps, false) > 0;
                    // The green sparkle every other mending gesture gets. Capped, because one
                    // burst per square of a nineteen-cube area is a packet storm and a wall of
                    // particles - a scattering across the area says the same thing.
                    if (any && sparkles[0] < SPARKLE_BUDGET) {
                        sparkles[0]++;
                        shown.playEvent(2005, new net.minecraft.util.math.BlockPos(px, py + 1, pz), 0);
                    }
                    return any;
                }
            }, Integer.MAX_VALUE);

        if (mended <= 0) {
            purse.reportNothing(player, 0, false);
            return false;
        }
        // Counted in gradations rather than squares, because a square may be put back by several
        // and what a mend is worth is measured in gradations - and only those that were paid for.
        HealingXp.award(player, stack, ledger.paidGradations());
        damage(stack, player);
        tell(
            player,
            TextFormatting.GRAY,
            I18n.translateToLocal("trmtgtnh.chunktamper.mended") + " " + mended);
        purse.reportShort(player, false);
        return true;
    }

    /**
     * Wears the whole cube in one gradation deeper, or as many as the tool is set to.
     *
     * <p>
     * Free, for the reason the single-square wear is free: it takes ground away rather than
     * putting it back, so there is nothing to buy, and charging for it would make laying a
     * plaza out cost more than the plaza. The tool itself is still spent, once for the gesture.
     *
     * <p>
     * Pinned squares inside the cube are skipped in silence rather than refused out loud. One
     * refusal per square is how a pinned junction turns a single click into a screen of chat.
     */
    public static boolean wearArea(World world, int x, int y, int z, EntityPlayer player, ItemStack stack) {
        if (!TrmtConfig.enabled || !TrmtConfig.tamperCanWear) return false;
        SurfaceFamily family = SurfaceRegistry.familyOf(com.trmtgtnh.util.Worlds.blockAt(world, x, y, z), com.trmtgtnh.util.Worlds.metaAt(world, x, y, z));
        if (family == null || !family.staged) return false;

        final EntityPlayer who = player;
        final int steps = ItemChunkTamper.stepsOf(stack);
        int worn = ErosionEngine.get()
            .sweepArea(world, x, y, z, ItemChunkTamper.reachOf(stack), new ErosionEngine.AreaWork() {

                @Override
                public boolean apply(World at, int px, int py, int pz) {
                    boolean any = false;
                    for (int step = 0; step < steps; step++) {
                        if (!TamperActions.wearAt(at, px, py, pz, who, false)) break;
                        any = true;
                    }
                    return any;
                }
            }, Integer.MAX_VALUE);

        if (worn <= 0) return false;
        // The scuff a boot makes, once for the gesture rather than once per square.
        net.minecraft.block.Block block = com.trmtgtnh.util.Worlds.blockAt(world, x, y, z);
        if (block != null) {
            net.minecraft.block.SoundType step = block.getSoundType(
                com.trmtgtnh.util.Worlds.stateAt(world, x, y, z),
                world,
                new net.minecraft.util.math.BlockPos(x, y, z),
                null);
            world.playSound(
                null,
                x + 0.5D,
                y + 0.5D,
                z + 0.5D,
                step.getStepSound(),
                net.minecraft.util.SoundCategory.BLOCKS,
                (step.getVolume() + 1.0F) / 8.0F,
                step.getPitch() * 0.5F);
        }
        damage(stack, player);
        tell(player, TextFormatting.GRAY, I18n.translateToLocal("trmtgtnh.chunktamper.worn") + " " + worn);
        return true;
    }

    /** Pins the whole cube, or releases it when it is already pinned where you clicked. */
    public static boolean pinArea(World world, int x, int y, int z, EntityPlayer player, ItemStack stack) {
        if (!TrmtConfig.enabled) return false;
        short state = ErosionState.NONE;
        com.trmtgtnh.erosion.ErosionEntry here = com.trmtgtnh.erosion.ErosionStore.get()
            .getEntry(world, x, y, z);
        if (here == null || !here.isVisible()) return false;
        state = here.packState();

        final boolean pin = !ErosionState.frozenOf(state);
        int changed = ErosionEngine.get()
            .sweepArea(world, x, y, z, ItemChunkTamper.reachOf(stack), new ErosionEngine.AreaWork() {

                @Override
                public boolean apply(World at, int px, int py, int pz) {
                    return ErosionEngine.get()
                        .setFrozen(at, px, py, pz, pin, false);
                }
            }, Integer.MAX_VALUE);

        if (changed <= 0) return false;
        damage(stack, player);
        tell(
            player,
            TextFormatting.AQUA,
            I18n.translateToLocal(pin ? "trmtgtnh.chunktamper.pinned" : "trmtgtnh.chunktamper.released") + " "
                + changed);
        return true;
    }

    private static void damage(ItemStack stack, EntityPlayer player) {
        if (stack == null || player == null) return;
        if (stack.getItem() instanceof ItemChunkTamper && !((ItemChunkTamper) stack.getItem()).wearsOut(stack)) return;
        stack.damageItem(1, player);
    }

    /** Whether the tool itself pays, rather than the player. */
    private static boolean freeTool(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemChunkTamper
            && ((ItemChunkTamper) stack.getItem()).isFree(stack);
    }
}
