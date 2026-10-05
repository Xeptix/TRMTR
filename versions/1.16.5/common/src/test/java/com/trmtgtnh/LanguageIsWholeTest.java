package com.trmtgtnh;

import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * That this mod's text is all here, and that nothing asks for text that is not.
 *
 * <p>
 * Written because it was not. There was no language file in this edition at all until the golem
 * landed, and nothing noticed for months: every string in the mod was showing its own key, and a
 * missing translation is not an error - it is a key, rendered exactly where the sentence should
 * have been. No build fails, no log line appears, and the only way to find it is to look at the
 * game and read what is on the screen.
 *
 * <p>
 * So this checks the two directions that can go wrong, and they are not the same question:
 *
 * <ul>
 * <li><strong>A key that lost its value.</strong> Every key the other edition has must be here,
 * either under the same name or under one of the declared renames below. A key that quietly stopped
 * existing takes a sentence off the screen with it.
 * <li><strong>A value that lost its key.</strong> Every advancement file names two translation keys,
 * and nothing anywhere checks that they resolve - an advancement with no title shows its key in the
 * toast, and the toast is gone in four seconds. This is the only place that check exists.
 * </ul>
 *
 * <p>
 * The first needs the other edition's tree and is skipped without it, the same way
 * {@link CoreMatchesOtherEditionTest} is skipped and for the same reason: a repository that must be
 * checked out beside another one in order to build is a repository nobody else can build. The second
 * needs only this tree, and always runs.
 */
class LanguageIsWholeTest {

    /** Where the other edition's tree is, filled in by the buildscript from an untracked file. */
    private static final String OTHER = "trmt.other.edition";

    /**
     * The keys the game asks for, which are the only ones whose spelling changed.
     *
     * <p>
     * A key the <em>mod</em> asks for by name through {@code Translate} is the mod's own and carries
     * untouched, which is 361 of the 384. These twenty-three are the game's: an item with no name of
     * its own lost the {@code .name} it used to be asked under, and a mob effect stopped being
     * called a potion.
     *
     * <p>
     * The two exceptions are in {@link #keepsItsName}: both tampers answer {@code getName} for
     * themselves, because what they are called depends on what they are made of, so the game never
     * asks and the key stays the mod's.
     */
    private static String here(String there) {
        if (keepsItsName(there)) return there;
        if (there.startsWith("potion.trmtgtnh.")) {
            return "effect.trmtgtnh." + there.substring("potion.trmtgtnh.".length());
        }
        if (there.startsWith("item.trmtgtnh.") && there.endsWith(".name")) {
            return there.substring(0, there.length() - ".name".length());
        }
        // A block, which was tile.<ns>.<path>.name and is block.<ns>.<path>. One key is under this
        // rule - the ghost's - and it arrived late, in both editions at once: a worn square normally
        // answers as the block it covers, so its own name is the fallback for a square whose record
        // has not reached the client, and neither edition had one until this test asked for it.
        if (there.startsWith("tile.trmtgtnh.") && there.endsWith(".name")) {
            return "block.trmtgtnh."
                + there.substring("tile.trmtgtnh.".length(), there.length() - ".name".length());
        }
        return there;
    }

    private static boolean keepsItsName(String key) {
        return "item.trmtgtnh.tamper.graded.name".equals(key)
            || "item.trmtgtnh.chunk_tamper.graded.name".equals(key);
    }

