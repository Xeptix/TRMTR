package com.trmtgtnh;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A second fence, wider than the portable core's, drawn where a port to another loader would cut.
 *
 * <p>
 * {@link CoreStaysPortableTest} holds twenty-six classes to naming nothing from Minecraft at all.
 * This holds a much larger set to a weaker but more useful rule: <strong>it may name Minecraft, but
 * it may not name Forge.</strong> Minecraft is the same on every loader; Forge is the thing a Fabric
 * build does not have.
 *
 * <h2>Why a test rather than a module</h2>
 *
 * <p>
 * The obvious way to enforce this is a {@code common} Gradle subproject compiled without Forge on its
 * classpath, which is how multi-loader projects normally do it. **That cannot be done on 1.12.2.**
 * Forge is distributed as patches applied to Minecraft, and the artifact this build compiles against
 * - {@code recompiled_minecraft-1.12.2.jar} - contains fifteen hundred Forge classes. There is no
 * Minecraft-without-a-loader classpath to point a subproject at, so a subproject would compile
 * against the same jar and prove nothing. A test can state the rule that a classpath cannot.
 *
 * <p>
 * The same rule will be enforceable by the build from 1.16.5 onward, where Loom and its relatives do
 * give you Minecraft without a loader. Until then this is the fence, and the point of having it now
 * is that the line stops moving: every one of these files is one a later port carries rather than
 * rewrites, and a Forge import added to one quietly turns it into the second kind.
 *
 * <h2>The seams</h2>
 *
 * <p>
 * What is left outside the fence is not arbitrary. Across every package listed here the whole of
 * Forge's surface comes to six kinds of thing, each named below with the file that needs it. Six is
 * a number small enough to put behind six interfaces when the time comes, and knowing that is most of
 * what this test is for.
 */
class LoaderNeutralTest {

    /** Packages where every file must be free of Forge, and the seams allowed inside them. */
    private static final String[] FENCED = { "com/trmtgtnh/util", "com/trmtgtnh/command", "com/trmtgtnh/erosion",
        "com/trmtgtnh/surface", "com/trmtgtnh/compat", "com/trmtgtnh/config", "com/trmtgtnh/client/texture" };

    /**
     * The files inside the fence that do name Forge, each with what it needs and why.
     *
     * <p>
     * This list is the port's own to-do list. Nothing may be added to it without a reason, and
     * everything on it is a place where a Fabric or NeoForge build will need its own answer - so it
     * is deliberately written as six problems rather than eleven files.
     */
    private static final String[][] SEAMS = {
        // 1. Getting a world by its dimension id. Every loader can do this; none agree how.
        { "com/trmtgtnh/erosion/ErosionEngine.java", "DimensionManager" },
        { "com/trmtgtnh/erosion/ErosionStore.java", "DimensionManager, and chunk persistence" },

        // 2. Chunk persistence. ChunkDataEvent exists on 1.16.5, 1.20.1 and NeoForge 1.21.1 under a
        // moved package; Fabric needs its own, and 1.21.1 offers attachments instead.

        // 3. The collision hook and the event bus. Both are Forge shapes; the hook itself becomes a
        // plain block override from 1.16.5 on, which is simpler than either.
        { "com/trmtgtnh/erosion/PhysicalDecay.java", "GetCollisionBoxesEvent and the event bus" },

        // 4. Asking whether a block is a plant, and what it drops.
        { "com/trmtgtnh/erosion/WearDrops.java", "ForgeHooks and IPlantable" },
        { "com/trmtgtnh/surface/SurfaceRegistry.java", "IPlantable" },

        // 5. Reading the settings file. Answered, and taken: ConfigFile is the mod's own reader and
        // writer of Forge's format, so FamilySettings and Presets have left this list entirely and
        // TrmtConfig's three thousand lines of settings name no Forge config type either. What keeps
        // it here is the event it listens to and the annotation it listens with - the config screen
        // reports a change through Forge's bus, and Forge's bus is not a settings problem.
        { "com/trmtgtnh/config/TrmtConfig.java", "the config-changed event, @SubscribeEvent and Loader.isModLoaded" },

        // 6. Asking whether another mod is present, and talking to it. Every loader has an answer.
        { "com/trmtgtnh/compat/QuestbookCompat.java", "Loader.isModLoaded" },
        { "com/trmtgtnh/compat/TrophyCompat.java", "Loader.isModLoaded" },
        { "com/trmtgtnh/compat/WailaCompat.java", "Loader.isModLoaded and inter-mod messaging" },

        // And the atlas, which is the one seam that is pure rendering: a sprite's size and the stitch
        // event. From 1.20.1 this becomes a sprite source, which both loader families reach.
        { "com/trmtgtnh/client/texture/WearTextures.java", "the stitch event and atlas sprites" },
        { "com/trmtgtnh/client/texture/FringeSprite.java", "atlas sprites" },
        { "com/trmtgtnh/client/texture/ModelFaces.java", "Forge's baked-model types" }, };

