package com.trmtgtnh.surface;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Frosted ice is not ground, and real ice still is - asked of the real blocks, not of a reading of them.
 *
 * <p>
 * Found by the 0.9.219 photograph pass: every demonstrate yard in this edition and in 1.16.5 poured a
 * waterfall off its edge, where the 1.7.10 yard does not. The ice family is chosen by material, and
 * frosted ice - the ice a Frost Walker boot lays, which the game melts within seconds - is ice by
 * material, so a platform of it was laid and melted. 1.7.10 has no such block. So the classifier is
 * asked, with the game loaded, about the three blocks that matter: the one that must now be refused,
 * and the two that must not have been refused with it.
 */
class FrostedIceIsNotGroundTest {

    @BeforeAll
    static void loadTheGame() {
        Bootstrap.register();
    }

    private static SurfaceFamily classify(Block block) throws Exception {
        Method classify = SurfaceRegistry.class.getDeclaredMethod("classify", Block.class, String.class);
        classify.setAccessible(true);
        return (SurfaceFamily) classify.invoke(null, block, String.valueOf(block.getRegistryName()));
    }

    @Test
    void frosted_ice_is_refused_and_real_ice_is_not() throws Exception {
        assertNull(
            classify(Blocks.FROSTED_ICE),
            "frosted ice melts by itself; as a surface it laid a demonstrate platform that flooded the yard");
        assertEquals(SurfaceFamily.ICE, classify(Blocks.ICE), "ordinary ice stopped being ice");
        assertEquals(SurfaceFamily.ICE, classify(Blocks.PACKED_ICE), "packed ice stopped being ice");
    }

    /**
     * The list a config already holds cannot put it back.
     *
     * <p>
     * Detection writes what it finds into each family's list and never takes an entry out, so every
     * config opened by 0.9.218 or earlier lists {@code minecraft:frosted_ice} under ice. With the
     * refusal in detection alone, every one of those worlds went on laying it: the second 0.9.219 pass
     * found the waterfall again in every instance, whose configs were written before the fix, and not in
     * the dev runs, whose configs are made fresh each time.
     */
    @Test
    void a_list_written_before_the_fix_cannot_put_it_back() throws Exception {
        Method apply = SurfaceRegistry.class
            .getDeclaredMethod("applyList", Map.class, Set.class, String[].class, SurfaceFamily.class);
        apply.setAccessible(true);
        Map<Integer, SurfaceFamily> out = new HashMap<Integer, SurfaceFamily>();
        apply.invoke(
            null,
            out,
            new HashSet<String>(),
            new String[] { "minecraft:frosted_ice", "minecraft:ice", "minecraft:packed_ice" },
            SurfaceFamily.ICE);

        int frosted = Block.getIdFromBlock(Blocks.FROSTED_ICE);
        for (int meta = 0; meta < 16; meta++) {
            assertFalse(
                out.containsKey(key(frosted, meta)),
                "a list written before the fix still lays frosted ice, at metadata " + meta);
        }
        assertEquals(
            SurfaceFamily.ICE,
            out.get(key(Block.getIdFromBlock(Blocks.ICE), 0)),
            "ordinary ice listed in the same list stopped counting");
    }

    private static Integer key(int id, int meta) throws Exception {
        Method key = SurfaceRegistry.class.getDeclaredMethod("key", int.class, int.class);
        key.setAccessible(true);
        return (Integer) key.invoke(null, Integer.valueOf(id), Integer.valueOf(meta));
    }
}
