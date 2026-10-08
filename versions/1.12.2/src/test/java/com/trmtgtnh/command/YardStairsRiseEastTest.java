package com.trmtgtnh.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.block.BlockStairs;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.EnumFacing;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The demonstration lays its stairs rising east, as the 1.7.10 edition's does.
 *
 * <p>
 * That edition lays metadata nought, a bottom-half stair rising east - metadata nought here too. This one
 * laid the default state's metadata, which faces north, so until 0.9.219 its stair platform ran its steps
 * across the frame where 1.7.10's repeat along it, and the second yard's close-up of the stairs compared two
 * different shapes. Asked of the loaded game.
 */
class YardStairsRiseEastTest {

    @BeforeAll
    static void loadTheGame() {
        Bootstrap.register();
    }

    @Test
    void a_stair_is_laid_rising_east_on_the_floor_of_its_cell() {
        int meta = CommandTrmt.naturalMeta(Blocks.STONE_STAIRS);
        assertEquals(0, meta, "1.7.10 lays its stairs at metadata nought");
        assertEquals(
            EnumFacing.EAST,
            Blocks.STONE_STAIRS.getStateFromMeta(meta)
                .getValue(BlockStairs.FACING));
        assertEquals(
            BlockStairs.EnumHalf.BOTTOM,
            Blocks.STONE_STAIRS.getStateFromMeta(meta)
                .getValue(BlockStairs.HALF));
    }

    @Test
    void everything_else_is_laid_as_it_normally_is() {
        assertEquals(Blocks.DIRT.getMetaFromState(Blocks.DIRT.getDefaultState()), CommandTrmt.naturalMeta(Blocks.DIRT));
        assertEquals(
            Blocks.STONE_SLAB.getMetaFromState(Blocks.STONE_SLAB.getDefaultState()),
            CommandTrmt.naturalMeta(Blocks.STONE_SLAB));
    }
}
