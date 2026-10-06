package com.trmtgtnh;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;

/**
 * Two things a block tells the client rather than the game, and this edition told neither.
 *
 * <p>
 * <strong>Written from a screenshot of a flat grey square on the side of a block.</strong> Worn grass
 * drew as a plain grey slab with a mottled band along its top edge, and both halves of that sentence
 * have their own cause:
 *
 * <ul>
 * <li><em>The flat grey.</em> A ghost drew in the solid pass, because nothing had ever said
 * otherwise. In the solid pass a cut-out texture's holes are drawn as though they were opaque, so
 * grass's fringe - a strip of pixels along the top of an otherwise empty picture - filled the whole
 * face.</li>
 * <li><em>The grey.</em> Vanilla's grass textures are grey in the file and green only because the
 * game multiplies a biome's colour into them. The quads asked for a tint, nothing was registered to
 * answer, and an unanswered tint is white. White times grey is grey.</li>
 * </ul>
 *
 * <p>
 * Both are one line per loader and both were missing on both loaders. {@code BlockGhost} carried a
 * comment saying the client module registered the pass; it did not. The 1.12.2 edition answers the
 * pass from the block itself and registers the colour in its client proxy, which is why it has never
 * had either fault and why neither showed up in a comparison of the two trees class by class.
 */
class GhostIsDrawnCutOutAndTintedTest {

    @Test
    void neither_loader_leaves_the_ghost_in_the_default_pass() throws IOException {
        // This began as "both loaders register cut-out mipped", which was the fix for the grey
        // rectangle and is now too narrow: a single pass for every ghost left worn ice drawing over
        // what was behind it. What has to hold is that each loader says something, and that what it
        // says can reach every pass a covered block might use. Which pass a given square takes is
        // GhostFacesFollowTheBlockTest's business.
        String forge = clientSetup("forge");
        assertTrue(
            forge.contains("setRenderLayer(") && forge.contains("GhostLayers::claims"),
            "Forge has to claim the passes it may need; left unsaid, a ghost draws in the solid pass "
                + "and a cut-out texture's holes are filled in");

        String fabric = clientSetup("fabric");
        assertTrue(
            fabric.contains("BlockRenderLayerMap") && fabric.contains("RenderType.cutoutMipped()"),
            "Fabric binds a block to one pass, so it registers the one that draws both a solid "
                + "picture and one with holes correctly - the per-quad blend mode overrides it");
    }

    @Test
    void both_loaders_register_what_colour_a_ghost_is() throws IOException {
        for (String module : new String[] { "forge", "fabric" }) {
            assertTrue(
                clientSetup(module).contains("GhostTint.handler()"),
                module + " has to register the ghost's colour handler, or every tinted face draws "
                    + "white - which on grass means grey");
        }
    }

    @Test
    void the_tint_answers_both_slots_the_quads_ask_for() throws IOException {
        String tint = String.join("\n", SourceTree.lines("com/trmtgtnh/client/GhostTint.java"));

        assertTrue(
            tint.contains("GhostQuads.GRASS_TINT") && tint.contains("GhostQuads.LIGHT_TINT"),
            "the handler has to answer both slots the model asks for, by the model's own names");
        assertTrue(
            tint.contains("getAverageGrassColor"),
            "the grass slot is the biome's grass colour, which is the whole point of asking per position");
        assertTrue(
            tint.contains("GhostLight.tinted("),
            "and a lit square's glow is multiplied into whichever colour it would otherwise have had, "
                + "so lighting a square does not replace what it is made of");
    }

    private static String clientSetup(String module) throws IOException {
        String file = module.equals("forge") ? "forge/src/main/java/com/trmtgtnh/forge/ForgeClientSetup.java"
            : "fabric/src/main/java/com/trmtgtnh/fabric/TrmtFabricClient.java";
        File at = new File(SourceTree.repoRoot(), file);
        assertTrue(at.isFile(), at.getAbsolutePath() + " is not there");
        return new String(Files.readAllBytes(at.toPath()), StandardCharsets.UTF_8);
    }
}
