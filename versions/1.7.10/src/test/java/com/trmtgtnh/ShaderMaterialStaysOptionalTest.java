package com.trmtgtnh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Worn ground may claim the shader material of the block it covers, and must switch itself off
 * instead of failing.
 *
 * <p>
 * This edition reaches a pair of methods <strong>Angelica</strong> added for exactly this -
 * {@code Iris.setShaderMaterialOverride} and {@code resetShaderMaterialOverride} - and neither is on
 * the compile classpath, because naming them for real would mean a build dependency on Angelica.
 * Both are found by name at runtime, behind a mod-loaded check, which is three things a compiler
 * cannot check and none of which can be exercised without a client, Angelica and a shader pack.
 *
 * <p>
 * So this reads the source, which is where the thing being guarded against is visible: a lookup that
 * is not reflective, a catch that keeps retrying a pair that has already failed to be there, an
 * import that would turn an optional seam into a dependency, a claim never given back when the
 * renderer moves on to ordinary ground, and a claim that follows the wear rather than what the
 * ground is made of. The later editions reach the same thing through Oculus and carry the same test
 * against their own shape; what the three have in common is that every failure is failure-closed.
 *
 * <p>
 * It does not prove that a shader pack draws the right thing. Nothing short of a client can.
 */
class ShaderMaterialStaysOptionalTest {

    private static final String SHADER_MATERIAL = "com/trmtgtnh/client/render/ShaderMaterial.java";

    private static final String MIXIN = "com/trmtgtnh/mixin/MixinGhostShaderMaterial.java";

    /** The names Angelica's shader half and its relatives go by, none of which may be imported. */
    private static final Pattern THEIR_PACKAGES = Pattern.compile(
        "^\\s*import\\s+(?:static\\s+)?(net\\.coderbot|net\\.irisshaders|com\\.gtnewhorizons\\.angelica)[^;]*;");

    /** The reflective handles this class caches, which every catch has to forget. */
    private static final Pattern CACHED = Pattern.compile("private static (?:Method|Field) (\\w+);");

    @Test
    void the_angelica_pair_is_reached_by_name_rather_than_imported() throws IOException {
        String body = text(SHADER_MATERIAL);

        assertTrue(
            body.contains("Class.forName(\"net.coderbot.iris.Iris\")"),
            "the holder of the pair has to be found at runtime, not named at compile time");
        assertTrue(
            body.contains("getMethod(\"setShaderMaterialOverride\""),
            "the claim is made through a method found by name");
        assertTrue(
            body.contains("getMethod(\"resetShaderMaterialOverride\""),
            "the claim has to be takeable back through a method found by name");
        assertTrue(
            body.contains("Loader.isModLoaded(\"angelica\")"),
            "the lookup is only worth making where Angelica is loaded, and asking first is what "
                + "keeps a client without it from paying for a reflective miss");
    }

