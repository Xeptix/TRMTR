package com.trmtgtnh;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * A fence around the part of this mod that has no Minecraft in it.
 *
 * <p>
 * The classes listed below carry the erosion model - what a record is, how it packs into sixteen
 * bits, what a chain is and how deep it goes - the arithmetic of what mending it costs, and a few
 * small pieces of bookkeeping beside them, such as how long a golem believes its stores could not
 * pay, or how much of the block atlas the wear textures may take, and not one of them mentions
 * Minecraft. For most of them that was not arranged deliberately at the time; it fell out of writing
 * the arithmetic before the rendering. The mending price and the golem's memory of refused fetches
 * were kept apart on purpose, because every promise made about them is a figure or a lifetime a
 * test has to be able to check. Either way it is the reason their tests run at all, because none
 * of them needs Minecraft on the classpath to load, and it is the one seam along which this mod
 * could ever be built for another version of the game without starting again. The list is not
 * counted here, so that adding to it does not leave this paragraph saying something false.
 *
 * <p>
 * Nothing enforced it. A single {@code import net.minecraft.block.Block} in any of these files
 * would close that door quietly, compile perfectly, and only be noticed by whoever came to open it.
 * So the boundary is a test rather than a plan: it costs nothing while it holds and fails the build
 * the moment it stops holding.
 *
 * <p>
 * A test rather than a Gradle module, deliberately. Extracting these into a subproject would make
 * the same promise and would also mean restructuring a buildscript that assumes one 1.7.10 project
 * - the Jabel target, spotless, the mixin annotation processor and the jar's own name all hang off
 * it. The promise is what has value now; the restructuring is worth its risk only when there is a
 * second thing to consume it.
 *
 * <p>
 * If a class genuinely needs Minecraft, the answer is to take it off this list rather than to
 * silence the test - but do it knowing that the list is the door, and every name taken off it
 * narrows what can be carried across.
 */
class CoreStaysPortableTest {

    /**
     * The classes that must stay free of the game, as paths under the source root.
     *
     * <p>
     * Package-visible rather than private so the test that compares this core against the other
     * edition's copy of it can ask this list rather than keep a second one. Two lists would drift,
     * and the drift would be invisible: a class dropped from one and not the other would simply
     * stop being checked by whichever test was not told.
     */
    static final String[] PORTABLE = { "com/trmtgtnh/erosion/ErosionKey.java", "com/trmtgtnh/erosion/ErosionState.java",
        "com/trmtgtnh/erosion/ErosionEntry.java", "com/trmtgtnh/erosion/ChunkErosionData.java",
        "com/trmtgtnh/erosion/ErosionChain.java", "com/trmtgtnh/erosion/SinkProfile.java",
        "com/trmtgtnh/erosion/MendLedger.java", "com/trmtgtnh/erosion/TrampleTally.java",
        "com/trmtgtnh/erosion/RunShape.java", "com/trmtgtnh/erosion/HealBank.java",
        "com/trmtgtnh/erosion/RecordReadout.java", "com/trmtgtnh/surface/SurfaceFamily.java",
        "com/trmtgtnh/surface/SurfaceTableCodec.java", "com/trmtgtnh/util/RefusedFetches.java",
        "com/trmtgtnh/util/MobEntries.java", "com/trmtgtnh/util/OfferedDraw.java",
        "com/trmtgtnh/util/WayfarerCategories.java", "com/trmtgtnh/util/InspectionReach.java",
        "com/trmtgtnh/util/InspectionSlot.java", "com/trmtgtnh/util/IntKeyMap.java",
        "com/trmtgtnh/util/LogSample.java",
        "com/trmtgtnh/item/WayfarerCore.java",
        "com/trmtgtnh/compat/QuestLessons.java", "com/trmtgtnh/client/texture/AtlasPlan.java",
        "com/trmtgtnh/client/texture/StateFiling.java", "com/trmtgtnh/client/texture/MovingLayerLedger.java",
        "com/trmtgtnh/client/texture/FaceRules.java", "com/trmtgtnh/client/texture/AnisotropicBorder.java",
        "com/trmtgtnh/client/texture/WearCompositor.java" };

    /**
     * Package roots that mean the game rather than the model.
     *
     * <p>
     * Matched on the import statement rather than on the whole file, because a comment is allowed to
     * mention Minecraft and several of these explain themselves by naming what they are standing in
     * for. What is being forbidden is a dependency, not a word.
     */
    private static final String[] FORBIDDEN = { "net.minecraft", "net.minecraftforge", "cpw.mods", "org.spongepowered",
        "com.gtnewhorizon" };

    @Test
    void the_erosion_core_names_nothing_from_the_game() throws IOException {
        File root = SourceTree.mainJava();
        List<String> complaints = new ArrayList<String>();

        List<String> pending = new ArrayList<String>();

        for (String relative : PORTABLE) {
            File file = new File(root, relative);
            // Not there yet, rather than gone. This edition is being built up a milestone at a
            // time and several of these rest on the config and surface layers, which have not
            // arrived; they are named here so that a class which quietly never turns up is still
            // visible, and the count is asserted below so the list cannot empty itself.
            if (!file.isFile()) {
                pending.add(relative);
                continue;
            }

            List<String> lines = Files.readAllLines(file.toPath(), Charset.forName("UTF-8"));
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i)
                    .trim();
                if (!line.startsWith("import ")) continue;
                for (String forbidden : FORBIDDEN) {
                    if (line.startsWith("import " + forbidden) || line.startsWith("import static " + forbidden)) {
                        complaints.add(relative + ":" + (i + 1) + "  " + line);
                    }
                }
            }
        }

        if (!complaints.isEmpty()) {
            StringBuilder message = new StringBuilder(
                "The erosion core has picked up the game. These classes are the only part of the mod "
                    + "that could be carried to another Minecraft version without a rewrite, and their "
                    + "tests run without Minecraft on the classpath because of it:\n");
            for (String complaint : complaints) {
                message.append("  ")
                    .append(complaint)
                    .append('\n');
            }
            message.append(
                "Either keep the game out of them - passing in what is needed usually does it - or "
                    + "take the class off the list in this test, knowing what that costs.");
            fail(message.toString());
        }

        assertTrue(
            pending.size() < PORTABLE.length,
            "Not one of the " + PORTABLE.length
                + " portable classes is present, so this test checked nothing. The list is read by "
                + "the drift check as well, so an empty core would quietly switch both off.");

        if (!pending.isEmpty()) {
            System.out.println(
                "Portable core: " + (PORTABLE.length - pending.size())
                    + " of "
                    + PORTABLE.length
                    + " carried across so far. Still to come, with the layers they rest on:");
            for (String one : pending) {
                System.out.println("  " + one);
            }
        }
    }
}