    /**
     * Forge, named anywhere in the code - imported or written out in full.
     *
     * <p>
     * Not just imports. This mod reaches across its own packages by writing whole names rather than
     * importing them, and it does the same to Forge: {@code ErosionEngine} has no Forge import and
     * calls {@code net.minecraftforge.common.DimensionManager} twice. A fence that only read imports
     * is one a single qualified call steps over, and the first version of this test was exactly that
     * until its own reverse check said so.
     */
    private static final Pattern FORGE = Pattern.compile("(net\\.minecraftforge\\.|cpw\\.mods\\.)");

    /** A line comment, or a block one, including javadoc. */
    private static final Pattern COMMENTS = Pattern.compile("//[^\\n]*|/\\*.*?\\*/", Pattern.DOTALL);

    /**
     * The file with its comments taken out.
     *
     * <p>
     * Because a javadoc explaining that Forge's {@code Configuration} is gone after 1.12.2 would
     * otherwise fail a fence looking for the word - which is the trap two other guards in this
     * project fell into before this one was written.
     */
    private static String codeOf(String body) {
        return COMMENTS.matcher(body)
            .replaceAll(" ");
    }

    @Test
    @DisplayName("the loader-neutral half of the mod names no loader")
    void theNeutralHalfNamesNoLoader() throws IOException {
        File root = SourceTree.mainJava();
        assertTrue(root.isDirectory(), "cannot find the source tree at " + root);

        Set<String> allowed = new HashSet<String>();
        for (String[] seam : SEAMS) allowed.add(seam[0]);

        List<String> broke = new ArrayList<String>();
        List<String> fenced = new ArrayList<String>();
        int lines = 0;

        for (String pkg : FENCED) {
            File folder = new File(root, pkg.replace('/', File.separatorChar));
            if (!folder.isDirectory()) {
                broke.add("the fenced package " + pkg + " is not there any more");
                continue;
            }
            for (File file : walk(folder)) {
                String relative = relative(root, file);
                String body = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                Matcher found = FORGE.matcher(codeOf(body));
                boolean namesForge = found.find();

                if (namesForge && !allowed.contains(relative)) {
                    broke.add(relative + " names " + found.group(1) + "… and is not a listed seam");
                } else if (!namesForge) {
                    fenced.add(relative);
                    lines += body.split("\n", -1).length;
                }
            }
        }

        // A seam that has stopped needing Forge should come off the list, or the list stops meaning
        // anything. This is the half of the check that keeps it honest as the ports progress.
        for (String[] seam : SEAMS) {
            File file = new File(root, seam[0].replace('/', File.separatorChar));
            if (!file.isFile()) {
                broke.add("the listed seam " + seam[0] + " is not there any more; take it off the list");
                continue;
            }
            String body = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            if (!FORGE.matcher(codeOf(body))
                .find()) {
                broke.add(
                    seam[0] + " no longer names Forge ("
                        + seam[1]
                        + ") - take it off the seam list, the fence is wider now");
            }
        }

        if (!broke.isEmpty()) {
            Collections.sort(broke);
            fail(
                "The loader-neutral fence has moved. These files are the ones a port to another loader\n"
                    + "carries rather than rewrites, so a Forge import added to one is a decision worth\n"
                    + "making on purpose:\n  "
                    + String.join("\n  ", broke));
        }

        System.out.println(
            "Loader-neutral: " + fenced.size()
                + " files, "
                + lines
                + " lines, across "
                + FENCED.length
                + " packages, with "
                + SEAMS.length
                + " named seams outside the fence.");
        // The measured figure, as a floor rather than a target. It may only go up: a file that leaves
        // the fence has to be added to the seam list above, deliberately, with a reason.
        assertTrue(
            fenced.size() >= 55,
            "the fence held " + fenced.size() + " files and should hold at least fifty-five");
    }

    /** Every seam has a reason written beside it, because a bare filename teaches nobody anything. */
    @Test
    @DisplayName("every seam says what it needs Forge for")
    void everySeamIsExplained() {
        for (String[] seam : SEAMS) {
            assertTrue(seam.length == 2, "a seam needs a file and a reason: " + Arrays.toString(seam));
            assertTrue(seam[1] != null && seam[1].length() > 4, "no reason given for " + seam[0]);
        }
    }

    private static List<File> walk(File folder) {
        List<File> out = new ArrayList<File>();
        File[] held = folder.listFiles();
        if (held == null) return out;
        for (File file : held) {
            if (file.isDirectory()) {
                out.addAll(walk(file));
            } else if (file.getName()
                .endsWith(".java")) {
                    out.add(file);
                }
        }
        Collections.sort(out);
        return out;
    }

    private static String relative(File root, File file) {
        return file.getAbsolutePath()
            .substring(
                root.getAbsolutePath()
                    .length() + 1)
            .replace(File.separatorChar, '/');
    }
}