    @Test
    void every_line_of_the_other_edition_s_text_is_here() throws IOException {
        String other = System.getProperty(OTHER, "")
            .trim();
        assumeTrue(
            !other.isEmpty(),
            "No other edition to compare against: put otherEdition=<path to another edition's tree> in "
                + "sibling.properties at the root of this repository to switch this check on.");
        File theirs = new File(other, "src/main/resources/assets/trmtgtnh/lang/en_us.lang");
        assumeTrue(theirs.isFile(), "The other edition has no en_us.lang at " + theirs);

        Map<String, String> ours = ours();
        List<String> missing = new ArrayList<String>();
        List<String> changed = new ArrayList<String>();
        int seen = 0;
        for (String line : Files.readAllLines(theirs.toPath(), StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || line.indexOf('=') < 0) continue;
            seen++;
            String key = line.substring(0, line.indexOf('='))
                .trim();
            String value = line.substring(line.indexOf('=') + 1);
            String mine = here(key);
            if (!ours.containsKey(mine)) {
                missing.add(key + (mine.equals(key) ? "" : "  (looked for it as " + mine + ")"));
            } else if (!ours.get(mine)
                .equals(value)) {
                changed.add(mine);
            }
        }

        if (!missing.isEmpty()) {
            StringBuilder message = new StringBuilder(
                "The other edition has text this one has not, so these show as their own keys on the "
                    + "screen and nothing else says so:\n");
            for (String one : missing) {
                message.append("  ")
                    .append(one)
                    .append('\n');
            }
            message.append(
                "Either run tools/mapping/carries/carry_lang.py again, or - if the key genuinely "
                    + "changed shape at this version - say so in here() above, which is the only "
                    + "place that knowledge is written down.");
            fail(message.toString());
        }

        // Worth failing on rather than ignoring. A value that differs is either a translation
        // improved in one edition and not the other, which should be fixed in both, or a carry that
        // mangled a line - and the second is silent in exactly the way this file exists to catch.
        if (!changed.isEmpty()) {
            StringBuilder message = new StringBuilder(
                "These keys are in both editions and say different things:\n");
            for (String one : changed) {
                message.append("  ")
                    .append(one)
                    .append('\n');
            }
            fail(message.toString());
        }

        if (seen == 0) fail("The other edition's language file read as empty, which cannot be right.");
    }

    @Test
    void every_advancement_asks_for_text_that_exists() throws IOException {
        File folder = new File(SourceTree.repoRoot(), "common/src/main/resources/data/trmtgtnh/advancements");
        if (!folder.isDirectory()) {
            fail(
                "There are no advancement files at " + folder + ". ModAchievements grants sixteen of "
                    + "them by name, and a grant that finds nothing does nothing - no warning, no "
                    + "exception, no sign on either side. That is how they came to be missing for "
                    + "months. Run tools/mapping/carries/carry_advancements.py.");
        }

        Map<String, String> ours = ours();
        Pattern asks = Pattern.compile("\"translate\"\\s*:\\s*\"([^\"]+)\"");
        List<String> dangling = new ArrayList<String>();
        File[] files = folder.listFiles();
        int asked = 0;
        for (File each : files == null ? new File[0] : files) {
            if (!each.getName()
                .endsWith(".json")) {
                continue;
            }
            String body = new String(Files.readAllBytes(each.toPath()), StandardCharsets.UTF_8);
            Matcher found = asks.matcher(body);
            while (found.find()) {
                asked++;
                String key = found.group(1);
                if (!ours.containsKey(key)) dangling.add(each.getName() + " asks for " + key);
            }
        }

        if (!dangling.isEmpty()) {
            StringBuilder message = new StringBuilder(
                "These advancements name text that is not in en_us.json, so the toast shows the key "
                    + "and is gone again in four seconds:\n");
            for (String one : new TreeSet<String>(dangling)) {
                message.append("  ")
                    .append(one)
                    .append('\n');
            }
            fail(message.toString());
        }

        if (asked == 0) {
            fail(
                "Not one advancement named any text, which means either the files are empty or the "
                    + "pattern above has stopped matching them. A check that cannot see the thing it "
                    + "is checking for always passes.");
        }
    }

    /** This edition's language file, as keys and values. */
    private static Map<String, String> ours() throws IOException {
        File mine = new File(SourceTree.repoRoot(), "common/src/main/resources/assets/trmtgtnh/lang/en_us.json");
        if (!mine.isFile()) {
            fail(
                "There is no language file at " + mine + ", so every string in this mod shows as its "
                    + "own key. Run tools/mapping/carries/carry_lang.py.");
        }
        String body = new String(Files.readAllBytes(mine.toPath()), StandardCharsets.UTF_8);
        // A deliberately small parser rather than a JSON library, because this file is written by a
        // script that writes one pair per line and nothing else - and a test that depends on the
        // shape of its input is a test that says so when the shape changes. Neither key nor value
        // can contain a quote: carry_lang.py would have escaped one, and the check below refuses the
        // file if a line it cannot read turns up.
        Map<String, String> out = new LinkedHashMap<String, String>();
        Pattern pair = Pattern.compile("^\\s*\"([^\"]+)\"\\s*:\\s*\"(.*)\"\\s*,?\\s*$");
        for (String line : body.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || "{".equals(trimmed) || "}".equals(trimmed)) continue;
            Matcher found = pair.matcher(line);
            if (!found.matches()) {
                fail("A line of en_us.json that this test cannot read: " + trimmed);
            }
            out.put(found.group(1), found.group(2));
        }
        return out;
    }
}
