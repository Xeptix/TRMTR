package com.trmtgtnh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.trmtgtnh.mixin.OculusGate;

/**
 * Worn ground may claim another block's shader material, and must switch itself off instead of
 * failing.
 *
 * <p>
 * <strong>Written because this was the one thing in its release resting on reading rather than
 * running.</strong> The claim goes through Oculus's own per-block shader-id holder, which is not on
 * the compile classpath and cannot be: naming it for real would mean a build dependency on Oculus.
 * So the seam is a {@code @Pseudo} mixin matched at runtime, a reflective method lookup and a config
 * plugin that refuses the whole thing where Oculus is absent - none of which a compiler checks, and
 * none of which can be exercised without a client, a shader pack and Oculus installed.
 *
 * <p>
 * What <em>can</em> be asked, and is asked here, is whether every way it goes wrong is a way it goes
 * quiet. The gate is run for real against this JVM, which has no Oculus in it, and must refuse its
 * one mixin while waving the rest of the config through. The rest is read out of the source, because
 * the thing being guarded against compiles perfectly and is only visible in the text: an inject that
 * would fail the build on a moved target, a catch that keeps retrying a lookup that has already
 * failed, an import that would turn an optional seam into a dependency, or a claim made from the
 * wrong variable - which is the one that would be invisible even with a shader pack running, because
 * it would merely keep a road shiny after it had worn through.
 *
 * <p>
 * It does not prove that a shader pack draws the right thing. Nothing short of a client can, and the
 * gate test below is the half that can be run.
 */
class ShaderMaterialStaysOptionalTest {

    private static final String GATED = "com.trmtgtnh.mixin.MixinOculusBlockContext";

    /** The names Oculus and its family go by, none of which may be imported. */
    private static final Pattern THEIR_PACKAGES = Pattern
        .compile("^\\s*import\\s+(?:static\\s+)?(net\\.coderbot|net\\.irisshaders|me\\.jellysquid)[^;]*;");

    // ---- the gate, run for real ----------------------------------------------------------------

    /** The reflective handles this class caches, which every catch has to forget. */
    private static final Pattern CACHED = Pattern.compile("private static (?:Method|Field) (\\w+);");

    @Test
    void the_gate_refuses_its_one_mixin_in_a_jvm_with_no_oculus() {
        OculusGate gate = new OculusGate();
        // Must not throw: under launchwrapper this reads Launch.classLoader, and in a plain JVM that
        // field is null. A gate that threw here would take the whole mixin config down with it, on
        // every client, over an optional feature.
        gate.onLoad("com.trmtgtnh.mixin");

        assertFalse(
            gate.shouldApplyMixin(OculusGate.HOLDER, GATED),
            "there is no Oculus in this JVM, so the mixin that targets its holder must be refused");
    }

    @Test
    void the_gate_waves_through_every_other_mixin_in_the_config() throws IOException {
        OculusGate gate = new OculusGate();
        gate.onLoad("com.trmtgtnh.mixin");

        List<String> others = new ArrayList<String>();
        for (String name : declaredMixins()) {
            if (("com.trmtgtnh.mixin." + name).equals(GATED)) continue;
            others.add(name);
            assertTrue(
                gate.shouldApplyMixin("whatever/it/Targets", "com.trmtgtnh.mixin." + name),
                name + " is not the gated mixin and must be applied whether Oculus is there or not");
        }
        assertFalse(others.isEmpty(), "found no other mixins in the config, which would switch this test off");
    }

    @Test
    void the_gate_is_a_switch_rather_than_a_wall() throws Exception {
        OculusGate gate = new OculusGate();
        gate.onLoad("com.trmtgtnh.mixin");
        assertFalse(gate.shouldApplyMixin(OculusGate.HOLDER, GATED));

        // What onLoad would have found on a client that has Oculus. Set directly because the probe
        // asks launchwrapper, which is not running here - the point of this is the decision that
        // hangs off it, which is the half that decides whether the feature exists at all.
        Field present = OculusGate.class.getDeclaredField("present");
        present.setAccessible(true);
        present.setBoolean(gate, true);

        assertTrue(
            gate.shouldApplyMixin(OculusGate.HOLDER, GATED),
            "with the holder present the mixin must be applied, or the feature is dead on every "
                + "client and nothing says so");
    }

    // ---- the promises that are only visible in the source --------------------------------------

