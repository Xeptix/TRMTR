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
 * told or the cell keeps the darkness it had while it was whole. Found on 2026-10-07 in the 1.16.5
 * edition, where Rubidium draws a sunken top from that cell and the second demonstrate yard came out
 * darker the deeper it was worn; the same light was wrong here.
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

        assertTrue(
            ClientProxy.lightAnswerMoved(worn, first),
            "the first pixel of sinking no longer re-lights the square");
        assertTrue(ClientProxy.lightAnswerMoved(worn, sunk), "sinking no longer re-lights the square");
        assertTrue(ClientProxy.lightAnswerMoved(sunk, worn), "rising out of a rut no longer re-lights it");
        assertTrue(ClientProxy.lightAnswerMoved(sunk, none), "a sunken square cleared no longer re-lights");
        assertFalse(ClientProxy.lightAnswerMoved(sunk, deeper), "sinking further re-lights for nothing");
        assertFalse(ClientProxy.lightAnswerMoved(none, worn), "wear that has not sunk re-lights for nothing");
    }

    @Test
    void both_ways_a_record_arrives_ask_and_the_answer_reaches_the_light() throws IOException {
        String source = String.join("\n", SourceTree.lines("com/trmtgtnh/client/ClientProxy.java"));
        assertTrue(
            body(source, "handleDelta").contains("relightIfMoved("),
            "a record arriving alone never re-lights the square it moved");
        assertTrue(
            body(source, "handleChunkErosion").contains("relightIfMoved("),
            "a chunk's records arriving together never re-light the squares they moved");
        assertTrue(
            body(source, "relightIfMoved").contains(".checkLight("),
            "the re-light is asked for and never reaches the light engine");
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
