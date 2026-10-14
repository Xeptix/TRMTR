package com.trmtgtnh.erosion;

/**
 * How the healing sweep turns what a pass has left over back into the whole seconds a record's clock counts,
 * and whether what it has left pays for the next gradation.
 *
 * <p>
 * Healing is priced in in-game days, counted in a double, and banked back into a clock that counts whole
 * seconds. What a pass has left is the elapsed seconds divided into days, less any partial wear bled off and
 * each gradation's price, and binary holds neither a twelve-hundredth nor a price such as 0.075 exactly, so a
 * remainder that should be exactly ten seconds comes back as 9.999999999999998. The engine used to cast that,
 * and a cast drops everything after the point, so it banked nine.
 *
 * <p>
 * Ground simply waiting for its next gradation, with nothing paid at all, lost most of it, because that is what
 * an abandoned path is doing nearly all of the time. Swept every ten seconds, as a chunk is while no more
 * loaded chunks hold wear than one sweep takes, such ground lost a second on between one pass in seven and a
 * half and one in thirteen, and a pass that paid a gradation lost one at three to six of the ten remainders
 * such a sweep can leave. That ground mended between three quarters of a percent and one and two fifths more
 * slowly than ground caught up in a single load - a day and two thirds over turf's hundred and twenty-five -
 * where the healing settings promise the two come out alike. Snow alone escaped it at that interval.
 *
 * <p>
 * The same error decides whether a remainder pays a price. A catch-up owing exactly four gradations of snow
 * has 89.99999999999996 seconds left for the fourth, and was refused it. Banked whole that only puts the
 * gradation off to the next pass, but for a family waiting on the weather the chunk's meter has already been
 * read for this one, so the gradation waited for the next the rain paid for. Both questions are answered here,
 * with one tolerance, so a remainder the bank would call a whole price is also one the price will take.
 *
 * <p>
 * Rounded down after a tolerance rather than to the nearest second. The nearest second banks up to half a
 * second that has not gone by - a gradation of turf in the rain costs nine hundred and thirty-seven and a
 * half - and keeping time that never elapsed out of the clock is the reason the rain discount is taken off the
 * price rather than the clock.
 *
 * <p>
 * The fraction a price genuinely has is still dropped when a sweep pays it, because the clock is saved as whole
 * seconds. At the shipped figures that is nothing on dry ground except a hundredth of a second a gradation on
 * earth, and about half a second a gradation on turf, earth and gravel in the rain.
 */
public final class HealBank {

    /** Seconds of world time in an in-game day: 24000 ticks at 20 a second. */
    public static final int SECONDS_PER_DAY = 1200;

    /**
     * How far short of a whole second, or of a price, a remainder may fall and still count as reaching it.
     *
     * <p>
     * A millisecond, a fiftieth of a tick. It is about thirty times the worst error the arithmetic was measured
     * to make over the whole range of the clock with two hundred and fifty-six prices taken off. Raised past a
     * few milliseconds it would start banking fractions a price really has, and paying gradations that much
     * before they are owed. Lowered below about a fifth of a millisecond it would lose nearly a whole second a
     * gradation at the prices the slow and brisk presets lean to, which sit a hair below whole seconds because
     * a rung's lean is a float - up to a seventh of a millisecond at the shipped figures. Lowered much further
     * it lets the arithmetic's own error back in on a clock that has run long enough.
     */
    static final double TOLERANCE_SECONDS = 1e-3d;

    private HealBank() {}

    /**
     * Whether what a pass has left pays for a price, to within the tolerance the bank rounds by.
     *
     * <p>
     * A remainder left a hair short of the price is taken as the price, since the bank beside this would keep
     * it as the whole price anyway and the gradation would only have waited a pass. Paying it that hair early
     * leaves a remainder a hair below nought, which {@link #bankedSeconds} banks as nothing. Written as the
     * negation of the comparison the engine used to break on, so a price or a remainder that is not a number
     * is taken exactly as it always was: this changes what a hair short of a price means, and nothing else. An
     * infinite price, or the {@code Double.MAX_VALUE} the engine charges a family with no settings, is never
     * paid.
     *
     * @param daysLeft what is left of the elapsed time, in days
     * @param price    what the next gradation costs, in days
     */
    public static boolean affords(double daysLeft, double price) {
        return !(daysLeft < price - TOLERANCE_SECONDS / SECONDS_PER_DAY);
    }

    /**
     * The whole seconds to bank for the days a pass did not spend.
     *
     * <p>
     * Never more than the seconds the pass started from, so however the arithmetic falls the clock cannot be
     * moved back past the record's own last stamp; and nought for a remainder that is not above nought, one
     * that is not a number included, so an overdrawn pass banks nothing rather than a negative stretch of time.
     *
     * @param daysLeft       what was left of the elapsed time once the pass had paid for what it could
     * @param elapsedSeconds the whole seconds the pass started from
     */
    public static int bankedSeconds(double daysLeft, int elapsedSeconds) {
        if (elapsedSeconds <= 0) return 0;
        double seconds = daysLeft * SECONDS_PER_DAY;
        if (!(seconds > 0d)) return 0;
        double whole = Math.floor(seconds + TOLERANCE_SECONDS);
        return whole >= elapsedSeconds ? elapsedSeconds : (int) whole;
    }
}
