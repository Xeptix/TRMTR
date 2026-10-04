package com.trmtgtnh.erosion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The pacing of ground that waits for the weather.
 *
 * <p>
 * Whether it is raining on a particular block needs a live world and is not testable here. What is
 * testable is the part somebody will actually feel and the part a config comment makes a promise
 * about: how long it has to rain before a path comes back. The promise in
 * {@code weather.wetHealSecondsPerStage} is that four minutes of weather mends a surface worn all
 * the way through, and that is arithmetic rather than opinion, so it can be held to.
 */
class WeatherMeterTest {

    /** Twenty ticks a second, which is the only reason any of these numbers are what they are. */
    private static final int TICKS_PER_SECOND = 20;

    @Test
    @DisplayName("three seconds of weather buys one gradation, and nothing buys none")
    void theDefaultRate() {
        assertEquals(0, Weather.stagesFor(0, 3f));
        assertEquals(0, Weather.stagesFor(59, 3f), "just short of three seconds is still nothing");
        assertEquals(1, Weather.stagesFor(60, 3f));
        assertEquals(1, Weather.stagesFor(119, 3f), "part of a second gradation is not a gradation");
        assertEquals(2, Weather.stagesFor(120, 3f));
    }

    @Test
    @DisplayName("a surface worn all the way through wants four minutes of it")
    void theWholeRunTakesFourMinutes() {
        // Eighty gradations is the longest run any family has - see FamilySettings.defaultsFor.
        int wanted = 80;
        int seconds = 4 * 60;
        assertEquals(wanted, Weather.stagesFor(seconds * TICKS_PER_SECOND, 3f));
        assertTrue(
            Weather.stagesFor((seconds - 1) * TICKS_PER_SECOND, 3f) < wanted,
            "a second short of four minutes must not finish the run");
    }

    @Test
    @DisplayName("ice wants half as long, having half as many gradations to give back")
    void iceIsShorter() {
        // Ice sinks four pixels rather than eight, so its chain is forty-eight steps, not eighty.
        assertEquals(48, Weather.stagesFor(48 * 3 * TICKS_PER_SECOND, 3f));
    }

    @Test
    @DisplayName("nought seconds a gradation means no meter at all, which is the old behaviour")
    void zeroDisablesTheMeter() {
        assertEquals(Integer.MAX_VALUE, Weather.stagesFor(1, 0f));
        assertEquals(Integer.MAX_VALUE, Weather.stagesFor(0, 0f), "even with no weather yet counted");
        assertEquals(Integer.MAX_VALUE, Weather.stagesFor(5, -2f), "and a nonsense value cannot invert it");
    }

    @Test
    @DisplayName("a slower meter is slower, in proportion, and a faster one faster")
    void theRateScales() {
        int minute = 60 * TICKS_PER_SECOND;
        assertEquals(20, Weather.stagesFor(minute, 3f));
        assertEquals(10, Weather.stagesFor(minute, 6f), "twice the price, half the gradations");
        assertEquals(40, Weather.stagesFor(minute, 1.5f), "half the price, twice the gradations");
    }

    @Test
    @DisplayName("a sub-tick price is held at one tick, so no setting can pay out infinitely fast")
    void theFloorHolds() {
        // Rounds to nought ticks a gradation, which would divide by zero if it were let through.
        assertEquals(600, Weather.stagesFor(600, 0.01f));
        assertTrue(Weather.stagesFor(600, 0.01f) <= 600, "at most one gradation a tick");
    }

    @Test
    @DisplayName("time running backwards pays nothing rather than something absurd")
    void negativeElapsedIsSafe() {
        assertEquals(0, Weather.stagesFor(-1, 3f));
        assertEquals(0, Weather.stagesFor(Integer.MIN_VALUE, 3f));
    }

    @Test
    @DisplayName("weather short of a whole gradation stays on the meter for the next visit")
    void theRemainderIsKept() {
        // Fifteen seconds a gradation, visited every ten: a reading taken to now paid nothing, ever.
        assertEquals(0, Weather.stagesFor(200, 15f));
        assertEquals(0, Weather.readingAfter(0, 200, 15f), "nothing was bought, so nothing moves");
        int reading = Weather.readingAfter(0, 200, 15f);
        assertEquals(1, Weather.stagesFor(400 - reading, 15f), "the next visit pays for both stretches");
        assertEquals(300, Weather.readingAfter(reading, 400, 15f), "and moves on by exactly what it paid for");
    }

    @Test
    @DisplayName("a meter read often pays out exactly what a meter read once would")
    void visitCadenceChangesNothing() {
        assertEquals(Weather.stagesFor(4800, 3f), paidInVisits(4800, 200, 3f), "a price under the visit gap");
        assertEquals(Weather.stagesFor(6000, 15f), paidInVisits(6000, 200, 15f), "a price over the visit gap");
        assertEquals(Weather.stagesFor(6000, 15f), paidInVisits(6000, 20, 15f), "the sweep at its fastest");
    }

    @Test
    @DisplayName("with no meter the reading simply follows the clock")
    void noMeterFollowsTheClock() {
        assertEquals(500, Weather.readingAfter(100, 500, 0f));
        assertEquals(100, Weather.readingAfter(300, 100, 3f), "a clock that went backwards is taken as it is");
    }

    private static int paidInVisits(int until, int gap, float secondsPerStage) {
        int reading = 0;
        int paid = 0;
        for (int now = gap; now <= until; now += gap) {
            paid += Weather.stagesFor(now - reading, secondsPerStage);
            reading = Weather.readingAfter(reading, now, secondsPerStage);
        }
        return paid;
    }
}
