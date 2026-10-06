package com.trmtgtnh;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * The settings text is read by a player, so it has to be about the game they are playing.
 *
 * <p>
 * `TrmtConfig` came across whole - which is what unblocked the portable core, because the core needs
 * the settings layer - and three thousand lines of setting descriptions came with it, written for
 * 1.7.10 and 1.12.2. Most of them are about this mod and are true at any version. A handful were
 * about the game, and were wrong here: sixteen achievements that are advancements at this version, a
 * metadata suffix that picks out a variant where variants are separate blocks now, Mending described
 * as a thing the game does not have, and four map settings whose descriptions promise a road that
 * darkens as it wears when nothing here can draw one.
 *
 * <p>
 * None of that fails a build, shows up in a log, or breaks a save. It is simply read, in a settings
 * screen, by somebody deciding what to switch on - which is why it needs a fence rather than a
 * careful afternoon.
 */
class SettingsTextIsForThisEditionTest {

    private static final String FILE = "com/trmtgtnh/config/TrmtConfig.java";

    /**
     * Where the word "achievement" is still allowed, and why.
     *
     * <p>
     * The setting is named {@code achievements} and keeps that name: the settings file is
     * deliberately the other editions', so a pack moving between versions should find its edits
     * where it left them. So the identifier stays and the prose does not, and a line that explains
     * the older editions' name is allowed to use it.
     */
    private static final String[] ALLOWED = { "achievements = config.getBoolean(", "\"achievements\",",
        "public static boolean achievements", "{@link #achievements}", };

    /** A line that is talking about another edition on purpose. */
    private static final String[] NAMES_ANOTHER_EDITION = { "older edition", "1.7.10", "1.12.2", };

    @Test
    void nothing_calls_an_advancement_an_achievement() throws IOException {
        List<String> wrong = new ArrayList<String>();
        List<String> lines = SourceTree.lines(FILE);
        for (int number = 1; number <= lines.size(); number++) {
            String line = lines.get(number - 1);
            if (!line.contains("achievement")) continue;
            if (namesTheSetting(line)) continue;
            // Sentence by sentence, not line by line. A description is one string literal two
            // thousand characters long, and the one sentence that explains the older editions' name
            // does say "older editions" - so a whole-line allowance let every other mention on that
            // line through. Watched happen twice: the guard passed on text reading "no achievement
            // is ever awarded" both when the allowance was the line and when the line was tested
            // before the sentences were.
            for (String sentence : line.split("(?<=\\.) ")) {
                if (!sentence.contains("achievement")) continue;
                if (namesAnotherEdition(sentence)) continue;
                wrong.add(FILE + ":" + number + "  " + sentence.trim());
            }
        }
        if (!wrong.isEmpty()) {
            fail(
                "1.12 replaced achievements with advancements and this edition writes advancement "
                    + "JSON, so a player reading this has no achievement page to look for. The "
                    + "setting keeps its name - that is what the allowance list is for - but the "
                    + "prose has to be about this game:\n  "
                    + String.join("\n  ", wrong));
        }
    }

    /**
     * Every setting the mod says aloud it cannot act on must say so in its own description too.
     *
     * <p>
     * `sayWhatMapsCannotDo` names four, once per load, at info - which is honest and is read by
     * nobody deciding whether to switch one on. The list is read out of that method rather than
     * written here twice, so a fifth setting added to it is a test failure until its description
     * says so as well.
     */
    @Test
    void every_setting_that_does_nothing_here_says_so_where_it_is_read() throws IOException {
        List<String> lines = SourceTree.lines(FILE);
        Set<String> idle = idleSettings(lines);
        // One, and it was four. mapTracksWear and mapWearDarkening stopped being idle when the map
        // darkening was built in the reduced form the vanilla palette can carry - an entry picked for
        // being darker, rather than a colour dimmed by a fraction. desirePathHighlight stopped being
        // idle when this edition gained the JourneyMap colour proxy it had been assumed not to need:
        // JourneyMap is handed an ordinary RGB value per position, so the highlight has somewhere to
        // be applied after all. The floor is here so that a day when this test finds none at all is a
        // day it has stopped looking, not a day it passed.
        assertTrue(
            idle.size() >= 1,
            "Found " + idle.size()
                + " settings named in sayWhatMapsCannotDo and expected at least "
                + "one - this test has stopped finding them rather than stopped finding faults");

        List<String> wrong = new ArrayList<String>();
        for (String key : idle) {
            String description = descriptionNear(lines, key);
            if (description == null) {
                wrong.add(key + ": no description found to check");
            } else if (!description.startsWith("Does nothing in this edition")) {
                wrong.add(key + ": " + description.substring(0, Math.min(90, description.length())) + "...");
            }
        }
        if (!wrong.isEmpty()) {
            fail(
                "These settings do nothing in this edition - the mod says so in the log once per "
                    + "load - and their own descriptions do not begin by saying so, which is where "
                    + "somebody actually reads them:\n  "
                    + String.join("\n  ", wrong));
        }
    }

    // ------------------------------------------------------------------
    // Reading the file
    // ------------------------------------------------------------------

    /** A line of code that names the setting itself, where the old spelling is the name. */
    private static boolean namesTheSetting(String line) {
        for (String one : ALLOWED) {
            if (line.contains(one)) return true;
        }
        return false;
    }

    /** A sentence that is talking about another edition on purpose, and may use its words. */
    private static boolean namesAnotherEdition(String sentence) {
        for (String one : NAMES_ANOTHER_EDITION) {
            if (sentence.contains(one)) return true;
        }
        return false;
    }

    /** The setting names inside {@code sayWhatMapsCannotDo}, without their category prefix. */
    private static Set<String> idleSettings(List<String> lines) {
        Set<String> found = new LinkedHashSet<String>();
        Pattern named = Pattern.compile("idle\\.add\\(\"([A-Za-z]+)\\.([A-Za-z]+)\"\\)");
        boolean inside = false;
        for (String line : lines) {
            if (line.contains("private static void sayWhatMapsCannotDo()")) inside = true;
            else if (inside && line.equals("    }")) break;
            if (!inside) continue;
            Matcher each = named.matcher(line);
            while (each.find()) {
                found.add(each.group(2));
            }
        }
        return found;
    }

    /**
     * The description a setting is registered with: the first long literal within a few lines of
     * where its name is passed.
     *
     * <p>
     * By position rather than by argument, because the overloads disagree about where the
     * description goes - a boolean takes name, category, default, description and a double takes
     * category, name, default, description, minimum, maximum. A long literal near the name is the
     * description in every one of them, and a short one is a category or a key.
     */
    private static String descriptionNear(List<String> lines, String key) {
        for (int at = 0; at < lines.size(); at++) {
            if (!lines.get(at)
                .trim()
                .equals("\"" + key + "\",")) {
                continue;
            }
            for (int look = at + 1; look < Math.min(at + 12, lines.size()); look++) {
                String text = literal(lines.get(look));
                if (text != null && text.length() > 80) return text;
            }
        }
        return null;
    }

    /** The one string literal on a line, if the line is a literal and a comma and nothing else. */
    private static String literal(String line) {
        String trimmed = line.trim();
        if (!trimmed.startsWith("\"")) return null;
        int end = trimmed.lastIndexOf('"');
        if (end <= 0) return null;
        return trimmed.substring(1, end);
    }
}