    @Test
    void nothing_names_angelica_at_compile_time() throws IOException {
        List<String> offenders = new ArrayList<String>();
        for (File file : everySource()) {
            int number = 0;
            for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                number++;
                if (THEIR_PACKAGES.matcher(line)
                    .find()) {
                    offenders.add(file.getName() + ":" + number + " " + line.trim());
                }
            }
        }
        assertTrue(offenders.isEmpty(), "an optional seam became a build dependency: " + offenders);
    }

    @Test
    void a_reflective_failure_gives_up_for_the_session() throws IOException {
        List<String> source = SourceTree.lines(SHADER_MATERIAL);

        // The handles this class caches, read out of the class rather than listed here, so that a
        // third one added later is guarded without anybody remembering to come back.
        List<String> handles = new ArrayList<String>();
        for (String line : source) {
            Matcher declared = CACHED.matcher(line.trim());
            if (declared.find()) handles.add(declared.group(1));
        }
        assertFalse(handles.isEmpty(), "found no cached handles, which would switch this test off");

        int catches = 0;
        for (int at = 0; at < source.size(); at++) {
            if (!source.get(at)
                .contains("catch (Throwable")) continue;
            catches++;
            String block = String.join("\n", source.subList(at, Math.min(source.size(), at + 12)));
            for (String handle : handles) {
                // Word-bounded on purpose. The first version of this asked whether the block
                // contained "set = null", and `reset = null` contains exactly that - so on the
                // 1.7.10 edition it passed with `set = null` deleted, which is the mutation it
                // existed to catch.
                assertTrue(
                    Pattern.compile("(?<![A-Za-z0-9_])" + handle + "\\s*=\\s*null")
                        .matcher(block)
                        .find(),
                    "the catch on line " + (at + 1)
                        + " does not forget "
                        + handle
                        + ", so a seam that has failed once keeps being asked, once per block, for the rest of the session");
            }
        }
        assertEquals(2, catches, "this test knows about two catches in this class; the shape changed");
    }

    @Test
    void the_mixin_costs_the_feature_and_nothing_else_if_the_seam_moves() throws IOException {
        // Read annotation by annotation and never through comments: a javadoc paragraph explaining
        // why an annotation carries something must not be able to stand in for the annotation.
        String inject = annotation(MIXIN, "@Inject(");
        String mixin = annotation(MIXIN, "@Mixin(");

        assertTrue(
            inject.contains("require = 0"),
            "without require = 0 a change in how vanilla renders a block would fail the mixin "
                + "rather than cost the feature: "
                + inject);
        assertTrue(
            mixin.contains("RenderBlocks"),
            "the claim is seated from the renderer, which is the only place the block being drawn "
                + "and its position are both in hand: "
                + mixin);
    }

    @Test
    void the_claim_is_given_back_when_the_renderer_moves_on() throws IOException {
        String body = text(MIXIN);

        assertTrue(
            body.contains("ShaderMaterial.claim((GhostBlock) block, x, y, z)"),
            "a ghost has to claim its covered block's material");
        assertTrue(
            body.contains("ShaderMaterial.claim(null, x, y, z)"),
            "and the claim has to be given back for the next block that is not a ghost, or every "
                + "ordinary block drawn after a worn one is drawn as that worn one's material");
        assertTrue(
            body.indexOf("ShaderMaterial.claim((GhostBlock) block") < body.indexOf("ShaderMaterial.claim(null"),
            "the ghost branch comes first; reversing them would reset a claim made for the block " + "being drawn");
    }

    @Test
    void the_claim_follows_what_the_ground_is_made_of() throws IOException {
        String body = text(SHADER_MATERIAL);

        assertTrue(
            body.contains("GhostRendering.showsOwnMaterial(origin, meta, appearance)"),
            "what to claim is decided by whether the square still shows the material it started "
                + "as, which is one question with one answer rather than a wear number compared "
                + "against a threshold here");
        assertTrue(
            body.contains("GhostRendering.counterpartOf(appearance)"),
            "and once it does not, the claim is the material it has worn into - which the pack has "
                + "real values for, rather than a number nobody authored");

        // One definition, so the claim and everything else that asks the same question cannot drift
        // apart. It is checked here because this is the test that cares what the answer means.
        int defined = 0;
        for (File file : everySource()) {
            for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                if (line.contains("boolean showsOwnMaterial(")) defined++;
            }
        }
        assertEquals(1, defined, "showsOwnMaterial is defined " + defined + " times");
    }

    // ---- helpers -------------------------------------------------------------------------------

    private static String text(String relative) throws IOException {
        return String.join("\n", SourceTree.lines(relative));
    }

    /**
     * One annotation as it is actually written, or empty if it is not there. Comments are skipped, so
     * a javadoc paragraph discussing an annotation can never stand in for the annotation itself.
     */
    private static String annotation(String relative, String starts) throws IOException {
        List<String> source = SourceTree.lines(relative);
        for (int at = 0; at < source.size(); at++) {
            String line = source.get(at)
                .trim();
            if (line.startsWith("*") || line.startsWith("//") || line.startsWith("/*")) continue;
            if (!line.startsWith(starts)) continue;
            StringBuilder whole = new StringBuilder(line);
            while (brackets(whole.toString()) > 0 && ++at < source.size()) {
                whole.append(' ')
                    .append(
                        source.get(at)
                            .trim());
            }
            return whole.toString();
        }
        return "";
    }

    private static int brackets(String of) {
        int open = 0;
        for (char each : of.toCharArray()) {
            if (each == '(') open++;
            if (each == ')') open--;
        }
        return open;
    }

    private static List<File> everySource() {
        List<File> found = new ArrayList<File>();
        gather(SourceTree.mainJava(), found);
        assertFalse(found.isEmpty(), "found no sources, which would switch this test off");
        return found;
    }

    private static void gather(File at, List<File> into) {
        File[] children = at.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) gather(child, into);
            else if (child.getName()
                .endsWith(".java")) into.add(child);
        }
    }
}
