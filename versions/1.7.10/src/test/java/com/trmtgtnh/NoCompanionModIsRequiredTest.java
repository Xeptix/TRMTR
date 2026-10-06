package com.trmtgtnh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * The mod works with the companion mods and refuses to need any of them.
 *
 * <p>
 * Tooltips, minimaps, quest books and trophies are all things this mod writes into when they are
 * there and says nothing about when they are not. That is a promise to anybody who installs it
 * without them, and it is the kind of promise a dependency line breaks silently: a mod declared
 * required does not degrade, it stops the game loading with a screen naming a mod the player never
 * asked for.
 *
 * <p>
 * Asked for by name on 2026-10-06 - "we shouldn't hard require any minimap mod" - and true at the
 * time, which is exactly why it is worth a fence. Nothing here was broken; this is what keeps it
 * that way. The two ports carry the same test against their own manifests.
 *
 * <p>
 * This edition says it in two places and both are read: {@code mcmod.info}, which is where it is
 * said today and says nothing at all, and the {@code @Mod} annotation, which is where it would be
 * said if anybody added one. A test that read only the empty one would pass for ever.
 */
class NoCompanionModIsRequiredTest {

    /**
     * Everything this edition may require, and nothing else.
     *
     * <p>
     * Short, because this edition requires nothing: its mixin loader is UniMixins, which is a tweaker
     * with no mod id to name, so there is nothing for Forge to be told about. Said here rather than
     * left implied, because "the list is empty" and "the list is missing" look alike from a distance.
     */
    private static final List<String> ALLOWED = Arrays.asList("minecraft", "forge", "fml");

    /** The mods this one integrates with, none of which it may need. */
    private static final List<String> COMPANIONS = Arrays.asList(
        "waila",
        "hwyla",
        "jade",
        "wthit",
        "theoneprobe",
        "journeymap",
        "xaerominimap",
        "xaeroworldmap",
        "xaerominimapfair",
        "chisel",
        "gregtech",
        "betterquesting",
        "amazingtrophies",
        "optifine",
        "angelica",
        "oculus",
        "iris",
        "canvas");

    @Test
    void the_manifest_requires_nothing() throws IOException {
        File at = new File(
            SourceTree.mainJava()
                .getParentFile(),
            "resources/mcmod.info");
        assertTrue(at.isFile(), at.getAbsolutePath() + " is not there");
        String text = new String(Files.readAllBytes(at.toPath()), Charset.forName("UTF-8"));

        Matcher block = Pattern.compile("\"dependencies\"\\s*:\\s*\\[([^\\]]*)\\]")
            .matcher(text);
        assertTrue(
            block.find(),
            "mcmod.info has no dependencies list at all, which this test cannot read as a pass - it "
                + "cannot tell that from having stopped reading the file");

        List<String> required = new ArrayList<String>();
        Matcher named = Pattern.compile("\"([^\"]+)\"")
            .matcher(block.group(1));
        while (named.find()) {
            required.add(
                named.group(1)
                    .toLowerCase(Locale.ROOT));
        }
        check("mcmod.info", required);
    }

    @Test
    void the_mod_annotation_requires_nothing() throws IOException {
        String source = String.join("\n", SourceTree.lines("com/trmtgtnh/Trmt.java"));

        List<String> required = new ArrayList<String>();
        Matcher declared = Pattern.compile("dependencies\\s*=\\s*\"([^\"]*)\"")
            .matcher(source);
        while (declared.find()) {
            for (String entry : declared.group(1)
                .split(";")) {
                String one = entry.trim()
                    .toLowerCase(Locale.ROOT);
                if (!one.startsWith("required")) continue;
                int colon = one.indexOf(':');
                if (colon < 0) continue;
                String id = one.substring(colon + 1)
                    .trim();
                int cut = id.indexOf('@');
                if (cut > 0) id = id.substring(0, cut);
                cut = id.indexOf('[');
                if (cut > 0) id = id.substring(0, cut);
                if (!id.isEmpty()) required.add(id.trim());
            }
        }
        check("the @Mod annotation", required);
    }

    private static void check(String where, List<String> required) {
        List<String> companions = new ArrayList<String>();
        List<String> unexpected = new ArrayList<String>();
        for (String one : required) {
            if (COMPANIONS.contains(one)) companions.add(one);
            else if (!ALLOWED.contains(one)) unexpected.add(one);
        }

        if (!companions.isEmpty()) {
            fail(
                where + " requires "
                    + companions
                    + ". This mod writes into those when they are installed and must say nothing when "
                    + "they are not; a required line stops the game loading for everybody else.");
        }
        assertEquals(
            new TreeSet<String>(),
            new TreeSet<String>(unexpected),
            where + " requires something this test has not been told about. A new hard dependency is a "
                + "decision: add it to ALLOWED here, with the reason, or take it out.");
    }
}
