package com.trmtgtnh;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Every translation key this edition's own code asks for has to be in the language file.
 *
 * <p>
 * <strong>{@code LanguageIsWholeTest} asks the other question and both are needed.</strong> That one
 * reads the other edition's file and insists every line of it is here, which catches a line dropped
 * in the carry. This one reads the source and insists every key it names is here, which catches a
 * line that was never there - and those are different failures with different causes.
 *
 * <p>
 * It was written because the second kind had happened twelve times over without anything noticing.
 * The golem's block-tooltip lines name twelve keys; the 1.7.10 edition has all twelve and the 1.12.2
 * edition has none of them, so the carry faithfully produced a tooltip that reads
 * {@code trmtgtnh.golem.waila.idle} where it should read "Nothing to do here". Nothing failed,
 * nothing was logged, and the only way it surfaced was a spike printing the lines into a log for
 * somebody to look at.
 *
 * <h2>What is not a key</h2>
 *
 * <p>
 * Two kinds of string in the source look like keys and are not, and both are listed rather than
 * guessed at by a rule - a rule about shapes would quietly excuse a real key that happened to match
 * it.
 *
 * <ul>
 * <li>A <strong>prefix</strong>, which is a key with something appended: the family, the colour, the
 * draught and the guide are all named {@code "trmtgtnh.family." + key}. The whole key only exists at
 * run time, and the file holding every one of them is what {@code LanguageIsWholeTest} guards.
 * <li>A <strong>name that is not text</strong>: an attribute modifier is given one, and the game
 * never translates it. It looks exactly like a key and is one only by convention.
 * </ul>
 */
class EveryKeyAskedForExistsTest {

    /** Strings that look like keys and are not. See the class note; each is here on purpose. */
    private static final Set<String> NOT_KEYS = new TreeSet<String>(
        Arrays.asList(
            // Prefixes, completed at run time.
            "trmtgtnh.draught.",
            "trmtgtnh.family.",
            "trmtgtnh.golem.upgrade.",
            "trmtgtnh.guide.",
            "trmtgtnh.light.colour.",
            // The name on the Stout upgrade's attribute modifier, which nothing translates.
            "trmtgtnh.golem.stout"));

    private static final Pattern KEY = Pattern.compile("\"(trmtgtnh\\.[A-Za-z0-9_.]+)\"");

    @Test
    void every_key_the_source_names_is_in_the_language_file() throws IOException {
        File lang = new File(
            SourceTree.mainJava()
                .getParentFile(),
            "resources/assets/trmtgtnh/lang/en_us.json");
        assertTrue(lang.isFile(), lang.getAbsolutePath() + " is not there");
        String text = new String(Files.readAllBytes(lang.toPath()), StandardCharsets.UTF_8);

        Set<String> asked = new TreeSet<String>();
        gather(SourceTree.mainJava(), asked);
        // And both loaders' own source, which shares this language file and was never read: a key named only
        // in the Forge or the Fabric module could go missing with nothing to say so. Found while planning the
        // update notice, which put a loader's own words in each module.
        for (String loader : new String[] { "forge", "fabric" }) {
            File module = new File(SourceTree.repoRoot(), loader + "/src/main/java");
            assertTrue(module.isDirectory(), module.getAbsolutePath() + " is not there, so its keys go unread");
            gather(module, asked);
        }
        assertTrue(asked.size() > 100, "found " + asked.size() + " keys, which would switch this test off");

        List<String> absent = new ArrayList<String>();
        for (String key : asked) {
            if (NOT_KEYS.contains(key)) continue;
            // By text rather than by parsing: a key is a quoted name in a JSON object and the only
            // place that exact quoted string appears is as one. Keeps this test free of a JSON
            // library, which is the same reason the drift check reads bytes.
            if (!text.contains("\"" + key + "\"")) absent.add(key);
        }

        if (!absent.isEmpty()) {
            StringBuilder said = new StringBuilder(
                "These keys are asked for in the source and are not in en_us.json, so whatever asks for "
                    + "one gets the key itself on screen:\n");
            for (String one : absent) {
                said.append("  ")
                    .append(one)
                    .append('\n');
            }
            said.append(
                "\nIf one of these is not really a key - a prefix something is appended to, or a name "
                    + "the game never translates - add it to NOT_KEYS with a line saying which.");
            fail(said.toString());
        }
    }

    private static void gather(File folder, Set<String> into) throws IOException {
        File[] inside = folder.listFiles();
        if (inside == null) return;
        for (File one : inside) {
            if (one.isDirectory()) {
                gather(one, into);
                continue;
            }
            if (!one.getName()
                .endsWith(".java")) {
                continue;
            }
            Matcher found = KEY.matcher(new String(Files.readAllBytes(one.toPath()), StandardCharsets.UTF_8));
            while (found.find()) {
                into.add(found.group(1));
            }
        }
    }
}
