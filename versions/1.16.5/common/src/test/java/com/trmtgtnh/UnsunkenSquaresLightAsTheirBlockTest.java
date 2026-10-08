package com.trmtgtnh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * A worn square that has not sunk answers light as the block it covers.
 *
 * <p>
 * Written as fifteen until 0.9.219, which is that answer only for a whole block of earth. Worn ice then
 * stopped all light - the 1.7.10 edition gives a clear stand-in ice's own figure, and a path across a frozen
 * lake darkened the water under it - and a worn stair held no light in its own square, which is where
 * vanilla's renderer lights the faces inside a square: on Forge the second yard's worn stairs drew their
 * risers black. So the methods are read for the call, and the game is asked what the blocks they now
 * defer to actually answer, because the fix is only as good as those figures.
 */
class UnsunkenSquaresLightAsTheirBlockTest {

    @BeforeAll
    static void loadTheGame() {
        Bootstrap.bootStrap();
    }

    @Test
    void an_unsunken_square_answers_light_as_the_block_it_covers() throws IOException {
        String light = flat(body(ghost(), "getLightBlock"));
        assertTrue(
            light.contains("if(sunkAt(world,pos))return0;") && light.contains("BlockStatecovered=coveredFor(world,pos);")
                && light.contains("returncovered==null?15:covered.getLightBlock(world,pos);"),
            "an unsunken square stops light whatever block it covers: " + light);
        String sky = flat(body(ghost(), "propagatesSkylightDown"));
        assertTrue(
            sky.contains("if(sunkAt(world,pos))returntrue;") && sky.contains("BlockStatecovered=coveredFor(world,pos);")
                && sky.contains("returncovered!=null&&covered.propagatesSkylightDown(world,pos);"),
            "an unsunken square answers skylight as earth would, whatever it covers: " + sky);
    }

    @Test
    void a_square_whose_block_is_unknown_is_earth_unless_worn_as_ice() throws IOException {
        String covered = flat(body(ghost(), "coveredFor"));
        assertTrue(
            covered.contains("if(covered!=null&&!(covered.getBlock()instanceofBlockGhost))returncovered;"),
            "the block a square covers is not what it answers light as: " + covered);
        assertTrue(
            covered.contains("==com.trmtgtnh.surface.SurfaceFamily.ICE?net.minecraft.world.level.block.Blocks.ICE.defaultBlockState():null;"),
            "an unknown square worn as ice is not answered as ice: " + covered);
    }

    /**
     * And what those blocks answer, which is the whole of the fix: ice and a stair let light into their
     * square, earth and packed ice stop all of it.
     */
    @Test
    void the_blocks_deferred_to_answer_as_the_fix_needs() {
        assertEquals(1, light(Blocks.ICE.defaultBlockState()), "ice stops more than a level of light");
        assertTrue(light(Blocks.COBBLESTONE_STAIRS.defaultBlockState()) < 15, "a stair holds no light in its square");
        assertEquals(15, light(Blocks.DIRT.defaultBlockState()), "earth lets light through");
        assertEquals(15, light(Blocks.PACKED_ICE.defaultBlockState()), "packed ice lets light through");
        assertFalse(
            Blocks.ICE.defaultBlockState()
                .propagatesSkylightDown(EmptyBlockGetter.INSTANCE, BlockPos.ZERO),
            "ice lets the sky straight down, so worn ice would light the water under it as if it were not there");
    }

    private static int light(net.minecraft.world.level.block.state.BlockState state) {
        return state.getLightBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
    }

    private static String ghost() throws IOException {
        return String.join("\n", SourceTree.lines("com/trmtgtnh/block/BlockGhost.java"));
    }

    private static String flat(String code) {
        return code.replaceAll("\\s+", "");
    }

    /** One method's body by its name, comments left out. */
    private static String body(String source, String name) {
        Matcher found = Pattern.compile("\\b" + name + "\\s*\\([^)]*\\)\\s*(throws\\s+[\\w.,\\s]+)?\\{")
            .matcher(source);
        assertTrue(found.find(), "no method " + name);
        int open = source.indexOf('{', found.start());
        int depth = 0;
        for (int at = open; at < source.length(); at++) {
            char each = source.charAt(at);
            if (each == '{') depth++;
            else if (each == '}' && --depth == 0) {
                return source.substring(open + 1, at)
                    .replaceAll("(?s)/\\*.*?\\*/", " ")
                    .replaceAll("//[^\\n]*", " ");
            }
        }
        return "";
    }
}
