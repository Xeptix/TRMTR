package com.trmtgtnh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.List;

import net.minecraft.block.BlockStairs;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.trmtgtnh.block.BlockGhost;

/**
 * The ghost's geometry, checked against the blocks it stands in for.
 *
 * <p>
 * Nearly every test in this repository reads source, because a test that needs Minecraft running
 * needs Minecraft running. These do not need it running - only its classes loaded and its registries
 * populated, which {@code Bootstrap.register()} does in about a second - and the two questions here
 * are worth that, because both are places where being a pixel out is a thing a player walks into.
 *
 * <p>
 * <strong>The stair table is the reason this file exists.</strong> {@code BlockGhost.stairBoxes} is a
 * hand port of vanilla's {@code BlockStairs.getCollisionBoxList}, which is private and takes a state
 * this mod cannot hand it at runtime. Here it can: reflection reaches a private method perfectly well
 * in a test, so all forty stair states are compared against vanilla's own answer rather than against
 * my reading of it.
 */
class GhostGeometryMatchesVanillaTest {

    @BeforeAll
    static void loadTheGame() {
        Bootstrap.register();
    }

    @Test
    @SuppressWarnings("unchecked")
    void every_stair_state_gives_vanillas_own_boxes() throws Exception {
        Method theirs = BlockStairs.class.getDeclaredMethod("getCollisionBoxList", IBlockState.class);
        theirs.setAccessible(true);

        int checked = 0;
        for (EnumFacing facing : EnumFacing.HORIZONTALS) {
            for (BlockStairs.EnumHalf half : BlockStairs.EnumHalf.values()) {
                for (BlockStairs.EnumShape shape : BlockStairs.EnumShape.values()) {
                    IBlockState state = Blocks.OAK_STAIRS.getDefaultState()
                        .withProperty(BlockStairs.FACING, facing)
                        .withProperty(BlockStairs.HALF, half)
                        .withProperty(BlockStairs.SHAPE, shape);

                    int code = (facing.getHorizontalIndex() & 3) | (half == BlockStairs.EnumHalf.TOP ? 4 : 0)
                        | (shape.ordinal() << 3);

                    assertEquals(
                        (List<AxisAlignedBB>) theirs.invoke(null, state),
                        BlockGhost.stairBoxes(code),
                        "the boxes for " + facing
                            + " "
                            + half
                            + " "
                            + shape
                            + " are not the ones vanilla walks on - a worn stair of this shape is drawn "
                            + "as something other than the stair it covers");
                    checked++;
                }
            }
        }
        assertEquals(40, checked, "four facings, two halves and five shapes is forty states");
    }

    @Test
    void a_bottom_slab_is_drawn_in_the_bottom_half() {
        BlockPos pos = new BlockPos(0, 64, 0);

        int bottom = BlockGhost.outlineOf(Blocks.STONE_SLAB.getDefaultState(), null, pos);
        assertEquals(0F, BlockGhost.floorOf(bottom), 0.001F, "a bottom slab starts at the floor of its cell");
        assertEquals(0.5F, BlockGhost.topOf(bottom), 0.001F, "and stops half way up");

        int top = BlockGhost.outlineOf(
            Blocks.STONE_SLAB.getDefaultState()
                .withProperty(net.minecraft.block.BlockSlab.HALF, net.minecraft.block.BlockSlab.EnumBlockHalf.TOP),
            null,
            pos);
        assertEquals(0.5F, BlockGhost.floorOf(top), 0.001F, "a top slab starts half way up");
        assertEquals(1F, BlockGhost.topOf(top), 0.001F, "and reaches the ceiling");
    }

