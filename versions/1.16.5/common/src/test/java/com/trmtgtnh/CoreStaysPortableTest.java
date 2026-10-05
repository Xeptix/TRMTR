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
 * small pieces of bookkeeping beside them, and not one of them mentions Minecraft. In the two older
 * editions that fence was a promise about what <em>could</em> be carried to another version of the
 * game. This edition is that carry having happened, and these twenty-eight files arrived here
 * unchanged, byte for byte, which is the only reason this port began at a milestone rather than at a
 * rewrite.
 *
 * <p>
 * Nothing enforced it. A single {@code import net.minecraft.world.level.Level} in any of these files
 * would close that door quietly, compile perfectly, and only be noticed by whoever came to open it
 * for the version after this one. So the boundary is a test rather than a plan: it costs nothing
 * while it holds and fails the build the moment it stops holding.
 *
 * <p>
 * <strong>This edition forbids more than the others do</strong>, and that is the one real difference
 * between the three copies of this test. 1.7.10 and 1.12.2 each have exactly one loader, so naming
 * Forge there is a portability problem and nothing worse. Here there are two jars built from one body
 * of code, and a loader named in a shared class is a compile error on the other loader rather than a
 * door quietly closing - so Fabric and Architectury are on the forbidden list beside Forge. The core
 * needs none of them: it names nothing at all beyond the JDK.
 *
 * <p>
 * If a class genuinely needs Minecraft, the answer is to take it off this list rather than to
 * silence the test - but do it knowing that the list is the door, and every name taken off it
 * narrows what can be carried to the version after this one.
 */
class CoreStaysPortableTest {

    /**
     * The classes that must stay free of the game, as paths under the source root.
     *
     * <p>
     * Package-visible rather than private so the test that compares this core against the other
     * editions' copies of it can ask this list rather than keep a second one. Two lists would drift,
     * and the drift would be invisible: a class dropped from one and not the other would simply
     * stop being checked by whichever test was not told.
     *
     * <p>
     * The same twenty-eight, in the same order, as the two older editions. Keeping the order makes the
     * three lists diffable by eye, which is how a class going missing from one of them gets noticed.
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
        "com/trmtgtnh/item/WayfarerCore.java",
        "com/trmtgtnh/compat/QuestLessons.java", "com/trmtgtnh/client/texture/AtlasPlan.java",
        "com/trmtgtnh/client/texture/StateFiling.java", "com/trmtgtnh/client/texture/MovingLayerLedger.java",
        "com/trmtgtnh/client/texture/FaceRules.java", "com/trmtgtnh/client/texture/AnisotropicBorder.java",
        "com/trmtgtnh/client/texture/WearCompositor.java" };

    /**
     * Package roots that mean the game or a loader rather than the model.
     *
     * <p>
     * Matched on the import statement rather than on the whole file, because a comment is allowed to
     * mention Minecraft and several of these explain themselves by naming what they are standing in
     * for. What is being forbidden is a dependency, not a word.
     */
    private static final String[] FORBIDDEN = { "net.minecraft", "net.minecraftforge", "net.fabricmc",
        "dev.architectury", "cpw.mods", "org.spongepowered", "com.gtnewhorizon" };

    @Test
    void the_erosion_core_names_nothing_from_the_game_or_either_loader() throws IOException {
        File root = SourceTree.mainJava();
        List<String> complaints = new ArrayList<String>();

        List<String> pending = new ArrayList<String>();

        for (String relative : PORTABLE) {
            File file = new File(root, relative);
            // Not there yet, rather than gone. The count is asserted below so the list cannot empty
            // itself and leave this test passing by checking nothing.
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
                "The erosion core has picked up the game or a loader. These classes are what made this "
                    + "port possible at all - they crossed from 1.12.2 unchanged - and their tests run "
                    + "without Minecraft on the classpath because of it:\n");
            for (String complaint : complaints) {
                message.append("  ")
                    .append(complaint)
                    .append('\n');
            }
            message.append(
                "Either keep the game out of them - passing in what is needed usually does it - or "
                    + "take the class off the list in this test, knowing what that costs. A loader "
                    + "named here is worse than the game: it breaks the other loader's jar outright.");
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
                    + " carried across so far. Still to come:");
            for (String one : pending) {
                System.out.println("  " + one);
            }
        }
    }
}
