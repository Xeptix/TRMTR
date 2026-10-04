package com.trmtgtnh.client.model;

import com.trmtgtnh.config.FamilySettings;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.erosion.ErosionState;
import com.trmtgtnh.surface.SurfaceFamily;
import com.trmtgtnh.surface.WearScale;

/**
 * Which of a surface's eighty pictures a record is drawn with.
 *
 * <p>
 * The other edition's {@code GhostRendering} carries this arithmetic, and it is carried here almost word for word.
 * What changed is only where the record comes from: there, a method was handed a position and asked the proxy for the
 * record at it; here the ghost's model is handed a block state, not a place, and the record arrives already read,
 * inside that state. So every method takes the record rather than three coordinates. The reasoning each one carries
 * is the other edition's, kept because it is the part worth having.
 *
 * <p>
 * The space is the counted one - eighty steps along a family's whole chain, whatever the atlas could afford to draw -
 * because {@code WearTextures.icon} asks in it and maps it onto the pictures it has.
 */
public final class WearSteps {

    /**
     * The share of the pictures the first run is given, before the ground has sunk at all.
     *
     * <p>
     * The first sixteen steps are the only ones with nothing but the picture to speak with: every later run drops the
     * block a pixel on its way past, and a rut with walls on four sides carries its own reading. So the first run gets
     * more than its share of the ramp. See {@link #eased}.
     */
    private static final float SHALLOW_SHARE = 0.40f;

    private WearSteps() {}

    /**
     * How far a square's picture has sunk, as a fraction of a block, for a ghost of a given outline.
     *
     * <p>
     * Here rather than in the model so that the depth and the shape it is held against are worked out in one
     * place: a shape half a block thick may only sink half as far, which is a fact about the shape and not
     * about the record.
     */
    public static float sunk(short record, int outline) {
        return com.trmtgtnh.block.BlockGhost.drawnSink(record, com.trmtgtnh.block.BlockGhost.shapeOf(outline)) / 16F;
    }

    /**
     * A layer within one run, spread onto the counted space, for a record the arithmetic below cannot place.
     *
     * <p>
     * Spread rather than copied, so the last gradation of a run is the last picture of the run whatever length
     * somebody has given it.
     */
    static int counted(int layer, FamilySettings settings) {
        if (layer <= 0) return 0;
        int runLast = (settings == null ? SurfaceFamily.MAX_STAGES : Math.max(1, settings.stages)) - 1;
        if (runLast <= 0) return 0;
        int last = WearScale.COUNTED_STEPS - 1;
        int placed = Math.round(layer / (float) runLast * last);
        return placed > last ? last : placed;
    }

    /**
     * The counted step a record's top is drawn at.
     *
     * <p>
     * With {@code client.wearNeverStepsBack}, each run gets its own slice of the pictures and fills it from one end to
     * the other. A block still wears visibly across its own run, and a deeper block is still always the more worn, but
     * the two readings can no longer disagree - adding them is what let the restart at a depth boundary outweigh the
     * rise and send the ground back toward clean as it dropped.
     */
    public static int topLayer(SurfaceFamily appearance, short record) {
        int layer = ErosionState.layerOf(record);
        FamilySettings settings = TrmtConfig.family(appearance);
        if (settings == null || TrmtConfig.wearCarriesWithDepth <= 0f) return counted(layer, settings);
        if (record == ErosionState.NONE) return counted(layer, settings);

        int last = WearScale.COUNTED_STEPS - 1;
        int sink = ErosionState.sinkOf(record);
        int runLast = Math.max(1, sink == 0 ? settings.stages : settings.layersPerDepth) - 1;
        float withinRun = runLast <= 0 ? 1f : layer / (float) runLast;

        if (TrmtConfig.wearNeverStepsBack) {
            float from = eased(runStart(settings, sink), settings);
            float to = eased(sink >= settings.maxSinkPixels ? 1f : runStart(settings, sink + 1), settings);
            int placed = Math.round((from + withinRun * (to - from)) * last);
            if (placed < 0) return 0;
            return placed > last ? last : placed;
        }
        float overall = eased(
            ErosionState.progressOf(record, settings.stages, settings.layersPerDepth, settings.maxSinkPixels),
            settings);

        float carry = TrmtConfig.wearCarriesWithDepth;
        int blended = Math.round((overall * carry + withinRun * (1f - carry)) * last);
        if (blended < 0) return 0;
        return blended > last ? last : blended;
    }

    /**
     * How worn a side face looks: from how far the ground has come overall, not from the layer it happens to be
     * showing, which restarts every time the ground drops a pixel. Scaled down, so the wall stays behind the floor.
     */
    public static int sideLayer(SurfaceFamily appearance, short record) {
        FamilySettings settings = TrmtConfig.family(appearance);
        if (settings == null || record == ErosionState.NONE) return 0;
        float progress = eased(
            ErosionState.progressOf(record, settings.stages, settings.layersPerDepth, settings.maxSinkPixels),
            settings);
        int last = WearScale.COUNTED_STEPS - 1;
        int layer = Math.round(progress * last * TrmtConfig.sideWearFraction);
        if (layer < 0) return 0;
        return layer > last ? last : layer;
    }

    /** Where a run begins, as a fraction of the whole chain - {@code ErosionState.progressOf}'s own arithmetic. */
    static float runStart(FamilySettings settings, int sink) {
        int first = Math.max(1, settings.stages);
        int per = Math.max(1, settings.layersPerDepth);
        int index = sink == 0 ? 0 : first + (sink - 1) * per;
        int total = first + Math.max(0, settings.maxSinkPixels) * per;
        if (total <= 1) return 0f;
        float fraction = index / (float) (total - 1);
        if (fraction < 0f) return 0f;
        return fraction > 1f ? 1f : fraction;
    }

    /**
     * Where a chain position sits among the drawn pictures: the first run given {@link #SHALLOW_SHARE} of them, the
     * rest the remainder. A family that never sinks is handed straight back - grass's whole chain is its first run,
     * and it gets every picture.
     */
    static float eased(float progress, FamilySettings settings) {
        if (progress <= 0f) return 0f;
        if (progress >= 1f) return 1f;
        float firstRunEnd = runStart(settings, 1);
        if (firstRunEnd <= 0f || firstRunEnd >= 1f) return progress;
        if (progress <= firstRunEnd) return SHALLOW_SHARE * (progress / firstRunEnd);
        return SHALLOW_SHARE + (1f - SHALLOW_SHARE) * ((progress - firstRunEnd) / (1f - firstRunEnd));
    }
}
