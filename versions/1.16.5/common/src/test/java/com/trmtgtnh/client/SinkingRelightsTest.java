package com.trmtgtnh.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.trmtgtnh.SourceTree;
import com.trmtgtnh.erosion.ErosionState;
import com.trmtgtnh.surface.SurfaceFamily;

/**
 * A painted square that sinks is re-lit.
 *
 * <p>
 * A ghost stops all light until its ground sinks and none once it has, read from the record rather than
 * the block. The 1.7.10 edition swaps a sinking ghost for its separate sunken variant, and a block that
 * changes is always re-lit; one ghost standing in for both never changes block, so the change has to be
 * told or the cell keeps the darkness it had while it was whole. Found on 2026-10-07: Rubidium draws a
 * sunken top from that cell, and the second demonstrate yard came out darker the deeper it was worn, on
 * that renderer alone.
 */
class SinkingRelightsTest {

    @Test
    void only_crossing_into_or_out_of_sunk_moves_the_answer() {
        short none = ErosionState.NONE;
        short worn = ErosionState.pack(SurfaceFamily.DIRT, 3, 0);
        short first = ErosionState.pack(SurfaceFamily.DIRT, 4, 1);
        short sunk = ErosionState.pack(SurfaceFamily.DIRT, 6, 2);
        short deeper = ErosionState.pack(SurfaceFamily.DIRT, 9, 5);

        assertEquals(0, ErosionState.sinkOf(worn), "the worn record this test leans on has sunk");
        assertEquals(1, ErosionState.sinkOf(first), "the one-pixel record this test leans on is not one pixel down");
        assertTrue(ErosionState.sinkOf(sunk) > 0, "the sunk record this test leans on has not sunk");

        assertTrue(ClientOverlay.lightAnswerMoved(worn, first), "the first pixel of sinking no longer re-lights the square");
        assertTrue(ClientOverlay.lightAnswerMoved(worn, sunk), "sinking no longer re-lights the square");
        assertTrue(ClientOverlay.lightAnswerMoved(sunk, worn), "rising out of a rut no longer re-lights it");
        assertTrue(ClientOverlay.lightAnswerMoved(sunk, none), "a sunken square cleared no longer re-lights");
        assertFalse(ClientOverlay.lightAnswerMoved(sunk, deeper), "sinking further re-lights for nothing");
        assertFalse(ClientOverlay.lightAnswerMoved(none, worn), "wear that has not sunk re-lights for nothing");
    }

    @Test
    void both_ways_a_record_arrives_ask_and_the_answer_reaches_the_light() throws IOException {
        String source = String.join("\n", SourceTree.lines("com/trmtgtnh/client/ClientOverlay.java"));
        assertTrue(
            body(source, "handleDelta").contains("relightIfMoved("),
            "a record arriving alone never re-lights the square it moved");
        assertTrue(
            body(source, "handleChunkErosion").contains("relightIfMoved("),
            "a chunk's records arriving together never re-light the squares they moved");
        assertTrue(
            body(source, "relightIfMoved").replaceAll("\\s+", "").contains(".getLightEngine().checkBlock(pos)"),
            "the re-light is asked for and never reaches the light engine");
    }

    /**
     * And re-lit again whenever the server's light for its column arrives.
     *
     * <p>
     * Found the same day by the harness's yard census, when the fix above cured Rubidium in one run and
     * not the next: this version sends light in packets of its own, a packet replaces each section's
     * light whole, and the server lights the real block - so every sunk square went back to the
     * darkness of a whole block of earth as the server's light landed, 302 of 328 in one run.
     */
    @Test
    void the_server_light_landing_relights_every_square_that_answers_its_own_way() {
        short worn = ErosionState.pack(SurfaceFamily.DIRT, 3, 0);
        short sunk = ErosionState.pack(SurfaceFamily.DIRT, 6, 2);
        assertTrue(ClientOverlay.answersLightOwnWay(sunk, 0), "a sunk square is left with the server's darkness");
        assertTrue(ClientOverlay.answersLightOwnWay(worn, 7), "a glowing square is left without its glow");
        assertFalse(ClientOverlay.answersLightOwnWay(worn, 0), "a whole worn square, lit as the server lit it, is re-lit for nothing");
    }

