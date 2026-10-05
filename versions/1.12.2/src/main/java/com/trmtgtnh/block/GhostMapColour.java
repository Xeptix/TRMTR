package com.trmtgtnh.block;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.block.material.MapColor;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.config.TrmtConfig;

/**
 * The one darker colour a worn square reports to a map, and how it is chosen.
 *
 * <p>
 * <strong>What this version cannot do, and what it can.</strong> 1.7.10 hands JourneyMap an RGB
 * value per position and darkens a worn square by any fraction of its own colour, so a road fades
 * smoothly from fresh to deeply trodden on a minimap. Every map at this version reads one vanilla answer -
 * {@code getMapColor}, handed the position - and that answer is one of sixty-four fixed palette
 * entries. There is no fraction to give and no darker sibling of a colour to pick: the palette is
 * what it is.
 *
 * <p>
 * The first answer to that was to give up the darkening entirely, which is what both ports did:
 * a worn square reported the colour of whatever it stood in for, so a path showed on a map only once
 * it had worn through into a different material, and the four map settings said so in the log and did
 * nothing. That is honest and it is not good enough - <em>a path you cannot see on the map is the
 * feature missing</em>, and the loss that matters is the road being invisible rather than the shade
 * being approximate.
 *
 * <p>
 * So this settles for one colour. A worn square that shows at all reports the palette entry nearest
 * to its own colour darkened by {@code surfaces.mapWearDarkening} - the same figure, read the same
 * way, applied once at full strength rather than scaled along the run. A path is visible from the
 * first gradation that shows and does not deepen after it, which is the whole of the compromise and
 * is worth stating plainly: <strong>the road is there, the wear is not readable from it.</strong>
 *
 * <p>
 * <p>
 * Beside the block because it is a block's answer to a question, and because {@code /trmt
 * mapcolour} asks it from a server thread.
 *
 * <p>
 * Chosen rather than listed, because the ground this mod wears is whatever a pack supplies. The
 * nearest entry is taken by squared distance in RGB, among the entries that are actually darker than
 * what they stand in for - without that last condition the nearest entry to a darkened green is often
 * the green it came from, and the answer is no change at all.
 */
public final class GhostMapColour {

    private GhostMapColour() {}

    /** Answers already worked out, by palette id and the darkening they were worked out for. */
    private static final Map<Long, MapColor> CHOSEN = new ConcurrentHashMap<Long, MapColor>();

    /** Said once, naming what a family's worn colour came out as. */
    private static final Map<Integer, Boolean> SAID = new ConcurrentHashMap<Integer, Boolean>();

    /**
     * How much darker a square has to be before it counts as darker at all.
     *
     * <p>
     * Four per cent of full brightness. Below that the eye reads two entries as the same colour on a
     * map tile a few pixels across, and picking one would spend a colour to no effect.
     */
    private static final int ENOUGH = 10;

    /**
     * The colour a worn square should report, given the colour of what it stands in for.
     *
     * <p>
     * The covered colour itself where the setting is off, where nothing in the palette is darker, or
     * where what came back is not a colour at all.
     */
    public static MapColor worn(MapColor covered) {
        if (covered == null || covered == MapColor.AIR) return covered;
        if (!TrmtConfig.mapTracksWear) return covered;

        float darkening = Math.min(Math.max(TrmtConfig.mapWearDarkening, 0f), 0.9f);
        if (darkening <= 0f) return covered;

        // Read without a lock, because this is asked for every square a map draws and a minimap
        // redrawing a region asks it thousands of times in a frame, off the main thread. The work
        // behind a miss is a sweep of sixty-four entries and is done at most twice for a colour if
        // two threads miss at once - which costs nothing and saves a lock on every hit.
        Long key = Long.valueOf(((long) covered.colorIndex << 32) | Float.floatToIntBits(darkening));
        MapColor held = CHOSEN.get(key);
        if (held != null) return held;

        MapColor picked = pick(covered, darkening);
        CHOSEN.put(key, picked);
        say(covered, picked);
        return picked;
    }

    /** The palette entry nearest the darkened colour, among those that are darker than it. */
    private static MapColor pick(MapColor covered, float darkening) {
        int base = covered.colorValue;
        float keep = 1f - darkening;
        int wantRed = Math.round(red(base) * keep);
        int wantGreen = Math.round(green(base) * keep);
        int wantBlue = Math.round(blue(base) * keep);
        int baseLuma = luma(base);

        MapColor best = covered;
        long nearest = Long.MAX_VALUE;
        for (MapColor each : MapColor.COLORS) {
            if (each == null || each == MapColor.AIR || each.colorValue == 0) continue;
            // Darker than what it stands in for, by enough to be seen. Without this the nearest
            // entry to a darkened colour is very often the colour itself.
            if (luma(each.colorValue) > baseLuma - ENOUGH) continue;

            long away = distance(each.colorValue, wantRed, wantGreen, wantBlue);
            if (away < nearest) {
                nearest = away;
                best = each;
            }
        }
        return best;
    }

    private static long distance(int colour, int red, int green, int blue) {
        long dr = red(colour) - red;
        long dg = green(colour) - green;
        long db = blue(colour) - blue;
        return dr * dr + dg * dg + db * db;
    }

    private static int red(int colour) {
        return (colour >> 16) & 0xFF;
    }

    private static int green(int colour) {
        return (colour >> 8) & 0xFF;
    }

    private static int blue(int colour) {
        return colour & 0xFF;
    }

    /** Rough and good enough for "is this darker": the usual weights, in integers. */
    private static int luma(int colour) {
        return (red(colour) * 299 + green(colour) * 587 + blue(colour) * 114) / 1000;
    }

    /**
     * Says what each colour came out as, once each.
     *
     * <p>
     * Worth a line because it is the one thing about this that a packmaker cannot find out by
     * reading the settings: how much of a path they will see depends on what the palette happened to
     * hold near the darkened colour of their ground.
     */
    private static void say(MapColor covered, MapColor picked) {
        if (SAID.put(Integer.valueOf(covered.colorIndex), Boolean.TRUE) != null) return;
        if (picked == covered) {
            Trmt.LOG.info(
                "Worn ground standing on colour {} keeps it on maps: nothing in the palette is darker "
                    + "than #{} by enough to be worth spending. A path there shows only once it has worn "
                    + "through into another material.",
                Integer.valueOf(covered.colorIndex),
                Integer.toHexString(covered.colorValue));
            return;
        }
        Trmt.LOG.info(
            "Worn ground standing on colour {} (#{}) is drawn on maps as colour {} (#{}), which is the "
                + "nearest the palette has to that colour darkened by surfaces.mapWearDarkening.",
            new Object[] { Integer.valueOf(covered.colorIndex), Integer.toHexString(covered.colorValue),
                Integer.valueOf(picked.colorIndex), Integer.toHexString(picked.colorValue) });
    }
}
