package com.trmtgtnh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Worn grass keeps its fringe under OptiFine, which tests for grass by identity rather than by name.
 *
 * <p>
 * <strong>This is the second half of the fault 0.9.218 exists for.</strong> Vanilla draws grass's
 * tinted fringe over any side whose texture is <em>named</em> {@code grass_side}, and a worn grass
 * wall is handed back under that name for exactly that reason. OptiFine for 1.7.10 compares the side
 * against its own cached copy of vanilla's sprite with {@code ==} instead - in all four faces of its
 * smooth-lighting method, and in the second of two tests per face in its flat-lit one. The wrapper
 * was refused, and every worn grass wall under OptiFine came out bare earth with no fringe at all.
 *
 * <p>
 * Measured on 2026-10-07 with the render probe: plain 1.7.10 answered the fringe hook at sixty-one
 * different gradations and rotations in one run, OptiFine at exactly one - the unworn side, which is
 * vanilla's own sprite and so the only one its comparison could match. With the fix, OptiFine
 * answers the same sixty-one, and not one differs.
 *
 * <p>
 * Nothing here can be seen from a dev run, which has no OptiFine in it, and the mixin is declared
 * {@code require = 0} because without OptiFine there is nothing for it to match. So every part of
 * the fix that could be undone without a sound is checked here instead.
 */
class OptiFineGrassSideTest {

    private static final String FIELD = "LTextureUtils;iconGrassSide:Lnet/minecraft/util/IIcon;";

    @Test
    void the_smooth_lighting_method_is_hooked_at_every_read() throws IOException {
        String block = handlerFor(
            read("src/main/java/com/trmtgtnh/mixin/MixinOptiFineGrassSide.java"),
            "renderStandardBlockWithAmbientOcclusion(");
        assertTrue(block.contains(FIELD), "the smooth-lighting hook does not read TextureUtils.iconGrassSide");
        assertTrue(
            !block.contains("ordinal"),
            "the smooth-lighting hook names an ordinal. All four reads in that method are the overlay "
                + "test, one per side face, and a numbered hook leaves three faces with no fringe. Better "
                + "Grass is decided in a separate method there, so there is nothing to step around.");
    }

    @Test
    void the_flat_lit_method_is_hooked_at_the_overlay_tests_and_never_at_better_grass() throws IOException {
        String block = handlerFor(
            read("src/main/java/com/trmtgtnh/mixin/MixinOptiFineGrassSide.java"),
            "renderStandardBlockWithColorMultiplier(");
        List<Integer> found = new ArrayList<Integer>();
        Matcher each = Pattern.compile("ordinal\\s*=\\s*(\\d+)")
            .matcher(block);
        while (each.find()) found.add(Integer.valueOf(each.group(1)));
        assertEquals(
            "[1, 3, 5, 7]",
            found.toString(),
            "the flat-lit hook must take exactly the odd reads of TextureUtils.iconGrassSide. Each face "
                + "reads it twice: first Better Grass asking whether to paint the side over with the top, "
                + "then the overlay test. Taking an even one tells Better Grass that a worn wall is grass, "
                + "and it grasses over the wear; missing an odd one leaves that face with no fringe.");
    }

    @Test
    void the_mixin_is_listed_so_that_it_applies_at_all() throws IOException {
        String config = read("src/main/resources/mixins.trmtgtnh.json");
        int client = config.indexOf("\"client\"");
        int server = config.indexOf("\"server\"");
        assertTrue(client >= 0 && server > client, "mixins.trmtgtnh.json no longer has the shape this reads");
        assertTrue(
            config.substring(client, server)
                .contains("\"MixinOptiFineGrassSide\""),
            "MixinOptiFineGrassSide is not in the client list of mixins.trmtgtnh.json, so it never "
                + "applies - and being require = 0, nothing says so.");
    }

    /**
     * The comparison is {@code ==}, so the stand-in the face is drawn with and the one handed to the
     * comparison must be one object. A second {@code new AsGrassSide} anywhere makes a fresh wrapper
     * that can never match, and the fringe goes again without a sound.
     */
    @Test
    void every_stand_in_comes_from_the_one_place_that_keeps_them() throws IOException {
        String rendering = read("src/main/java/com/trmtgtnh/block/GhostRendering.java");
        int made = count(rendering, "new AsGrassSide(");
        assertEquals(
            1,
            made,
            "GhostRendering builds an AsGrassSide in " + made
                + " places. It must be built only inside asGrassSide(), which hands out one per sprite: "
                + "OptiFine compares the side with ==, and a fresh wrapper never equals the one the face "
                + "was drawn with.");
        int start = rendering.indexOf("private static IIcon asGrassSide(");
        assertTrue(start >= 0, "asGrassSide() is gone");
        int end = rendering.indexOf("\n    }", start);
        assertTrue(
            rendering.substring(start, end)
                .contains("new AsGrassSide("),
            "the one AsGrassSide that is built is not built inside asGrassSide()");
    }

    /** The annotation and handler a method name belongs to, from its name to the end of the handler. */
    private static String handlerFor(String source, String method) {
        int at = source.indexOf("method = \"" + method);
        assertTrue(at >= 0, "MixinOptiFineGrassSide does not hook " + method);
        int start = source.lastIndexOf("@ModifyExpressionValue", at);
        int end = source.indexOf("private IIcon trmt$", at);
        assertTrue(start >= 0 && end > at, "could not find the handler for " + method);
        return source.substring(start, end);
    }

    private static int count(String text, String what) {
        int found = 0;
        for (int at = text.indexOf(what); at >= 0; at = text.indexOf(what, at + 1)) found++;
        return found;
    }

    private static String read(String relative) throws IOException {
        File here = new File(System.getProperty("user.dir"));
        for (File at = here; at != null; at = at.getParentFile()) {
            File found = new File(at, relative);
            if (found.isFile()) return new String(Files.readAllBytes(found.toPath()), Charset.forName("UTF-8"));
        }
        throw new IOException(relative + " is not where this test looks for it");
    }
}
