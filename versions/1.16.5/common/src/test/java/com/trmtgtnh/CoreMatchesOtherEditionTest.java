package com.trmtgtnh;

import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The portable core is the same file in every edition, or the build says so.
 *
 * <p>
 * This mod exists more than once - for Minecraft 1.7.10, for 1.12.2, and here - and all of them share
 * the arithmetic that decides how ground wears: what a record is, how it packs into sixteen bits, how
 * long a chain is, what a mend costs. Those classes name nothing from the game, which is what lets
 * them be the same file rather than several files that agree for now.
 *
 * <p>
 * Copied rather than extracted into a library, and that was a decision rather than an oversight:
 * extracting one would have made every change to the core a library release before any edition could
 * build. The cost of copying is that copies drift, and drift here would be the worst kind - the
 * editions would wear ground at subtly different rates and nothing would ever say so. So the copies
 * are compared, byte for byte, and any difference fails the build on every side.
 *
 * <p>
 * Byte for byte, which means no edition may reformat these files. A formatter with slightly different
 * settings would report a difference that is not one, and the first person to see it would quite
 * reasonably put it right by reformatting the other copy. That matters more here than it did between
 * the first two editions: this one is built by a different toolchain entirely, with its own opinions.
 *
 * <p>
 * <strong>Each edition compares against one other, and that is enough.</strong> 1.12.2 compares
 * against 1.7.10 and this compares against 1.12.2, so a difference anywhere breaks a link in the
 * chain and some build fails. Comparing every edition against every other would need each tree to
 * know where all the others are, which is exactly the machine-specific knowledge this avoids.
 *
 * <p>
 * Where the other edition is, is not written here. A repository that has to be checked out beside
 * another one in order to build is a repository nobody else can build, so the path is read from a
 * system property the buildscript fills in from an untracked file, and this test simply does not run
 * when that is absent. On a machine with both trees it holds; on a machine with one, it is quiet.
 *
 * <p>
 * Both source layouts are tried, because they differ by edition and will keep differing: the single
 * projects keep the core at {@code src/main/java}, and the multi-loader ones keep it at
 * {@code common/src/main/java}, since a class shared by two loaders cannot live in either's module.
 */
class CoreMatchesOtherEditionTest {

    /** Where the other edition's tree is, filled in by the buildscript from an untracked file. */
    private static final String OTHER = "trmt.other.edition";

    /** The places an edition might keep the core, newest layout last. */
    private static final String[] LAYOUTS = { "src/main/java", "common/src/main/java" };

    @Test
    void the_portable_core_is_the_same_in_every_edition() throws IOException {
        String other = System.getProperty(OTHER, "")
            .trim();
        assumeTrue(
            !other.isEmpty(),
            "No other edition to compare against: put otherEdition=<path to another edition's tree> in "
                + "sibling.properties at the root of this repository to switch this check on.");

        File otherRoot = null;
        for (String layout : LAYOUTS) {
            File candidate = new File(other, layout);
            if (candidate.isDirectory()) {
                otherRoot = candidate;
                break;
            }
        }
        assumeTrue(
            otherRoot != null,
            OTHER + " is set to " + other + ", which has neither " + LAYOUTS[0] + " nor " + LAYOUTS[1] + " under it.");

        File ownRoot = SourceTree.mainJava();
        List<String> drifted = new ArrayList<String>();
        List<String> pending = new ArrayList<String>();
        int compared = 0;

        for (String relative : CoreStaysPortableTest.PORTABLE) {
            File own = new File(ownRoot, relative);
            File theirs = new File(otherRoot, relative);
            if (!own.isFile() || !theirs.isFile()) {
                pending.add(relative + (own.isFile() ? "  (not carried across yet)" : "  (not in this edition)"));
                continue;
            }
            compared++;
            byte[] mine = Files.readAllBytes(own.toPath());
            byte[] yours = Files.readAllBytes(theirs.toPath());
            if (!Arrays.equals(mine, yours)) {
                drifted.add(relative + "  (" + mine.length + " bytes here, " + yours.length + " there)");
            }
        }

        if (!drifted.isEmpty()) {
            StringBuilder message = new StringBuilder(
                "The portable core has drifted between editions. These are meant to be the same file, "
                    + "so that every version of the mod wears ground by the same arithmetic:\n");
            for (String one : drifted) {
                message.append("  ")
                    .append(one)
                    .append('\n');
            }
            message.append("  other edition: ")
                .append(otherRoot.getAbsolutePath())
                .append('\n')
                .append(
                    "Copy the intended version over the other rather than editing one to resemble it, "
                        + "and remember that no side may reformat these files.");
            fail(message.toString());
        }

        if (compared == 0) {
            fail(
                "Not one of the " + CoreStaysPortableTest.PORTABLE.length
                    + " portable classes is present in both editions, so this check is comparing nothing. "
                    + "Either "
                    + otherRoot.getAbsolutePath()
                    + " is not another edition's tree, or the core has not been carried across at all.");
        }

        if (!pending.isEmpty()) {
            System.out.println(
                "Portable core: " + compared
                    + " compared and identical, "
                    + pending.size()
                    + " still to be carried across:");
            for (String one : pending) {
                System.out.println("  " + one);
            }
        }
    }
}
