package com.trmtgtnh.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;

import net.minecraft.core.Registry;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.trmtgtnh.surface.SurfaceFamily;
import com.trmtgtnh.surface.SurfaceRegistry;

/**
 * Every family's road block is that family's ground - asked of the loaded game, not of the spelling.
 *
 * <p>
 * Found in 0.9.219: {@code roadBlock} answered {@code Blocks.GRASS} and {@code Blocks.SNOW}, the older
 * editions' names for the grass block and the snow block, which under this version's names are the plant
 * that grows on grass and the thin layer of snow. The demonstrate roads were laid in plants and snow
 * layers, the golem pens floored with them, and JourneyMap's stock colour for grass came from the plant -
 * and nothing complained, because both are perfectly good blocks. So the game is loaded and each answer is
 * asked what it is: the registry name it was given, and the family detection puts it in.
 */
class RoadBlocksAreGroundTest {

    /** Each family a road can be laid in, with the block its road should be made of. */
    private static final Object[][] ROADS = { { SurfaceFamily.GRASS, "minecraft:grass_block" },
        { SurfaceFamily.DIRT, "minecraft:dirt" }, { SurfaceFamily.SAND, "minecraft:sand" },
        { SurfaceFamily.GRAVEL, "minecraft:gravel" }, { SurfaceFamily.STONE, "minecraft:stone" },
        { SurfaceFamily.COBBLE, "minecraft:cobblestone" }, { SurfaceFamily.NETHER, "minecraft:netherrack" },
        { SurfaceFamily.END, "minecraft:end_stone" }, { SurfaceFamily.SNOW, "minecraft:snow_block" },
        { SurfaceFamily.ICE, "minecraft:ice" } };

    @BeforeAll
    static void loadTheGame() {
        Bootstrap.bootStrap();
    }

    private static SurfaceFamily classify(Block block) throws Exception {
        Method classify = SurfaceRegistry.class.getDeclaredMethod("classify", Block.class, String.class);
        classify.setAccessible(true);
        return (SurfaceFamily) classify.invoke(null, block, String.valueOf(Registry.BLOCK.getKey(block)));
    }

    @Test
    void every_road_is_laid_in_its_own_familys_plainest_ground() throws Exception {
        for (Object[] road : ROADS) {
            SurfaceFamily family = (SurfaceFamily) road[0];
            Block block = CommandTrmt.roadBlock(family);
            assertEquals(
                road[1],
                String.valueOf(Registry.BLOCK.getKey(block)),
                "the " + family + " road is laid in the wrong block");
            assertEquals(
                family,
                classify(block),
                "the " + family + " road block is not " + family + " ground to this mod's own detection");
        }
    }
}
