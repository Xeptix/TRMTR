package com.trmtgtnh.erosion;

import com.trmtgtnh.surface.SurfaceFamily;

/**
 * The arithmetic of a trample tally: the count a plant in the way or a leaf underfoot keeps of the
 * crossings made through it or over it, and how that count fades.
 *
 * <p>
 * A tally is an ordinary {@link ErosionEntry} that never becomes visible. A leaf or a plant has no
 * gradations to wear through, only a point at which it breaks, so a tally needs no new field, no new
 * bit and no new format - what it needs is for its threshold to mean one thing. It did not. The draw
 * used to be divided by the speed settings when it was made and divided again when it was compared,
 * so at an erosion speed of eight a plant went on the first crossing rather than the fourth to the
 * sixth. Drawing here at face value, and leaving the speeds to
 * {@link ErosionEntry#effectiveThreshold()} alone, is what makes that division happen once.
 *
 * <p>
 * Nothing from the game is in it, deliberately. Every promise made about trampling - thirty-two to
 * forty-eight crossings on foot, a full tally gone in twenty in-game days, a plant crossed less than
 * about twice a day recovering - is a figure, and a figure is only a promise if a test can run it.
 * The engine decides where a tally lives and whether a crossing counts at all; this decides what a
 * counted crossing, and the time between crossings, does to it.
 */
public final class TrampleTally {

    /**
     * Seconds of world time in an in-game day, the unit the decay fraction is counted in.
     *
     * <p>
     * The same day the engine's healing counts in, so that healing.wearDecayPerDay means the same
     * thing on a plant as it does on the ground beneath it.
     */
    public static final int SECONDS_PER_DAY = 1200;

    /**
     * The smallest threshold a draw ever gives, the same floor the ground's own draw uses.
     *
     * <p>
     * It keeps a range set to nought or below from storing a threshold of nothing, and it is also what
     * lets {@link #isFaceValue} tell a real draw from the nought the old strip branch wrote, which is
     * why it must stay well above {@link #TOLERANCE}.
     */
    static final float FLOOR = 0.01f;

    /**
     * How far outside the range a stored threshold may sit and still be taken for a draw from it.
     *
     * <p>
     * A float read back from a save is exactly the float that was written, so this only has to absorb
     * the rounding in working out a draw. It is kept a tenth of {@link #FLOOR} so that a stored nought
     * can never pass.
     */
    static final float TOLERANCE = 1e-3f;

    private TrampleTally() {}

    /**
     * Draws a tally's threshold from a family's range, at face value.
     *
     * <p>
     * Never scaled by the speed settings. {@link ErosionEntry#effectiveThreshold()} does that at the
     * moment of comparison, and doing it here as well is exactly the double division this class
     * exists to end. A range whose maximum is not above its minimum gives the minimum, and nothing is
     * ever drawn below {@link #FLOOR}.
     *
     * @param roll a uniform number from nought to one, normally the engine's {@code Random.nextFloat()};
     *             anything outside that is held to its nearer end, so a bad roll cannot draw outside
     *             the range
     */
    public static float draw(float min, float max, float roll) {
        float drawn;
        if (max <= min) {
            drawn = Math.max(min, FLOOR);
        } else {
            float held = roll > 1f ? 1f : roll >= 0f ? roll : 0f;
            drawn = min + held * (max - min);
            if (drawn > max) drawn = max;
        }
        // Written this way round so a range that is not a number comes out at the floor too.
        return drawn >= FLOOR ? drawn : FLOOR;
    }

    /**
     * A fresh tally at a face-value draw: invisible, carrying no wear, and touched now.
     *
     * <p>
     * The one way the engine starts a tally, which is what keeps the draw unscaled in one place rather
     * than at every call. The tests of this class can prove that a tally started here breaks after the
     * right number of crossings at any speed. They cannot prove that the engine hands it the family's
     * range unscaled; only the scaled-once case of the dedicated-server trample probe sees that.
     */
    public static ErosionEntry start(SurfaceFamily family, float min, float max, float roll, int nowSeconds) {
        return new ErosionEntry(family, draw(min, max, roll), nowSeconds);
    }

    /**
     * Whether a stored threshold could have been drawn from {@code [min, max]} by {@link #draw}.
     *
     * <p>
     * Records saved before 0.9.205 can hold one of two wrong numbers. A protected record that went
     * through the healing sweep's strip branch holds nought, and a tally drawn while
     * general.erosionSpeed times general.globalSpeed was anything but one holds a draw already divided
     * by that speed. The engine redraws anything this rejects and keeps its wear. The nought is always
     * rejected, and so is every draw baked at a combined speed above one and a half or below two
     * thirds, because at those speeds nothing drawn from sixteen to twenty-four can land back inside
     * it. Between those two speeds a baked draw can land inside the range, and it is kept: it is a
     * number the new code could have drawn itself, and nothing about it says otherwise.
     *
     * <p>
     * The range is fixed today. If it ever becomes configurable, changing it will redraw every
     * existing tally that falls outside the new range, keeping its wear - the right outcome, but one
     * worth knowing about before it happens.
     */
    public static boolean isFaceValue(float stored, float min, float max) {
        if (Float.isNaN(stored)) return false;
        float lowest = draw(min, max, 0f);
        float highest = draw(min, max, 1f);
        return stored >= lowest - TOLERANCE && stored <= highest + TOLERANCE;
    }

