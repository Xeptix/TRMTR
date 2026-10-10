package com.trmtgtnh.erosion;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;

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
    public static void roll(net.minecraft.world.level.Level world, int x, int y, int z, SurfaceFamily base) {
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
    public static ItemStack rollFor(net.minecraft.world.level.Level world, int x, int y, int z, SurfaceFamily base,
        float chance) {
        if (world == null || world.isClientSide() || base == null) return null;
        if (!TrmtConfig.wearDropsEnabled || chance <= 0f) return null;
        if (y <= com.trmtgtnh.util.Heights.bottom(world) + 1) return null;

        // buried: not a top-level block. An opaque cube above, as the other editions ask it - until 0.9.220 this
        // asked canOcclude, which a snow layer or a carpet says yes to (see Worlds.isOpaque).
        if (com.trmtgtnh.util.Worlds.isOpaque(world, x, y + 1, z)) return null;

        if (world.random.nextFloat() >= chance) return null;

        ItemStack drop = dropFor(world, base);
        return drop == null || drop.getItem() == null ? null : drop;
    }

    /** Puts a stack on the ground where it came loose, with the little hop a drop has. */
    public static void spawn(net.minecraft.world.level.Level world, int x, int y, int z, ItemStack drop) {
        if (world == null || world.isClientSide() || drop == null) return;
        ItemEntity entity = new ItemEntity(world, x + 0.5D, y + 1.1D, z + 0.5D, drop);
        entity.setDeltaMovement(
            (world.random.nextDouble() - 0.5D) * 0.12D,
            0.2D,
            (world.random.nextDouble() - 0.5D) * 0.12D);
        entity.setPickUpDelay(10);
        world.addFreshEntity(entity);
    }

    /**
     * Whether this is the sort of thing worn ground sheds.
     *
     * <p>
     * The mirror of {@link #dropFor}, and it has to be a mirror rather than a list because one of
     * the three cases is not a list: grass hands out whatever Forge's seed table holds, which is
     * every seed every farming mod in the pack registered, and that table is not readable from
     * outside either loader. So a seed is recognised by what it does: an item that places a
     * block which grows out of the ground, asked through the same seam the ground-cover detection
     * uses. Both older editions ask the item instead, because at those versions a seed item says so
     * about itself; here it does not, and the block it would place does.
     *
     * <p>
     * A modded seed that is neither would be left lying on the ground rather than picked up, which
     * is the safe way round for a test that cannot be exact: the golem ignores something it might
     * have collected, instead of swallowing something it should not have.
     */
    public static boolean isWearDrop(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;
        net.minecraft.world.item.Item item = stack.getItem();
        if (item == Items.SNOWBALL || item == Items.FLINT) return true;
        if (!(item instanceof net.minecraft.world.item.BlockItem)) return false;
        return com.trmtgtnh.surface.Plants.growsOnGround(
            ((net.minecraft.world.item.BlockItem) item).getBlock()
                .defaultBlockState());
    }

    /**
     * One seed off vanilla's grass table, rolled until it gives one, or null when it never does.
     *
     * <p>
     * A loot table is server-side data, so a client-side call gets nothing rather than a guess.
     * Every caller is already past an isClientSide() test; this is belt and braces.
     *
     * <p>
     * Rolled at the origin rather than where the wear happened, because grass's table has no
     * condition that reads a position and {@code dropFor} is not handed one.
     *
     * <p>
     * The grass block itself is filtered out rather than assumed absent. With no tool in hand the
     * table should not offer it - that drop wants shears - but a pack that has rewritten the table
     * is exactly the case this whole path exists to pick up, and handing a worn path a block to
     * drop instead of a seed would be a strange way to find that out.
     */
    private static ItemStack grassSeed(net.minecraft.world.level.Level world) {
        if (!(world instanceof net.minecraft.server.level.ServerLevel)) return null;
        // Rolled until it gives a seed, a bounded number of times. The 1.7.10 edition's seed table hands a seed over on
        // every roll that reaches it, so a turf square sheds at the full wearDropChance; vanilla's grass table here
        // gives one about one time in eight, and one roll shed at an eighth of the rate until 0.9.222 (spec WD62).
        // Sixty-four rolls leave about one chance in five thousand of nothing, and a pack's own seeds still come in
        // through the table.
        for (int roll = 0; roll < SEED_ROLLS; roll++) {
            java.util.List<ItemStack> rolled = com.trmtgtnh.util.Worlds.drops(
                (net.minecraft.server.level.ServerLevel) world,
                net.minecraft.world.level.block.Blocks.GRASS.defaultBlockState(),
                0,
                0,
                0);
            if (rolled == null) return null;
            for (ItemStack stack : rolled) {
                if (stack != null && !stack.isEmpty()
                    && stack.getItem() != net.minecraft.world.level.block.Blocks.GRASS.asItem()) {
                    return stack;
                }
            }
        }
        return null;
    }

    /** How many times vanilla's grass table is rolled for one seed. See {@link #grassSeed}. */
    static final int SEED_ROLLS = 64;

    private static ItemStack dropFor(net.minecraft.world.level.Level world, SurfaceFamily base) {
        switch (base) {
            case GRASS:
                // Vanilla's own grass loot table, which is where a seed comes from on both
                // loaders now - see grassSeed below for why this is not a loader question.
                return grassSeed(world);
            case SNOW:
                return new ItemStack(Items.SNOWBALL);
            case GRAVEL:
                return new ItemStack(Items.FLINT);
            default:
                return null;
        }
    }
}
