package com.trmtgtnh;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Something that is not a client has to work out what erodes.
 *
 * <p>
 * <strong>Written after this edition shipped a release candidate in which nothing eroded.</strong>
 * {@code SurfaceRegistry.resolve()} was reached from exactly two places: the texture stitcher, which
 * is client-only, and {@code /trmt reload}. So a client resolved a table on its way to building wear
 * sprites, a server never resolved one at all, and in single player the client adopts the server's
 * table when it joins - replacing a good table with an empty one. Every square stayed pristine and
 * {@code /trmt demonstrate} answered "Nothing is detected as erodable". On a dedicated server nobody
 * would ever have seen a path form.
 *
 * <p>
 * It survived 352 tests, a two-loader probe suite and every hour of play, because all of those drive
 * a client and a client resolves on its way to an atlas. It was found by someone making a world and
 * typing a command.
 *
 * <p>
 * So this holds the shape that was missing: the detection chain is reachable from a path that is not
 * the client's and not a reload, and both loaders wire the moment it happens. The 1.12.2 edition gets
 * this for free by resolving in its mod lifecycle, which is both sides by construction; this edition
 * has no such lifecycle and has to say so out loud.
 */
class DetectionRunsOnAServerTest {

    private static final String EVENTS = "com/trmtgtnh/server/ServerEvents.java";

    @Test
    void a_server_starting_resolves_what_erodes() throws IOException {
        String events = String.join("\n", SourceTree.lines(EVENTS));

        assertTrue(
            events.contains("public static void serverStarting("),
            "a server has to have a moment where it works out what erodes");

        int at = events.indexOf("public static void serverStarting(");
        String body = events.substring(at, Math.min(events.length(), at + 700));
        assertTrue(
            body.contains("SurfaceRegistry.resolve()"),
            "serverStarting has to resolve the surface table; without it a server has none");
        assertTrue(
            body.contains("PhysicalDecay.markSinkableBlocks()"),
            "and mark what can sink, which is the other half of the chain a reload runs");
    }

    @Test
    void both_loaders_reach_it_before_the_world_loads() throws IOException {
        String forge = read(new File(SourceTree.repoRoot(), "forge/src/main/java/com/trmtgtnh/forge/TrmtForge.java"));
        assertTrue(
            forge.contains("FMLServerAboutToStartEvent") && forge.contains("ServerEvents.serverStarting("),
            "Forge has to call it from the about-to-start event, which is before the world loads");

        String fabric = read(
            new File(SourceTree.repoRoot(), "fabric/src/main/java/com/trmtgtnh/fabric/TrmtFabric.java"));
        assertTrue(
            fabric.contains("SERVER_STARTING") && fabric.contains("ServerEvents.serverStarting("),
            "Fabric has to call it from SERVER_STARTING, for the same reason");
    }

    @Test
    void detection_is_reachable_from_somewhere_that_is_not_a_client() throws IOException {
        // The invariant that was actually broken. A call from the texture stitcher does not count -
        // that is a client building an atlas - and neither does the reload command, because a
        // feature that works only after somebody types a command is a feature that does not work.
        List<String> callers = new ArrayList<String>();
        gather(SourceTree.mainJava(), callers);
        assertFalse(callers.isEmpty(), "nothing calls resolve() at all, which cannot be right");

        List<String> serverSide = new ArrayList<String>();
        for (String caller : callers) {
            String where = caller.replace('\\', '/');
            if (where.contains("/client/")) continue;
            if (where.endsWith("ConfigReload.java")) continue;
            serverSide.add(where);
        }

        assertFalse(
            serverSide.isEmpty(),
            "every call to SurfaceRegistry.resolve() is on a client path or behind /trmt reload, so a "
                + "server would never work out what erodes: "
                + callers);
    }

    // ---- helpers -------------------------------------------------------------------------------

    private static String read(File file) throws IOException {
        assertTrue(file.isFile(), file.getAbsolutePath() + " is not there");
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static void gather(File at, List<String> into) throws IOException {
        File[] children = at.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) {
                gather(child, into);
                continue;
            }
            if (!child.getName()
                .endsWith(".java")) continue;
            for (String line : Files.readAllLines(child.toPath(), StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.startsWith("*") || trimmed.startsWith("//")) continue;
                if (trimmed.contains("SurfaceRegistry.resolve()")) {
                    into.add(child.getPath());
                    break;
                }
            }
        }
    }
}
