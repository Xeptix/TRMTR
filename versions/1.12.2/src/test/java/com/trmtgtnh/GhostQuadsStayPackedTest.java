package com.trmtgtnh;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Worn ground packs its own quads, and must never go back to asking Forge to pack them.
 *
 * <p>
 * <strong>Written after OptiFine's shaders broke this edition three different ways, all from one
 * cause.</strong> The model used to hand back {@code UnpackedBakedQuad}s. Those size their packed
 * array from {@code format.getNextOffset()} when they are constructed and fill it from the same
 * format later - and {@code VertexFormat} is mutable and shared, so when a shader pack loads and the
 * chunk format grows, an array measured before the growth is written past its end. Forge's
 * {@code LightUtil.pack} does that without a bounds check; its loop carries a
 * {@code // TODO handle overflow} where the test would be.
 *
 * <p>
 * On 2026-10-05, under OptiFine HD U G5 with Complementary and with the internal shaders alike, that
 * showed as enormous stretched blades standing in the hollow; corrected to the block format it showed
 * as a regular chevron pattern instead; and switching shaders off mid-session crashed the game with
 * {@code ArrayIndexOutOfBoundsException: 28} - four vertices of seven ints, one int past the end -
 * inside {@code UnpackedBakedQuad.getVertexData}, tesselating {@code trmtgtnh:ghost_grass}. Shaders
 * off was always correct, which is why it survived every test and every hour of play until somebody
 * turned shaders on.
 *
 * <p>
 * The cure is to pack the ints by hand, in the layout vanilla's own blocks use, and hand over a plain
 * {@code BakedQuad}: the renderer copies that with {@code addVertexData}, which is the path OptiFine
 * instruments, and nothing reads a stride that something else can change underneath it. The 1.16.5
 * edition has always done it that way, because a packed array is the one thing both its loaders take;
 * this edition is now the same shape.
 *
 * <p>
 * Nothing here loads a class. What is being guarded is a shape in the source, because the thing it
 * guards against compiles perfectly and only shows on somebody else's machine with a shader pack on.
 */
class GhostQuadsStayPackedTest {

    private static final String MODEL = "com/trmtgtnh/client/model/GhostBakedModel.java";

    @Test
    void the_model_packs_its_own_vertices() throws IOException {
        String model = String.join("\n", SourceTree.lines(MODEL));

        assertTrue(
            model.contains("private static final int INTS_PER_VERTEX = 7"),
            "the block format is seven ints a vertex, written here rather than asked of a format "
                + "object that another mod can grow");
        assertTrue(
            model.contains("new int[4 * INTS_PER_VERTEX]"),
            "the array has to be measured from that constant, not from a stride that moves");
        assertTrue(
            model.contains("Float.floatToRawIntBits("),
            "positions and texture coordinates are packed as raw float bits, which is what the "
                + "renderer reads back");
        assertTrue(model.contains("new BakedQuad("), "the model has to hand back a plain packed quad");
    }

    @Test
    void nothing_in_the_model_package_asks_forge_to_pack_a_quad() throws IOException {
        List<String> offenders = new ArrayList<String>();
        File models = new File(SourceTree.mainJava(), "com/trmtgtnh/client/model");
        assertTrue(models.isDirectory(), models.getAbsolutePath() + " is not there");

        File[] files = models.listFiles();
        assertTrue(files != null && files.length > 0, "found no model sources, which would switch this off");
        for (File file : files) {
            if (!file.getName()
                .endsWith(".java")) continue;
            int number = 0;
            for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                number++;
                String trimmed = line.trim();
                if (trimmed.startsWith("*") || trimmed.startsWith("//")) continue;
                if (trimmed.contains("UnpackedBakedQuad") || trimmed.contains("getNextOffset()")) {
                    offenders.add(file.getName() + ":" + number + " " + trimmed);
                }
            }
        }

        assertTrue(
            offenders.isEmpty(),
            "a model is letting Forge size and pack its quads again, which is what OptiFine's " + "shaders crash on: "
                + offenders);
    }
}
