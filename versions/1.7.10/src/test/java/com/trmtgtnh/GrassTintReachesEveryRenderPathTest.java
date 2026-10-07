package com.trmtgtnh;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;

/**
 * Worn grass keeps its untinted sides in every render path, not just the one vanilla happens to use.
 *
 * <p>
 * <strong>This is the fault the test exists for.</strong> A grass block's sides are drawn untinted
 * with a separately tinted fringe over them, and a block only gets that treatment if the renderer
 * recognises it as grass. This mod reaches that recognition two ways: the position-free
 * {@code getIcon} answers vanilla's own {@code grass_top} for the top face, and
 * {@code MixinGrassTint} substitutes the {@code Blocks.grass} the renderer compares against.
 *
 * <p>
 * <strong>OptiFine takes neither route by accident.</strong> Measured on 2026-10-06 with a probe:
 * plain 1.7.10 asks the position-free icon twice in a run, and OptiFine not once in fifty-three icon
 * lookups. That leaves the identity comparison as the only way through - and the mixin was hooking
 * one render method of three, so with smooth lighting on, which is the default, the block went
 * through an ambient-occlusion method the mixin never saw. The tint landed on the dirt side texture
 * and a worn path came out olive, on the one renderer most people run.
 *
 * <p>
 * The measurement, on the same frame and crop: plain +4.5 green, Angelica +6.0, OptiFine +11.2
 * before and +4.7 after.
 *
 * <p>
 * {@code MixinGrassSideOverlay} had already learned this for the fringe and says so in its own
 * comment; the tint did not. So this checks that both of them name all three methods, because the
 * next person to add a hook here will reach for the one that looks sufficient.
 */
class GrassTintReachesEveryRenderPathTest {

    /** The three the renderer draws a standard block through, by the names mixins target them with. */
    private static final String[] PATHS = { "renderStandardBlockWithAmbientOcclusion(",
        "renderStandardBlockWithAmbientOcclusionPartial(", "renderStandardBlockWithColorMultiplier(" };

    private static final String[] GRASS_MIXINS = { "MixinGrassTint.java", "MixinGrassSideOverlay.java" };

    @Test
    void both_grass_mixins_hook_every_path_a_block_is_drawn_through() throws IOException {
        for (String name : GRASS_MIXINS) {
            File at = mixin(name);
            assertTrue(at != null, name + " is not where this test looks for it");
            String source = new String(Files.readAllBytes(at.toPath()), Charset.forName("UTF-8"));

            for (String path : PATHS) {
                assertTrue(
                    source.contains(path),
                    name + " does not hook "
                        + path
                        + ", so a block drawn through it keeps none of grass's treatment. With smooth "
                        + "lighting on - the default - that is the path most blocks take, and under a "
                        + "renderer that does not ask the position-free icon it is the only route left. "
                        + "A worn grass side then comes out olive.");
            }
        }
    }

    /**
     * And the one that cannot be seen from the source: both are declared {@code require = 0}, so
     * neither says anything when it fails to bind.
     *
     * <p>
     * That is deliberate and should stay - a renderer update that moved these must not refuse to
     * start a pack of two hundred and thirty-five mods over a cosmetic blemish - but it is also why
     * this fault lived in a shipped release. The note has to stay next to the annotation so the next
     * person reads it before trusting that a hook which compiles is a hook which runs.
     */
    @Test
    void the_silence_of_these_hooks_is_written_down_beside_them() throws IOException {
        for (String name : GRASS_MIXINS) {
            String source = new String(Files.readAllBytes(mixin(name).toPath()), Charset.forName("UTF-8"));
            assertTrue(
                source.contains("require = 0"),
                name + " no longer declares require = 0; if that is deliberate, this test should say so");
            assertTrue(
                source.contains("Not required to apply") || source.contains("not required"),
                name + " declares require = 0 and does not say why. An injector that says nothing when "
                    + "it fails to bind needs its reason written down beside it, because the next person "
                    + "to read a green flank will not guess it.");
        }
    }

    /** This edition's mixin source, found by walking up from wherever the test is being run. */
    private static File mixin(String name) {
        File here = new File(System.getProperty("user.dir"));
        for (File at = here; at != null; at = at.getParentFile()) {
            File found = new File(at, "src/main/java/com/trmtgtnh/mixin/" + name);
            if (found.isFile()) return found;
        }
        return null;
    }
}
