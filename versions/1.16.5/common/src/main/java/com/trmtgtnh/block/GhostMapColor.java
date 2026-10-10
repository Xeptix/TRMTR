package com.trmtgtnh.block;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MaterialColor;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.surface.SurfaceFamily;

/**
 * The one darker color a worn square reports to a map, and how it is chosen.
 *
 * <p>
 * <strong>What this version cannot do, and what it can.</strong> 1.7.10 hands JourneyMap an RGB value
 * per position and darkens a worn square by any fraction of its own color, so a road fades smoothly
 * from fresh to deeply trodden on a minimap. Every map at this version reads one vanilla answer -
 * {@code getMapColor}, handed the position - and that answer is one of sixty-four fixed palette
 * entries. There is no fraction to give and no darker sibling of a color to pick: the palette is
 * what it is.
 *
 * <p>
 * The first answer to that was to give up the darkening entirely, which is what this edition shipped:
 * a worn square reported the color of whatever it stood in for, so a path showed on a map only once
 * it had worn through into a different material, and the four map settings said so in the log and did
 * nothing. That is honest and it is not good enough - <em>a path you cannot see on the map is the
 * feature missing</em>, and the loss that matters is the road being invisible rather than the shade
 * being approximate.
 *
 * <p>
 * So this settles for one color. A worn square that shows at all reports the palette entry nearest
 * to its own color darkened by {@code surfaces.mapWearDarkening} - the same figure, read the same
 * way, applied once at full strength rather than scaled along the run. A path is visible from the
 * first gradation that shows and does not deepen after it, which is the whole of the compromise and
 * is worth stating plainly: <strong>the road is there, the wear is not readable from it.</strong>
 *
 * <p>
 * <p>
 * Beside the block rather than among the client's drawing, because it is a block's answer to a
 * question and {@code /trmt mapcolor} asks it from a server thread.
 *
 * <p>
 * Chosen rather than listed, because the ground this mod wears is whatever a pack supplies. The
 * nearest entry is taken by squared distance in RGB, among the entries that are actually darker than
 * what they stand in for - without that last condition the nearest entry to a darkened green is often
 * the green it came from, and the answer is no change at all.
 */
public final class GhostMapColor {

    private GhostMapColor() {}

    /** Answers already worked out, by palette id and the darkening they were worked out for. */
    private static final Map<Long, MaterialColor> CHOSEN = new ConcurrentHashMap<Long, MaterialColor>();

    /** Said once, naming what a family's worn color came out as. */
    private static final Map<Integer, Boolean> SAID = new ConcurrentHashMap<Integer, Boolean>();

    /**
     * How much darker a square has to be before it counts as darker at all.
     *
     * <p>
     * Four per cent of full brightness. Below that the eye reads two entries as the same color on a
     * map tile a few pixels across, and picking one would spend a color to no effect.
     */
    private static final int ENOUGH = 10;

    /** What a square with no family is given where a color has to be given - the 1.7.10 edition's grey. */
    public static final int NO_FAMILY = 0x7F7F7F;

    /**
     * The color a map draws a family in when it has nothing finer to go on, or null where there is no family.
     *
     * <p>
     * The 1.7.10 edition's {@code GhostLogic.mapColor(SurfaceFamily)}: the map color of the vanilla block the family
     * stands for ({@link GhostFamily#standIn}, that edition's {@code vanillaCounterpart}), except gravel, which reads as
     * stone, and end stone, which reads as sand - there vanilla's own answer names the wrong material, and a map of worn
     * ground must never say the material changed. This version's gravel and end stone already answer so, and the two
     * lines stay so the rule does not hang on that. One rule for every map answer this edition gives: the block's own
     * map color, and JourneyMap's covered, fade and palette answers (0.9.222, spec CO19, CO20, CO21, CO25).
     */
    public static MaterialColor ofFamily(SurfaceFamily family) {
        if (family == null) return null;
        if (family == SurfaceFamily.GRAVEL) return MaterialColor.STONE;
        if (family == SurfaceFamily.END) return MaterialColor.SAND;
        return GhostFamily.standIn(family)
            .getMapColor(null, null);
    }

    /**
     * As {@link #ofFamily(SurfaceFamily)}, told whether the square is drawn untinted: grass drawn untinted is bare
     * earth, and reads as dirt - the 1.7.10 edition's {@code GhostLogic.mapColor(appearance, untinted)}, which says
     * reporting turf for it was the map calling a dirt path a lawn (0.9.222, spec CO25).
     */
    public static MaterialColor ofFamily(SurfaceFamily family, boolean untinted) {
        if (untinted && family == SurfaceFamily.GRASS) return ofFamily(SurfaceFamily.DIRT);
        return ofFamily(family);
    }

    /**
     * The family's map color as a plain 0xRRGGBB, grey only where there is no family - the 1.7.10 edition's
     * {@code GhostLogic.fallbackMapColor}, the form JourneyMap is handed (0.9.222, spec CO19, CO20, CO21).
     */
    public static int rgbOf(SurfaceFamily family) {
        MaterialColor color = ofFamily(family);
        return color == null ? NO_FAMILY : color.col;
    }

