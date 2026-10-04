package com.trmtgtnh.erosion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.surface.SurfaceFamily;

/**
 * The figures trampling promises, run without the game.
 *
 * <p>
 * Every number the trampling config comments give - thirty-two to forty-eight crossings on foot, a
 * full tally gone in twenty in-game days, a plant crossed less than about twice a day recovering, four
 * crossings rather than one at an erosion speed of eight - is pinned by one of these. The speeds are
 * statics on the config, so they are put back to one before and after every test: one test leaving
 * erosionSpeed at eight would otherwise fail whichever ran next, for a reason that had nothing to do
 * with it.
 */
class TrampleTallyTest {

    private static final float MIN = 16f;
    private static final float MAX = 24f;
    private static final float ON_FOOT = 0.5f;
    private static final double FRACTION = 0.05d;
    private static final int DAY = TrampleTally.SECONDS_PER_DAY;

    @BeforeEach
    void startAtShippedSpeeds() {
        restoreDefaults();
    }

    @AfterEach
    void restoreDefaults() {
        TrmtConfig.erosionSpeed = 1.0d;
        TrmtConfig.globalSpeed = 1.0d;
    }

    /** A leaf tally with a threshold of sixteen, already carrying some wear, last touched when given. */
    private static ErosionEntry worn(float wear, int touched) {
        ErosionEntry tally = TrampleTally.start(SurfaceFamily.LEAVES, MIN, MIN, 0f, touched);
        tally.recordStep(wear, touched);
        return tally;
    }

    // ------------------------------------------------------------------
    // Drawing and recognising a threshold
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a draw stays inside its range, and a range with no width gives its minimum")
    void drawsStayInRange() {
        float[] rolls = { 0f, 0.5f, 0.99999f };
        for (float roll : rolls) {
            float drawn = TrampleTally.draw(MIN, MAX, roll);
            assertTrue(drawn >= MIN && drawn <= MAX, "a roll of " + roll + " drew " + drawn);
        }
        assertEquals(MIN, TrampleTally.draw(MIN, MAX, 0f), 0f, "a roll of nought is the minimum");
        assertEquals(20f, TrampleTally.draw(MIN, MAX, 0.5f), 0.0001f, "half way along is half way along");

        assertEquals(MIN, TrampleTally.draw(MIN, MIN, 0.7f), 0f, "a range with no width gives its minimum");
        assertEquals(MAX, TrampleTally.draw(MAX, MIN, 0.3f), 0f, "and so does one set the wrong way round");

        assertEquals(0.01f, TrampleTally.draw(0f, 0f, 0.5f), 0f, "nothing is drawn below the floor");
        assertEquals(0.01f, TrampleTally.draw(-5f, -1f, 0.5f), 0f, "however far below it the range was set");
    }

    @Test
    @DisplayName("a tally starts invisible and unworn, at its family's draw, touched now")
    void aTallyStartsBlank() {
        ErosionEntry tally = TrampleTally.start(SurfaceFamily.LEAVES, MIN, MAX, 0.5f, 4321);
        assertEquals(SurfaceFamily.LEAVES, tally.getFamily(), "family");
        assertEquals(-1, tally.getStage(), "no stage");
        assertFalse(tally.isVisible(), "a tally is never drawn on the block");
        assertEquals(0f, tally.getWear(), 0f, "no wear yet");
        assertEquals(20f, tally.getThreshold(), 0.0001f, "the draw, at face value");
        assertEquals(4321, tally.getLastTouchedSeconds(), "the clock starts now");
        assertTrue(
            tally.isPrunable(),
            "with nothing counted it is prunable, so a tally never crossed costs a save nothing");
    }

    @Test
    @DisplayName("every draw reads as a face value, while the old nought and speed-baked draws do not")
    void faceValuesAreRecognised() {
        for (int i = 0; i <= 1000; i++) {
            float drawn = TrampleTally.draw(MIN, MAX, i / 1000f);
            assertTrue(TrampleTally.isFaceValue(drawn, MIN, MAX), "a draw of " + drawn + " must be kept");
        }
        assertTrue(TrampleTally.isFaceValue(MIN, MIN, MIN), "a range with no width still has its one face value");

        assertFalse(TrampleTally.isFaceValue(0f, MIN, MAX), "the nought the old strip branch wrote is redrawn");
        assertFalse(TrampleTally.isFaceValue(8f, MIN, MAX), "sixteen baked in at a speed of two is redrawn");
        assertFalse(TrampleTally.isFaceValue(48f, MIN, MAX), "twenty-four baked in at a speed of a half is redrawn");
        assertFalse(TrampleTally.isFaceValue(Float.NaN, MIN, MAX), "and so is a number that is not one");

        // Twenty-one baked in at a speed of 1.2 lands inside the range. There is no telling it from a
        // fresh draw, and the new code could have drawn it, so it is kept rather than redrawn.
        assertTrue(TrampleTally.isFaceValue(17.5f, MIN, MAX), "a baked draw inside the range is kept");
    }

