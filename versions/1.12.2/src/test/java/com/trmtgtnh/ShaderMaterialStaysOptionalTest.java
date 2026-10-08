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
 * running.</strong> The claim goes through another mod's own classes - Oculus's per-block shader-id
 * holder and, from 0.9.219, OptiFine's vertex builder - none of which is on the compile classpath or
 * can be: naming them for real would mean a build dependency. So each seam is a {@code @Pseudo} mixin
 * matched at runtime, reflective lookups and a config plugin that refuses the mixin where its mod is
 * absent - none of which a compiler checks.
 *
 * <p>
 * What <em>can</em> be asked, and is asked here, is whether every way it goes wrong is a way it goes
 * quiet. The gate is run for real against this JVM, which has neither mod in it, and must refuse both
 * mixins while waving the rest of the config through. The rest is read out of the source, because the
 * thing being guarded against compiles perfectly and is only visible in the text: an inject that would
 * fail the build on a moved target, a catch that keeps retrying a lookup that has already failed, an
 * import that would turn an optional seam into a dependency, or a claim made from the wrong variable -
 * which is the one that would be invisible even with a shader pack running, because it would merely
 * keep a road shiny after it had worn through.
 *
 * <p>
 * It does not prove that a shader pack draws the right thing. Nothing short of a client can, and the
 * photographs under each pack are the half that does.
 */
class ShaderMaterialStaysOptionalTest {

    private static final String GATED = "com.trmtgtnh.mixin.MixinOculusBlockContext";

    private static final String GATED_OPTIFINE = "com.trmtgtnh.mixin.MixinOptiFineShaderSeat";

    /** Each seam's mixin, with the constant its target has to come from. */
    private static final String[][] SEAMS = {
        { "com/trmtgtnh/mixin/MixinOculusBlockContext.java", "targets = OculusGate.HOLDER" },
        { "com/trmtgtnh/mixin/MixinOptiFineShaderSeat.java", "targets = OculusGate.OPTIFINE_SEAT" } };

    /** The names Oculus, its family and OptiFine go by, none of which may be imported. */
    private static final Pattern THEIR_PACKAGES = Pattern.compile(
        "^\\s*import\\s+(?:static\\s+)?(net\\.coderbot|net\\.irisshaders|me\\.jellysquid|net\\.optifine)[^;]*;");

    /** The reflective handles a class caches, which every catch in it has to forget. */
    private static final Pattern CACHED = Pattern.compile("private static (?:Method|Field) (\\w+);");

    // ---- the gate, run for real ----------------------------------------------------------------

    @Test
    void the_gate_refuses_both_mixins_in_a_jvm_with_neither_mod() {
        OculusGate gate = new OculusGate();
        // Must not throw: under launchwrapper this reads Launch.classLoader, and in a plain JVM that
        // field is null. A gate that threw here would take the whole mixin config down with it, on
        // every client, over an optional feature.
        gate.onLoad("com.trmtgtnh.mixin");

        assertFalse(
            gate.shouldApplyMixin(OculusGate.HOLDER, GATED),
            "there is no Oculus in this JVM, so the mixin that targets its holder must be refused");
        assertFalse(
            gate.shouldApplyMixin(OculusGate.OPTIFINE_SEAT, GATED_OPTIFINE),
            "there is no OptiFine in this JVM, so the mixin that targets its vertex builder must be refused");
    }

    @Test
    void the_gate_waves_through_every_other_mixin_in_the_config() throws IOException {
        OculusGate gate = new OculusGate();
        gate.onLoad("com.trmtgtnh.mixin");

        List<String> others = new ArrayList<String>();
        for (String name : declaredMixins()) {
            String whole = "com.trmtgtnh.mixin." + name;
            if (whole.equals(GATED) || whole.equals(GATED_OPTIFINE)) continue;
            others.add(name);
            assertTrue(
                gate.shouldApplyMixin("whatever/it/Targets", whole),
                name + " is not a gated mixin and must be applied whatever is installed");
        }
        assertFalse(others.isEmpty(), "found no other mixins in the config, which would switch this test off");
    }