    @Test
    void a_demonstration_builds_stairs_and_slabs_the_way_they_are_placed() {
        // The trap, stated as a test so it cannot quietly come back: a block lists its states in
        // whatever order its properties enumerate, and for every vanilla stair and slab that order
        // begins with the upside-down half. A demonstration that takes the first declared metadata
        // builds the whole yard inverted - stairs upside down, slabs hung from the ceiling of their
        // cell with a half-block gap beneath them - and the ghosts then draw that faithfully, which
        // makes it look like a fault in the drawing.
        for (net.minecraft.block.Block block : new net.minecraft.block.Block[] { Blocks.OAK_STAIRS, Blocks.PURPUR_SLAB,
            Blocks.STONE_SLAB }) {
            int first = -1;
            for (IBlockState state : block.getBlockState()
                .getValidStates()) {
                int meta = block.getMetaFromState(state);
                if (meta >= 0 && meta < 16) {
                    first = meta;
                    break;
                }
            }
            int natural = block.getMetaFromState(block.getDefaultState());

            assertTrue(first != natural, block.getRegistryName() + " would need no rule if these agreed");
            assertEquals(
                0F,
                BlockGhost.floorOf(BlockGhost.outlineOf(block.getDefaultState(), null, new BlockPos(0, 64, 0))),
                0.001F,
                block.getRegistryName() + " placed as it normally is has to sit on the floor of its cell");
        }
    }

    @Test
    void a_block_narrower_than_its_square_keeps_its_own_footing() {
        BlockPos at = new BlockPos(0, 64, 0);

        // Ground that fills its square takes the worn box, which is a height off the top of a full
        // cell. ORDINARY is this rule saying "no opinion".
        for (net.minecraft.block.Block ordinary : new net.minecraft.block.Block[] { Blocks.STONE, Blocks.DIRT,
            Blocks.GRASS_PATH, Blocks.STONE_SLAB }) {
            IBlockState state = ordinary.getDefaultState();
            assertEquals(
                com.trmtgtnh.block.GhostInherit.ORDINARY,
                com.trmtgtnh.block.GhostInherit
                    .ownFootingAt(only(state, at), at, net.minecraft.block.Block.getStateId(state)),
                ordinary.getRegistryName() + " fills its square, so wear is the right shape for it");
        }

        // A cake is a pad inset on all four sides. A stand-in handing back a full cell over one of
        // these turns a block you fall through at the edges into a block you stand on.
        IBlockState narrow = Blocks.CAKE.getDefaultState();
        net.minecraft.util.math.AxisAlignedBB kept = com.trmtgtnh.block.GhostInherit
            .ownFootingAt(only(narrow, at), at, net.minecraft.block.Block.getStateId(narrow));
        assertTrue(
            kept != com.trmtgtnh.block.GhostInherit.ORDINARY && kept != null,
            "a block inset from its own square has to keep its own box");
        assertTrue(kept.minX > 0.0D || kept.maxX < 1.0D, "and that box is narrower than the square: " + kept);
    }

    /** The smallest world that will answer for one block, for asking a block its own shape. */
    private static net.minecraft.world.IBlockAccess only(final IBlockState state, final BlockPos at) {
        return new net.minecraft.world.IBlockAccess() {

            @Override
            public net.minecraft.tileentity.TileEntity getTileEntity(BlockPos pos) {
                return null;
            }

            @Override
            public int getCombinedLight(BlockPos pos, int minimum) {
                return 0;
            }

            @Override
            public IBlockState getBlockState(BlockPos pos) {
                return pos.equals(at) ? state : Blocks.AIR.getDefaultState();
            }

            @Override
            public boolean isAirBlock(BlockPos pos) {
                return !pos.equals(at);
            }

            @Override
            public net.minecraft.world.biome.Biome getBiome(BlockPos pos) {
                return null;
            }

            @Override
            public int getStrongPower(BlockPos pos, net.minecraft.util.EnumFacing direction) {
                return 0;
            }

            @Override
            public net.minecraft.world.WorldType getWorldType() {
                return net.minecraft.world.WorldType.DEFAULT;
            }

            @Override
            public boolean isSideSolid(BlockPos pos, net.minecraft.util.EnumFacing side, boolean fallback) {
                return fallback;
            }
        };
    }

    @Test
    void a_grass_path_is_a_cell_a_pixel_short() {
        int outline = BlockGhost.outlineOf(Blocks.GRASS_PATH.getDefaultState(), null, new BlockPos(0, 64, 0));
        assertEquals(0F, BlockGhost.floorOf(outline), 0.001F, "a path sits on the floor");
        assertTrue(
            BlockGhost.topOf(outline) < 1F && BlockGhost.topOf(outline) > 0.9F,
            "and stops just short of the top - it was " + BlockGhost.topOf(outline));
    }
}
