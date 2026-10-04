package com.trmtgtnh.erosion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The healing clock's bank and the price it pays from, run without the game.
 *
 * <p>
 * Every remainder is worked out the way ErosionEngine.heal works it out - the elapsed seconds divided into
 * days, and each price taken off in turn - because the fault lives in that arithmetic, not in any figure a
 * test could type in directly. Where a test says a second used to be lost or a gradation refused, it also
 * checks that the old cast or comparison really does it, so the test cannot go on passing after its scenario
 * has quietly stopped being one. Touches no config, so it needs nothing put back.
 */
class HealBankTest {

    private static final int DAY = HealBank.SECONDS_PER_DAY;

    /** The bank as it was, kept only to prove each scenario is a real one. */
    private static int truncated(double daysLeft) {
        return (int) (daysLeft * DAY);
    }

    /** What heal() has left after paying these prices, in days, from so many elapsed seconds. */
    private static double afterPaying(int elapsedSeconds, double... prices) {
        double daysLeft = elapsedSeconds / (double) DAY;
        for (double price : prices) daysLeft -= price;
        return daysLeft;
    }

    /**
     * When a square waiting with no partial wear, swept every so many seconds, has been given back eighty
     * gradations at one price, each pass worked out as heal() works it out: pay what the remainder affords,
     * bank the rest.
     *
     * @param fixed true for HealBank's comparison and bank, false for the exact comparison and the cast
     */
    private static int sweptUntilHealed(double price, int interval, boolean fixed) {
        int now = 0;
        int touched = 0;
        int paid = 0;
        while (paid < 80) {
            now += interval;
            int elapsed = now - touched;
            double left = elapsed / (double) DAY;
            while (paid < 80 && (fixed ? HealBank.affords(left, price) : left >= price)) {
                left -= price;
                paid++;
            }
            touched = now - (fixed ? HealBank.bankedSeconds(left, elapsed) : truncated(left));
        }
        return now;
    }

    @Test
    @DisplayName("a pass that pays for nothing banks every second it started from")
    void nothingPaidBanksEverything() {
        assertEquals(54, truncated(afterPaying(55)), "the old cast banked 54 of 55 seconds");
        int lostBefore = 0;
        for (int elapsed = 1; elapsed <= 200000; elapsed++) {
            double left = afterPaying(elapsed);
            // Up to the dearest shipped gradation, the Nether's six thousand seconds, with the longest sweep
            // interval on top: the waits a sweep can actually see.
            if (elapsed <= 7200 && truncated(left) != elapsed) lostBefore++;
            assertEquals(elapsed, HealBank.bankedSeconds(left, elapsed), "after " + elapsed + " seconds");
        }
        assertEquals(385, lostBefore, "the old cast lost a second after 385 of the first 7,200 waits");
        assertEquals(Integer.MAX_VALUE, HealBank.bankedSeconds(afterPaying(Integer.MAX_VALUE), Integer.MAX_VALUE));
    }

    @Test
    @DisplayName("ground swept every ten seconds is healed on the sweep its time runs out, as one caught up in a load is")
    void sweptGroundKeepsPace() {
        assertEquals(
            150000,
            sweptUntilHealed(1.5625d, 10, true),
            "eighty gradations of turf, a hundred and twenty-five days");
        assertEquals(152010, sweptUntilHealed(1.5625d, 10, false), "which the cast made a day and two thirds late");
        assertEquals(384000, sweptUntilHealed(4.0d, 10, true), "eighty of stone, three hundred and twenty days");
        assertEquals(388000, sweptUntilHealed(4.0d, 10, false), "which the cast made three and a third days late");
    }

    @Test
    @DisplayName("a gradation paid at a shipped price leaves exactly the seconds that are left")
    void shippedPricesBankWhole() {
        // Days a gradation, and the whole seconds each comes to: turf, sand, gravel, cobble, stone, snow, ice,
        // and the Nether and the End. Earth's hundred over seventy-nine is not a whole number of seconds and
        // is taken with the fractions below.
        double[] days = { 1.5625d, 0.375d, 0.5625d, 2.125d, 4.0d, 0.075d, 0.65d, 5.0d };
        int[] seconds = { 1875, 450, 675, 2550, 4800, 90, 780, 6000 };
        for (int i = 0; i < days.length; i++) {
            int lostBefore = 0;
            for (int elapsed = seconds[i]; elapsed <= seconds[i] + 24000; elapsed++) {
                double left = afterPaying(elapsed, days[i]);
                if (truncated(left) != elapsed - seconds[i]) lostBefore++;
                assertEquals(
                    elapsed - seconds[i],
                    HealBank.bankedSeconds(left, elapsed),
                    days[i] + " days paid from " + elapsed + " seconds");
            }
            assertTrue(lostBefore > 0, "the old cast lost a second somewhere at " + days[i] + " days a gradation");
        }
    }

