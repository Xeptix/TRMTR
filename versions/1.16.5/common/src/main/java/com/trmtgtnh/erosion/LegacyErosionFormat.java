package com.trmtgtnh.erosion;

import com.trmtgtnh.config.FamilySettings;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.surface.SurfaceFamily;

/**
 * Reads wear that was written before a position had a depth of its own.
 *
 * <p>
 * The old record held a family and a stage, and how far the ground had physically dropped was
 * worked out from that stage's place in its family's run: the last few stages sank, the earlier
 * ones only discolored. Depth is a stored number now, so the conversion has to answer two
 * questions that used to be one — how far down is this, and how worn does it look.
 *
 * <p>
 * Both answers come from the same place the old code got them, which is the point: this asks the
 * previous version's own arithmetic what a stage meant rather than guessing, so a path that was
 * three pixels deep before the update is three pixels deep after it.
 */
final class LegacyErosionFormat {

    /** The stage counts the previous version shipped, by family ordinal. */
    private static final int[] OLD_STAGES = new int[SurfaceFamily.values().length];

    /** The sink ceilings it shipped, in sixteenths. */
    private static final int[] OLD_MAX_SINK = new int[SurfaceFamily.values().length];

    /** Where in a family's run sinking used to begin, as a fraction. */
    private static final float[] OLD_SINK_START = new float[SurfaceFamily.values().length];

    static {
        put(SurfaceFamily.GRASS, 16, 0, 1.0f);
        put(SurfaceFamily.DIRT, 8, 8, 0.0f);
        put(SurfaceFamily.SAND, 8, 6, 0.35f);
        put(SurfaceFamily.GRAVEL, 6, 5, 0.4f);
        put(SurfaceFamily.COBBLE, 5, 2, 0.6f);
        put(SurfaceFamily.STONE, 5, 2, 0.6f);
    }

    private LegacyErosionFormat() {}

    private static void put(SurfaceFamily family, int stages, int maxSink, float sinkStart) {
        OLD_STAGES[family.ordinal()] = stages;
        OLD_MAX_SINK[family.ordinal()] = maxSink;
        OLD_SINK_START[family.ordinal()] = sinkStart;
    }

    /**
     * Turns one old record into a current one, or null if it described nothing usable.
     *
     * <p>
     * The wear and the threshold come across untouched. They are a fraction of the way to the
     * next step and the size of that step, and both still mean exactly that.
     */
    static ErosionEntry upgrade(SurfaceFamily family, int stage, float wear, float threshold, int touched) {
        if (family == null || stage < 0) return null;
        int ordinal = family.ordinal();
        int oldStages = ordinal < OLD_STAGES.length && OLD_STAGES[ordinal] > 0 ? OLD_STAGES[ordinal] : 8;
        if (stage >= oldStages) stage = oldStages - 1;

        int sink = legacySink(ordinal, stage, oldStages);
        int layer = legacyLayer(family, stage, oldStages);

        ErosionEntry entry = new ErosionEntry(family, threshold, touched);
        entry.setAppearance(family, layer, threshold);
        entry.setWear(wear);
        entry.setSink(sink);
        entry.setLastTouchedSeconds(touched);
        return entry;
    }

    /** What the previous version's sink profile would have rendered for this stage. */
    private static int legacySink(int ordinal, int stage, int oldStages) {
        int maxSink = ordinal < OLD_MAX_SINK.length ? OLD_MAX_SINK[ordinal] : 0;
        if (maxSink <= 0) return 0;

        float first = OLD_SINK_START[ordinal] * (oldStages - 1);
        if (stage < first) return 0;
        if (oldStages - 1 <= first) return ErosionState.clampSink(maxSink);

        float progress = (stage - first) / ((oldStages - 1) - first);
        return ErosionState.clampSink(1 + Math.round(progress * (maxSink - 1)));
    }

    /**
     * Rescales an old stage onto the current run of visual layers.
     *
     * <p>
     * A stage two of five and a layer two of sixteen are not the same amount of wear, so this
     * maps the position along the run rather than the number itself. Families that kept their
     * count — grass at sixteen — come through unchanged.
     */
    private static int legacyLayer(SurfaceFamily family, int stage, int oldStages) {
        FamilySettings settings = TrmtConfig.family(family);
        int layers = settings == null ? oldStages : Math.max(1, settings.stages);
        if (oldStages <= 1) return 0;
        int mapped = Math.round(stage * (layers - 1) / (float) (oldStages - 1));
        if (mapped < 0) return 0;
        return mapped >= SurfaceFamily.MAX_STAGES ? SurfaceFamily.MAX_STAGES - 1 : mapped;
    }
}