    /**
     * How much wear a tally sheds in an in-game day: the decay fraction times the threshold as it
     * stands.
     *
     * <p>
     * Measured against the effective threshold, speeds and reinforcement included, so a full tally
     * takes the same number of days to clear at any speed. The speeds change how much traffic a plant
     * takes, not how long it remembers that traffic. Nought when either figure is nought or below,
     * which is how a healing.wearDecayPerDay of 0 comes to mean that a tally never fades at all.
     */
    public static double perDay(double fractionPerDay, float effectiveThreshold) {
        if (!(fractionPerDay > 0d) || !(effectiveThreshold > 0f)) return 0d;
        return fractionPerDay * effectiveThreshold;
    }

    /**
     * What a tally's wear has faded to after so many seconds, never below nought.
     *
     * <p>
     * A straight line, with no weather in it and no stages to step back through, so fading over one
     * long span gives the same figure as fading over any number of shorter spans that add up to it.
     * The engine leans on that twice: a chunk left unloaded for a month catches up in a single pass,
     * and a fade that is never saved is worked out again, to the same figure, the next time the chunk
     * is looked at. Wear comes back unchanged when no time has passed, when nothing fades, or when
     * there is no wear to lose.
     */
    public static float faded(float wear, double perDay, int elapsedSeconds) {
        if (elapsedSeconds <= 0 || !(perDay > 0d) || wear <= 0f) return wear;
        double left = wear - perDay * elapsedSeconds / SECONDS_PER_DAY;
        return left <= 0d ? 0f : (float) left;
    }

    /**
     * Fades a tally from its last touch up to now.
     *
     * <p>
     * The clock is moved only when the wear moves. Restamping after a span too short to change a float
     * would throw that span away, and a sweep calling often enough could then hold a tally at its wear
     * indefinitely. With no time passed, or a fraction of nought, neither the wear nor the clock is
     * touched - so with healing.wearDecayPerDay at 0 this never does anything.
     *
     * @return whether the wear changed
     */
    public static boolean fade(ErosionEntry tally, int nowSeconds, double fractionPerDay) {
        int elapsed = nowSeconds - tally.getLastTouchedSeconds();
        if (elapsed <= 0) return false;
        float before = tally.getWear();
        float after = faded(before, perDay(fractionPerDay, tally.effectiveThreshold()), elapsed);
        if (after == before) return false;
        tally.setWear(after);
        tally.setLastTouchedSeconds(nowSeconds);
        return true;
    }

    /**
     * Counts one crossing against a tally and says whether that crossing breaks it.
     *
     * <p>
     * The fade comes first, and has to. {@link ErosionEntry#recordStep} restamps the clock, so a
     * crossing counted without fading first would throw away every second of recovery since the sweep
     * last reached this chunk, and on a busy server the sweep reaches each chunk only every so often.
     *
     * @param fades false while healing is switched off, in which case nothing is shed and every
     *              crossing counts for good
     * @return true when the wear has reached the effective threshold, meaning the block should break
     */
    public static boolean cross(ErosionEntry tally, float amount, int nowSeconds, double fractionPerDay,
        boolean fades) {
        if (fades) fade(tally, nowSeconds, fractionPerDay);
        tally.recordStep(amount, nowSeconds);
        return tally.thresholdReached();
    }

    /**
     * How many crossings of one weight a fresh tally takes to break, with no fading between them.
     *
     * <p>
     * Counted rather than divided: it adds in floats and compares with {@code >=}, exactly as
     * {@link ErosionEntry#recordStep} and {@link ErosionEntry#thresholdReached()} do, so the answer is
     * the one the game reaches and not one that rounding puts a crossing either side of it.
     *
     * @return the number of crossings, or -1 when no number of them ever breaks it - a crossing that
     *         weighs nothing, a threshold that is not a finite number, or a threshold so large that
     *         adding one more crossing no longer changes a float
     */
    public static int crossingsToBreak(float effectiveThreshold, float perCrossing) {
        if (!(perCrossing > 0f) || Float.isNaN(effectiveThreshold) || Float.isInfinite(effectiveThreshold)) {
            return -1;
        }
        float wear = 0f;
        int crossings = 0;
        while (true) {
            float next = wear + perCrossing;
            crossings++;
            if (next >= effectiveThreshold) return crossings;
            if (next == wear) return -1;
            wear = next;
        }
    }

    /**
     * How many crossings an in-game day a block must see before its tally outruns its fade.
     *
     * <p>
     * Crossed less often than this, the fade between two crossings is more than one crossing adds, so
     * the tally never holds more than a single crossing's weight and the block stands for good. Crossed
     * more often, the tally climbs and the block goes in the end. Nought when nothing fades, because
     * then any traffic at all breaks it eventually, and positive infinity when a crossing weighs
     * nothing, because then no traffic ever does.
     */
    public static double breakEvenPerDay(double fractionPerDay, float effectiveThreshold, float perCrossing) {
        if (!(perCrossing > 0f)) return Double.POSITIVE_INFINITY;
        return perDay(fractionPerDay, effectiveThreshold) / perCrossing;
    }
}
