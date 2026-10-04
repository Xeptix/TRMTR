package com.trmtgtnh.util;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * Asking a world about a position by three numbers, the way the 1.7.10 edition does.
 *
 * <p>
 * This class exists for one reason, and it is a porting decision rather than a design one. 1.7.10
 * addresses a position as three integers and asks the world for a block and a metadata value; 1.12.2
 * addresses it as a {@code BlockPos} and answers with a state. The mod is written in the first idiom
 * from end to end - the erosion store's keys, the chain, the healing sweep and seventeen hundred lines
 * of engine all pass x, y and z - and rewriting all of that into the second would not be a port, it
 * would be a different mod with the same behaviour and none of the other edition's history.
 *
 * <p>
 * So the idiom is bridged here, in one small class, and the files that use it stay line-for-line
 * comparable with their twins in the other edition. What is lost is a little speed: a
 * {@code BlockPos} is made per call where 1.7.10 made none. That is the right trade for now and the
 * wrong one for ever - the hot paths are worth revisiting once the port is finished and there is
 * something to measure, and this comment is where whoever does that should start.
 *
 * <p>
 * Metadata is the other half of the bridge. 1.12.2 keeps it only as a compatibility view over a
 * block's state, so it is lossy for a block with more than sixteen variants; every question this mod
 * asks with one is about ground it detected by metadata in the first place, so the two agree.
 */
public final class Worlds {

    private Worlds() {}

    /** The state at a position. */
    public static IBlockState stateAt(IBlockAccess access, int x, int y, int z) {
        return access.getBlockState(new BlockPos(x, y, z));
    }

    /** The block at a position, as {@code world.getBlock(x, y, z)} answered in 1.7.10. */
    public static Block blockAt(IBlockAccess access, int x, int y, int z) {
        return stateAt(access, x, y, z).getBlock();
    }

    /**
     * The metadata at a position.
     *
     * <p>
     * Asked of the block rather than of the state, because that is the direction the compatibility
     * view runs, and defensively, because a block that cannot express its state as a number is one to
     * treat as its plain self rather than one to bring a sweep down.
     */
    public static int metaAt(IBlockAccess access, int x, int y, int z) {
        IBlockState state = stateAt(access, x, y, z);
        try {
            return state.getBlock()
                .getMetaFromState(state);
        } catch (RuntimeException awkwardBlock) {
            return 0;
        }
    }

    /** Whether the position is in a loaded chunk, as {@code blockExists} asked in 1.7.10. */
    public static boolean loaded(World world, int x, int y, int z) {
        return world.isBlockLoaded(new BlockPos(x, y, z));
    }

    /** Whether that chunk is in memory, as {@code chunkExists} asked in 1.7.10. */
    public static boolean chunkLoaded(World world, int chunkX, int chunkZ) {
        return world.getChunkProvider()
            .getLoadedChunk(chunkX, chunkZ) != null;
    }

    /** Whether the position holds air. */
    public static boolean isAir(World world, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        IBlockState state = world.getBlockState(pos);
        return state.getBlock()
            .isAir(state, world, pos);
    }

    public static void setToAir(World world, int x, int y, int z) {
        world.setBlockToAir(new BlockPos(x, y, z));
    }

    /** Drops what this block would drop, at this metadata value. */
    public static void dropAsItem(World world, Block block, int meta, int x, int y, int z, int fortune) {
        BlockPos pos = new BlockPos(x, y, z);
        IBlockState state;
        try {
            state = block.getStateFromMeta(meta);
        } catch (RuntimeException awkwardBlock) {
            state = block.getDefaultState();
        }
        block.dropBlockAsItem(world, pos, state, fortune);
    }

    /**
     * The break effect - the puff of the block's own pixels, and its sound.
     *
     * <p>
     * Event 2001 in both editions, but what it carries changed: 1.7.10 packs the block id and the
     * metadata into one number by hand, and 1.12.2 asks for the state's own id, which is the same idea
     * with the packing done for you.
     */
    public static void playBreakEffect(World world, int x, int y, int z, Block block, int meta) {
        IBlockState state;
        try {
            state = block.getStateFromMeta(meta);
        } catch (RuntimeException awkwardBlock) {
            state = block.getDefaultState();
        }
        world.playEvent(2001, new BlockPos(x, y, z), Block.getStateId(state));
    }

    /**
     * The same two questions of a chunk rather than of a world.
     *
     * <p>
     * A chunk is not an {@code IBlockAccess} in 1.12.2, and the healing sweep walks one directly -
     * deliberately, because going through the world for every position in a chunk it already holds
     * would look each one up again.
     */
    public static Block blockAt(net.minecraft.world.chunk.Chunk chunk, int x, int y, int z) {
        return chunk.getBlockState(new BlockPos(x, y, z))
            .getBlock();
    }

    public static int metaAt(net.minecraft.world.chunk.Chunk chunk, int x, int y, int z) {
        IBlockState state = chunk.getBlockState(new BlockPos(x, y, z));
        try {
            return state.getBlock()
                .getMetaFromState(state);
        } catch (RuntimeException awkwardBlock) {
            return 0;
        }
    }

    /** Whether the block at a position is an opaque cube, which needs its state to answer now. */
    public static boolean isOpaque(IBlockAccess access, int x, int y, int z) {
        IBlockState state = stateAt(access, x, y, z);
        return state.getBlock()
            .isOpaqueCube(state);
    }

    /** Sets a position's metadata, keeping the block it is on. */
    public static void setMeta(World world, int x, int y, int z, int meta, int flags) {
        BlockPos pos = new BlockPos(x, y, z);
        Block block = world.getBlockState(pos)
            .getBlock();
        try {
            world.setBlockState(pos, block.getStateFromMeta(meta), flags);
        } catch (RuntimeException awkwardBlock) {
            world.setBlockToAir(pos);
        }
    }

    /** What this block would drop at this metadata value. */
    public static java.util.List<net.minecraft.item.ItemStack> drops(World world, Block block, int meta, int x, int y,
        int z, int fortune) {
        BlockPos pos = new BlockPos(x, y, z);
        IBlockState state;
        try {
            state = block.getStateFromMeta(meta);
        } catch (RuntimeException awkwardBlock) {
            state = block.getDefaultState();
        }
        // A NonNullList rather than an ordinary one, because that is what 1.12.2 hands a block to fill
        // and it refuses a null entry on the way in - which is the point of it.
        net.minecraft.util.NonNullList<net.minecraft.item.ItemStack> out = net.minecraft.util.NonNullList.create();
        block.getDrops(out, world, pos, state, fortune);
        return out;
    }

    /** Whether the block at a position is air, without reading the state twice. */
    public static boolean isAirBlock(World world, int x, int y, int z) {
        return blockAt(world, x, y, z) == Blocks.AIR;
    }
}
