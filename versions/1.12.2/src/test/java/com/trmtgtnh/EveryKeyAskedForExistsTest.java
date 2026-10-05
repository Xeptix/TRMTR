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
 * <strong>Written after this edition shipped fourteen keys short of what it asks for, and nothing
 * noticed.</strong> A missing translation is not an error - it is a key, rendered exactly where the
 * sentence should have been - so no build fails, no log line appears, and the only way to find one is
 * to open the screen it is on. Twelve of the fourteen were the golem's block-tooltip lines, which
 * means a golem looked at through Hwyla read {@code trmtgtnh.golem.waila.idle} where it should have
 * said "Nothing to do here", for as long as the golem has existed here. The thirteenth was the chunk
 * tamper's settings screen saying {@code trmtgtnh.mode.off} for "None". They were found from the
 * 1.16.5 edition, which has this test, by asking the same question of this tree from outside it.
 *
 * <p>
 * The carry is not to blame and that is the point: 1.7.10 has all fourteen, and the carry brought
 * across the keys it was asked for. What it could not do is notice a key nobody had asked it for.
 *
 * <h2>Two shapes, because the game asks for some of these itself</h2>
 *
 * <p>
 * A key the <em>mod</em> names is in the source as a literal, and this reads them. A key the
 * <em>game</em> asks for is built from a registry name - {@code setTranslationKey("trmtgtnh.ghost")}
 * becomes {@code tile.trmtgtnh.ghost.name} - so the literal in the source is not the key, and the
 * rule below converts it before looking.
 *
 * <h2>What is not a key</h2>
 *
 * <p>
 * Two kinds of string look like keys and are not, and both are listed rather than caught by a rule -
 * a rule about shapes would quietly excuse a real key that happened to match it.
 *
 * <ul>
 * <li>A <strong>prefix</strong>, which is a key with something appended: the family, the colour, the
 * draught and the guide are all named {@code "trmtgtnh.family." + key}. The whole key only exists at
 * run time.
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
            "trmtgtnh.mode.",
            // The name on the Stout upgrade's attribute modifier, which nothing translates.
            "trmtgtnh.golem.stout",
            // The id of this mod's toggle in Waila's own settings list, which Waila names for
            // itself. A key by shape and not by use.
            "trmtgtnh.wear"));

    /**
     * A literal handed to {@code setTranslationKey}, which the game turns into a key of its own.
     *
     * <p>
     * One block does this - the ghost - and what the game asks for is {@code tile.<it>.name}. Items
     * would be {@code item.<it>.name} and are not listed here because every item in this mod names
     * its key itself.
     */
    private static final Pattern SET_BY_THE_GAME = Pattern.compile(
        "setTranslationKey\\(\"(trmtgtnh\\.[A-Za-z0-9_.]+)\"\\)");

    private static final Pattern KEY = Pattern.compile("\"(trmtgtnh\\.[A-Za-z0-9_.]+)\"");

    @Test
    void every_key_the_source_names_is_in_the_language_file() throws IOException {
        File lang = new File(
            SourceTree.mainJava()
                .getParentFile(),
            "resources/assets/trmtgtnh/lang/en_us.lang");
        assertTrue(lang.isFile(), lang.getAbsolutePath() + " is not there");
        Set<String> declared = new TreeSet<String>();
        for (String line : Files.readAllLines(lang.toPath(), StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#") && trimmed.indexOf('=') > 0) {
                declared.add(trimmed.substring(0, trimmed.indexOf('='))
                    .trim());
            }
        }

        Set<String> asked = new TreeSet<String>();
        Set<String> blocks = new TreeSet<String>();
        gather(SourceTree.mainJava(), asked, blocks);
        assertTrue(asked.size() > 100, "found " + asked.size() + " keys, which would switch this test off");

        List<String> absent = new ArrayList<String>();
        for (String key : asked) {
            if (NOT_KEYS.contains(key)) continue;
            // A block's own literal is not the key the game asks for, so it is checked in its
            // converted form below rather than as it stands.
            if (blocks.contains(key)) continue;
            if (!declared.contains(key)) absent.add(key);
        }
        for (String block : blocks) {
            String asTheGameAsks = "tile." + block + ".name";
            if (!declared.contains(asTheGameAsks)) absent.add(asTheGameAsks + "  (from setTranslationKey)");
        }

        if (!absent.isEmpty()) {
            StringBuilder said = new StringBuilder(
                "These keys are asked for in the source and are not in en_us.lang, so whatever asks "
                    + "for one gets the key itself on screen:\n");
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

    private static void gather(File folder, Set<String> into, Set<String> blocks) throws IOException {
        File[] inside = folder.listFiles();
        if (inside == null) return;
        for (File one : inside) {
            if (one.isDirectory()) {
                gather(one, into, blocks);
                continue;
            }
            if (!one.getName()
                .endsWith(".java")) {
                continue;
            }
            String text = new String(Files.readAllBytes(one.toPath()), StandardCharsets.UTF_8);
            Matcher named = SET_BY_THE_GAME.matcher(text);
            while (named.find()) {
                blocks.add(named.group(1));
            }
            Matcher found = KEY.matcher(text);
            while (found.find()) {
                into.add(found.group(1));
            }
        }
    }
}
