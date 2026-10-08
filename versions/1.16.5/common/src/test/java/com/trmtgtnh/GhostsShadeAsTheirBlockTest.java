package com.trmtgtnh;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;

/**
 * A whole ghost shades the corners beside it as the block it replaced did, and a hollowed one does not.
 *
 * <p>
 * The 1.7.10 edition's {@code renderAsNormalBlock}, which is {@code !sunken} there - and its sunken variant is the
 * hollowed one, chosen once the ground has sunk or when the block covered is short. This version answered that a
 * ghost shades nothing, everywhere, until 0.9.219: the first yard's close-ups showed sunk earth beside a worn
 * square that had not sunk drawn evenly lit here and shaded toward the step on 1.7.10.
 *
 * <p>
 * Read from the source, squeezed, because what it guards compiles either way and shows only in a running game.
 */
class GhostsShadeAsTheirBlockTest {

    private static final String BLOCK = "common/src/main/java/com/trmtgtnh/block/BlockGhost.java";

    @Test
    void a_whole_ghost_shades_and_a_hollowed_one_does_not() throws IOException {
        String ghost = squeezed(read());
        assertTrue(
            ghost.contains(
                "publicfloatgetShadeBrightness(BlockStatestate,BlockGetterlevel,BlockPospos){returnwholeAt(level,pos)?WHOLE_SHADE:1.0F;}"),
            "the shade is not answered per square from wholeAt, so a worn square that has not sunk lights the "
                + "corners beside it as though it were not there");
        assertTrue(ghost.contains("publicstaticfinalfloatWHOLE_SHADE=0.2F;"), "a whole ghost's shade is not vanilla's 0.2");
        String whole = ghost.substring(ghost.indexOf("staticbooleanwholeAt(BlockGetterworld,BlockPospos){"));
        whole = whole.substring(0, whole.indexOf("}}") + 2);
        assertTrue(whole.contains("if(sunkAt(world,pos))returnfalse;"), "a sunken square shades as though it were whole");
        assertTrue(
            whole.contains("if(SurfaceShape.of(covered).isPartial())returnfalse;"),
            "a square standing in for a slab or a stair shades as though it were whole");
        assertTrue(
            whole.contains(".max(net.minecraft.core.Direction.Axis.Y)>=0.999D;"),
            "a square standing in for a path or farmland shades as though it were whole");
    }

    private static String squeezed(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", " ")
            .replaceAll("//[^\\n]*", " ")
            .replaceAll("\\s+", "");
    }

    private static String read() throws IOException {
        File at = new File(SourceTree.repoRoot(), BLOCK);
        assertTrue(at.isFile(), at.getAbsolutePath() + " is not there");
        return new String(Files.readAllBytes(at.toPath()), Charset.forName("UTF-8"));
    }
}
