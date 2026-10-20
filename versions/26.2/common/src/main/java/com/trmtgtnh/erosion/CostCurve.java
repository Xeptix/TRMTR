package com.trmtgtnh.erosion;

import java.util.Locale;

/**
 * How the price of a gradation changes as ground wears further down its run.
 *
 * <p>
 * Every step used to cost the same: one threshold drawn from one flat range, from the first
 * gradation to the eightieth. That is not what any of these surfaces do. A single footfall shows in
 * fresh powder and the next one shows far less, because the first one packed it; sand and gravel
 * displace freely and then compact; turf tears at a touch and the earth underneath does not. The
 * flat range was also the reason early wear felt inert, because the gradation a player actually
 * watches appear - the first - cost exactly as much as the last.
 *
 * <p>
 * A curve is a straight ramp from its own start to a ceiling, reached at some fraction of the way
 * through and held flat after it. Two numbers describe one entirely: how many times dearer the
 * ceiling is, and where it arrives. A ceiling that arrives at the very end is a plain straight line
 * with no plateau at all, which is what ice wants.
 *
 * <p>
 * <b>Every curve is normalised, and that is the load-bearing decision here.</b> The multipliers over
 * a whole run average to exactly one, so a curve only ever redistributes what a run costs and never
 * changes the total. Which means the balance table holds whatever curve a family is given, and
 * switching one can never quietly retune a family's difficulty while appearing to be a change of
 * shape. Normalised against the actual number of gradations rather than against the continuous
 * integral, because a chain is a few dozen discrete steps and the two answers are not the same.
 */
public enum CostCurve {

    /**
     * No change along the run, which is what every family did before curves existed.
     *
     * <p>
     * Right for dressed stone and what it is made into. Stone does not compact underfoot and does
     * not pack down: a scuff is a scuff, and the hundredth costs what the first did.
     */
    FLAT("flat", 1f, 1f),

    /**
     * Turf, and the earth under it. Gentle.
     *
     * <p>
     * Sod tears at a touch - which is why a shortcut across a lawn shows before anything else in
     * this list - and the earth it exposes is packed and rooted and gives up far less easily.
     */
    SOD("sod", 1.8f, 0.6f),

    /**
     * Loose grains and loose stones. Moderate.
     *
     * <p>
     * Sand and gravel displace under the first passes rather than resisting them, and then settle
     * into something that has already found its shape and does not want another.
     */
    SETTLE("settle", 2.5f, 0.7f),

    /**
     * Snow. Steep, and it stops climbing near the end.
     *
     * <p>
     * One footfall in fresh powder is a whole gradation. What that footfall leaves is packed snow,
     * which is a different substance and takes several passes to move at all - and once it is
     * thoroughly packed it stops getting harder, because there is nothing left to compress.
     */
    PACK("pack", 4f, 0.75f),

    /**
     * Ice, and nothing else. The steepest here, and the only one that never levels off.
     *
     * <p>
     * A twelvefold climb in a straight line from first gradation to last. Ice does not compact and
     * then settle the way snow does; scuffing it merely polishes what is left, so every gradation
     * is dearer than the one before it right to the end of the run. Four passes for the first,
     * something near fifty for the last.
     */
    GLAZE("glaze", 12f, 1f);

    /** Longest chain the mean is worth caching for. Beyond it the figure is worked out each time. */
    private static final int CACHE_TO = 512;

    private final String key;

    /** How many times dearer the last gradation is than the first, before normalising. */
    private final float rise;

    /** Where the ceiling arrives, as a fraction of the run. One means at the very end. */
    private final float knee;

    /** Discrete means by chain length, filled as lengths are met. Nought means not yet worked out. */
    private final float[] means = new float[CACHE_TO + 1];

    CostCurve(String key, float rise, float knee) {
        this.key = key;
        this.rise = rise;
        this.knee = knee;
    }

    public String key() {
        return key;
    }

    /** How many times dearer the end of a run is than its start. One for the flat curve. */
    public float rise() {
        return rise;
    }

    /**
     * The multiplier on a gradation's price, at this position along a run of this length.
     *
     * @param index  which gradation, counting from nought at untouched ground
     * @param length how many gradations the whole run has, not how many this world allows - a
     *               ceiling on wear shortens what is reachable and must not reshape what is priced
     */
    public float at(int index, int length) {
        if (rise == 1f || length <= 1) return 1f;
        int step = index < 0 ? 0 : (index >= length ? length - 1 : index);
        return raw(step / (float) (length - 1)) / mean(length);
    }

    /** The ramp itself, before it is held to an average of one. */
    private float raw(float progress) {
        float along = knee <= 0f ? 1f : progress / knee;
        if (along > 1f) along = 1f;
        return 1f + (rise - 1f) * along;
    }

    /**
     * What the ramp averages over a run of this length.
     *
     * <p>
     * Worked out across the actual gradations rather than by integrating the line, because a run is
     * a few dozen discrete steps and the continuous answer is close but not equal - and close is
     * not good enough for a figure whose whole purpose is that the total does not move.
     */
    private float mean(int length) {
        boolean cacheable = length <= CACHE_TO;
        if (cacheable) {
            float held = means[length];
            if (held > 0f) return held;
        }
        float total = 0f;
        for (int i = 0; i < length; i++) {
            total += raw(i / (float) (length - 1));
        }
        float mean = total / length;
        if (mean <= 0f) mean = 1f;
        if (cacheable) means[length] = mean;
        return mean;
    }

    /** The curve of this name, or null when nothing answers to it. */
    public static CostCurve byKey(String name) {
        if (name == null) return null;
        String wanted = name.trim()
            .toLowerCase(Locale.ROOT);
        for (CostCurve curve : values()) {
            if (curve.key.equals(wanted)) return curve;
        }
        return null;
    }

    /** Every name a config file may use, for the settings screen's own list. */
    public static String[] keys() {
        CostCurve[] all = values();
        String[] names = new String[all.length];
        for (int i = 0; i < all.length; i++) names[i] = all[i].key;
        return names;
    }
}
