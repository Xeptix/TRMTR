package com.trmtgtnh.item;

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
import com.trmtgtnh.server.MaterialCost;
import com.trmtgtnh.surface.SurfaceFamily;
import com.trmtgtnh.surface.SurfaceRegistry;

/**
 * Barring a block from spawning mobs, and letting them back.
 *
 * <p>
 * Left-click a block to bar hostiles, right-click to bar passives; sneak and click the same to
 * let a category back. Barring costs a material - several of it, the Wayfarer paying half -
 * because a peaceful patch of ground should be worth building; letting a category back is only a
 * scratch on the tool. The two flags live in the position's own erosion record next to its wear,
 * so a warded block need never have been walked on, and the record is dropped again once nothing
 * bars anything and nothing else is keeping it. What the flags then do to a spawn is in
 * {@code ServerEvents.onCheckSpawn}.
 *
 * <p>
 * Server-side only. {@link ItemChunkTamper} has already decided this is a ward click; here is the
 * rule.
 */
public final class WardGestures {

    private static final int HOSTILE = 0x1;
    private static final int PASSIVE = 0x2;

    private WardGestures() {}

    /** Left-click: bars hostiles, or lets them back when sneaking. */
    public static boolean left(World world, int x, int y, int z, EntityPlayer player, ItemStack stack,
        boolean sneaking) {
        return toggle(world, x, y, z, player, stack, HOSTILE, !sneaking);
    }

    /** Right-click: bars passives, or lets them back when sneaking. */
    public static boolean right(World world, int x, int y, int z, EntityPlayer player, ItemStack stack) {
        return toggle(world, x, y, z, player, stack, PASSIVE, player == null || !player.isSneaking());
    }

    private static boolean toggle(World world, int x, int y, int z, EntityPlayer player, ItemStack stack, int bit,
        boolean bar) {
        if (world == null || world.isRemote || !TrmtConfig.wardEnabled) return false;
        Block block = com.trmtgtnh.util.Worlds.blockAt(world, x, y, z);
        if (block == null || com.trmtgtnh.util.Worlds.isAir(world, x, y, z)) return false;

        int ward = wardAt(world, x, y, z);
        boolean already = (ward & bit) != 0;
        boolean hostile = bit == HOSTILE;

        if (bar) {
            if (already) {
                say(
                    player,
                    TextFormatting.AQUA,
                    msg(hostile ? "trmtgtnh.ward.hostile.already" : "trmtgtnh.ward.passive.already"));
                return true;
            }
            boolean free = player != null && player.capabilities.isCreativeMode;
            if (!free) {
                boolean wayfarer = stack != null && stack.getItem() instanceof ItemMagicTamper;
                // Nought means free, as the setting says. See the same line for lighting.
                int count = Math.max(0, TrmtConfig.wardCostCount);
                if (wayfarer && count > 0) count = Math.max(1, count / 2);
                String[] materials = hostile ? TrmtConfig.wardHostileMaterials : TrmtConfig.wardPassiveMaterials;
                if (!MaterialCost.pay(player, materials, count)) {
                    say(player, TextFormatting.RED, msg("trmtgtnh.ward.needs"));
                    return false;
                }
            }
            setWardAt(world, x, y, z, ward | bit);
            ModAchievements.onWarded(player);
            world.playSound(
                null,
                x + 0.5D,
                y + 0.5D,
                z + 0.5D,
                net.minecraft.init.SoundEvents.ENTITY_WITHER_SPAWN,
                net.minecraft.util.SoundCategory.BLOCKS,
                0.4f,
                1.6f);
            world.playEvent(2005, new net.minecraft.util.math.BlockPos(x, y, z), 0);
            damage(stack, player);
            say(player, TextFormatting.AQUA, msg(hostile ? "trmtgtnh.ward.hostile.on" : "trmtgtnh.ward.passive.on"));
            return true;
        }

        // Letting a category back: a durability hit and nothing else.
        if (!already) {
            say(
                player,
                TextFormatting.YELLOW,
                msg(hostile ? "trmtgtnh.ward.hostile.none" : "trmtgtnh.ward.passive.none"));
            return true;
        }
        setWardAt(world, x, y, z, ward & ~bit);
        world.playSound(
            null,
            x + 0.5D,
            y + 0.5D,
            z + 0.5D,
            net.minecraft.init.SoundEvents.BLOCK_STONE_BREAK,
            net.minecraft.util.SoundCategory.BLOCKS,
            0.5f,
            0.8f);
        damage(stack, player);
        say(player, TextFormatting.GRAY, msg(hostile ? "trmtgtnh.ward.hostile.off" : "trmtgtnh.ward.passive.off"));
        return true;
    }

    // ------------------------------------------------------------------

    private static int wardAt(World world, int x, int y, int z) {
        ErosionEntry entry = ErosionStore.get()
            .getEntry(world, x, y, z);
        return entry == null ? 0 : entry.getWard();
    }

    private static void setWardAt(World world, int x, int y, int z, int ward) {
        int dimension = world.provider.getDimension();
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        int key = ErosionKey.packWorld(x, y, z);
        com.trmtgtnh.erosion.ChunkErosionData data = ErosionStore.get()
            .getOrCreateChunk(dimension, chunkX, chunkZ);
        ErosionEntry entry = data.get(key);
        if (entry == null) {
            // Family from the block if it is a surface, dirt otherwise - it only matters if this
            // block ever wears; the record itself starts invisible, so no ghost is drawn. Same as
            // reinforcing an unworn block. Its threshold is a stand-in until the square is first
            // walked on, when the engine draws the family's own.
            SurfaceFamily family = SurfaceRegistry.familyOf(
                com.trmtgtnh.util.Worlds.blockAt(world, x, y, z),
                com.trmtgtnh.util.Worlds.metaAt(world, x, y, z));
            if (family == null || !family.staged) family = SurfaceFamily.DIRT;
            entry = new ErosionEntry(family, ErosionEntry.UNDRAWN_THRESHOLD, ErosionEngine.nowSeconds(world));
            data.put(key, entry);
        }
        entry.setWard(ward);
        if (ward == 0 && entry.isPrunable()) {
            data.remove(key);
        }
        data.markDirty();
        ErosionStore.get()
            .markModified(world, chunkX, chunkZ);
    }

    private static void damage(ItemStack stack, EntityPlayer player) {
        if (stack == null) return;
        if (player != null && player.capabilities.isCreativeMode) return;
        // A tool that does not wear - the Wayfarer - takes no scratch; everything else does.
        if (stack.getItem() instanceof ItemChunkTamper && !((ItemChunkTamper) stack.getItem()).wearsOut(stack)) {
            return;
        }
        stack.damageItem(1, player);
        // The other edition follows this by taking a broken tool out of the hand, because 1.7.10
        // leaves it there as a stack of nought with its damage reset to new. A stack of nothing is
        // empty everywhere in 1.12.2, so there is nothing left to tidy.
    }

    private static String msg(String key) {
        return I18n.translateToLocal(key);
    }

    private static void say(EntityPlayer player, TextFormatting color, String message) {
        if (player != null) player.sendMessage(new TextComponentString(color + message));
    }
}
