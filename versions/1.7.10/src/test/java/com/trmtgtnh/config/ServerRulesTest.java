package com.trmtgtnh.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.trmtgtnh.erosion.SinkProfile;
import com.trmtgtnh.surface.SurfaceFamily;

/**
 * The arithmetic that turns a server's bytes into this client's geometry.
 *
 * <p>
 * Applying them needs a live Forge {@code Configuration} and a family map, so the applying half is
 * not testable here. What is testable is the half that would actually hurt: three numbers arrive off
 * the wire as unsigned bytes and go straight into fields every reader assumes are inside a range
 * nobody was checking. It survived only because the next config read wiped whatever came through -
 * and that read is exactly what has now been stopped, so an unchecked value would last the whole
 * visit rather than a moment. The clamps went in with the fix for that reason, and this is what
 * holds them there.
 */
class ServerRulesTest {

    @Test
    @DisplayName("a sinking start arrives in hundredths and lands on the exact fraction")
    void fractionIsExact() {
        assertEquals(0f, ServerRules.fractionOf(0), 0f);
        assertEquals(0.45f, ServerRules.fractionOf(45), 0f);
        assertEquals(1f, ServerRules.fractionOf(100), 0f);
    }

    @Test
    @DisplayName("a sinking start outside nought to one is held there, as one read from the file is")
    void fractionIsClamped() {
        assertEquals(1f, ServerRules.fractionOf(137), 0f, "a byte past a hundred is still a whole one");
        assertEquals(1f, ServerRules.fractionOf(255), 0f, "and so is the largest a byte can carry");
        assertEquals(0f, ServerRules.fractionOf(-3), 0f, "nothing below nothing");
    }

    @Test
    @DisplayName("a stage count is held to what a painted position can carry")
    void stagesAreClamped() {
        assertEquals(8, ServerRules.clampStages(8));
        assertEquals(SurfaceFamily.MAX_STAGES, ServerRules.clampStages(SurfaceFamily.MAX_STAGES));
        // A byte off the wire reaches 255, and a ghost block keeps its layer in the four bits of its
        // own metadata, so a seventeenth gradation would wrap onto the first with nothing to notice.
        assertEquals(SurfaceFamily.MAX_STAGES, ServerRules.clampStages(255));
        assertTrue(SurfaceFamily.MAX_STAGES <= 16, "a layer has to fit in a block's four bits of metadata");
    }

    @Test
    @DisplayName("a depth is held to what the sink field can carry")
    void sinkIsClamped() {
        assertEquals(4, ServerRules.clampSink(4));
        assertEquals(SinkProfile.MAX_SINK_PIXELS, ServerRules.clampSink(SinkProfile.MAX_SINK_PIXELS));
        assertEquals(SinkProfile.MAX_SINK_PIXELS, ServerRules.clampSink(200));
    }

    @Test
    @DisplayName("nothing legitimate is clamped away, so the ceilings match the ones the file uses")
    void ceilingsAgreeWithTheConfig() {
        // A server writes these from its own config, which Forge holds to the same two ceilings. If
        // these ever parted company the clamp would start quietly refusing values a server was
        // entitled to send, which is a worse fault than the one it was added for.
        assertEquals(16, SurfaceFamily.MAX_STAGES);
        assertEquals(15, SinkProfile.MAX_SINK_PIXELS);
    }
}
