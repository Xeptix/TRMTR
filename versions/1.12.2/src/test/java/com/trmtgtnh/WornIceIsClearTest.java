package com.trmtgtnh;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Worn ice is still clear: it hides nothing beside it, and stops only the light ice stops.
 *
 * <p>
 * The 1.7.10 edition says it in one line - an unsunken stand-in is an opaque cube only where it is
 * {@code !sunken && !clear && !window}, and a clear one stops {@code lightOpacityFor} rather than all light.
 * This edition asked the first clause and dropped the second in both places, so until 0.9.219 a worn but
 * unsunken ice square let the real ice under it and the worn squares beside it leave off every face they
 * shared with it - the first yard's ice close-up read as a single pane with the next platform showing
 * through, where 1.7.10 shows the ice's depth - and stopped all light, so a path worn across a frozen lake
 * darkened the water under it. Read from the methods, because a {@code clearCovers} nobody calls is the
 * fault this guards against.
 */
class WornIceIsClearTest {

    @Test
    void a_clear_square_hides_nothing_beside_it() throws IOException {
        String side = flat(body(ghost(), "doesSideBlockRendering"));
        int sunk = side.indexOf("if(sunkAt(world,pos))returnfalse;");
        int clear = side.indexOf("if(clearCovers(world,pos)!=null)returnfalse;");
        int whole = side.indexOf("returnfloorOf(outline)<=0F&&topOf(outline)>=1F;");
        assertTrue(
            sunk >= 0 && clear > sunk && whole > clear,
            "a worn ice square still hides the faces beside it: " + side);
    }

    @Test
    void a_clear_square_stops_the_light_its_ice_stops() throws IOException {
        String light = flat(body(ghost(), "getLightOpacity"));
        assertTrue(
            light.contains("IBlockStatecovered=clearCovers(world,pos);")
                && light.contains("returncovered==null?255:covered.getLightOpacity();"),
            "a worn ice square stops all light: " + light);
    }

    @Test
    void clear_is_ice_over_a_block_that_does_not_fill_its_square() throws IOException {
        String clear = flat(body(ghost(), "clearCovers"));
        assertTrue(
            clear.contains("!=SurfaceFamily.ICE)returnnull;"),
            "a square worn as something other than ice counts as clear: " + clear);
        assertTrue(
            clear.contains("returncovered.isOpaqueCube()?null:covered;"),
            "packed ice counts as clear, though 1.7.10 gives it a solid twin: " + clear);
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