    @Test
    @DisplayName("a remainder left a hair short of a price pays for it, and one really short does not")
    void aHairShortStillPays() {
        // Four gradations of snow owed exactly: after three, the arithmetic leaves 89.99999999999996 seconds.
        double snow = afterPaying(360, 0.075d, 0.075d, 0.075d);
        assertTrue(snow < 0.075d, "the exact comparison refused the fourth gradation of snow");
        assertTrue(HealBank.affords(snow, 0.075d));
        assertEquals(0, HealBank.bankedSeconds(snow - 0.075d, 360), "and paying it leaves nothing to bank");
        assertEquals(90, HealBank.bankedSeconds(snow, 360), "refused, the bank alone would still have kept it whole");
        // Three gradations of ice owed exactly, the third refused the same way.
        double ice = afterPaying(2340, 0.65d, 0.65d);
        assertTrue(ice < 0.65d, "the exact comparison refused the third gradation of ice");
        assertTrue(HealBank.affords(ice, 0.65d));

        assertTrue(HealBank.affords(89.9995d / DAY, 0.075d), "half a millisecond short pays");
        assertFalse(HealBank.affords(89.998d / DAY, 0.075d), "two milliseconds short does not");
        assertFalse(HealBank.affords(89d / DAY, 0.075d), "nor a second short");
        assertFalse(HealBank.affords(1e6d, Double.MAX_VALUE), "a family with no settings is never paid");
    }

    @Test
    @DisplayName("wear bled to nought, with or without a gradation after it, leaves the seconds that are left")
    void bleedThenGradation() {
        // healing.wearDecayPerDay at 0.05 against a threshold of sixteen, and half a unit of wear.
        double daysToZero = 0.5f / (0.05d * 16f);
        assertEquals(750d, daysToZero * DAY, 0d, "five eighths of a day");
        assertEquals(2, truncated(afterPaying(753, daysToZero)), "the old cast banked 2 of 3 seconds");
        assertEquals(3, HealBank.bankedSeconds(afterPaying(753, daysToZero), 753));
        assertEquals(0, truncated(afterPaying(841, daysToZero, 0.075d)), "and 0 of 1 after a gradation of snow");
        assertEquals(1, HealBank.bankedSeconds(afterPaying(841, daysToZero, 0.075d), 841));
    }

    @Test
    @DisplayName("a long catch-up banks its remainder whole, however far the clock has run")
    void longCatchUp() {
        double[] eighty = new double[80];
        Arrays.fill(eighty, 0.075d);
        assertEquals(992799, truncated(afterPaying(1000000, eighty)), "the old cast banked one short");
        assertEquals(992800, HealBank.bankedSeconds(afterPaying(1000000, eighty), 1000000));
        assertEquals(
            Integer.MAX_VALUE - 7200,
            HealBank.bankedSeconds(afterPaying(Integer.MAX_VALUE, eighty), Integer.MAX_VALUE));
    }

    @Test
    @DisplayName("a fraction a price really has is kept back, never rounded up")
    void aFractionIsNotRoundedUp() {
        // Turf in the rain: 1.5625 days at twice the speed is 937.5 seconds, so a thousand leave 62.5.
        assertEquals(62, HealBank.bankedSeconds(afterPaying(1000, 1.5625d / 2d), 1000));
        // Earth in the rain: a hundred days over seventy-nine, halved, leaves 1240.506 of two thousand.
        assertEquals(1240, HealBank.bankedSeconds(afterPaying(2000, 100d / 79d / 2d), 2000));
        assertEquals(10, HealBank.bankedSeconds(10.99d / DAY, 20), "a hundredth short of eleven is ten");
    }

    @Test
    @DisplayName("the tolerance is a millisecond and no more")
    void toleranceIsAMillisecond() {
        assertEquals(10, HealBank.bankedSeconds(9.9995d / DAY, 20), "half a millisecond short of ten is ten");
        assertEquals(9, HealBank.bankedSeconds(9.998d / DAY, 20), "two milliseconds short of ten is nine");
    }

    @Test
    @DisplayName("never more than the seconds that elapsed, and never below nought")
    void boundedByTheWait() {
        assertEquals(
            600,
            HealBank.bankedSeconds(1.0d, 600),
            "a day left over from six hundred seconds banks the six hundred");
        assertEquals(0, HealBank.bankedSeconds(-1e-12d, 100), "an overdrawn remainder banks nothing");
        assertEquals(0, HealBank.bankedSeconds(Double.NaN, 100), "nor does one that is not a number");
        assertEquals(0, HealBank.bankedSeconds(0.5d, 0), "nor a pass with no time behind it");
    }
}