    @Test
    void the_gate_is_a_switch_rather_than_a_wall() throws Exception {
        OculusGate gate = new OculusGate();
        gate.onLoad("com.trmtgtnh.mixin");
        assertFalse(gate.shouldApplyMixin(OculusGate.HOLDER, GATED));
        assertFalse(gate.shouldApplyMixin(OculusGate.OPTIFINE_SEAT, GATED_OPTIFINE));

        // What onLoad would have found on a client that has each mod. Set directly because the probe
        // asks launchwrapper, which is not running here - the point of this is the decision that hangs
        // off it, which is the half that decides whether the feature exists at all.
        Field present = OculusGate.class.getDeclaredField("present");
        present.setAccessible(true);
        present.setBoolean(gate, true);
        assertTrue(
            gate.shouldApplyMixin(OculusGate.HOLDER, GATED),
            "with the holder present the mixin must be applied, or the feature is dead on every client "
                + "and nothing says so");
        assertFalse(
            gate.shouldApplyMixin(OculusGate.OPTIFINE_SEAT, GATED_OPTIFINE),
            "Oculus being present said yes to OptiFine's mixin as well");

        Field optiFine = OculusGate.class.getDeclaredField("optiFinePresent");
        optiFine.setAccessible(true);
        optiFine.setBoolean(gate, true);
        assertTrue(
            gate.shouldApplyMixin(OculusGate.OPTIFINE_SEAT, GATED_OPTIFINE),
            "with OptiFine present its mixin must be applied, or the feature is dead under OptiFine");
    }

    // ---- the promises that are only visible in the source --------------------------------------

    @Test
    void each_mixin_costs_the_feature_and_nothing_else_when_its_mod_moves_it() throws IOException {
        // Read line by line and only outside comments. The javadoc of these classes explains why the
        // inject carries require = 0, so asking whether the file contains those words passes on the
        // explanation after somebody has deleted the thing it explains - which is exactly what happened
        // the first time this was written.
        for (String[] seam : SEAMS) {
            String inject = annotation(seam[0], "@Inject(");
            String mixin = annotation(seam[0], "@Mixin(");
            String pseudo = annotation(seam[0], "@Pseudo");

            assertFalse(
                pseudo.isEmpty(),
                seam[0] + ": the target is not on the compile classpath, so it needs @Pseudo");
            assertTrue(
                inject.contains("require = 0"),
                seam[0] + ": without require = 0 a version that moved this method would fail the mixin rather "
                    + "than cost the feature: "
                    + inject);
            assertTrue(
                inject.contains("remap = false"),
                seam[0] + ": another mod's class is in no refmap, so the inject has to be left unmapped: " + inject);
            assertTrue(
                mixin.contains(seam[1]),
                seam[0] + ": the target has to come from the same constant the gate probes for, or the two can "
                    + "disagree about which class this is about: "
                    + mixin);
        }
    }

    @Test
    void the_config_hands_the_gate_the_decision() throws IOException {
        String config = String.join("\n", SourceTree.lines("../resources/mixins.trmtgtnh.json"));
        assertTrue(
            config.contains("\"plugin\": \"com.trmtgtnh.mixin.OculusGate\""),
            "the gate only runs if the config names it as its plugin");
        for (String gated : new String[] { GATED, GATED_OPTIFINE }) {
            assertTrue(
                config.contains(gated.substring(gated.lastIndexOf('.') + 1)),
                "the gated mixin has to be in the config for the gate to have anything to refuse: " + gated);
        }
    }

