package com.trmtgtnh;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;

/**
 * A tooltip can only show what somebody asked for.
 *
 * <p>
 * {@code InspectionCache} holds the server's reply about one position and {@code WailaCompat} reads
 * it, and between the two sat a poll that no tick performed. So {@code has(x, y, z)} was false for
 * every position for the whole of this edition's life, and a tooltip over worn ground showed its wear
 * - which is read from the record the client already has - but never its reinforcement, its ward or
 * how far along its run it was, all three of which only the server knows.
 *
 * <p>
 * Found by the unwired sweep, like the crafting trigger beside it: two public methods, both called by
 * nobody, both of them a feature that compiles and ships and does not exist.
 */
class TooltipsAskTheServerTest {

    @Test
    void the_crosshair_is_asked_about() throws IOException {
        String client = String.join("\n", SourceTree.lines("com/trmtgtnh/client/ClientSide.java"));

        // The call, not the declaration. Checking for the bare name passes on a file that declares
        // the method and never calls it, which is precisely the fault being guarded against - and is
        // what the first draft of this test did, proved by removing the call and watching it pass.
        assertTrue(
            client.contains("        askAboutCrosshair();"),
            "something has to ask, once a tick, about the block under the crosshair");
        assertTrue(
            client.contains("InspectionCache.poll("),
            "and the asking is the poll - without it the cache is never filled and every reply-backed "
                + "line of a tooltip is missing");
        assertTrue(
            client.contains("InspectionReach.asks("),
            "gated as the 1.12.2 edition gates it, so a packet goes only when something will read the "
                + "answer and the square is worth asking about");
    }

    @Test
    void the_reader_says_it_is_there_rather_than_being_guessed_at() throws IOException {
        String cache = String.join("\n", SourceTree.lines("com/trmtgtnh/client/InspectionCache.java"));
        assertTrue(
            cache.contains("public static void noteReader()"),
            "whether anything reads the reply is the gate on asking at all");

        // Each tooltip mod says so itself as it registers, which is exact - the 1.12.2 edition asks
        // whether Waila is installed, and this edition has two different readers on two loaders.
        for (String plugin : new String[] { "forge/src/main/java/com/trmtgtnh/forge/JadeTooltip.java",
            "fabric/src/main/java/com/trmtgtnh/fabric/WthitTooltip.java" }) {
            assertTrue(
                read(plugin).contains("InspectionCache.noteReader()"),
                plugin + " has to say it is there, or nothing is ever asked for it to show");
        }
    }

    private static String read(String relative) throws IOException {
        File at = new File(SourceTree.repoRoot(), relative);
        assertTrue(at.isFile(), at.getAbsolutePath() + " is not there");
        return new String(Files.readAllBytes(at.toPath()), StandardCharsets.UTF_8);
    }
}
