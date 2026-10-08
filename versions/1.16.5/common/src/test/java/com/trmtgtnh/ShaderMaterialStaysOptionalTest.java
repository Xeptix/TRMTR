package com.trmtgtnh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.trmtgtnh.mixin.OculusGate;

/**
 * Worn ground may claim another block's shader material, and must switch itself off instead of
 * failing.
 *
 * <p>
 * <strong>Written because this was the one thing in its release resting on reading rather than
 * running.</strong> The claim goes through other mods' own classes - Oculus's per-block shader-id
 * holder, from 0.9.219 OptiFine's vertex builder, and on Fabric Canvas's FREX material maps - none of
 * which is on the compile classpath or can be: naming them for real would mean a build dependency. So
 * each seam is a {@code @Pseudo} mixin matched at runtime or plain reflection, with a config plugin that
 * refuses each mixin where its mod is absent - none of which a compiler checks.
 *
 * <p>
 * What <em>can</em> be asked is whether every way it goes wrong is a way it goes quiet. The gate is run
 * for real: against this JVM, which has neither mod in it, and then against a class loader that has each
 * target's bytes in it and nothing else - which is the whole of what the gate claims to look at. The
 * rest is read out of the source, because the thing being guarded against compiles perfectly and is only
 * visible in the text: an inject that would fail the build on a moved target, a catch that keeps
 * retrying a lookup that has already failed, or a claim made from the wrong variable, which is the one
 * mistake a shader pack would not report as an error - only as a road that stays shiny after it has
 * worn through.
 *
 * <p>
 * It does not prove that a shader pack draws the right thing. Nothing short of a client can, and the
 * photographs under each pack are the half that does.
 */
class ShaderMaterialStaysOptionalTest {

    private static final String GATED = "com.trmtgtnh.mixin.MixinOculusBlockContext";

    private static final String GATED_OPTIFINE = "com.trmtgtnh.mixin.MixinOptiFineShaderSeat";

    private static final String GATED_SODIUM = "com.trmtgtnh.mixin.MixinSettledFacesSodium";

    /** Each seam's mixin, with the constant its target has to come from. */
    private static final String[][] SEAMS = {
        { "com/trmtgtnh/mixin/MixinOculusBlockContext.java", "targets = OculusGate.HOLDER" },
        { "com/trmtgtnh/mixin/MixinOptiFineShaderSeat.java", "targets = OculusGate.OPTIFINE_SEAT" },
        { "com/trmtgtnh/mixin/MixinSettledFacesSodium.java", "targets = OculusGate.SODIUM_FACES" } };

    /** The names Oculus, its family, OptiFine and Canvas go by, none of which may be imported. */
    private static final Pattern THEIR_PACKAGES = Pattern.compile(
        "^\\s*import\\s+(?:static\\s+)?(net\\.coderbot|net\\.irisshaders|me\\.jellysquid|net\\.optifine|grondag)[^;]*;");

    /** The three trees this edition answers for. */
    private static final String[] MODULES = { "common", "forge", "fabric" };

    /** The reflective handles a class caches, which every catch in it has to forget. */
    private static final Pattern CACHED = Pattern.compile("private static (?:Method|Field) (\\w+);");

    // ---- the gate, run for real ----------------------------------------------------------------