    /**
     * The covered block's own vanilla map color as 0xRRGGBB, or its family's where it will not say - the second and
     * third answers of the 1.7.10 edition's {@code JourneyMapColors.originColor}, after JourneyMap's own (0.9.222, spec
     * CO19). Asked through a view that shows the covered block at its square, as every other question this edition
     * puts to a covered block is, so a block that looks at its own square sees itself rather than the ghost.
     */
    public static int coveredRgb(BlockState covered, BlockGetter world, BlockPos pos, SurfaceFamily family) {
        try {
            MaterialColor own = covered.getMapColor(world == null ? null : new OriginView(world, pos, covered), pos);
            if (own != null) return own.col;
        } catch (RuntimeException awkwardBlock) {
            // A block of somebody else's that will not say. Its family's color is the next answer.
        }
        return rgbOf(family);
    }

    /**
     * The color a worn square should report, given the color of what it stands in for.
     *
     * <p>
     * The covered color itself where the setting is off, where nothing in the palette is darker, or
     * where what came back is not a color at all.
     */
    public static MaterialColor worn(MaterialColor covered) {
        if (covered == null || covered == MaterialColor.NONE) return covered;
        if (!TrmtConfig.mapTracksWear) return covered;

        float darkening = Math.min(Math.max(TrmtConfig.mapWearDarkening, 0f), 0.9f);
        if (darkening <= 0f) return covered;

        // Read without a lock, because this is asked for every square a map draws and a minimap
        // redrawing a region asks it thousands of times in a frame, off the main thread. The work
        // behind a miss is a sweep of sixty-four entries and is done at most twice for a color if
        // two threads miss at once - which costs nothing and saves a lock on every hit.
        Long key = Long.valueOf(((long) covered.id << 32) | Float.floatToIntBits(darkening));
        MaterialColor held = CHOSEN.get(key);
        if (held != null) return held;

        MaterialColor picked = pick(covered, darkening);
        CHOSEN.put(key, picked);
        say(covered, picked);
        return picked;
    }

    /** The palette entry nearest the darkened color, among those that are darker than it. */
    private static MaterialColor pick(MaterialColor covered, float darkening) {
        int base = covered.col;
        float keep = 1f - darkening;
        int wantRed = Math.round(red(base) * keep);
        int wantGreen = Math.round(green(base) * keep);
        int wantBlue = Math.round(blue(base) * keep);
        int baseLuma = luma(base);

        MaterialColor best = covered;
        long nearest = Long.MAX_VALUE;
        for (MaterialColor each : MaterialColor.MATERIAL_COLORS) {
            if (each == null || each == MaterialColor.NONE || each.col == 0) continue;
            // Darker than what it stands in for, by enough to be seen. Without this the nearest
            // entry to a darkened color is very often the color itself.
            if (luma(each.col) > baseLuma - ENOUGH) continue;

            long away = distance(each.col, wantRed, wantGreen, wantBlue);
            if (away < nearest) {
                nearest = away;
                best = each;
            }
        }
        return best;
    }

    private static long distance(int color, int red, int green, int blue) {
        long dr = red(color) - red;
        long dg = green(color) - green;
        long db = blue(color) - blue;
        return dr * dr + dg * dg + db * db;
    }

    private static int red(int color) {
        return (color >> 16) & 0xFF;
    }

    private static int green(int color) {
        return (color >> 8) & 0xFF;
    }

    private static int blue(int color) {
        return color & 0xFF;
    }

    /** Rough and good enough for "is this darker": the usual weights, in integers. */
    private static int luma(int color) {
        return (red(color) * 299 + green(color) * 587 + blue(color) * 114) / 1000;
    }

    /**
     * Says what each color came out as, once each.
     *
     * <p>
     * Worth a line because it is the one thing about this that a packmaker cannot find out by
     * reading the settings: how much of a path they will see depends on what the palette happened to
     * hold near the darkened color of their ground.
     */
    private static void say(MaterialColor covered, MaterialColor picked) {
        if (SAID.put(Integer.valueOf(covered.id), Boolean.TRUE) != null) return;
        if (picked == covered) {
            Trmt.LOG.info(
                "Worn ground standing on color {} keeps it on maps: nothing in the palette is darker "
                    + "than #{} by enough to be worth spending. A path there shows only once it has worn "
                    + "through into another material.",
                Integer.valueOf(covered.id),
                Integer.toHexString(covered.col));
            return;
        }
        Trmt.LOG.info(
            "Worn ground standing on color {} (#{}) is drawn on maps as color {} (#{}), which is the "
                + "nearest the palette has to that color darkened by surfaces.mapWearDarkening.",
            new Object[] { Integer.valueOf(covered.id), Integer.toHexString(covered.col),
                Integer.valueOf(picked.id), Integer.toHexString(picked.col) });
    }
}
