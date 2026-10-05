package com.trmtgtnh.erosion;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.surface.SurfaceRegistry;

/**
 * What becomes of the plants standing in a route as the route wears in.
 *
 * <p>
 * A tuft of grass is not ground and never wears, but it does not survive the ground under it being
 * walked into a path either - whatever wore the square went through the plant to do it. So when a
 * square advances a gradation, whatever is resting on it goes with it. Nothing is counted and
 * nothing is stored for the plant's own position: this is a consequence of the ground moving,
 * asked once at the moment the ground moves, and that is the whole of the mechanism.
 *
 * <p>
 * That is what separates it from the older trampling switch, which gives the plant a wear record
 * of its own and breaks it on its own tally. Two things recommend this one. It costs no entry in
 * the chunk's record, and the per-chunk cap is a real ceiling that a meadow of flowers can eat.
 * And it only ever fires where a path is visibly forming, so a plant never vanishes off a square
 * that still looks untouched.
 *
 * <p>
 * This edits the world, which is why every caller does it last rather than first: breaking a block
 * runs neighbour updates through arbitrary mod code, and everything the engine wanted of the
 * position should be finished before that happens.
 */
public final class GroundCover {

    /**
     * The items a knocked-down plant has actually turned out to leave behind.
     *
     * <p>
     * Learned rather than listed, because what a plant drops is the plant's own business and no
     * table written here would keep up. Sugar cane leaves an item that is not its block and is not
     * a seed and implements nothing; a modded tuft may leave anything at all. Watching what comes
     * off the ones this actually breaks costs nothing and is right for every mod in the pack
     * without naming one of them.
     *
     * <p>
     * Session-lived and never saved, which is the whole point - it describes the pack that is
     * loaded. Bounded because it is fed by arbitrary mod code and an unbounded set fed by mod code
     * is a leak waiting for the one mod that returns a fresh item every call.
     */
    private static final Set<Item> KNOCKED_LOOSE = Collections.newSetFromMap(new ConcurrentHashMap<Item, Boolean>());

    private static final int MOST_REMEMBERED = 512;

    private GroundCover() {}

    /**
     * Whether something planted is holding this square together.
     *
     * <p>
     * Asked of the block above through whatever view of the world the caller has, because both
     * sides have to reach the same answer and both sides can see that block: the client draws what
     * it decides and the server stands people on it, and a disagreement here is a player sunk into
     * ground that looks flat. Nothing about the record is consulted at all - a plant either is
     * there or is not.
     */
    public static boolean holdsAt(net.minecraft.world.level.BlockGetter world, int x, int y, int z) {
        if (world == null || y < 0 || y >= 255) return false;
        // Asked of the registry rather than of the config, so a client uses its server's answer for
        // the visit. See SurfaceRegistry.groundCoverHoldsOn.
        if (!TrmtConfig.enabled || !SurfaceRegistry.groundCoverHoldsOn()) return false;
        BlockState above = com.trmtgtnh.util.Worlds.stateAt(world, x, y + 1, z);
        if (above == null) return false;
        return SurfaceRegistry.isGroundCoverHolder(above);
    }

