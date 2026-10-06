package com.trmtgtnh;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.Test;

/**
 * A setting that names its valid values is chosen from, not typed into.
 *
 * <p>
 * {@code ConfigFile.Setting} carries {@code getValidValues()} and {@code requiresMcRestart()} in all
 * three editions. The 1.7.10 and 1.12.2 screens pass both to Forge's own config GUI, which turns the
 * first into a button that cycles the allowed values and the second into a note beside the entry.
 *
 * <p>
 * This edition's screen is Cloth rather than Forge's - the one deliberate added dependency - and when
 * it was written neither was carried across. Every setting with a fixed set of values was a free text
 * field: a wear pattern could be typed as anything at all and the screen would take it, where the
 * other two editions would not let you choose a pattern that does not exist. A setting needing a
 * restart looked exactly like one that did not.
 *
 * <p>
 * Found by {@code tools/unwired.py} on 2026-10-06, which is what that sweep is for: both methods were
 * present, public and called by nobody. Class presence is not wiring.
 */
class ConfigScreenOffersWhatTheSettingSaysTest {

    private static final String SCREEN = "common/src/main/java/com/trmtgtnh/client/gui/ConfigScreen.java";

    @Test
    void a_setting_with_valid_values_is_offered_as_a_chooser() throws IOException {
        String source = read();
        assertTrue(
            source.contains("setting.getValidValues()"),
            SCREEN + " never asks a setting for its valid values, so a setting that has them is drawn "
                + "as a text box and anything at all can be typed into it");
        assertTrue(
            source.contains("entries.startSelector("),
            "and the values have to become a chooser - asking for them and then ignoring them is the "
                + "same screen with an extra call in it");
    }

    @Test
    void a_setting_that_needs_a_restart_says_so() throws IOException {
        String source = read();
        assertTrue(
            source.contains("setting.requiresMcRestart()"),
            SCREEN + " never asks whether a setting needs a restart, so one that does looks exactly "
                + "like one that does not");
        assertTrue(
            source.contains(".requireRestart("),
            "and the answer has to reach Cloth, or nothing is shown for it");
    }

    /**
     * A setting that declares a human name is shown by it.
     *
     * <p>
     * Three do, and the other two editions show all three, because they hand the key to Forge's
     * screen and Forge looks it up. This screen is not Forge's: the translations were shipped in
     * {@code en_us.json} and nothing read them, so the three settings a player is most likely to go
     * looking for were labelled by their keys. Found by the same sweep as the two guards above.
     */
    @Test
    void a_setting_that_declares_a_name_is_shown_by_it() throws IOException {
        String source = read();
        assertTrue(
            source.contains("setting.getLanguageKey()"),
            SCREEN + " never asks a setting for its language key, so a setting that declares a human "
                + "name is labelled by its key instead - 'Show erosion' where both older editions "
                + "say 'Show worn paths'");
        assertTrue(
            source.contains("Translate.get(key)"),
            "and the key has to be looked up, or asking for it changes nothing");
        // The call site, and not only the method: a helper that asks the right question and is
        // called by nobody is the fault this whole class is about, one level further in.
        assertTrue(
            source.contains("Component name = named(setting);"),
            SCREEN + " does not name its entries with named(setting), so whatever that method asks "
                + "the setting is not what the screen shows");
    }

    /**
     * Every declared key has something to look up.
     *
     * <p>
     * The screen falls back to the spaced-out key when a translation is missing, which is the right
     * thing to do and also silent: a key declared and never translated looks exactly like the fault
     * the test above was written for. So the declaration and the translation are checked together,
     * against the shipped language file.
     */
    @Test
    void every_declared_language_key_is_translated() throws IOException {
        String config = textOf(CONFIG);
        String lang = textOf(LANG);

        java.util.regex.Matcher declared = java.util.regex.Pattern
            .compile("setLanguageKey\\(\"([^\"]+)\"\\)")
            .matcher(config);
        int found = 0;
        while (declared.find()) {
            found++;
            String key = declared.group(1);
            assertTrue(
                lang.contains("\"" + key + "\""),
                CONFIG + " declares the language key " + key + " and " + LANG + " has no entry for it, "
                    + "so that setting is labelled by its key and the declaration does nothing");
        }
        assertTrue(found > 0, CONFIG + " declares no language keys at all, which both older editions do");
    }

    private static final String CONFIG = "common/src/main/java/com/trmtgtnh/config/TrmtConfig.java";

    private static final String LANG = "common/src/main/resources/assets/trmtgtnh/lang/en_us.json";

    private static String textOf(String relative) throws IOException {
        java.io.File at = new java.io.File(SourceTree.repoRoot(), relative);
        assertTrue(at.isFile(), at.getAbsolutePath() + " is not there");
        return new String(
            java.nio.file.Files.readAllBytes(at.toPath()),
            java.nio.charset.Charset.forName("UTF-8"));
    }

    private static String read() throws IOException {
        java.io.File at = new java.io.File(SourceTree.repoRoot(), SCREEN);
        assertTrue(at.isFile(), at.getAbsolutePath() + " is not there");
        return new String(
            java.nio.file.Files.readAllBytes(at.toPath()),
            java.nio.charset.Charset.forName("UTF-8"));
    }
}
