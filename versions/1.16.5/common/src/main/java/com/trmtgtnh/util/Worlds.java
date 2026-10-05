package com.trmtgtnh.util;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Asking a world about a position by three numbers, the way the 1.7.10 edition does.
 *
 * <p>
 * This class exists for one reason, and it is a porting decision rather than a design one. 1.7.10
 * addresses a position as three integers; every version since addresses it as a {@code BlockPos}. The
 * mod is written in the first idiom from end to end - the erosion store's keys, the chain, the healing
 * sweep and seventeen hundred lines of engine all pass x, y and z - and rewriting all of that would
 * not be a port, it would be a different mod with the same behaviour and none of the other editions'
 * history. So the idiom is bridged here, in one small class, and the files that use it stay
 * line-for-line comparable with their twins.
 *
 * <p>
 * What is lost is a little speed: a {@code BlockPos} is made per call where 1.7.10 made none. That is
 * the right trade for now and the wrong one for ever - the hot paths are worth revisiting once the
 * port is finished and there is something to measure, and this comment is where whoever does that
 * should start.
 *
 * <p>
 * <strong>The metadata half of the bridge is gone, and it cannot come back.</strong> The 1.12.2
 * edition has {@code metaAt}, {@code setMeta} and a pair of {@code getStateFromMeta} calls, and notes
 * that 1.12.2 keeps metadata "only as a compatibility view over a block's state". 1.13 removed even
 * the view. There is no number to ask for and none to set, so those four methods are not ported and
 * nothing here takes a {@code meta} parameter.
 *
 * <p>
 * The consequence reaches further than this class and is worth stating plainly: <strong>the surface
 * layer keys by {@link BlockState} in this edition</strong>, where the older two key by block and
 * metadata. That is not a loss. Every question this mod asked with a metadata value was about ground
 * it had detected by metadata in the first place, so a state is the same question asked exactly -
 * and strictly better for a modded block with more than sixteen variants, which metadata could not
 * describe. The portable core is untouched by this: it already stores a whole state id rather than a
 * block and a metadata pair, which is why those twenty-eight classes crossed unchanged.
 */
public final class Worlds {

    private Worlds() {}

    /** The state at a position. */
    public static BlockState stateAt(BlockGetter access, int x, int y, int z) {
        return access.getBlockState(new BlockPos(x, y, z));
    }

    /** The block at a position, as {@code world.getBlock(x, y, z)} answered in 1.7.10. */
    public static Block blockAt(BlockGetter access, int x, int y, int z) {
        return stateAt(access, x, y, z).getBlock();
    }

    /** Whether the position is in a loaded chunk, as {@code blockExists} asked in 1.7.10. */
    public static boolean loaded(Level level, int x, int y, int z) {
        return level.isLoaded(new BlockPos(x, y, z));
    }

    /**
     * Whether that chunk is in memory, as {@code chunkExists} asked in 1.7.10.
     *
     * <p>
     * Asked without causing a load, which is the whole point: the healing sweep walks chunks it has
     * records for and must not drag one off disk to find out there is nothing to do in it.
     */
    public static boolean chunkLoaded(Level level, int chunkX, int chunkZ) {
        return level.getChunkSource()
            .getChunkNow(chunkX, chunkZ) != null;
    }

    /** Whether the position holds air. The state answers this itself now. */
    public static boolean isAir(BlockGetter access, int x, int y, int z) {
        return stateAt(access, x, y, z).isAir();
    }

    public static void setToAir(Level level, int x, int y, int z) {
        level.removeBlock(new BlockPos(x, y, z), false);
    }

    /**
     * Puts a state at a position, with the update flags the caller wants.
     *
     * <p>
     * Both older editions have a setBlockMetadataWithNotify for this, because what they change at a
     * position is a number beside the block. Here there is no number and the state is the whole
     * thing, so the same call takes one - which also means a caller can change one property and
     * leave the rest alone, which is what the snow depth does.
     *
     * <p>
     * The flags are vanilla's own and mean what they have always meant: 2 tells the clients and
     * does not run a block update, which is what a cosmetic change wants.
     */
    public static void setState(Level level, int x, int y, int z, BlockState state, int flags) {
        if (level == null || state == null) return;
        level.setBlock(new BlockPos(x, y, z), state, flags);
    }

    /**
     * Drops what the state at this position would drop.
     *
     * <p>
     * Takes the state rather than a block and a metadata value, and takes it rather than reading it,
     * because every caller is about to replace what is there and already holds what it is replacing.
     */
    public static void dropAsItem(Level level, BlockState state, int x, int y, int z) {
        Block.dropResources(state, level, new BlockPos(x, y, z));
    }

    /**
     * The break effect - the puff of the block's own pixels, and its sound.
     *
     * <p>
     * Event 2001 in every edition, but what it carries changed twice: 1.7.10 packs a block id and a
     * metadata value into one number by hand, 1.12.2 asks for the state's own id, and here it is the
     * same idea under a shorter name.
     */
    public static void playBreakEffect(Level level, int x, int y, int z, BlockState state) {
        level.levelEvent(2001, new BlockPos(x, y, z), Block.getId(state));
    }

    /**
     * The same question of a chunk rather than of a world.
     *
     * <p>
     * A chunk is not a {@code BlockGetter} the healing sweep can use interchangeably, and the sweep
     * walks one directly - deliberately, because going through the world for every position in a
     * chunk it already holds would look each one up again.
     */
    public static BlockState stateAt(LevelChunk chunk, int x, int y, int z) {
        return chunk.getBlockState(new BlockPos(x, y, z));
    }

    public static Block blockAt(LevelChunk chunk, int x, int y, int z) {
        return stateAt(chunk, x, y, z).getBlock();
    }

    /**
     * Whether the block at a position is an opaque cube.
     *
     * <p>
     * The state answers it now, and under a name that says what it is for: whether this block lets the
     * renderer stop drawing what is behind it.
     */
    public static boolean isOpaque(BlockGetter access, int x, int y, int z) {
        return stateAt(access, x, y, z).canOcclude();
    }

    /**
     * What the state at this position would drop.
     *
     * <p>
     * Needs a server world, which the older editions' version did not: drops are decided by a loot
     * table here, and loot tables live on the server. Every caller of this is already server-side -
     * mending ground and breaking it are both server decisions - so the narrower parameter costs
     * nothing and says something true.
     */
    public static List<ItemStack> drops(ServerLevel level, BlockState state, int x, int y, int z) {
        return Block.getDrops(state, level, new BlockPos(x, y, z), null);
    }

    /**
     * Whether a block has enough shape to be a full cube, asked with nowhere to ask about.
     *
     * <p>
     * Both older editions ask {@code isFullCube()} of a state and need nothing else. Here the shape
     * may depend on where the block is, so the question takes a world and a position - and the two
     * callers of this have neither: one is sorting a registry and one is identifying a kind of block.
     * {@code EmptyBlockGetter} is the game's own answer to that, a world with nothing in it, which
     * is exactly what a block considered in the abstract stands in.
     *
     * <p>
     * A block that throws rather than answer is not a full cube as far as this is concerned. That is
     * the same way round both older editions fail, and it is the safe direction: the callers use a
     * false to mean "thin enough to walk through", and treating an awkward block as thin leaves it
     * alone where treating it as solid would let a path form on top of it.
     */
    public static boolean fullCube(BlockState state) {
        if (state == null) return false;
        try {
            return state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        } catch (RuntimeException awkwardBlock) {
            return false;
        }
    }
}
