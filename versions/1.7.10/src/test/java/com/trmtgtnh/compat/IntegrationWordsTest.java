package com.trmtgtnh.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Holds what is said about the companion-mod switches to what the code does.
 *
 * <p>
 * Words rather than logic, which is unusual here and deliberate. The trophy count on the README and
 * the project page went stale the moment a seventh trophy arrived in 0.9.200, and the enhancements
 * switch promised to govern trophies and loot, which it never did, from the day it was written.
 * Neither shows from inside the game, and the integration settings are not on the settings screen,
 * so the file is the only place anybody reads them. Nothing here loads a class; six files are read
 * as text.
 *
 * <p>
 * The count is of the ids TrophyCompat.define names, which is what a pack with every feature on
 * writes. Fewer is possible and the trophies comment says why; this only stops the number everybody
 * quotes drifting away from the list.
 */
class IntegrationWordsTest {

    private static final String COMPAT = "src/main/java/com/trmtgtnh/compat/TrophyCompat.java";

    private static final String CONFIG = "src/main/java/com/trmtgtnh/config/TrmtConfig.java";

    private static final String LOOT = "src/main/java/com/trmtgtnh/item/ModLoot.java";

    private static final String LANG = "src/main/resources/assets/trmtgtnh/lang/en_US.lang";

    private static final String ID = "\"(trmt_[a-z]+)\"";

    private static final String[] WORDS = { "no", "one", "two", "three", "four", "five", "six", "seven", "eight",
        "nine", "ten", "eleven", "twelve" };

    @Test
    void every_trophy_defined_has_a_name_and_every_name_a_trophy() throws IOException {
        assertEquals(
            ids(defined(), ID),
            ids(read(LANG), "(?m)^tile\\.amazingtrophies\\.trophy\\.(trmt_[a-z]+)\\.name="),
            "TrophyCompat.define and en_US.lang's trophy names disagree");
    }

    @Test
    void the_readme_the_project_page_and_the_comment_count_what_is_defined() throws IOException {
        int count = ids(defined(), ID).size();
        assertTrue(count < WORDS.length, "TrophyCompat.define names " + count + "; add words to this test");
        String word = WORDS[count];
        assertTrue(
            line(read("README.md"), "| **Amazing Trophies** |").contains(" " + word + " "),
            "README.md's Amazing Trophies row should say " + word);
        if (present("docs/CURSEFORGE.md")) {
            assertTrue(
                line(read("docs/CURSEFORGE.md"), "| Amazing Trophies |").contains(" " + word + " "),
                "docs/CURSEFORGE.md's Amazing Trophies row should say " + word);
        }
        assertTrue(
            read(CONFIG).contains("to pick up - " + word + " of them"),
            "the integration.trophies comment should say " + word);
    }

    @Test
    void the_enhancements_switch_neither_governs_trophies_or_loot_nor_says_it_does() throws IOException {
        String compat = read(COMPAT);
        int at = compat.indexOf("public static boolean active()");
        assertTrue(at >= 0, "TrophyCompat.active() is not where this test looks for it");
        assertFalse(
            compat.substring(at, compat.indexOf('}', at))
                .contains("gtnhEnhanced"),
            "Phase 9 decided trophies do not follow integration.gtnhEnhanced; change the words first if that changes");
        assertFalse(
            read(LOOT).contains("TrmtConfig.gtnhEnhanced"),
            "The chest finds do not read integration.gtnhEnhanced; change the words first if that changes");

        String comment = line(read(CONFIG), "\"The pack personality switch.");
        assertFalse(comment.contains("no trophies"), "the gtnhEnhanced comment promises a trophy gate there is not");
        assertFalse(comment.contains("loot-table finds"), "the gtnhEnhanced comment promises a loot gate there is not");
        assertTrue(
            comment.contains("integration.trophies"),
            "the gtnhEnhanced comment should name the trophies switch");

        String readme = read("README.md");
        assertFalse(
            line(readme, "| **GregTech** |").contains("loot"),
            "README.md's GregTech row claims finds the switch does not govern");
        if (present("docs/CURSEFORGE.md")) {
            assertFalse(
                line(read("docs/CURSEFORGE.md"), "| GregTech |").contains("loot"),
                "docs/CURSEFORGE.md's GregTech row claims finds the switch does not govern");
        }
        int from = readme.indexOf("`integration.gtnhEnhanced` flips on");
        assertTrue(from >= 0, "README.md's integration paragraph is not where this test looks for it");
        int to = readme.indexOf("\n\n", from);
        String paragraph = readme.substring(from, to < 0 ? readme.length() : to);
        assertFalse(
            paragraph.contains("no trophies"),
            "README's integration paragraph promises a trophy gate there is not");
        assertFalse(
            paragraph.contains("vanilla loot"),
            "README's integration paragraph promises a loot gate there is not");
        assertTrue(
            paragraph.contains("`integration.trophies`"),
            "README's integration paragraph should name the trophies switch");
    }

