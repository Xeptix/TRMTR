package com.trmtgtnh.client.settings;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Which numbers slide, and how a slider's position becomes a number (0.9.228; spec M7).
 *
 * <p>
 * A whole number slides when both its bounds are set and they are at most 100 apart, so every step is a pixel or more
 * on a 100-pixel bar; a decimal slides when it lies between 0 and 1 - chances, fractions, strengths - and moves in
 * hundredths of its span's order. {@code client.wearGradations} and {@code client.wearRotations} slide whatever their
 * bounds say, as they have since 0.9.228's first screen. Everything else is a field, and every slider has its typed
 * field beside it, so a number the bar cannot land on can still be typed.
 */
public final class SliderRule {

    /** Every setting that slides in 1.7.10's file, as {@code category.key}: the rule's answer, held by its test. */
    public static final String[] EXPECTED_1710 = { "client.wearGradations", "client.wearRotations",
        "client.wearCarriesWithDepth", "client.sideWearFraction", "client.desirePathHighlight",
        "client.layeredBlockShellLift", "surfaces.mapWearDarkening", "healing.wearDecayPerDay",
        "trampling.vegetationDropChance", "trampling.leavesDropChance", "trampling.groundCoverDropChance",
        "golem.wearDropChance", "general.bonemealRadius", "general.chunkTamperDefaultReach",
        "general.chunkTamperMaxReach", "general.chunkTamperMaxSteps", "general.tamperCooldownTicks",
        "general.tamperMendCost", "general.tamperPatchCost", "golem.baseRadius", "golem.blockCost", "golem.greenBand",
        "golem.guardRange", "golem.maxRadius", "golem.storeReach", "golem.targetLayers", "golem.workReach",
        "loot.chunkTamper", "loot.draughtHeavyFoot", "loot.draughtLightness", "loot.enchantBook", "loot.golemEgg",
        "loot.golemOmniUpgrade", "loot.golemUpgrade", "loot.guideBook", "loot.guideBookRare", "loot.tamper",
        "loot.wayfarer", "pathlight.cost", "pathlight.level", "performance.movementSampleTicks", "reinforce.maxLevel",
        "spawnward.cost", "families.grass.layersPerDepth", "families.grass.maxSinkPixels", "families.grass.stages",
        "families.dirt.layersPerDepth", "families.dirt.maxSinkPixels", "families.dirt.stages",
        "families.sand.layersPerDepth", "families.sand.maxSinkPixels", "families.sand.stages",
        "families.gravel.layersPerDepth", "families.gravel.maxSinkPixels", "families.gravel.stages",
        "families.stone.layersPerDepth", "families.stone.maxSinkPixels", "families.stone.stages",
        "families.cobble.layersPerDepth", "families.cobble.maxSinkPixels", "families.cobble.stages",
        "families.nether.layersPerDepth", "families.nether.maxSinkPixels", "families.nether.stages",
        "families.end.layersPerDepth", "families.end.maxSinkPixels", "families.end.stages",
        "families.snow.layersPerDepth", "families.snow.maxSinkPixels", "families.snow.stages",
        "families.ice.layersPerDepth", "families.ice.maxSinkPixels", "families.ice.stages",
        // Bounded in the file and sliding by the rule, and missing from the table until 0.9.228 (found by P2a).
        "client.overlayDistanceChunks", "general.maxWearFraction", "general.wearDropChance" };

    /** The widest a whole number's range may be and still slide. */
    public static final int WHOLE_SPAN = 100;

    private SliderRule() {}

    /**
     * Whether a setting slides.
     *
     * @param kind {@code 'I'} whole; {@code 'D'} or {@code 'F'} decimal
     */
    public static boolean slides(char kind, String min, String max, String category, String key) {
        if ("client".equals(category) && ("wearGradations".equals(key) || "wearRotations".equals(key))) return true;
        BigDecimal low = NumberWords.bound(min);
        BigDecimal high = NumberWords.bound(max);
        if (low == null || high == null || high.compareTo(low) <= 0) return false;
        if (kind == 'I') return high.subtract(low)
            .compareTo(new BigDecimal(WHOLE_SPAN)) <= 0;
        if (kind == 'D' || kind == 'F') return low.signum() >= 0 && high.compareTo(BigDecimal.ONE) <= 0;
        return false;
    }

    /**
     * What a slider snaps to: 1 for a whole number; for a decimal a hundredth of the power of ten its span rounds up to
     * - 0.01 for a span of 1 or of 0.9, 0.001 for a span of 0.05.
     */
    public static double step(char kind, double min, double max) {
        return stepDecimal(kind, min, max).doubleValue();
    }

    static BigDecimal stepDecimal(char kind, double min, double max) {
        if (kind == 'I') return BigDecimal.ONE;
        double span = max - min;
        if (!(span > 0)) return new BigDecimal("0.01");
        int power = (int) Math.ceil(Math.log10(span) - 1e-9);
        return BigDecimal.ONE.movePointRight(power)
            .movePointLeft(2);
    }

    /** Where a value stands along the bar, nought to one, clamped. */
    public static double fraction(double value, double min, double max) {
        if (!(max > min)) return 0D;
        double at = (value - min) / (max - min);
        return at < 0D ? 0D : at > 1D ? 1D : at;
    }

    /** The value a position along the bar stands for, snapped to the step and inside the bounds. */
    public static double value(double fraction, double min, double max, char kind) {
        double placed = fraction < 0D ? 0D : fraction > 1D ? 1D : fraction;
        BigDecimal step = stepDecimal(kind, min, max);
        BigDecimal low = BigDecimal.valueOf(min);
        BigDecimal high = BigDecimal.valueOf(max);
        double raw = min + placed * (max - min);
        BigDecimal steps = BigDecimal.valueOf(raw)
            .subtract(low)
            .divide(step, 0, RoundingMode.HALF_UP);
        BigDecimal snapped = low.add(steps.multiply(step));
        if (snapped.compareTo(low) < 0) snapped = low;
        if (snapped.compareTo(high) > 0) snapped = high;
        return snapped.doubleValue();
    }
}