    @Test
    void the_server_light_is_let_in_before_the_squares_are_told() throws IOException {
        // In each loader's module, because it names a game method - see CommonMixinsNeedNoRefmapTest.
        for (String loader : new String[] { "forge", "fabric" }) {
            String mixins = new String(
                java.nio.file.Files.readAllBytes(
                    new java.io.File(SourceTree.repoRoot(), loader + "/src/main/resources/trmtgtnh-" + loader + ".mixins.json")
                        .toPath()),
                java.nio.charset.StandardCharsets.UTF_8).replaceAll("\\s+", "");
            String client = mixins.substring(mixins.indexOf("\"client\":["));
            assertTrue(
                client.substring(0, client.indexOf(']')).contains("\"MixinServerLightArrives\""),
                "the hook on the server's light packet is not applied on the " + loader + " client");
            String hook = body(
                new String(
                    java.nio.file.Files.readAllBytes(
                        new java.io.File(
                            SourceTree.repoRoot(),
                            loader + "/src/main/java/com/trmtgtnh/" + loader + "/mixin/MixinServerLightArrives.java").toPath()),
                    java.nio.charset.StandardCharsets.UTF_8),
                "trmt\\$relightPainted").replaceAll("\\s+", "");
            assertTrue(
                hook.contains("ClientOverlay.serverLightArrived(packet.getX(),packet.getZ())"),
                "the server's light arrives on " + loader + " and nothing re-lights the squares painted in its column: " + hook);
        }
        String overlay = String.join("\n", SourceTree.lines("com/trmtgtnh/client/ClientOverlay.java"));
        assertTrue(
            body(overlay, "serverLightArrived").replaceAll("\\s+", "")
                .contains("relightPainted(chunkX,chunkZ)"),
            "the server's light arrives and the column's painted squares are never re-lit");
        String arrived = body(overlay, "relightPainted").replaceAll("\\s+", "");
        int letIn = arrived.indexOf("engine.runUpdates(Integer.MAX_VALUE,true,true)");
        int told = arrived.indexOf("engine.checkBlock(pos)");
        assertTrue(letIn >= 0, "the queued server light is never let in before the squares are told");
        assertTrue(told >= 0, "the squares in the column are never told to the light engine");
        assertTrue(
            letIn < told,
            "the squares are told before the server's light is let in, so the engine weighs them against the "
                + "light about to be replaced and the server's darkness lands on top");
        assertTrue(arrived.contains("answersLightOwnWay("), "every square in the column is re-lit, not only those that need it");
    }

    /**
     * And the painter asks the same, the moment it has painted a column.
     *
     * <p>
     * Found on 2026-10-07, after the hook above: the server sends a chunk's light just ahead of the chunk
     * and its wear just after, so the light packet arrived with nothing yet painted to re-light, and the
     * paint's own re-light was weighed against the light still queued. A dev run of the first yard alone
     * came back with 142 of 213 sunk squares dark. Asked inside the branch that painted something, and
     * before the redraw, so the mesh is built against the light the squares end up with.
     */
    @Test
    void a_column_painted_ahead_of_its_light_is_relit_by_the_painter() throws IOException {
        String paint = body(String.join("\n", SourceTree.lines("com/trmtgtnh/client/OverlayPainter.java")), "paintChunk")
            .replaceAll("\\s+", "");
        int painted = paint.indexOf("if(touched>0){");
        int relit = paint.indexOf("ClientOverlay.relightPainted(chunkX,chunkZ)");
        int redrawn = paint.indexOf("redraw((chunkX<<4)");
        assertTrue(painted >= 0 && relit > painted, "the painter never re-lights a column it has painted: " + paint);
        assertTrue(redrawn > relit, "the column is redrawn before it is re-lit, so its mesh carries the old light");
    }

    /** One method's body by its name, comments left out. */
    private static String body(String source, String name) {
        Matcher found = Pattern.compile("\\b" + name + "\\s*\\([^)]*\\)\\s*(throws\\s+[\\w.,\\s]+)?\\{")
            .matcher(source);
        assertTrue(found.find(), "no method " + name);
        int open = source.indexOf('{', found.start());
        int depth = 0;
        for (int at = open; at < source.length(); at++) {
            char each = source.charAt(at);
            if (each == '{') depth++;
            else if (each == '}' && --depth == 0) {
                return source.substring(open + 1, at)
                    .replaceAll("(?s)/\\*.*?\\*/", " ")
                    .replaceAll("//[^\\n]*", " ");
            }
        }
        return "";
    }
}
