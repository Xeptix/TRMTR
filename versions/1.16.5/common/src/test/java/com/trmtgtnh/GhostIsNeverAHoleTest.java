package com.trmtgtnh;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;

/**
 * A worn square draws something, whoever is doing the drawing.
 *
 * <p>
 * The ghost is painted into the client's own copy of the world, in place of the ground. That is what
 * makes an empty answer so much worse here than for an ordinary block: there is no grass underneath
 * to fall back to, because the ghost <em>is</em> the grass now, so a model that returns no quads
 * leaves a hole with the sky showing through it.
 *
 * <p>
 * <strong>It happened, and in the one place nobody was looking.</strong> On 2026-10-06 the 1.16.5
 * Forge edition was driven in a real instance with Rubidium installed - a Sodium port, and one of the
 * most commonly installed mods on this version. Rubidium meshes chunks itself and never calls
 * {@code BlockRenderDispatcher.renderModel}, which is where the mixin that tells the model its square
 * sits, so the model was asked five hundred times with no square and answered nothing every time. The
 * photographs show a rectangular pit in flat grass. The dev run, and the same instance with OptiFine
 * instead, drew the path correctly - which is how a fault like this survives a port being declared
 * complete.
 *
 * <p>
 * Two things came out of it, and this guards both. The square now arrives through
 * {@code getModelData}, which is Forge's own per-block hook and which Rubidium does call; and when
 * nothing tells the model where it is, by either road, it draws the plain block rather than nothing.
 * The second is the one that matters for a renderer nobody has tried yet: the worst it can now look
 * is unworn.
 */
class GhostIsNeverAHoleTest {

    private static final String FORGE = "forge/src/main/java/com/trmtgtnh/forge/GhostModelForge.java";

    private static final String FABRIC = "fabric/src/main/java/com/trmtgtnh/fabric/GhostModelFabric.java";

    @Test
    void neither_loader_answers_a_chunk_mesh_with_nothing() throws IOException {
        for (String each : new String[] { FORGE, FABRIC }) {
            String source = read(each);
            assertTrue(
                source.contains("return fallback.getQuads(state, side, random)"),
                each + " answers getQuads with something other than the plain block when it has not "
                    + "been told which square it is drawing. An empty list there is a hole in the "
                    + "world, because the ghost has replaced the ground rather than covering it:\n  "
                    + quadsIn(source));
        }
    }

    @Test
    void forge_takes_the_square_from_the_model_data_as_well_as_the_seat() throws IOException {
        String source = read(FORGE);
        assertTrue(
            source.contains("public IModelData getModelData("),
            FORGE + " does not answer Forge's own per-block model data hook, so a renderer that asks "
                + "for it - Rubidium does, vanilla's chunk mesher does not - has no way to tell this "
                + "model which square it is drawing");
        assertTrue(
            source.contains("data instanceof Square"),
            FORGE + " answers the model data hook and then does not read what came back in getQuads, "
                + "which is the same as not answering it");
        assertTrue(
            source.contains("GhostSeat.level()"),
            FORGE + " has dropped the seat, which is the only road open on vanilla's own renderer");
    }

    /** The lines around an answer, so a failure says what is there instead of what is not. */
    private static String quadsIn(String source) {
        StringBuilder out = new StringBuilder();
        for (String line : source.split("\n")) {
            if (line.contains("getQuads") || line.contains("return Collections.emptyList")) {
                out.append(line.trim())
                    .append("\n  ");
            }
        }
        return out.toString()
            .trim();
    }

    private static String read(String relative) throws IOException {
        File at = new File(SourceTree.repoRoot(), relative);
        assertTrue(at.isFile(), at.getAbsolutePath() + " is not there");
        return new String(Files.readAllBytes(at.toPath()), Charset.forName("UTF-8"));
    }
}
