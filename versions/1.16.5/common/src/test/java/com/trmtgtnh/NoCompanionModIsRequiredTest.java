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
 * mandatory does not degrade, it stops the game loading with a screen naming a mod the player never
 * asked for.
 *
 * <p>
 * Asked for by name on 2026-10-06 - "we shouldn't hard require any minimap mod" - and true at the
 * time, which is exactly why it is worth a fence. Nothing here was broken; this is what keeps it
 * that way.
 *
 * <p>
 * The hard dependencies are listed rather than merely filtered, so adding one is a decision somebody
 * makes in this file rather than a line that slips into a manifest. Cloth Config is on the list and
 * is the one deliberate exception, taken knowingly for this edition's config screen.
 */
class NoCompanionModIsRequiredTest {

    /**
     * Everything this edition may require, and nothing else.
     *
     * <p>
     * The loader and the game itself, and Cloth, which this edition's config screen is built on. That
     * last one is a decision already taken and written down; it is here so that it stays the only one.
     */
    private static final List<String> ALLOWED = Arrays.asList(
        "minecraft",
        "forge",
        "fabric",
        "fabricloader",
        "fabric-api",
        "cloth-config",
        "cloth-config2",
        "java");

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
        "oculus",
        "iris",
        "canvas",
        "modmenu");

    @Test
    void the_forge_manifest_requires_nothing_but_the_loader() throws IOException {
        File at = new File(SourceTree.repoRoot(), "forge/src/main/resources/META-INF/mods.toml");
        assertTrue(at.isFile(), at.getAbsolutePath() + " is not there");
        String text = new String(Files.readAllBytes(at.toPath()), Charset.forName("UTF-8"));

        // Each dependency block is a modId and, somewhere under it, whether it is mandatory. Read as
        // blocks rather than by line, because the two are several lines apart and a file with three
        // of them would otherwise pair the wrong ones up.
        List<String> required = new ArrayList<String>();
        for (String block : text.split("\\[\\[dependencies\\.")) {
            Matcher id = Pattern.compile("modId\\s*=\\s*\"([^\"]+)\"")
                .matcher(block);
            if (!id.find()) continue;
            if (Pattern.compile("mandatory\\s*=\\s*true")
                .matcher(block)
                .find()) {
                required.add(id.group(1)
                    .toLowerCase(Locale.ROOT));
            }
        }
        check("forge/META-INF/mods.toml", required);
    }

    @Test
    void the_fabric_manifest_requires_nothing_but_the_loader() throws IOException {
        File at = new File(SourceTree.repoRoot(), "fabric/src/main/resources/fabric.mod.json");
        assertTrue(at.isFile(), at.getAbsolutePath() + " is not there");
        String text = new String(Files.readAllBytes(at.toPath()), Charset.forName("UTF-8"));

        // Only "depends" is a hard requirement. "recommends" and "suggests" are the loader's own way
        // of saying what this test is about, and are deliberately left alone.
        String depends = objectAfter(text, "\"depends\"");
        if (depends == null) {
            fail("fabric.mod.json has no depends block at all, which this test cannot read as a pass");
        }
        List<String> required = new ArrayList<String>();
        Matcher key = Pattern.compile("\"([^\"]+)\"\\s*:")
            .matcher(depends);
        while (key.find()) {
            required.add(key.group(1)
                .toLowerCase(Locale.ROOT));
        }
        check("fabric/fabric.mod.json", required);
    }

    private static void check(String where, List<String> required) {
        assertTrue(
            !required.isEmpty(),
            where + " declares no hard dependency at all, not even the loader - which means this test "
                + "has stopped reading the file rather than stopped finding faults");

        List<String> companions = new ArrayList<String>();
        List<String> unexpected = new ArrayList<String>();
        for (String one : required) {
            if (COMPANIONS.contains(one)) companions.add(one);
            else if (!ALLOWED.contains(one)) unexpected.add(one);
        }

        if (!companions.isEmpty()) {
            fail(
                where + " requires " + companions
                    + ". This mod writes into those when they are installed and must say nothing when "
                    + "they are not; a mandatory line stops the game loading for everybody else.");
        }
        assertEquals(
            new TreeSet<String>(),
            new TreeSet<String>(unexpected),
            where + " requires something this test has not been told about. A new hard dependency is a "
                + "decision: add it to ALLOWED here, with the reason, or take it out of the manifest.");
    }

    /** The {@code { ... }} that follows a key, or null. */
    private static String objectAfter(String text, String key) {
        int at = text.indexOf(key);
        if (at < 0) return null;
        int open = text.indexOf('{', at);
        if (open < 0) return null;
        int depth = 0;
        for (int i = open; i < text.length(); i++) {
            char here = text.charAt(i);
            if (here == '{') depth++;
            else if (here == '}') {
                depth--;
                if (depth == 0) return text.substring(open + 1, i);
            }
        }
        return null;
    }
}
