package com.trmtgtnh.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The in-game duration format, which the Wear Table both prints and reads back. The one property
 * that matters is that the two are inverses: what a cell shows must parse to what it showed, or
 * typing nothing and pressing enter would move a value.
 */
class DurationFormatTest {

    @Test
    @DisplayName("known spans read the way a person would say them")
    void knownSpans() {
        assertEquals("0s", DurationFormat.format(0d));
        assertEquals("4d 12h", DurationFormat.format(4.5d));
        assertEquals("1d", DurationFormat.format(1d));
        // 1920 days = 5 years and 95 days.
        assertEquals("5y 95d", DurationFormat.format(1920d));
    }

    @Test
    @DisplayName("format then parse lands on the same whole span")
    void roundTrips() {
        double[] days = { 0d, 0.5d, 1d, 4.5d, 15d, 60d, 360d, 1920d, 12345.678d };
        for (double d : days) {
            double back = DurationFormat.parse(DurationFormat.format(d));
            // To the second: format rounds to whole seconds, so parse matches to within one.
            assertTrue(Math.abs(back - d) <= 1.0d / 86400d + 1e-9d, "round trip for " + d + " gave " + back);
        }
    }

    @Test
    @DisplayName("a bare number is days, and units may come in any order or case")
    void lenientParsing() {
        assertEquals(10d, DurationFormat.parse("10"), 1e-9d);
        assertEquals(1d + 12d / 24d, DurationFormat.parse("1d 12h"), 1e-9d);
        assertEquals(1d + 12d / 24d, DurationFormat.parse("12H 1D"), 1e-9d);
        assertEquals(365d, DurationFormat.parse("1y"), 1e-9d);
        assertEquals(0d, DurationFormat.parse("nonsense"), 1e-9d);
    }
}