    /** The body of TrophyCompat.define, so an id written anywhere else in the class is not counted. */
    private static String defined() throws IOException {
        String compat = read(COMPAT);
        int from = compat.indexOf("private static List<Trophy> define()");
        int to = from < 0 ? -1 : compat.indexOf("return trophies;", from);
        assertTrue(to >= 0, "TrophyCompat.define() is not where this test looks for it");
        return compat.substring(from, to);
    }

    private static Set<String> ids(String text, String regex) {
        Set<String> found = new TreeSet<String>();
        Matcher m = Pattern.compile(regex)
            .matcher(text);
        while (m.find()) found.add(m.group(1));
        return found;
    }

    /** The first line holding the marker, trimmed; fails rather than returning nothing. */
    private static String line(String text, String marker) {
        for (String each : text.split("\\r?\\n")) {
            if (each.contains(marker)) return each.trim();
        }
        throw new AssertionError("No line holds " + marker);
    }

    /**
     * One of the files this test compares, source or document.
     *
     * <p>
     * Which root it is resolved against depends on which it is, because the two are not always the
     * same folder. In this tree they are. In the published repository the manual and the store pages
     * describe every edition of the mod and sit at its root, while the source sits one level down
     * under the edition being built - so a single root cannot find both, and asking for one root was
     * the bug this comment replaced.
     */
    private static String read(String relative) throws IOException {
        File root = relative.startsWith("src/") ? sourceRoot() : documentRoot();
        File file = new File(root, relative);
        return new String(Files.readAllBytes(file.toPath()), Charset.forName("UTF-8"));
    }

    /** The folder holding {@code src/main/java} - this edition, wherever it is checked out. */
    private static File sourceRoot() {
        File here = new File(System.getProperty("user.dir")).getAbsoluteFile();
        for (File at = here; at != null; at = at.getParentFile()) {
            if (new File(at, "src/main/java").isDirectory()) return at;
        }
        throw new AssertionError("Could not find src/main/java above " + here);
    }

    /**
     * Whether a document this test can check is here to be checked.
     *
     * <p>
     * The store copy is instructions for whoever uploads the mod rather than anything a reader of
     * the source needs, so it is kept out of the published repository. It is checked where it lives
     * - this tree - and skipped where it does not. The manual is not optional and is never guarded
     * this way: a test that could find nothing to assert about would pass everywhere and mean
     * nothing, which is the failure this whole file exists to prevent.
     */
    private static boolean present(String relative) {
        return new File(documentRoot(), relative).isFile();
    }

    /**
     * The folder holding the manual, whatever the test is run from.
     *
     * <p>
     * Found by looking for the manual rather than for the source tree, for the reason {@link #read}
     * gives: in the published repository it is a level above the source.
     */
    private static File documentRoot() {
        File here = new File(System.getProperty("user.dir")).getAbsoluteFile();
        for (File at = here; at != null; at = at.getParentFile()) {
            if (new File(at, "README.md").isFile()) return at;
        }
        throw new AssertionError("Could not find README.md above " + here);
    }
}
