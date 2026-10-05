package com.trmtgtnh.entity;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.Level;
import com.trmtgtnh.util.OreNames;

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
    public static void onHeadPlaced(Level world, int x, int y, int z, Player player) {
        if (world == null || world.isClientSide() || !TrmtConfig.golemEnabled) return;
        if (!isPlayerHead(world, x, y, z)) return;

        Block top = resolve(TrmtConfig.golemBodyTop);
        Block bottom = resolve(TrmtConfig.golemBodyBottom);
        Block arm = resolve(TrmtConfig.golemArms);
        if (top == null || bottom == null || arm == null) return;

        // The body hangs straight down from the head.
        if (!matches(world, x, y - 1, z, top)) return;
        if (!matches(world, x, y - 2, z, bottom)) return;

        // Arms flank the upper body block, either east-west or north-south.
        boolean eastWest = matches(world, x - 1, y - 1, z, arm)
            && matches(world, x + 1, y - 1, z, arm);
        boolean northSouth = !eastWest && matches(world, x, y - 1, z - 1, arm)
            && matches(world, x, y - 1, z + 1, arm);
        if (!eastWest && !northSouth) return;

        // Take the shape apart before standing the golem in it, so nothing is left floating and
        // the block updates happen before the entity arrives.
        world.removeBlock(new net.minecraft.core.BlockPos(x, y, z), false);
        world.removeBlock(new net.minecraft.core.BlockPos(x, y - 1, z), false);
        world.removeBlock(new net.minecraft.core.BlockPos(x, y - 2, z), false);
        if (eastWest) {
            world.removeBlock(new net.minecraft.core.BlockPos(x - 1, y - 1, z), false);
            world.removeBlock(new net.minecraft.core.BlockPos(x + 1, y - 1, z), false);
        } else {
            world.removeBlock(new net.minecraft.core.BlockPos(x, y - 1, z - 1), false);
            world.removeBlock(new net.minecraft.core.BlockPos(x, y - 1, z + 1), false);
        }

        EntityGolemOfWays golem = ModEntities.newGolem(world);
        golem.moveTo(x + 0.5D, y - 2.05D, z + 0.5D, 0f, 0f);
        golem.setAnchor(x, y - 2, z);
        if (player != null) {
            golem.setSummonedBy(player.getGameProfile()
                .getName());
            com.trmtgtnh.item.ModAchievements.onGolemBuilt(player);
        }
        world.addFreshEntity(golem);
        // A puff where the stone was, so it reads as the shape becoming the thing.
        world.levelEvent(2001, new net.minecraft.core.BlockPos(x, y - 1, z), net.minecraft.core.Registry.BLOCK.getId(top));
        Trmt.LOG.debug(
            "A Golem of Ways was built at {}, {}, {}",
            Integer.valueOf(x),
            Integer.valueOf(y),
            Integer.valueOf(z));
    }

    /** Any player's head. Whose it is does not matter; that it is a face does. */
    private static boolean isPlayerHead(Level world, int x, int y, int z) {
        // 1.13 split the one skull block with its five metadata values into five blocks, so the
        // block itself is the whole answer and there is no tile entity to ask. PLAYER_HEAD and not
        // PLAYER_WALL_HEAD on purpose: a head on a wall is beside the body rather than on top of
        // it, which is what the other edition's metadata was saying.
        return com.trmtgtnh.util.Worlds.blockAt(world, x, y, z) == Blocks.PLAYER_HEAD;
    }

    private static boolean matches(Level world, int x, int y, int z, Block wanted) {
        return com.trmtgtnh.util.Worlds.blockAt(world, x, y, z) == wanted;
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
            net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation
                .tryParse(name);
            // tryParse rather than the constructor, which throws on a name with a capital or a
            // space in it. 1.12.2's getBlockFromName answered null for one of those, and a settings
            // file somebody has typed into is exactly where one turns up - so a bad entry is still
            // skipped here rather than taking the server down on the first head anybody places.
            if (id == null) continue;
            Block block = net.minecraft.core.Registry.BLOCK.get(id);
            if (block != null && block != Blocks.AIR) return block;
            Item item = net.minecraft.core.Registry.ITEM.get(id);
            if (item != null) {
                Block fromItem = Block.byItem(item);
                if (fromItem != null && fromItem != Blocks.AIR) return fromItem;
            }
        }
        return null;
    }

    /** For a stack, so the recipe book and the guide can show what is wanted. */
    public static ItemStack sample(String[] candidates) {
        Block block = resolve(candidates);
        if (block == null) return null;
        Item item = block.asItem();
        if (item == null || item == net.minecraft.world.item.Items.AIR) return null;
        return new ItemStack(item, 1);
    }
}
