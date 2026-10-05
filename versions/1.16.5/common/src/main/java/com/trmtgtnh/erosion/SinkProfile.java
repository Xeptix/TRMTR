package com.trmtgtnh.erosion;

import com.trmtgtnh.config.FamilySettings;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.surface.SurfaceFamily;
import com.trmtgtnh.surface.SurfaceShape;

/**
 * How far a worn surface has physically sunk.
 *
 * <p>
 * Upstream lets a path wear down as well as discolour: eroded sand drops from a full block to
 * ten pixels. It can afford that because it replaces the block, so its server agrees the block
 * is short. Here the block in the world is still full-height grass, so the depth has to be
 * derived identically on both sides from the one thing both sides know — the wear stage.
 *
 * <p>
 * Two depths come out of this, and they are deliberately different:
 *
 * <ul>
 * <li>The <b>rendered</b> depth ramps up across the sinking stages, so a path visibly deepens
 * as it wears.</li>
 * <li>The <b>collision</b> depth follows it exactly. An earlier version held collision flat at
 * the deepest value across every sinking stage, on the reasoning that a patchwork of
 * neighbouring stages would otherwise be a staircase. That was tolerable while the deepest rut
 * was a quarter of a block; at half a block it would mean standing seven sixteenths inside
 * ground that still looks solid. The staircase it avoided is now one-sixteenth risers, which is
 * nothing next to that.</li>
 * </ul>
 */
public final class SinkProfile {

    /**
     * Ceiling on how far anything may sink, in sixteenths of a block.
     *
     * <p>
     * Fifteen, because the record holds the depth in four bits and one of the sixteen values it
     * can take is "flat". Sixteen is not a depth anyway - it is the block being gone, which is
     * {@link GroundGivesWay}'s business rather than this one's.
     *
     * <p>
     * The number worth knowing is not this one but eight, which is what every family ships at and
     * what anything above should be chosen deliberately. Two separate engine limits sit exactly at
     * half a block. A player's step height is 0.5, so a rut eight pixels deep can be climbed out
     * of with no margin at all and a ninth pixel cannot be climbed out of at all. And mob
     * path-following rounds an entity's height to a node by adding a half and truncating: at eight
     * pixels that still lands on the node the mob is walking along, and at nine it lands one below,
     * at which point mobs truncate their path every tick. Past eight this is a pit rather than a
     * road, which is a thing somebody may well want and is not a thing to arrive at by accident.
     */
    public static final int MAX_SINK_PIXELS = 15;

    /** What every family ships at, and the deepest rut a player can still walk out of. */
    public static final int WALKABLE_SINK_PIXELS = 8;

    private SinkProfile() {}

    /**
     * Rendered depth in sixteenths for a stage, or 0 if this stage has not begun sinking.
     *
     * @param appearance which wear appearance is showing
     * @param stage      the stage within that appearance
     */
    public static int shown(SurfaceFamily appearance, int storedSink) {
        return shown(appearance, storedSink, SurfaceShape.FULL);
    }

    /**
     * The same, for ground that does not fill its block.
     *
     * <p>
     * Half the depth for a slab, and that is the whole of what a slab needs done differently. The
     * chain is untouched, so a slab takes exactly as many crossings to wear through as the block
     * it was cut from and exactly as long to recover; only the picture is drawn shallower, because
     * half a block of stone cannot lose eight pixels and still be there.
     */
    public static int shown(SurfaceFamily appearance, int storedSink, SurfaceShape shape) {
        if (!TrmtConfig.physicalDecayShows()) return 0;
        FamilySettings settings = TrmtConfig.family(appearance);
        // Held against what this family is configured to reach as well as the global ceiling, so
        // lowering a family's depth takes effect on ground that already sank past it rather than
        // waiting for that ground to heal first.
        int allowed = settings == null ? MAX_SINK_PIXELS : settings.maxSinkPixels;
        if (shape != null && shape.isHalfDepth()) allowed /= 2;
        int depth = clamp(storedSink);
        return depth > allowed ? clamp(allowed) : depth;
    }

    /**
     * Collision depth in sixteenths: what is drawn, and zero unless the server has said
     * collision actually follows the visuals.
     */
    public static int collides(SurfaceFamily appearance, int storedSink) {
        return collides(appearance, storedSink, SurfaceShape.FULL);
    }

    /** As above, for ground that does not fill its block. */
    public static int collides(SurfaceFamily appearance, int storedSink, SurfaceShape shape) {
        if (!TrmtConfig.physicalDecayCollides()) return 0;
        return shown(appearance, storedSink, shape);
    }

    /** True when any stage of this family ever sinks, so callers can skip the maths. */
    public static boolean familySinks(SurfaceFamily appearance) {
        FamilySettings settings = TrmtConfig.family(appearance);
        return settings != null && settings.maxSinkPixels > 0 && TrmtConfig.physicalDecayShows();
    }

    private static int clamp(int pixels) {
        if (pixels < 0) return 0;
        return pixels > MAX_SINK_PIXELS ? MAX_SINK_PIXELS : pixels;
    }

    /** Block-space height for a depth in sixteenths. */
    public static double heightFor(int sinkPixels) {
        return (16 - clamp(sinkPixels)) / 16.0D;
    }
}
