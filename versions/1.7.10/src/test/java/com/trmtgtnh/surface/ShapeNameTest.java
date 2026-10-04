package com.trmtgtnh.surface;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * That a shaped block's name reduces to the material it was cut from.
 *
 * <p>
 * Every rule that reads a block's name wants the material rather than the shape, and two of the
 * shape words - "slab" and "stair" - are also on the list of decorative stonework the mod leaves
 * alone. So a name that keeps its shape word is not merely classified imprecisely; it is refused
 * outright, which is exactly what happened to every doubled slab in the game.
 */
class ShapeNameTest {

    private static String strip(String name) throws Exception {
        Method method = SurfaceRegistry.class.getDeclaredMethod("withoutShapeWords", String.class);
        method.setAccessible(true);
        return (String) method.invoke(null, name);
    }

    @Test
    @DisplayName("a slab and a stair reduce to what they were cut from")
    void theShapeComesOff() throws Exception {
        assertEquals("stone", strip("stone_slab"));
        assertEquals("cobblestone", strip("cobblestone_stairs"));
        assertEquals("sandstone", strip("sandstone_slab"));
        assertEquals("stone", strip("stone_stairs"));
    }

    @Test
    @DisplayName("two slabs stacked are a block of their own and vanilla says so in the name")
    void theDoublingComesOffToo() throws Exception {
        assertEquals("stone", strip("double_stone_slab"));
        assertEquals("wooden", strip("double_wooden_slab"));
    }

    @Test
    @DisplayName("a name with no shape word in it is left exactly as it is")
    void nothingIsInvented() throws Exception {
        assertEquals("stone", strip("stone"));
        assertEquals("cobblestone", strip("cobblestone"));
        assertEquals("andesite", strip("andesite"));
    }

    @Test
    @DisplayName("stripping never leaves nothing, because nothing classifies as nothing")
    void neverStrippedToEmpty() throws Exception {
        assertEquals("slab", strip("slab"));
        assertEquals("stairs", strip("stairs"));
        assertEquals("double", strip("double"));
    }
}