    // ------------------------------------------------------------------
    // The speeds divide a tally once
    // ------------------------------------------------------------------

    /**
     * The fix itself, measured through the way a tally is started.
     *
     * <p>
     * The old code divided the draw by the speeds and then divided it again at comparison, so an
     * erosion speed of eight left a plant one crossing deep. These start a tally exactly as the engine
     * does and count crossings to the break. What they cannot see is the engine's call site: that it
     * hands {@link TrampleTally#start} the family's range unscaled is proved only by case 3 of the
     * dedicated-server trample probe.
     */
    @Test
    @DisplayName("the speeds divide a tally once, so an erosion speed of eight means four crossings and not one")
    void theSpeedsDivideOnce() {
        assertCrossingsToBreak(1d, 1d, 32);
        assertCrossingsToBreak(2d, 1d, 16); // not 8
        assertCrossingsToBreak(2d, 2d, 8);
        assertCrossingsToBreak(8d, 1d, 4); // not 1
    }

    private static void assertCrossingsToBreak(double erosionSpeed, double globalSpeed, int expected) {
        TrmtConfig.erosionSpeed = erosionSpeed;
        TrmtConfig.globalSpeed = globalSpeed;
        String speeds = "erosionSpeed " + erosionSpeed + ", globalSpeed " + globalSpeed;

        ErosionEntry tally = TrampleTally.start(SurfaceFamily.VEGETATION, MIN, MIN, 0f, 0);
        assertEquals(MIN, tally.getThreshold(), 0f, "what is stored is the face value at " + speeds);

        int crossings = 0;
        boolean broken = false;
        while (!broken && crossings < 1000) {
            broken = TrampleTally.cross(tally, ON_FOOT, 0, FRACTION, true);
            crossings++;
        }
        assertEquals(expected, crossings, "crossings on foot to break a sixteen at " + speeds);
        assertEquals(
            TrampleTally.crossingsToBreak(MIN / (float) (erosionSpeed * globalSpeed), ON_FOOT),
            crossings,
            "and the counting helper agrees at " + speeds);
    }

    // ------------------------------------------------------------------
    // Fading
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a day sheds the fraction of the threshold, and wear never goes below nought")
    void fadeArithmetic() {
        assertEquals(0.8d, TrampleTally.perDay(FRACTION, 16f), 1e-9d, "a twentieth of sixteen");
        assertEquals(0d, TrampleTally.perDay(0d, 16f), 0d, "nothing fades at a fraction of nought");
        assertEquals(0d, TrampleTally.perDay(FRACTION, 0f), 0d, "or against a threshold of nought");

        assertEquals(4.6f, TrampleTally.faded(5f, 0.8d, DAY / 2), 0.0001f, "half a day takes off 0.4");
        assertEquals(
            0f,
            TrampleTally.faded(1f, 0.8d, 10 * DAY),
            0f,
            "ten days take off everything, and stop at nought");

        assertEquals(5f, TrampleTally.faded(5f, 0d, 100 * DAY), 0f, "a rate of nought leaves wear exactly as it was");
        assertEquals(5f, TrampleTally.faded(5f, 0.8d, 0), 0f, "and so does no time passing");
        assertEquals(5f, TrampleTally.faded(5f, 0.8d, -60), 0f, "and a clock that reads earlier than the touch");
    }

    @Test
    @DisplayName("a full tally is gone in twenty in-game days, however large its threshold")
    void aFullTallyClearsInTwentyDays() {
        float[] thresholds = { 0.5f, 16f, 24f, 1000f };
        for (float threshold : thresholds) {
            double perDay = TrampleTally.perDay(FRACTION, threshold);
            assertEquals(
                0f,
                TrampleTally.faded(threshold, perDay, 20 * DAY),
                0f,
                "a full tally of " + threshold + " is exactly nought after twenty days");
            assertTrue(
                TrampleTally.faded(threshold, perDay, 20 * DAY - DAY / 10) > 0f,
                "and not quite gone after nineteen point nine");
        }
    }

