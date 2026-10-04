package com.trmtgtnh.erosion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The block-space arithmetic behind a rut's depth.
 *
 * <p>
 * The depth itself is read from config, which needs a live Forge {@code Configuration} and so
 * is not testable here. What is testable is the part that would actually hurt if it were
 * wrong: the conversion from a depth in sixteenths to a height the server and client both put
 * a player on. A disagreement there is not a cosmetic bug — it is a player being shoved out of
 * the ground every tick.
 */
class SinkProfileTest {

    @Test
    @DisplayName("an unworn surface is a full block")
    void noSinkIsFullHeight() {
        assertEquals(1.0D, SinkProfile.heightFor(0), 0.0D);
    }

    @Test
    @DisplayName("depth converts to height in exact sixteenths")
    void depthConvertsExactly() {
        assertEquals(15.0D / 16.0D, SinkProfile.heightFor(1), 0.0D);
        assertEquals(14.0D / 16.0D, SinkProfile.heightFor(2), 0.0D);
        assertEquals(13.0D / 16.0D, SinkProfile.heightFor(3), 0.0D);
        assertEquals(10.0D / 16.0D, SinkProfile.heightFor(6), 0.0D);
    }

    @Test
    @DisplayName("depth is clamped, so no configuration can sink a block out of its own space")
    void depthIsClamped() {
        assertEquals(1.0D, SinkProfile.heightFor(-4), 0.0D, "negative depth cannot raise a block");
        double deepest = SinkProfile.heightFor(SinkProfile.MAX_SINK_PIXELS);
        assertEquals(deepest, SinkProfile.heightFor(99), 0.0D, "past the ceiling stays at the ceiling");
        assertTrue(deepest > 0.0D, "a block never sinks away entirely");
    }

    @Test
    @DisplayName("height falls as depth grows, with no ties")
    void deeperIsAlwaysLower() {
        double previous = Double.MAX_VALUE;
        for (int depth = 0; depth <= SinkProfile.MAX_SINK_PIXELS; depth++) {
            double height = SinkProfile.heightFor(depth);
            assertTrue(height < previous, "depth " + depth + " must sit below depth " + (depth - 1));
            previous = height;
        }
    }
}
