package com.trmtgtnh.erosion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The promise a cost curve makes, and the two shapes the balance table was agreed against.
 *
 * <p>
 * A curve is allowed to move where a run's cost sits and is not allowed to move how much the run
 * costs. Everything else about the rebalance rests on that: the crossings, the recovery times and
 * the traffic it takes to hold ground were all settled before any curve existed, and they only
 * survive a family being given one because the multipliers average to exactly one. If that ever
 * stops being true, a curve becomes a difficulty knob wearing the mask of a shape knob, and it will
 * be found by somebody wondering why their gravel changed - which is precisely the shape of failure
 * this mod keeps having to dig out of.
 *
 * <p>
 * Nothing here needs Minecraft, which is why it can be pinned at all.
 */
class CostCurveTest {

    /** Chain lengths that actually occur: dirt starts a layer in, ice sinks half as far. */
    private static final int[] REAL_LENGTHS = { 48, 79, 80 };

    @Test
    @DisplayName("every curve averages to one across a run, so no total ever moves")
    void curvesPreserveTheTotal() {
        for (CostCurve curve : CostCurve.values()) {
            for (int length : REAL_LENGTHS) {
                double total = 0d;
                for (int i = 0; i < length; i++) total += curve.at(i, length);
                assertEquals(
                    length,
                    total,
                    length * 1e-4d,
                    curve.key() + " over " + length + " gradations must cost what a flat run costs");
            }
        }
    }

    @Test
    @DisplayName("the flat curve is genuinely flat, so families without one are untouched")
    void flatIsFlat() {
        for (int i = 0; i < 80; i++) {
            assertEquals(1f, CostCurve.FLAT.at(i, 80), 0f);
        }
    }

    @Test
    @DisplayName("ice runs from four passes a gradation to forty-eight, which is what was agreed")
    void glazeHitsTheAgreedEndpoints() {
        // Ice: thresholds 10 to 16, so an average of 13, and one player crossing banks half a wear
        // unit - hence twice the average is the passes an unshaped gradation would take.
        double unshaped = 2d * 13d;
        double first = unshaped * CostCurve.GLAZE.at(0, 48);
        double last = unshaped * CostCurve.GLAZE.at(47, 48);
        assertEquals(4d, first, 0.05d, "the first gradation of ice");
        assertEquals(48d, last, 0.5d, "the last gradation of ice");
    }

    @Test
    @DisplayName("snow opens under a single pass and caps three quarters of the way along")
    void packOpensCheapAndThenLevelsOff() {
        double unshaped = 2d * 1.2d;
        assertEquals(0.84d, unshaped * CostCurve.PACK.at(0, 80), 0.02d, "the first gradation of snow");
        assertEquals(3.35d, unshaped * CostCurve.PACK.at(79, 80), 0.05d, "the last gradation of snow");

        // Past the knee it stops climbing, which is the whole difference between snow and ice:
        // packed snow has nothing left to compress, and scuffed ice goes on polishing.
        float atKnee = CostCurve.PACK.at(60, 80);
        float atEnd = CostCurve.PACK.at(79, 80);
        assertEquals(atKnee, atEnd, 1e-5f, "everything past three quarters costs the same");
        assertTrue(CostCurve.GLAZE.at(47, 48) > CostCurve.GLAZE.at(36, 48), "ice never levels off");
    }

    @Test
    @DisplayName("a curve only ever rises, so no gradation is cheaper than the one before it")
    void curvesNeverFallBack() {
        for (CostCurve curve : CostCurve.values()) {
            for (int length : REAL_LENGTHS) {
                for (int i = 1; i < length; i++) {
                    assertTrue(
                        curve.at(i, length) >= curve.at(i - 1, length) - 1e-6f,
                        curve.key() + " fell back at gradation " + i);
                }
            }
        }
    }

    @Test
    @DisplayName("an index outside the run is held to its ends rather than extrapolated")
    void outOfRangeIsClamped() {
        assertEquals(CostCurve.GLAZE.at(0, 48), CostCurve.GLAZE.at(-5, 48), 0f);
        assertEquals(CostCurve.GLAZE.at(47, 48), CostCurve.GLAZE.at(900, 48), 0f);
        // A run of one gradation has nowhere to travel, so it cannot be shaped at all.
        assertEquals(1f, CostCurve.GLAZE.at(0, 1), 0f);
    }

    @Test
    @DisplayName("names round-trip, since a config file is where these are chosen")
    void namesResolve() {
        for (CostCurve curve : CostCurve.values()) {
            assertEquals(curve, CostCurve.byKey(curve.key()));
            assertEquals(
                curve,
                CostCurve.byKey(
                    curve.key()
                        .toUpperCase()),
                "a config file is not case law");
        }
        assertEquals(null, CostCurve.byKey("nonsense"));
        assertEquals(null, CostCurve.byKey(null));
        assertEquals(CostCurve.values().length, CostCurve.keys().length);
    }
}
