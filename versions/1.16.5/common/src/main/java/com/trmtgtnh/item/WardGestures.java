package com.trmtgtnh.item;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.world.level.Level;

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
    public static boolean left(Level world, int x, int y, int z, Player player, ItemStack stack,
        boolean sneaking) {
        return toggle(world, x, y, z, player, stack, HOSTILE, !sneaking);
    }

    /** Right-click: bars passives, or lets them back when sneaking. */
    public static boolean right(Level world, int x, int y, int z, Player player, ItemStack stack) {
        return toggle(world, x, y, z, player, stack, PASSIVE, player == null || !player.isShiftKeyDown());
    }

    private static boolean toggle(Level world, int x, int y, int z, Player player, ItemStack stack, int bit,
        boolean bar) {
        if (world == null || world.isClientSide() || !TrmtConfig.wardEnabled) return false;
        Block block = com.trmtgtnh.util.Worlds.blockAt(world, x, y, z);
        if (block == null || com.trmtgtnh.util.Worlds.isAir(world, x, y, z)) return false;

        int ward = wardAt(world, x, y, z);
        boolean already = (ward & bit) != 0;
        boolean hostile = bit == HOSTILE;

        if (bar) {
            if (already) {
                say(
                    player,
                    ChatFormatting.AQUA,
                    msg(hostile ? "trmtgtnh.ward.hostile.already" : "trmtgtnh.ward.passive.already"));
                return true;
            }
            boolean free = player != null && player.abilities.instabuild;
            if (!free) {
                boolean wayfarer = stack != null && stack.getItem() instanceof ItemMagicTamper;
                // Nought means free, as the setting says. See the same line for lighting.
                int count = Math.max(0, TrmtConfig.wardCostCount);
                if (wayfarer && count > 0) count = Math.max(1, count / 2);
                String[] materials = hostile ? TrmtConfig.wardHostileMaterials : TrmtConfig.wardPassiveMaterials;
                if (!MaterialCost.pay(player, materials, count)) {
                    say(player, ChatFormatting.RED, msg("trmtgtnh.ward.needs"));
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
                net.minecraft.sounds.SoundEvents.WITHER_SPAWN,
                net.minecraft.sounds.SoundSource.BLOCKS,
                0.4f,
                1.6f);
            world.levelEvent(2005, new net.minecraft.core.BlockPos(x, y, z), 0);
            damage(stack, player);
            say(player, ChatFormatting.AQUA, msg(hostile ? "trmtgtnh.ward.hostile.on" : "trmtgtnh.ward.passive.on"));
            return true;
        }

        // Letting a category back: a durability hit and nothing else.
        if (!already) {
            say(
                player,
                ChatFormatting.YELLOW,
                msg(hostile ? "trmtgtnh.ward.hostile.none" : "trmtgtnh.ward.passive.none"));
            return true;
        }
        setWardAt(world, x, y, z, ward & ~bit);
        world.playSound(
            null,
            x + 0.5D,
            y + 0.5D,
            z + 0.5D,
            net.minecraft.sounds.SoundEvents.STONE_BREAK,
            net.minecraft.sounds.SoundSource.BLOCKS,
            0.5f,
            0.8f);
        damage(stack, player);
        say(player, ChatFormatting.GRAY, msg(hostile ? "trmtgtnh.ward.hostile.off" : "trmtgtnh.ward.passive.off"));
        return true;
    }

    // ------------------------------------------------------------------

    private static int wardAt(Level world, int x, int y, int z) {
        ErosionEntry entry = ErosionStore.get()
            .getEntry(world, x, y, z);
        return entry == null ? 0 : entry.getWard();
    }

    private static void setWardAt(Level world, int x, int y, int z, int ward) {
        // The store's own index for this level. Both older editions ask the world for a
        // dimension number; there is none here, and the store has always been the thing that knows
        // which of its shelves a level is on.
        int dimension = ErosionStore.get()
            .indexOf(world);
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
            SurfaceFamily family = SurfaceRegistry.familyOf(com.trmtgtnh.util.Worlds.stateAt(world, x, y, z));
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

    private static void damage(ItemStack stack, Player player) {
        if (stack == null) return;
        if (player != null && player.abilities.instabuild) return;
        // A tool that does not wear - the Wayfarer - takes no scratch; everything else does.
        if (stack.getItem() instanceof ItemChunkTamper && !((ItemChunkTamper) stack.getItem()).wearsOut(stack)) {
            return;
        }
        // What to do when it breaks is handed over now rather than left to the game: announce
        // that the thing in the main hand went, which is what makes the sound and the particles.
        // Nobody holding it means nobody to damage it, where that edition would have damaged it on
        // behalf of nobody.
        if (player == null) return;
        stack.hurtAndBreak(
            1,
            player,
            broken -> broken.broadcastBreakEvent(net.minecraft.world.entity.EquipmentSlot.MAINHAND));
        // The other edition follows this by taking a broken tool out of the hand, because 1.7.10
        // leaves it there as a stack of nought with its damage reset to new. A stack of nothing is
        // empty everywhere in 1.12.2, so there is nothing left to tidy.
    }

    private static String msg(String key) {
        return com.trmtgtnh.util.Translate.get(key);
    }

    private static void say(Player player, ChatFormatting color, String message) {
        // The second argument is who said it, which this version wants: nobody did, and
        // NIL_UUID is how the game spells that.
        if (player != null) {
            player.sendMessage(new TextComponent(color + message), net.minecraft.Util.NIL_UUID);
        }
    }
}
