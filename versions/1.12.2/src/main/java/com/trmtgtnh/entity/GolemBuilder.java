package com.trmtgtnh.entity;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntitySkull;
import net.minecraft.world.World;
import net.minecraftforge.oredict.OreDictionary;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.util.BlockEntry;

/**
 * Building a Golem of Ways out of blocks, the way an iron golem is built.
 *
 * <p>
 * A body of packed earth under a block of packed stone, arms of lighter earth either side of the
 * upper block, and any player's head set on top last - which is the moment it wakes. The head is
 * the trigger because it is the piece that gives the thing a face, and because placing it last is
 * how everyone already expects a golem to be made.
 *
 * <p>
 * Which blocks count is config, tried in order, so a GregTech pack asks for compressed earth and
 * cobble - a genuinely large amount of both - while a plain one asks for the ordinary blocks in the
 * same shape. That is deliberate rather than a compromise: the golem is cheap to stand up and
 * expensive to run, because it eats tools and material for as long as it works.
 */
public final class GolemBuilder {

    private GolemBuilder() {}

    /** A player head was placed; if it completes the shape, stand a golem up. */
    public static void onHeadPlaced(World world, int x, int y, int z, EntityPlayer player) {
        if (world == null || world.isRemote || !TrmtConfig.golemEnabled) return;
        if (!isPlayerHead(world, x, y, z)) return;

        Block top = resolve(TrmtConfig.golemBodyTop);
        Block bottom = resolve(TrmtConfig.golemBodyBottom);
        Block arm = resolve(TrmtConfig.golemArms);
        if (top == null || bottom == null || arm == null) return;

        int topMeta = metaOf(TrmtConfig.golemBodyTop);
        int bottomMeta = metaOf(TrmtConfig.golemBodyBottom);
        int armMeta = metaOf(TrmtConfig.golemArms);

        // The body hangs straight down from the head.
        if (!matches(world, x, y - 1, z, top, topMeta)) return;
        if (!matches(world, x, y - 2, z, bottom, bottomMeta)) return;

        // Arms flank the upper body block, either east-west or north-south.
        boolean eastWest = matches(world, x - 1, y - 1, z, arm, armMeta)
            && matches(world, x + 1, y - 1, z, arm, armMeta);
        boolean northSouth = !eastWest && matches(world, x, y - 1, z - 1, arm, armMeta)
            && matches(world, x, y - 1, z + 1, arm, armMeta);
        if (!eastWest && !northSouth) return;

        // Take the shape apart before standing the golem in it, so nothing is left floating and
        // the block updates happen before the entity arrives.
        world.setBlockToAir(new net.minecraft.util.math.BlockPos(x, y, z));
        world.setBlockToAir(new net.minecraft.util.math.BlockPos(x, y - 1, z));
        world.setBlockToAir(new net.minecraft.util.math.BlockPos(x, y - 2, z));
        if (eastWest) {
            world.setBlockToAir(new net.minecraft.util.math.BlockPos(x - 1, y - 1, z));
            world.setBlockToAir(new net.minecraft.util.math.BlockPos(x + 1, y - 1, z));
        } else {
            world.setBlockToAir(new net.minecraft.util.math.BlockPos(x, y - 1, z - 1));
            world.setBlockToAir(new net.minecraft.util.math.BlockPos(x, y - 1, z + 1));
        }

        EntityGolemOfWays golem = new EntityGolemOfWays(world);
        golem.setLocationAndAngles(x + 0.5D, y - 2.05D, z + 0.5D, 0f, 0f);
        golem.setAnchor(x, y - 2, z);
        if (player != null) {
            golem.setSummonedBy(player.getName());
            com.trmtgtnh.item.ModAchievements.onGolemBuilt(player);
        }
        world.spawnEntity(golem);
        // A puff where the stone was, so it reads as the shape becoming the thing.
        world.playEvent(2001, new net.minecraft.util.math.BlockPos(x, y - 1, z), Block.getIdFromBlock(top));
        Trmt.LOG.debug(
            "A Golem of Ways was built at {}, {}, {}",
            Integer.valueOf(x),
            Integer.valueOf(y),
            Integer.valueOf(z));
    }

    /** Any player's head. Whose it is does not matter; that it is a face does. */
    private static boolean isPlayerHead(World world, int x, int y, int z) {
        if (com.trmtgtnh.util.Worlds.blockAt(world, x, y, z) != Blocks.SKULL) return false;
        TileEntity tile = world.getTileEntity(new net.minecraft.util.math.BlockPos(x, y, z));
        return tile instanceof TileEntitySkull && ((TileEntitySkull) tile).getSkullType() == 3;
    }

    private static boolean matches(World world, int x, int y, int z, Block wanted, int meta) {
        if (com.trmtgtnh.util.Worlds.blockAt(world, x, y, z) != wanted) return false;
        return meta == OreDictionary.WILDCARD_VALUE || com.trmtgtnh.util.Worlds.metaAt(world, x, y, z) == meta;
    }

    /**
     * The first entry in a role's list this pack can actually supply.
     *
     * <p>
     * Anything outside vanilla is skipped while the enhancements are off, so a pack that has asked
     * to behave as plain Forge builds the plain shape even when the compressed blocks happen to be
     * installed.
     */
    private static Block resolve(String[] candidates) {
        if (candidates == null) return null;
        for (String entry : candidates) {
            if (entry == null || entry.trim()
                .isEmpty()) {
                continue;
            }
            String name = BlockEntry.nameOf(entry);
            if (!TrmtConfig.gtnhEnhanced && !name.startsWith("minecraft:")) continue;
            Block block = Block.getBlockFromName(name);
            if (block != null && block != Blocks.AIR) return block;
            Item item = Item.REGISTRY.getObject(new net.minecraft.util.ResourceLocation(name));
            if (item != null) {
                Block fromItem = Block.getBlockFromItem(item);
                if (fromItem != null && fromItem != Blocks.AIR) return fromItem;
            }
        }
        return null;
    }

    /** The metadata of whichever entry {@link #resolve} settled on, or a wildcard when none. */
    private static int metaOf(String[] candidates) {
        if (candidates == null) return OreDictionary.WILDCARD_VALUE;
        for (String entry : candidates) {
            if (entry == null || entry.trim()
                .isEmpty()) {
                continue;
            }
            String name = BlockEntry.nameOf(entry);
            if (!TrmtConfig.gtnhEnhanced && !name.startsWith("minecraft:")) continue;
            Block block = Block.getBlockFromName(name);
            if (block != null && block != Blocks.AIR) {
                return BlockEntry.metaOf(entry, OreDictionary.WILDCARD_VALUE);
            }
        }
        return OreDictionary.WILDCARD_VALUE;
    }

    /** For a stack, so the recipe book and the guide can show what is wanted. */
    public static ItemStack sample(String[] candidates) {
        Block block = resolve(candidates);
        if (block == null) return null;
        Item item = Item.getItemFromBlock(block);
        if (item == null) return null;
        int meta = metaOf(candidates);
        return new ItemStack(item, 1, meta == OreDictionary.WILDCARD_VALUE ? 0 : meta);
    }
}
