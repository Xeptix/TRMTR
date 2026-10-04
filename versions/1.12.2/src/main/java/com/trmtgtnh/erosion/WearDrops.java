package com.trmtgtnh.erosion;

import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.ForgeHooks;

import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.surface.SurfaceFamily;

/**
 * The odd thing a surface gives up as it is worn.
 *
 * <p>
 * Trodden grass sheds the occasional seed, packed snow a snowball, gravel a chip of flint - a
 * small nod to the fact that wearing ground in is doing something to it, at a rate low enough
 * that it is a surprise rather than a harvest.
 *
 * <p>
 * That is the rate traffic pays. A golem asks for its own, because it takes a whole level off a
 * square in one gesture where a footstep moves a shade - and a golem told to farm is meant to bring
 * back a harvest rather than a surprise.
 *
 * <p>
 * The seed comes from Forge's own grass-seed table, so it is whatever this pack's mods have
 * registered there - vanilla wheat on plain Forge, and every farming mod's seed on a pack that
 * has them - with no list of our own to fall out of date.
 *
 * <p>
 * Only the top of a column drops, and only above the bottom of the world. Ordinary foot traffic
 * only ever wears an exposed surface, so this changes nothing there; it is the area tampers,
 * which will wear a buried block as readily as a surface one, that the guard is really for - a
 * cube gesture drops from the ground it exposes, not from every block inside it.
 */
public final class WearDrops {

    private WearDrops() {}

    /** Rolls one drop for a block that just wore a stage, if it is an exposed surface above y=1. */
    public static void roll(net.minecraft.world.World world, int x, int y, int z, SurfaceFamily base) {
        ItemStack drop = rollFor(world, x, y, z, base, TrmtConfig.wearDropChance);
        if (drop != null) spawn(world, x, y, z, drop);
    }

    /**
     * What a square would shed, without putting it anywhere.
     *
     * <p>
     * Split from the throwing because a golem does not throw: the guide says it keeps what its own
     * working turns up, and a stack handed straight into its store is the difference between a
     * farm and eighty items a stroke lying on the floor waiting to despawn.
     *
     * <p>
     * The chance is the caller's rather than the config's for the same reason. Traffic wears the
     * ground a step at a time and sheds something once in a hundred; a golem takes a level off in
     * one gesture and sheds on that, which is a different unit and wants a different number.
     *
     * @return what came loose, or null when nothing did
     */
    public static ItemStack rollFor(net.minecraft.world.World world, int x, int y, int z, SurfaceFamily base,
        float chance) {
        if (world == null || world.isRemote || base == null) return null;
        if (!TrmtConfig.wearDropsEnabled || chance <= 0f) return null;
        if (y <= 1) return null;

        Block above = com.trmtgtnh.util.Worlds.blockAt(world, x, y + 1, z);
        if (above != null && above.isOpaqueCube(above.getDefaultState())) return null; // buried: not a top-level block

        if (world.rand.nextFloat() >= chance) return null;

        ItemStack drop = dropFor(world, base);
        return drop == null || drop.getItem() == null ? null : drop;
    }

    /** Puts a stack on the ground where it came loose, with the little hop a drop has. */
    public static void spawn(net.minecraft.world.World world, int x, int y, int z, ItemStack drop) {
        if (world == null || world.isRemote || drop == null) return;
        EntityItem entity = new EntityItem(world, x + 0.5D, y + 1.1D, z + 0.5D, drop);
        entity.motionX = (world.rand.nextDouble() - 0.5D) * 0.12D;
        entity.motionY = 0.2D;
        entity.motionZ = (world.rand.nextDouble() - 0.5D) * 0.12D;
        entity.setPickupDelay(10);
        world.spawnEntity(entity);
    }

    /**
     * Whether this is the sort of thing worn ground sheds.
     *
     * <p>
     * The mirror of {@link #dropFor}, and it has to be a mirror rather than a list because one of
     * the three cases is not a list: grass hands out whatever Forge's seed table holds, which is
     * every seed every farming mod in the pack registered, and that table is not readable from
     * outside Forge. So a seed is recognised by being one - {@code ItemSeeds}, or anything
     * plantable - which is what those entries are.
     *
     * <p>
     * A modded seed that is neither would be left lying on the ground rather than picked up, which
     * is the safe way round for a test that cannot be exact: the golem ignores something it might
     * have collected, instead of swallowing something it should not have.
     */
    public static boolean isWearDrop(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;
        net.minecraft.item.Item item = stack.getItem();
        if (item == Items.SNOWBALL || item == Items.FLINT) return true;
        return item instanceof net.minecraft.item.ItemSeeds || item instanceof net.minecraftforge.common.IPlantable;
    }

    private static ItemStack dropFor(net.minecraft.world.World world, SurfaceFamily base) {
        switch (base) {
            case GRASS:
                // Forge's own table: vanilla wheat seeds, plus every seed a farming mod added.
                return ForgeHooks.getGrassSeed(world.rand, 0);
            case SNOW:
                return new ItemStack(Items.SNOWBALL);
            case GRAVEL:
                return new ItemStack(Items.FLINT);
            default:
                return null;
        }
    }
}
