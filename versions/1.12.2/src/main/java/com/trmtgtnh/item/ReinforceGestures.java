package com.trmtgtnh.item;

import java.util.WeakHashMap;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.World;

import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.erosion.ErosionEngine;
import com.trmtgtnh.erosion.ErosionEntry;
import com.trmtgtnh.erosion.ErosionKey;
import com.trmtgtnh.erosion.ErosionStore;
import com.trmtgtnh.server.ReinforceCost;
import com.trmtgtnh.surface.SurfaceFamily;
import com.trmtgtnh.surface.SurfaceRegistry;

/**
 * Reinforcing a block, and taking a reinforcement back off.
 *
 * <p>
 * Right-click adds one, paying a material - or, with the Wayfarer, sets the level its screen names and
 * pays only for a rise; two left-clicks take one away. The level lives in the
 * position's own erosion record - which is why a reinforced block need not be a worn one, and why
 * reinforcing an unworn block simply creates an invisible record that carries nothing but the
 * level. See {@link com.trmtgtnh.erosion.Reinforcement} for what the level then does to an
 * explosion.
 *
 * <p>
 * Server-side only. The gestures are dispatched from {@link ItemChunkTamper}, which has already
 * decided this is a reinforce click; here is only the rule.
 */
public final class ReinforceGestures {

    /** Where each player's un-reinforce is half-done: x, y, z, and the tick it was armed. */
    private static final WeakHashMap<EntityPlayer, int[]> ARMED = new WeakHashMap<EntityPlayer, int[]>();

    /** Two left-clicks at the same block within this many ticks take a reinforcement off. */
    private static final int ARM_WINDOW_TICKS = 40;

    private ReinforceGestures() {}

    /**
     * Right-click: raises a block's reinforcement, for one material.
     *
     * <p>
     * The chunk tamper adds one level, up to the cap. The Wayfarer sets the level its screen names, up
     * or down, and pays only when that is a rise. Either earns the reinforcing achievement for a rise and
     * for nothing else: the reply that the block is already there, a refused payment, a lowering and a
     * clear earn nothing, as two left-clicks earn nothing.
     */
    public static boolean reinforce(World world, int x, int y, int z, EntityPlayer player, ItemStack stack) {
        if (world == null || world.isRemote || !TrmtConfig.reinforceEnabled) return false;
        Block block = com.trmtgtnh.util.Worlds.blockAt(world, x, y, z);
        if (block == null || com.trmtgtnh.util.Worlds.isAir(world, x, y, z)) return false;

        int level = level(world, x, y, z);
        int cap = Math.min(3, TrmtConfig.reinforceMaxLevel);
        boolean free = player != null && player.capabilities.isCreativeMode;

        // The Wayfarer sets a block straight to a chosen level, up or down, for one material -
        // where the chunk tamper only ever adds one and pays for each. That is the whole of its
        // reinforce bonus. Only an increase costs anything; dropping a level is free.
        boolean wayfarer = stack != null && stack.getItem() instanceof ItemMagicTamper;
        if (wayfarer) {
            int target = Math.max(0, Math.min(cap, ItemChunkTamper.reinforceLevelOf(stack)));
            if (target == level) {
                say(player, TextFormatting.AQUA, I18n.translateToLocal("trmtgtnh.reinforce.already"));
                return true;
            }
            // Asked once, because three things turn on it: only a rise is paid for, only a rise
            // earns the achievement, and a rise sounds higher than a drop.
            boolean raising = target > level;
            if (raising && !free && !ReinforceCost.pay(player)) {
                say(player, TextFormatting.RED, I18n.translateToLocal("trmtgtnh.reinforce.needs"));
                return false;
            }
            setLevel(world, x, y, z, target);
            // The chunk tamper's achievement, for the same thing: a block taken up a level. This branch
            // once returned without it, and since warding and lighting award on paths the Wayfarer
            // shares, reinforcing was the one mode the finest tamper could never earn.
            if (raising) ModAchievements.onReinforced(player);
            world.playSound(
                null,
                x + 0.5D,
                y + 0.5D,
                z + 0.5D,
                net.minecraft.init.SoundEvents.BLOCK_STONE_BREAK,
                net.minecraft.util.SoundCategory.BLOCKS,
                0.8f,
                raising ? 1.4f : 0.8f);
            world.playEvent(
                2001,
                new net.minecraft.util.math.BlockPos(x, y, z),
                Block.getIdFromBlock(block) + (com.trmtgtnh.util.Worlds.metaAt(world, x, y, z) << 12));
            say(
                player,
                TextFormatting.AQUA,
                target <= 0 ? I18n.translateToLocal("trmtgtnh.reinforce.none")
                    : I18n.translateToLocalFormatted(
                        "trmtgtnh.reinforce.now",
                        Integer.valueOf(target),
                        Integer.valueOf(cap)));
            return true;
        }

        if (level >= cap) {
            say(player, TextFormatting.AQUA, I18n.translateToLocal("trmtgtnh.reinforce.full"));
            return true;
        }

        if (!free && !ReinforceCost.pay(player)) {
            say(player, TextFormatting.RED, I18n.translateToLocal("trmtgtnh.reinforce.needs"));
            return false;
        }

        setLevel(world, x, y, z, level + 1);
        ModAchievements.onReinforced(player);
        world.playSound(
            null,
            x + 0.5D,
            y + 0.5D,
            z + 0.5D,
            net.minecraft.init.SoundEvents.BLOCK_STONE_BREAK,
            net.minecraft.util.SoundCategory.BLOCKS,
            0.8f,
            1.4f);
        // A puff of block-breaking particles, which reads as "something set here" without a block
        // actually breaking.
        world.playEvent(
            2001,
            new net.minecraft.util.math.BlockPos(x, y, z),
            Block.getIdFromBlock(block) + (com.trmtgtnh.util.Worlds.metaAt(world, x, y, z) << 12));
        say(
            player,
            TextFormatting.AQUA,
            I18n.translateToLocalFormatted("trmtgtnh.reinforce.now", Integer.valueOf(level + 1), Integer.valueOf(cap)));
        return true;
    }

