package com.trmtgtnh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The other seam: where the running server is, asked without naming a loader.
 *
 * <p>
 * The settings layer asks this to decide whether there is anybody to tell about a change. Forge can
 * answer from a static call; Fabric has to catch the server as it starts and let it go as it stops.
 * Neither of those belongs in a shared class, so both answer through here.
 *
 * <p>
 * What can be checked without a game is the part that actually goes wrong: that an unwired seam says
 * "nothing is running" rather than throwing, and that a wired one is really consulted each time
 * rather than once and cached. A cached answer would be wrong in the one case this exists for - a
 * single-player world being closed - and would look right in every other.
 *
 * <p>
 * The server itself is never built here. {@code MinecraftServer} cannot be constructed outside a
 * game, so the host returns null and the test counts the asking instead. That is enough: what is
 * being guarded is the plumbing, and the decision about whether a server is <em>running</em> rather
 * than merely present is made inside each loader module, where it can be.
 */
class TrmtHostTest {

    @AfterEach
    void leaveNothingBehind() {
        Trmt.forgetHost();
    }

    @Test
    void says_nothing_is_running_before_a_loader_has_spoken() {
        Trmt.forgetHost();
        assertNull(Trmt.runningServer(), "unwired must be null rather than an exception");
    }

    @Test
    void asks_the_host_every_single_time() {
        int[] asked = { 0 };
        Trmt.useHost(() -> {
            asked[0]++;
            return null;
        });

        Trmt.runningServer();
        Trmt.runningServer();
        Trmt.runningServer();
        assertEquals(3, asked[0], "the answer must never be cached: a world can close between two asks");
    }

    @Test
    void forgetting_puts_it_back_to_nothing_running() {
        Trmt.useHost(() -> null);
        Trmt.forgetHost();
        assertNull(Trmt.runningServer());
    }

    @Test
    void the_mod_is_named_the_same_as_the_build_says() throws IOException {
        // The older editions take these from a generated Tags class that their buildscripts write;
        // this edition writes them out by hand, so the one thing that can go wrong is disagreeing
        // with gradle.properties - which is what names the jar, the mod id in both loaders' metadata
        // and the settings file. Read from the file rather than compared against a second copy of the
        // string, because a second copy would agree with itself for ever.
        Properties build = new Properties();
        File file = buildProperties();
        InputStream open = new FileInputStream(file);
        try {
            build.load(open);
        } finally {
            open.close();
        }

        assertEquals(build.getProperty("mod_id"), Trmt.MODID, "Trmt.MODID disagrees with " + file);
        assertEquals(build.getProperty("mod_name"), Trmt.NAME, "Trmt.NAME disagrees with " + file);
    }

    /** The root {@code gradle.properties}, found by walking up as {@link SourceTree} does. */
    private static File buildProperties() {
        File here = new File(System.getProperty("user.dir"));
        for (File at = here; at != null; at = at.getParentFile()) {
            File candidate = new File(at, "gradle.properties");
            if (candidate.isFile()) {
                Properties held = new Properties();
                try {
                    InputStream open = new FileInputStream(candidate);
                    try {
                        held.load(open);
                    } finally {
                        open.close();
                    }
                } catch (IOException unreadable) {
                    continue;
                }
                // The loader modules have a gradle.properties of their own holding one line, so the
                // first one found walking up is not necessarily the right one.
                if (held.getProperty("mod_id") != null) return candidate;
            }
        }
        throw new IllegalStateException(
            "Could not find the gradle.properties that names the mod, from " + here.getAbsolutePath());
    }
}