    @Test
    void the_mixin_costs_the_feature_and_nothing_else_when_oculus_moves_it() throws IOException {
        // Read line by line and only outside comments. The javadoc of this very class explains why
        // the inject carries require = 0, so asking whether the file contains those words passes on
        // the explanation after somebody has deleted the thing it explains - which is exactly what
        // happened the first time this was written, and is the second time that shape has bitten in
        // one release.
        String inject = annotation("com/trmtgtnh/mixin/MixinOculusBlockContext.java", "@Inject(");
        String mixin = annotation("com/trmtgtnh/mixin/MixinOculusBlockContext.java", "@Mixin(");
        String pseudo = annotation("com/trmtgtnh/mixin/MixinOculusBlockContext.java", "@Pseudo");

        assertFalse(pseudo.isEmpty(), "the target is not on the compile classpath, so it needs @Pseudo");
        assertTrue(
            inject.contains("require = 0"),
            "without require = 0 a version of Oculus that moved this method would fail the mixin "
                + "rather than cost the feature: "
                + inject);
        assertTrue(
            inject.contains("remap = false"),
            "another mod's class is in no refmap, so the inject has to be left unmapped: " + inject);
        assertTrue(
            mixin.contains("targets = OculusGate.HOLDER"),
            "the target has to come from the same constant the gate probes for, or the two can "
                + "disagree about which class this is about: "
                + mixin);
    }

    @Test
    void the_config_hands_the_gate_the_decision() throws IOException {
        String config = String.join("\n", SourceTree.lines("../resources/mixins.trmtgtnh.json"));
        assertTrue(
            config.contains("\"plugin\": \"com.trmtgtnh.mixin.OculusGate\""),
            "the gate only runs if the config names it as its plugin");
        assertTrue(
            config.contains(GATED.substring(GATED.lastIndexOf('.') + 1)),
            "the gated mixin has to be in the config for the gate to have anything to refuse");
    }

    @Test
    void nothing_names_oculus_at_compile_time() throws IOException {
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
    void the_holder_is_named_once_and_only_as_a_string() throws IOException {
        int naming = 0;
        for (File file : everySource()) {
            // The javadoc of the two classes that use it explains the seam, so only the constant's
            // own declaration is counted: a second literal would be a second thing to keep in step.
            for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                if (line.contains("String HOLDER = \"net.coderbot")) naming++;
            }
        }
        assertEquals(
            1,
            naming,
            "the holder's name belongs in one constant, so a version of Oculus that renames it is "
                + "one edit rather than a hunt");
    }

    @Test
    void a_reflective_failure_gives_up_for_the_session() throws IOException {
        List<String> source = SourceTree.lines("com/trmtgtnh/client/render/ShaderMaterial.java");

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
                        + ", so a seam that has failed once keeps being asked once per face for "
                        + "the rest of the session");
            }
        }
        assertEquals(2, catches, "this test knows about two catches in this class; the shape changed");
    }

    @Test
    void the_claim_follows_the_picture_rather_than_the_wear() throws IOException {
        List<String> model = SourceTree.lines("com/trmtgtnh/client/model/GhostBakedModel.java");

        int claims = -1;
        int substitutes = -1;
        for (int at = 0; at < model.size(); at++) {
            String line = model.get(at);
            if (line.contains("ShaderMaterial.claim(")) claims = at;
            if (line.contains("if (top == null) top = earth;")) substitutes = at;
        }

        assertTrue(claims >= 0, "nothing claims a shader material in the model any more");
        assertTrue(substitutes >= 0, "the earth substitution this test anchors on has moved");
        assertTrue(
            model.get(claims)
                .contains("top == null ? -1 : origin"),
            "the claim must be made from the sprite the model chose: " + model.get(claims)
                .trim());
        assertTrue(
            claims < substitutes,
            "the claim is made after the earth sprite is substituted in, so top is never null by "
                + "then and a worn-through road would go on claiming the block it has worn out of - "
                + "which is the one way this can be wrong that a shader pack would not show as an "
                + "error, only as a road that stays shiny");
    }

    // ---- helpers -------------------------------------------------------------------------------

    private static String text(String relative) throws IOException {
        return String.join("\n", SourceTree.lines(relative));
    }

    /**
     * One annotation as it is actually written, or empty if it is not there.
     *
     * <p>
     * Comment lines are skipped, so a javadoc paragraph discussing an annotation can never stand in
     * for the annotation. An annotation that wraps over lines is joined up to its closing bracket.
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

    private static List<String> declaredMixins() throws IOException {
        String config = text("../resources/mixins.trmtgtnh.json");
        List<String> names = new ArrayList<String>();
        Matcher each = Pattern.compile("\"(Mixin[A-Za-z0-9_]+)\"")
            .matcher(config);
        while (each.find()) names.add(each.group(1));
        return names;
    }
}