    @Test
    @DisplayName("fading in two pieces comes to the same as fading in one go")
    void splitSpansMatchOneSpan() {
        double perDay = TrampleTally.perDay(FRACTION, 16f);
        int[][] spans = { { 1, 1 }, { 10, 590 }, { 600, 600 }, { 1234, 4321 }, { 6000, 9000 } };
        for (int[] span : spans) {
            float inOne = TrampleTally.faded(12f, perDay, span[0] + span[1]);
            float inTwo = TrampleTally.faded(TrampleTally.faded(12f, perDay, span[0]), perDay, span[1]);
            assertTrue(inOne > 0f, "the spans are chosen to leave some wear, or this would prove nothing");
            assertEquals(inOne, inTwo, 0.0001f, span[0] + " then " + span[1] + " seconds");
        }

        // A thousand ten-second sweeps, as a busy chunk would see. Each rounds to a float and restamps,
        // so a little rounding gathers; the tolerance is looser than the pair above and still nothing
        // anyone could see on a threshold of sixteen.
        float swept = 12f;
        for (int i = 0; i < 1000; i++) {
            swept = TrampleTally.faded(swept, perDay, 10);
        }
        assertEquals(TrampleTally.faded(12f, perDay, 10000), swept, 0.001f, "a thousand sweeps against one catch-up");
    }

    @Test
    @DisplayName("fade leaves the clock alone unless the wear actually moves")
    void fadeMovesTheClockOnlyWithTheWear() {
        ErosionEntry tally = worn(5f, 1000);
        assertFalse(TrampleTally.fade(tally, 1000, FRACTION), "no time has passed");
        assertFalse(TrampleTally.fade(tally, 400, FRACTION), "nor when the clock reads earlier than the touch");
        assertEquals(5f, tally.getWear(), 0f, "the wear is untouched");
        assertEquals(1000, tally.getLastTouchedSeconds(), "and so is the clock");

        assertTrue(TrampleTally.fade(tally, 1000 + DAY, FRACTION), "a day later it has faded");
        assertEquals(4.2f, tally.getWear(), 0.0001f, "by a twentieth of sixteen");
        assertEquals(1000 + DAY, tally.getLastTouchedSeconds(), "and the clock is brought up to now");

        // healing.wearDecayPerDay at 0 is a setting that silently means never, so it is pinned here.
        ErosionEntry never = worn(5f, 1000);
        assertFalse(TrampleTally.fade(never, 1000 + 100 * DAY, 0d), "a fraction of nought fades nothing");
        assertEquals(5f, never.getWear(), 0f, "not in a hundred days");
        assertEquals(1000, never.getLastTouchedSeconds(), "and the clock stays where it was");
    }

    @Test
    @DisplayName("a crossing after two idle days first sheds a tenth of the threshold, unless nothing fades")
    void aCrossingFadesFirst() {
        ErosionEntry tally = worn(5f, 0);
        assertFalse(TrampleTally.cross(tally, ON_FOOT, 2 * DAY, FRACTION, true), "nowhere near breaking");
        assertEquals(
            5f - 0.1f * MIN + ON_FOOT,
            tally.getWear(),
            0.0001f,
            "3.9 rather than 5.5: counting without fading first would have thrown the two days away");
        assertEquals(2 * DAY, tally.getLastTouchedSeconds(), "the crossing is the new touch");

        ErosionEntry kept = worn(5f, 0);
        TrampleTally.cross(kept, ON_FOOT, 2 * DAY, FRACTION, false);
        assertEquals(
            5.5f,
            kept.getWear(),
            0.0001f,
            "with healing off nothing is shed and every crossing counts for good");
    }

    // ------------------------------------------------------------------
    // Counting
    // ------------------------------------------------------------------

    @Test
    @DisplayName("crossings to break are counted the way the game counts them")
    void crossingsAreCounted() {
        assertEquals(32, TrampleTally.crossingsToBreak(16f, 0.5f), "sixteen on foot");
        assertEquals(48, TrampleTally.crossingsToBreak(24f, 0.5f), "twenty-four on foot");
        assertEquals(16, TrampleTally.crossingsToBreak(16f, 1.0f), "sixteen mounted");
        assertEquals(22, TrampleTally.crossingsToBreak(16f, 0.75f), "sixteen on a lead, rounded up and not down");
        assertEquals(-1, TrampleTally.crossingsToBreak(16f, 0f), "a crossing that weighs nothing never breaks it");
        assertEquals(-1, TrampleTally.crossingsToBreak(16f, -1f), "and nor does one that weighs less");
    }