    /**
     * Left-click: takes a reinforcement off, but only on the second click at the same block.
     *
     * <p>
     * Armed on the first click and applied on the second, the same way a dangerous single-press is
     * made to take two - so a stray left-click while walking a wall does not quietly undo it.
     */
    public static boolean unreinforce(World world, int x, int y, int z, EntityPlayer player) {
        if (world == null || world.isRemote || !TrmtConfig.reinforceEnabled || player == null) return false;
        int level = level(world, x, y, z);
        if (level <= 0) {
            ARMED.remove(player);
            return false;
        }

        int[] armed = ARMED.get(player);
        int now = player.ticksExisted;
        boolean sameBlock = armed != null && armed[0] == x
            && armed[1] == y
            && armed[2] == z
            && now - armed[3] <= ARM_WINDOW_TICKS;
        if (!sameBlock) {
            ARMED.put(player, new int[] { x, y, z, now });
            say(player, TextFormatting.YELLOW, I18n.translateToLocal("trmtgtnh.reinforce.arm"));
            return true;
        }

        ARMED.remove(player);
        setLevel(world, x, y, z, level - 1);
        world.playSound(
            null,
            x + 0.5D,
            y + 0.5D,
            z + 0.5D,
            net.minecraft.init.SoundEvents.BLOCK_STONE_BREAK,
            net.minecraft.util.SoundCategory.BLOCKS,
            0.6f,
            0.8f);
        say(
            player,
            TextFormatting.AQUA,
            level - 1 <= 0 ? I18n.translateToLocal("trmtgtnh.reinforce.none")
                : I18n.translateToLocalFormatted(
                    "trmtgtnh.reinforce.now",
                    Integer.valueOf(level - 1),
                    Integer.valueOf(Math.min(3, TrmtConfig.reinforceMaxLevel))));
        return true;
    }

    // ------------------------------------------------------------------

    /** The reinforcement level at a position, for anything that is not a player's own gesture. */
    public static int levelAt(World world, int x, int y, int z) {
        return level(world, x, y, z);
    }

    /**
     * Raises a position's reinforcement, held to the configured ceiling.
     *
     * <p>
     * Exposed for the golem, which pays for its material the same way and so is allowed to set the
     * same figure. It does not take payment itself: whoever calls it has already settled that.
     */
    public static void raiseBy(World world, int x, int y, int z, int steps) {
        int cap = Math.min(3, TrmtConfig.reinforceMaxLevel);
        int now = level(world, x, y, z);
        int wanted = Math.min(cap, now + Math.max(0, steps));
        if (wanted != now) setLevel(world, x, y, z, wanted);
    }

    private static int level(World world, int x, int y, int z) {
        ErosionEntry entry = ErosionStore.get()
            .getEntry(world, x, y, z);
        return entry == null ? 0 : entry.getReinforce();
    }

    private static void setLevel(World world, int x, int y, int z, int level) {
        int dimension = world.provider.getDimension();
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        int key = ErosionKey.packWorld(x, y, z);
        com.trmtgtnh.erosion.ChunkErosionData data = ErosionStore.get()
            .getOrCreateChunk(dimension, chunkX, chunkZ);
        ErosionEntry entry = data.get(key);
        if (entry == null) {
            // Family from the block if it is a surface, dirt otherwise - it only matters if this
            // block ever wears; the record itself starts invisible, so no ghost is drawn. Its
            // threshold is a stand-in until the square is first walked on, when the engine draws
            // the family's own.
            SurfaceFamily family = SurfaceRegistry.familyOf(
                com.trmtgtnh.util.Worlds.blockAt(world, x, y, z),
                com.trmtgtnh.util.Worlds.metaAt(world, x, y, z));
            if (family == null || !family.staged) family = SurfaceFamily.DIRT;
            entry = new ErosionEntry(family, ErosionEntry.UNDRAWN_THRESHOLD, ErosionEngine.nowSeconds(world));
            data.put(key, entry);
        }
        entry.setReinforce(level);
        if (level <= 0 && entry.isPrunable()) {
            data.remove(key);
        }
        data.markDirty();
        ErosionStore.get()
            .markModified(world, chunkX, chunkZ);
    }

    private static void say(EntityPlayer player, TextFormatting colour, String message) {
        if (player != null) player.sendMessage(new TextComponentString(colour + message));
    }
}
