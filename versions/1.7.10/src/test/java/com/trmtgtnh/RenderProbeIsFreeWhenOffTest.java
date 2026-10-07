package com.trmtgtnh;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The render probe costs nothing when it is off.
 *
 * <p>
 * {@code GhostRendering.probe} is a diagnostic behind {@code -Dtrmt.spike.render=true}, and it is
 * called from the busiest code this mod has: the icon and colour of every face of every ghost block,
 * asked by the chunk mesher. It returns at once when it is off - but the message it is handed is
 * built <em>before</em> the call, so a gate only inside it still cost every face a string, for every
 * player, in a released jar. That was the state of it on 2026-10-07, the day it found the second half
 * of the OptiFine fringe fault.
 *
 * <p>
 * So every call carries the gate itself, on the same line, where the compiler can see a constant and
 * the whole call disappears. This reads the source and refuses a call that does not.
 */
class RenderProbeIsFreeWhenOffTest {

    private static final String[] WHERE = { "src/main/java/com/trmtgtnh/block/GhostRendering.java",
        "src/main/java/com/trmtgtnh/block/GhostLogic.java" };

    @Test
    void every_probe_call_is_gated_where_it_is_made() throws IOException {
        List<String> ungated = new ArrayList<String>();
        int calls = 0;
        for (String relative : WHERE) {
            String[] lines = read(relative).split("\n");
            for (int at = 0; at < lines.length; at++) {
                String line = lines[at].trim();
                if (line.startsWith("*") || line.startsWith("//")) continue;
                if (line.contains("static void probe(")) continue;
                if (!line.matches(".*\\bprobe\\(.*")) continue;
                calls++;
                if (!line.contains("if (PROBE)") && !line.contains("if (GhostRendering.PROBE)")) {
                    ungated.add(relative + ":" + (at + 1) + "  " + line);
                }
            }
        }
        assertTrue(calls >= 6, "found only " + calls + " probe calls; this test is no longer looking at them");
        assertTrue(
            ungated.isEmpty(),
            "probe() called without the gate at the call site, so its message is built for every face "
                + "while the probe is off:\n  "
                + String.join("\n  ", ungated));
    }

    private static String read(String relative) throws IOException {
        File here = new File(System.getProperty("user.dir"));
        for (File at = here; at != null; at = at.getParentFile()) {
            File found = new File(at, relative);
            if (found.isFile()) return new String(Files.readAllBytes(found.toPath()), Charset.forName("UTF-8"));
        }
        throw new IOException(relative + " is not where this test looks for it");
    }
}