    @Test
    void nothing_names_either_mod_at_compile_time() throws IOException {
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
    void each_target_is_named_once_and_only_as_a_string() throws IOException {
        int holder = 0;
        int seat = 0;
        for (File file : everySource()) {
            // The javadoc of the classes that use them explains each seam, so only the constants' own
            // declarations are counted: a second literal would be a second thing to keep in step.
            for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                if (line.contains("String HOLDER = \"net.coderbot")) holder++;
                if (line.contains("String OPTIFINE_SEAT = \"net.optifine")) seat++;
            }
        }
        assertEquals(
            1,
            holder,
            "the holder's name belongs in one constant, so a version of Oculus that renames it is one edit");
        assertEquals(
            1,
            seat,
            "the vertex builder's name belongs in one constant, so a version of OptiFine that renames it is one edit");
    }

    @Test
    void a_reflective_failure_gives_up_for_the_session() throws IOException {
        givesUp("com/trmtgtnh/client/render/ShaderMaterial.java", 2);
        givesUp("com/trmtgtnh/client/render/OptiFineMaterial.java", 3);
    }

    @Test
    void the_claim_follows_the_picture_and_the_wear_rather_than_the_block() throws IOException {
        List<String> model = SourceTree.lines("com/trmtgtnh/client/model/GhostBakedModel.java");

        List<Integer> claims = new ArrayList<Integer>();
        List<Integer> substitutes = new ArrayList<Integer>();
        for (int at = 0; at < model.size(); at++) {
            String line = model.get(at);
            if (line.trim()
                .startsWith("//")) continue;
            if (line.contains("ShaderMaterial.claim(")) claims.add(Integer.valueOf(at));
            if (line.contains("if (top == null) top = earth;")) substitutes.add(Integer.valueOf(at));
        }

        assertEquals(2, claims.size(), "the flat square and the stair each make one claim; the shape changed");
        assertEquals(2, substitutes.size(), "the earth substitution this test anchors on has moved");
        for (int each = 0; each < claims.size(); each++) {
            String line = model.get(
                claims.get(each)
                    .intValue());
            assertTrue(
                line.contains("claim(origin, top == null ? null : appearance)"),
                "the claim must be made from the block, the family its wear has reached, and whether the "
                    + "picture is the bare earth: "
                    + line.trim());
            assertTrue(
                claims.get(each)
                    .intValue()
                    < substitutes.get(each)
                        .intValue(),
                "the claim is made after the earth sprite is substituted in, so top is never null by then and "
                    + "a worn-through road would go on claiming the block it has worn out of");
        }
    }

    // ---- helpers -------------------------------------------------------------------------------

    /** Every catch in a class forgets every handle that class caches, and there are as many as expected. */
    private static void givesUp(String relative, int expected) throws IOException {
        List<String> source = SourceTree.lines(relative);

        // The handles this class caches, read out of the class rather than listed here, so that one
        // added later is guarded without anybody remembering to come back.
        List<String> handles = new ArrayList<String>();
        for (String line : source) {
            Matcher declared = CACHED.matcher(line.trim());
            if (declared.find()) handles.add(declared.group(1));
        }
        assertFalse(handles.isEmpty(), relative + ": found no cached handles, which would switch this test off");

        int catches = 0;
        for (int at = 0; at < source.size(); at++) {
            if (!source.get(at)
                .contains("catch (Throwable")) continue;
            catches++;
            String block = String.join("\n", source.subList(at, Math.min(source.size(), at + 12)));
            for (String handle : handles) {
                // Word-bounded on purpose. The first version of this asked whether the block contained
                // "set = null", and `reset = null` contains exactly that - so on the 1.7.10 edition it
                // passed with `set = null` deleted, which is the mutation it existed to catch.
                assertTrue(
                    Pattern.compile("(?<![A-Za-z0-9_])" + handle + "\\s*=\\s*null")
                        .matcher(block)
                        .find(),
                    relative + ": the catch on line "
                        + (at + 1)
                        + " does not forget "
                        + handle
                        + ", so a seam that has failed once keeps being asked once per face for the rest of "
                        + "the session");
            }
        }
        assertEquals(
            expected,
            catches,
            relative + ": this test knows about " + expected + " catches; the shape changed");
    }

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