    @Test
    void the_gate_refuses_both_mixins_in_a_jvm_with_neither_mod() {
        OculusGate gate = new OculusGate();
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
            if (whole.equals(GATED) || whole.equals(GATED_OPTIFINE) || whole.equals(GATED_SODIUM)) continue;
            others.add(name);
            assertTrue(
                gate.shouldApplyMixin("whatever/it/Targets", whole),
                name + " is not a gated mixin and must be applied whatever is installed");
        }
        assertFalse(others.isEmpty(), "found no other mixins in the config, which would switch this test off");
    }

    @Test
    void the_gate_turns_on_when_it_can_see_the_holder(@TempDir Path pretendMod) throws Exception {
        OculusGate gate = gateSeeing(pretendMod, OculusGate.HOLDER);
        assertTrue(
            gate.shouldApplyMixin(OculusGate.HOLDER, GATED),
            "with the holder's bytes in sight the mixin must be applied, or the feature is dead on every "
                + "client that has Oculus and nothing says so");
        assertFalse(
            gate.shouldApplyMixin(OculusGate.OPTIFINE_SEAT, GATED_OPTIFINE),
            "Oculus being there said yes to OptiFine's mixin as well");
    }

    @Test
    void the_gate_turns_on_when_it_can_see_optifine(@TempDir Path pretendMod) throws Exception {
        OculusGate gate = gateSeeing(pretendMod, OculusGate.OPTIFINE_SEAT);
        assertTrue(
            gate.shouldApplyMixin(OculusGate.OPTIFINE_SEAT, GATED_OPTIFINE),
            "with OptiFine's bytes in sight its mixin must be applied, or the feature is dead under OptiFine");
        assertFalse(
            gate.shouldApplyMixin(OculusGate.HOLDER, GATED),
            "OptiFine being there said yes to Oculus's mixin as well");
    }

    @Test
    void the_gate_turns_on_when_it_can_see_the_sodium_face_test(@TempDir Path pretendMod) throws Exception {
        OculusGate gate = gateSeeing(pretendMod, OculusGate.SODIUM_FACES);
        assertTrue(
            gate.shouldApplyMixin(OculusGate.SODIUM_FACES, GATED_SODIUM),
            "with the Sodium family there its face test must be hooked, or a settled snow layer's step is left open");
        assertFalse(
            new OculusGate().shouldApplyMixin(OculusGate.SODIUM_FACES, GATED_SODIUM),
            "a gate that has seen nothing let the Sodium face hook in");
        assertFalse(gate.shouldApplyMixin(OculusGate.HOLDER, GATED), "the Sodium family being there said yes to Oculus's mixin");
    }

    /**
     * A gate whose context loader holds one class's bytes and nothing else - exactly what a client with
     * that mod has and this one has not. The gate asks for the resource rather than the class, so a file
     * of nothing answers the question honestly.
     */
    private static OculusGate gateSeeing(Path pretendMod, String className) throws Exception {
        Path bytes = pretendMod.resolve(className.replace('.', '/') + ".class");
        Files.createDirectories(bytes.getParent());
        Files.write(bytes, new byte[0]);

        ClassLoader original = Thread.currentThread()
            .getContextClassLoader();
        try (URLClassLoader pretending = new URLClassLoader(
            new URL[] { pretendMod.toUri()
                .toURL() },
            null)) {
            Thread.currentThread()
                .setContextClassLoader(pretending);
            OculusGate gate = new OculusGate();
            gate.onLoad("com.trmtgtnh.mixin");
            return gate;
        } finally {
            Thread.currentThread()
                .setContextClassLoader(original);
        }
    }

    // ---- the promises that are only visible in the source --------------------------------------

    @Test
    void each_mixin_costs_the_feature_and_nothing_else_when_its_mod_moves_it() throws IOException {
        // Read annotation by annotation and never through comments: the javadoc of these classes
        // explains why the inject carries require = 0, so asking whether the file contains those words
        // passes on the explanation after somebody has deleted the thing it explains.
        for (String[] seam : SEAMS) {
            String inject = annotation(seam[0], "@Inject(");
            String mixin = annotation(seam[0], "@Mixin(");
            String pseudo = annotation(seam[0], "@Pseudo");

            assertFalse(pseudo.isEmpty(), seam[0] + ": the target is not on the compile classpath, so it needs @Pseudo");
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
                seam[0] + ": the target has to come from the same constant the gate probes for: " + mixin);
        }
    }

    @Test
    void the_optifine_seat_is_named_for_both_ways_optifine_runs() throws IOException {
        // Forge runs OptiFine's own classes in Forge's names and Fabric runs them remapped by OptiFabric
        // into intermediary ones. A descriptor written in only one of the two is a seat that never
        // fires on the other loader, and nothing says so.
        String inject = annotation("com/trmtgtnh/mixin/MixinOptiFineShaderSeat.java", "@Inject(");
        assertTrue(
            inject.contains("pushEntity(Lnet/minecraft/block/BlockState;Lcom/mojang/blaze3d/vertex/IVertexBuilder;)V"),
            "the seat is not named in Forge's runtime names: " + inject);
        assertTrue(
            inject.contains("pushEntity(Lnet/minecraft/class_2680;Lnet/minecraft/class_4588;)V"),
            "the seat is not named in Fabric's intermediary names: " + inject);
    }

    @Test
    void the_config_hands_the_gate_the_decision() throws IOException {
        String config = text("../resources/trmtgtnh-common.mixins.json");
        assertTrue(
            config.contains("\"plugin\": \"com.trmtgtnh.mixin.OculusGate\""),
            "the gate only runs if the config names it as its plugin");
        for (String gated : new String[] { GATED, GATED_OPTIFINE, GATED_SODIUM }) {
            assertTrue(
                config.contains(gated.substring(gated.lastIndexOf('.') + 1)),
                "the gated mixin has to be in the config for the gate to have anything to refuse: " + gated);
        }
    }

    @Test
    void both_loaders_carry_the_config_the_gate_lives_in() throws IOException {
        // What makes Iris work on Fabric. Oculus is a fork of Iris that kept the upstream packages,
        // so the one seam answers for Oculus on Forge and Iris on Fabric - but only while the
        // gated mixin travels in the config both loaders declare. Moving it into the Forge module,
        // which is where a reader would expect anything named after Oculus to live, would take
        // Fabric shader support away and nothing else would notice. The OptiFine seat is the same:
        // OptiFabric runs OptiFine on Fabric.
        String fabric = read(new File(SourceTree.repoRoot(), "fabric/src/main/resources/fabric.mod.json"));
        assertTrue(
            fabric.contains("trmtgtnh-common.mixins.json"),
            "the Fabric jar has to declare the common mixin config, or the shader seams never apply there");

        File forge = new File(SourceTree.repoRoot(), "forge/src/main/resources/META-INF/mods.toml");
        assertTrue(forge.isFile(), forge.getAbsolutePath() + " is not there");

        // And the mixins themselves stay in common, which is what lets one copy answer for both.
        for (String[] seam : SEAMS) {
            assertTrue(
                new File(SourceTree.mainJava(), seam[0]).isFile(),
                seam[0] + " belongs in the shared module; a loader-specific copy would have to be kept in step by hand");
        }
    }

    @Test
    void nothing_in_any_module_names_these_mods_at_compile_time() throws IOException {
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
            for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                if (line.contains("String HOLDER = \"net.coderbot")) holder++;
                if (line.contains("String OPTIFINE_SEAT = \"net.optifine")) seat++;
            }
        }
        assertEquals(1, holder, "the holder's name belongs in one constant, so a version of Oculus that renames it is one edit");
        assertEquals(1, seat, "the vertex builder's name belongs in one constant, so a version of OptiFine that renames it is one edit");
    }

    @Test
    void a_reflective_failure_gives_up_for_the_session() throws IOException {
        givesUp(new File(SourceTree.mainJava(), "com/trmtgtnh/client/render/ShaderMaterial.java"), 2);
        givesUp(new File(SourceTree.mainJava(), "com/trmtgtnh/client/render/OptiFineMaterial.java"), 3);
        givesUp(new File(SourceTree.repoRoot(), "fabric/src/main/java/com/trmtgtnh/fabric/FrexMaterial.java"), 2);
    }

    @Test
    void the_claim_follows_the_picture_and_the_wear_rather_than_the_block() throws IOException {
        List<String> model = SourceTree.lines("com/trmtgtnh/client/model/GhostQuads.java");

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
            String line = model.get(claims.get(each)
                .intValue());
            assertTrue(
                line.contains("claim(origin, top == null ? null : appearance)"),
                "the claim must be made from the block, the family its wear has reached, and whether the "
                    + "picture is the bare earth: "
                    + line.trim());
            assertTrue(
                claims.get(each)
                    .intValue() < substitutes.get(each)
                        .intValue(),
                "the claim is made after the earth sprite is substituted in, so top is never null by then and "
                    + "a worn-through road would go on claiming the block it has worn out of");
        }
        // And the claim Canvas takes is asked the same way, from the same picture.
        String all = String.join("\n", model);
        assertTrue(
            all.contains("ShaderMaterial.claimFor(origin, top == null ? null : appearance)"),
            "the claim handed to Canvas is not the one the model makes");
    }

    @Test
    void canvas_is_given_the_claim_on_every_square() throws IOException {
        String model = read(new File(SourceTree.repoRoot(), "fabric/src/main/java/com/trmtgtnh/fabric/GhostModelFabric.java"));
        String body = model.substring(model.indexOf("public void emitBlockQuads"));
        body = body.substring(0, body.indexOf("    private static"));
        assertTrue(
            body.replaceAll("\\s+", "")
                .contains("FrexMaterial.of(GhostQuads.claimOf(record,origin,rotation)"),
            "the Fabric model never hands Canvas the claim, so under Canvas worn ground is a stranger again");
    }

    // ---- helpers -------------------------------------------------------------------------------

    /** Every catch in a class forgets every handle that class caches, and there are as many as expected. */
    private static void givesUp(File file, int expected) throws IOException {
        List<String> source = Arrays.asList(read(file).split("\r?\n"));
        String name = file.getName();

        // The handles this class caches, read out of the class rather than listed here, so that one
        // added later is guarded without anybody remembering to come back.
        List<String> handles = new ArrayList<String>();
        for (String line : source) {
            Matcher declared = CACHED.matcher(line.trim());
            if (declared.find()) handles.add(declared.group(1));
        }
        assertFalse(handles.isEmpty(), name + ": found no cached handles, which would switch this test off");

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
                    name + ": the catch on line "
                        + (at + 1)
                        + " does not forget "
                        + handle
                        + ", so a seam that has failed once keeps being asked for the rest of the session");
            }
        }
        assertEquals(expected, catches, name + ": this test knows about " + expected + " catches; the shape changed");
    }

    private static String text(String relative) throws IOException {
        return String.join("\n", SourceTree.lines(relative));
    }

    /** One file read whole, named by an absolute path rather than relative to the source root. */
    private static String read(File file) throws IOException {
        assertTrue(file.isFile(), file.getAbsolutePath() + " is not there");
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
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
        for (String module : MODULES) {
            File at = new File(SourceTree.repoRoot(), module + "/src/main/java");
            assertTrue(at.isDirectory(), at.getAbsolutePath() + " is not there");
            gather(at, found);
        }
        assertTrue(found.size() > 100, "found " + found.size() + " sources, which would switch this off");
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
        String config = text("../resources/trmtgtnh-common.mixins.json");
        List<String> names = new ArrayList<String>();
        Matcher each = Pattern.compile("\"(Mixin[A-Za-z0-9_]+)\"")
            .matcher(config);
        while (each.find()) names.add(each.group(1));
        return names;
    }
}