    @Test
    @DisplayName("the break-even rate is the fade divided by one crossing's weight")
    void breakEvenRates() {
        assertEquals(1.6d, TrampleTally.breakEvenPerDay(FRACTION, 16f, ON_FOOT), 1e-6d, "a sixteen on foot");
        assertEquals(2.4d, TrampleTally.breakEvenPerDay(FRACTION, 24f, ON_FOOT), 1e-6d, "a twenty-four on foot");
        assertEquals(
            0.2d,
            TrampleTally.breakEvenPerDay(FRACTION, 2f, ON_FOOT),
            1e-6d,
            "a sixteen at an erosion speed of eight");
        assertEquals(
            0d,
            TrampleTally.breakEvenPerDay(0d, 16f, ON_FOOT),
            0d,
            "with nothing fading, any traffic breaks it in the end");
        double weightless = TrampleTally.breakEvenPerDay(FRACTION, 16f, 0f);
        assertTrue(Double.isInfinite(weightless) && weightless > 0d, "and with nothing counting, no traffic does");
    }

    // ------------------------------------------------------------------
    // What the fade is for
    // ------------------------------------------------------------------

    /**
     * Decision B's promise: a plant on a quiet path recovers, and one on a busy route goes.
     *
     * <p>
     * At the shipped figures the line for a threshold of sixteen on foot is 1.6 crossings a day. Below
     * it, the fade between two crossings is more than one crossing adds, so the tally never climbs.
     * Above it, the tally gains the difference every day and breaks in roughly {@code T / (c*a - f*T)}
     * days, which for three crossings a day is about twenty-three.
     */
    @Test
    @DisplayName("a plant crossed one and a half times a day stands for good, and three times a day it goes")
    void onlyABusyRouteBreaksAPlant() {
        ErosionEntry quiet = TrampleTally.start(SurfaceFamily.VEGETATION, MIN, MIN, 0f, 0);
        for (int now = 800; now <= 400 * DAY; now += 800) {
            assertFalse(TrampleTally.cross(quiet, ON_FOOT, now, FRACTION, true), "a quiet path broke a plant");
            assertTrue(quiet.getWear() <= ON_FOOT + 0.0001f, "a quiet path's tally held more than one crossing");
        }

        ErosionEntry busy = TrampleTally.start(SurfaceFamily.VEGETATION, MIN, MIN, 0f, 0);
        double brokeAfter = -1d;
        for (int now = 400; now <= 400 * DAY && brokeAfter < 0d; now += 400) {
            if (TrampleTally.cross(busy, ON_FOOT, now, FRACTION, true)) brokeAfter = now / (double) DAY;
        }
        assertTrue(brokeAfter >= 0d, "three crossings a day must break a plant within four hundred days");
        double predicted = MIN / (3 * ON_FOOT - TrampleTally.perDay(FRACTION, MIN));
        assertEquals(predicted, brokeAfter, 1d, "and within a day of the figure the config comments give");
    }

    /**
     * Why the sweep may fade a tally without marking its chunk for saving.
     *
     * <p>
     * A fade that is lost when a chunk unloads unsaved leaves the old wear and the old stamp on disk.
     * Because the fade is a straight line from that stamp, working it out again later has to land on
     * the figure the lost copy would have reached. If it did not, skipping those saves would quietly
     * give tallies back wear they had already shed, or take away wear they had not.
     */
    @Test
    @DisplayName("a fade that is never saved works out to the same figure later as one that was")
    void anUnsavedFadeIsWorkedOutAgain() {
        ErosionEntry loaded = worn(10f, 0);
        ErosionEntry onDisk = worn(10f, 0);

        assertTrue(TrampleTally.fade(loaded, 600, FRACTION), "the sweep fades the copy in memory");
        assertEquals(0, onDisk.getLastTouchedSeconds(), "while what was saved still carries the old stamp");

        int later = 600 + 2 * DAY;
        TrampleTally.fade(loaded, later, FRACTION);
        TrampleTally.fade(onDisk, later, FRACTION);
        assertEquals(loaded.getWear(), onDisk.getWear(), 0.0001f, "both arrive at the same wear");
        assertEquals(loaded.getLastTouchedSeconds(), onDisk.getLastTouchedSeconds(), "and the same stamp");
        assertEquals(
            TrampleTally.faded(10f, TrampleTally.perDay(FRACTION, MIN), later),
            onDisk.getWear(),
            0.0001f,
            "which is the one straight line from the original touch");
    }
}
