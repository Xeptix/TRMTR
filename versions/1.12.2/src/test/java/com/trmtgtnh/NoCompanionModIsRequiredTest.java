package com.trmtgtnh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
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
 * {@code required-after} does not degrade, it stops the game loading with a screen naming a mod the
 * player never asked for.
 *
 * <p>
 * Asked for by name on 2026-10-06 - "we shouldn't hard require any minimap mod" - and true at the
 * time, which is exactly why it is worth a fence. Nothing here was broken; this is what keeps it
 * that way.
 *
 * <p>
 * The hard dependencies are listed rather than merely filtered, so adding one is a decision somebody
 * makes in this file rather than a line that slips into an annotation. MixinBooter is on the list,
 * named deliberately so that a pack without it is told rather than left to wonder.
 */
class NoCompanionModIsRequiredTest {

    /** Everything this edition may require, and nothing else. */
    private static final List<String> ALLOWED = Arrays.asList("minecraft", "forge", "fml", "mixinbooter");

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
        "canvas");

    @Test
    void the_mod_annotation_requires_nothing_but_the_loader() throws IOException {
        String source = String.join("\n", SourceTree.lines("com/trmtgtnh/Trmt.java"));

        // Forge's own grammar: entries joined by semicolons, each a verb and a mod id, and only the
        // ones that say "required" stop a launch. "after" and "before" are ordering and are not what
        // this is about.
        List<String> required = new ArrayList<String>();
        Matcher declared = Pattern.compile("dependencies\\s*=\\s*\"([^\"]*)\"")
            .matcher(source);
        boolean found = false;
        while (declared.find()) {
            found = true;
            for (String entry : declared.group(1)
                .split(";")) {
                String one = entry.trim()
                    .toLowerCase(Locale.ROOT);
                if (!one.startsWith("required")) continue;
                int colon = one.indexOf(':');
                if (colon < 0) continue;
                String id = one.substring(colon + 1)
                    .trim();
                // A version range may follow the id in brackets.
                int bracket = id.indexOf('@');
                if (bracket > 0) id = id.substring(0, bracket);
                bracket = id.indexOf('[');
                if (bracket > 0) id = id.substring(0, bracket);
                if (!id.isEmpty()) required.add(id.trim());
            }
        }

        assertTrue(
            found,
            "Trmt.java declares no dependencies attribute at all. That may be correct - this edition "
                + "has had one since MixinBooter was named - but this test cannot tell the difference "
                + "between 'requires nothing' and 'this test has stopped reading the file', so the "
                + "attribute is expected to be present even when it is short.");

        List<String> companions = new ArrayList<String>();
        List<String> unexpected = new ArrayList<String>();
        for (String one : required) {
            if (COMPANIONS.contains(one)) companions.add(one);
            else if (!ALLOWED.contains(one)) unexpected.add(one);
        }

        if (!companions.isEmpty()) {
            fail(
                "Trmt.java requires " + companions
                    + ". This mod writes into those when they are installed and must say nothing when "
                    + "they are not; a required line stops the game loading for everybody else.");
        }
        assertEquals(
            new TreeSet<String>(),
            new TreeSet<String>(unexpected),
            "Trmt.java requires something this test has not been told about. A new hard dependency is "
                + "a decision: add it to ALLOWED here, with the reason, or take it out of the "
                + "annotation.");
    }
}