    /**
     * Knocks down whatever is standing on the square that just wore, if anything is.
     *
     * <p>
     * Given the coordinates of the <em>ground</em> rather than of the plant, because every caller
     * has just finished working on the ground and none of them has any reason to know what is
     * above it.
     *
     * <p>
     * The dimension and height bounds are tested here rather than left to the callers. Two of the
     * three arrive through paths that have already tested them and one does not, and a rule whose
     * whole cost is a real block being destroyed is not a rule to leave to whoever calls next.
     *
     * @param noisy    whether to play the break sound and particles, which is false across a sweep
     *                 where eighty of them at once would be a wall of noise rather than a signal
     * @param physical whether the ground actually dropped a level rather than merely darkening a
     *                 gradation, which by default is the only thing that brings a plant down
     */
    public static void breakAbove(Level world, int x, int y, int z, boolean noisy, boolean physical) {
        if (world == null || world.isClientSide()) return;
        if (!TrmtConfig.enabled || !TrmtConfig.groundCoverBreaks) return;
        // Eighty gradations is the whole run and eight sunk pixels is the whole run, so waiting for
        // the ground to physically drop is a tenth as often - and it is the moment the plant
        // visibly has nothing left to stand on. A gradation is a change of colour; a level is the
        // ground going out from under it.
        if (TrmtConfig.groundCoverOnSink && !physical) return;
        if (!Dimensions.allowed(world)) return;
        // The plant lives at y + 1, so the top layer of the world can hold ground but never cover.
        if (y < TrmtConfig.minY || y >= TrmtConfig.maxY || y >= 255) return;

        int above = y + 1;
        BlockState plant = com.trmtgtnh.util.Worlds.stateAt(world, x, above, z);
        if (plant == null || com.trmtgtnh.util.Worlds.isAir(world, x, above, z)) return;

        if (!SurfaceRegistry.isGroundCover(plant)) return;
        // A holder is not knocked down; it is why the square never got this far. Tested here as
        // well as where the wear is held back, because the two are one rule seen from both ends and
        // a plant that held the ground and then broke anyway would be the worst of both.
        if (SurfaceRegistry.groundCoverHoldsOn() && SurfaceRegistry.isGroundCoverHolder(plant)) return;
        // Hoed ground is the one honest signal that somebody planted this. Natura's cotton is a
        // crop by Forge's reckoning and also grows wild on plain grass, so asking the plant what
        // it is cannot separate a field from a meadow - asking what it was planted in can.
        if (!TrmtConfig.groundCoverOnTilled && com.trmtgtnh.util.Worlds.blockAt(world, x, y, z) == Blocks.FARMLAND)
            return;

        float chance = TrmtConfig.groundCoverDropChance;
        if (chance >= 1f || (chance > 0f && world.random.nextFloat() < chance)) {
            remember(plant, world, x, above, z);
            com.trmtgtnh.util.Worlds.dropAsItem(world, plant, x, above, z);
        }
        // The sound and particles of something breaking, then the block itself. Spelled out rather
        // than calling the world's own destroy helper, which is unmapped here - the same way
        // trampling does it, so the two read alike. Breaking the lower half of a double plant is
        // safe: the neighbour update takes the upper half with it and vanilla drops nothing twice.
        if (noisy) com.trmtgtnh.util.Worlds.playBreakEffect(world, x, above, z, plant);
        // A stalk goes with its base and this does not have to walk it. Cactus and cane both
        // refuse to stand on nothing - BlockCactus and BlockReed each answer onNeighborBlockChange
        // by testing canBlockStay and breaking themselves - so taking the bottom one takes the
        // whole column, each piece dropping in full as it goes.
        com.trmtgtnh.util.Worlds.setToAir(world, x, above, z);
    }

    /**
     * Notes what this block leaves behind, so a golem knows to pick it up.
     *
     * <p>
     * Asked of the block rather than of the entities afterwards, because the drops are spawned by
     * the game's own path and handing them back is not part of it. It is one extra query on a block
     * that is about to be destroyed anyway, and only on the far rarer branch where the drop roll
     * has already succeeded.
     *
     * <p>
     * A loot table is server-side data, so this needs a server level rather than any level. Every
     * caller is already past an isClientSide() test and so always has one; it is tested rather than
     * cast because a cast that cannot fail today is a crash the first time somebody calls this from
     * somewhere new, and a plant whose drops went unlearned is a golem that walks past one item.
     */
    private static void remember(BlockState plant, Level world, int x, int y, int z) {
        if (KNOCKED_LOOSE.size() >= MOST_REMEMBERED) return;
        if (!(world instanceof ServerLevel)) return;
        try {
            List<ItemStack> drops = com.trmtgtnh.util.Worlds.drops((ServerLevel) world, plant, x, y, z);
            if (drops == null) return;
            for (ItemStack stack : drops) {
                if (stack != null && stack.getItem() != null) KNOCKED_LOOSE.add(stack.getItem());
            }
        } catch (RuntimeException awkwardPlant) {
            // A block that cannot say what it drops simply is not learned from. Nothing here is
            // load-bearing enough to be worth letting an exception out of a wear tick for.
        }
    }

    /**
     * Whether this is the sort of thing a forming path knocks loose, and so worth carrying home.
     *
     * <p>
     * Two answers, because there are two shapes of drop. A flower, a cactus and Biomes O' Plenty's
     * foliage all drop their own block, which the cover set already knows about and which is true
     * before anything has ever been broken. Sugar cane, seeds and a good deal of modded produce
     * drop something else entirely, and those are known only from having seen one come off.
     */
    public static boolean isCoverDrop(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;
        Item item = stack.getItem();
        if (KNOCKED_LOOSE.contains(item)) return true;
        // The stack's damage value is not asked about. In both older editions it picks out which
        // variant of a block this item is; here the variants are separate items with separate
        // blocks behind them, so the item alone says which block it would place.
        Block block = Block.byItem(item);
        return block != null && block != Blocks.AIR && SurfaceRegistry.isGroundCover(block);
    }
}
