package com.trmtgtnh.client.texture;

import java.util.Locale;

/**
 * Whether a block's face may be turned a quarter where it is worn, read from the face's own pixels (0.9.229, Xep,
 * 2026-11-16: "If possible to have the patternless flag be smarter, I'd prefer that, so the entire family of nether
 * isn't impacted because it has nether bricks for example"; docs/goals/0.9.229-rotation-spec.md).
 *
 * <p>
 * A worn block's whole face picture turns by its position's quarter turn so worn ground does not read as a repeating
 * tile, and that is only right for a face with no direction: netherrack, dirt, sand, gravel, cobble, end stone,
 * stone. A face built of straight lines - bricks, planks, tiles, a frame, a pillar's rings - turned beside an unturned
 * one breaks every line at the seam, and a face with a grain shows it running the other way. So the decision is a
 * face's, never a family's: nether bricks keep still while netherrack beside them turns.
 *
 * <p>
 * Three measures, each of the face's light alone, and any one makes a face directional:
 * <ul>
 * <li><b>lines</b>, how much of the face's variation is whole rows or whole columns: the variance of the row means
 * (or of the column means, whichever is larger) times the edge, over the variance of every pixel. A face of
 * unrelated pixels comes to about one whatever its size; mortar courses, board edges and frames come to seven or
 * more. The line is {@link #LINES};</li>
 * <li><b>grain</b>, the larger of the face's summed light steps across and down over the smaller, which planks and a
 * log's bark show at three to four and patternless ground below 2.1. The line is {@link #GRAIN};</li>
 * <li><b>straight</b>, how far the face's two strongest row (or column) boundaries are each one edge the whole width
 * of the face: the light stepping the same way all along it, and three times the face's typical step that way. Near
 * nought for ground, one for a mortar course, a frame or a slab's rim. It catches the made faces whose lines are thin
 * against their variation, which the lines measure passes: a frame round a busy face, a carving's border. The line is
 * {@link #STRAIGHT}.</li>
 * </ul>
 * Measured on 2026-11-16 on every vanilla top face of a family that wears, in 1.7.10 and 26.3 (tools/face_turns.py):
 * the patternless faces came to lines 4.35, grain 2.07 and straight 0.47 at most (26.3's stone, terracotta, and
 * reinforced deepslate's top), the directional faces no name names to lines 5.42, and straight 0.61 at least (the
 * target's top, the bricks' courses). A face whose light does not vary at all has no direction to show, and turns.
 *
 * <p>
 * The block's own names are a second vote ({@link #HINTS}): a name that says brick, plank, tile, log, pillar and the
 * like keeps the face from turning whatever its pixels measure, because a chiseled top or a pillar's rings or a
 * running bond can measure as quietly as stone. A hint can only stop a turn, never start one. A looks file's
 * {@code turns} for the block wins over both, and the family's default ({@code FamilySettings.turnsByDefault}) is used
 * only where the face could not be read.
 *
 * <p>
 * Two vanilla faces are named rather than measured (2026-11-16, decided from the first classification table: keep
 * both still). 26.3's dripstone block has streaks that run one way, and reinforced deepslate's top is a frame. Their
 * measures (lines 3.87 and 3.19, straight 0.34 and 0.47) sit among the ground's own: 1.7.10's snow reads straight
 * 0.47 too, so no line drawn between them would leave every other vanilla verdict where it is. So "dripstone" and
 * "reinforced" are hints, which also keeps still every carving a mod names after dripstone.
 *
 * <p>
 * Free of the game and the same file in every edition (CoreStaysPortableTest), so each face is judged alike in all
 * eight.
 */
public final class FaceDirection {

    private FaceDirection() {}

    /** Lines at or past this make a face directional. Between 4.35 (26.3's stone) and 6.51 (26.3's smooth stone). */
    public static final float LINES = 5.4f;

    /** Grain at or past this makes a face directional. Between 2.07 (26.3's terracotta) and 2.81 (1.7.10's log). */
    public static final float GRAIN = 2.5f;

    /** Straight at or past this makes a face directional. Between 0.47 and 0.61; see the class comment. */
    public static final float STRAIGHT = 0.55f;

    /**
     * Words in a block's or face's name that mean it was made with a direction or a laid pattern, matched without
     * regard to case anywhere in the name after its namespace. Generous on purpose: one that matches too much costs a
     * block its turn, which is how every worn block drew before 0.9.229.
     */
    public static final String[] HINTS = { "brick", "plank", "tile", "log", "pillar", "chiseled", "chiselled",
        "polished", "carved", "carving", "engraved", "lines", "wood", "stem", "hyphae", "smooth", "cut_", "_cut",
        "pattern", "panel", "mosaic", "column", "stripped", "trim", "frame", "border", "beam", "shingle", "paver",
        "paving", "bond", "square", "scale", "weave", "braid", "herringbone", "ornate", "inlay", "glazed", "pane",
        "carpet", "rail", "slab_top", "board", "parquet", "spiral", "slant", "zag", "vertical", "horizontal", "groove",
        "slated", "twist", "stripe", "lantern", "dripstone", "reinforced" };

    /** What the three measures read on one face. */
    public static final class Reading {

        /** Whether there were pixels to read. */
        public final boolean read;

        /** The lines measure: about one for unrelated pixels, seven and more for courses and frames. */
        public final float lines;

        /** The grain measure: one for a face alike both ways, three and more for planks and bark. */
        public final float grain;

        /** The straight measure: near nought for ground, one for a course or a frame. */
        public final float straight;

        Reading(boolean read, float lines, float grain, float straight) {
            this.read = read;
            this.lines = lines;
            this.grain = grain;
            this.straight = straight;
        }

        /** Whether any measure says the face has a direction. Unread is not directional; see {@link #turns}. */
        public boolean directional() {
            return read && (lines >= LINES || grain >= GRAIN || straight >= STRAIGHT);
        }

        @Override
        public String toString() {
            return read ? String.format(Locale.ROOT, "lines %.2f, grain %.2f, straight %.2f", lines, grain, straight)
                : "unread";
        }
    }

    /** A face that could not be read. */
    public static final Reading UNREAD = new Reading(false, 0f, 0f, 0f);

    /**
     * The three measures of a face, from its pixels as 0xAARRGGBB rows. Only the top-left square as wide as the face
     * is read, which is the first frame of an animated strip and the whole of a square face. Nothing for no pixels, a
     * face narrower than two or a buffer shorter than its square.
     */
    public static Reading read(int[] pixels, int width, int height) {
        int n = Math.min(width, height);
        if (pixels == null || n < 2 || pixels.length < (long) width * (n - 1) + n) return UNREAD;
        double[] light = new double[n * n];
        double sum = 0.0;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                int p = pixels[y * width + x];
                double l = 0.299 * ((p >>> 16) & 0xFF) + 0.587 * ((p >>> 8) & 0xFF) + 0.114 * (p & 0xFF);
                light[y * n + x] = l;
                sum += l;
            }
        }
        double mean = sum / (n * n);
        double variance = 0.0;
        for (double l : light) variance += (l - mean) * (l - mean);
        variance /= n * n;
        // A face of one light, or near enough that the ratios would be rounding: nothing to turn wrong.
        if (variance < 1e-3) return new Reading(true, 0f, 1f, 0f);

        double rows = 0.0;
        double columns = 0.0;
        for (int i = 0; i < n; i++) {
            double row = 0.0;
            double column = 0.0;
            for (int j = 0; j < n; j++) {
                row += light[i * n + j];
                column += light[j * n + i];
            }
            row = row / n - mean;
            column = column / n - mean;
            rows += row * row;
            columns += column * column;
        }
        // The variance of the row means is rows / n, and times the edge over the pixels' variance that is rows /
        // variance.
        double lines = Math.max(rows, columns) / variance;

        // Steps to the next pixel across and down, wrapping, as a face tiles against itself.
        double across = 0.0;
        double down = 0.0;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                double l = light[y * n + x];
                across += Math.abs(light[y * n + (x + 1) % n] - l);
                down += Math.abs(light[((y + 1) % n) * n + x] - l);
            }
        }
        double small = Math.min(across, down);
        double grain = small <= 1e-9 ? (Math.max(across, down) <= 1e-9 ? 1.0 : GRAIN) : Math.max(across, down) / small;
        double straight = Math.max(straight(light, n, false), straight(light, n, true));
        return new Reading(true, (float) lines, (float) grain, (float) straight);
    }

    /**
     * Of the boundaries between one row and the next (or one column and the next), the mean of the two that are most
     * one edge: how far the steps across the boundary all go the same way (their sum over the sum of their sizes),
     * times how strong it is against the face's typical step that way, held at three times it.
     */
    private static double straight(double[] light, int n, boolean columns) {
        double[] signed = new double[n];
        double[] total = new double[n];
        double all = 0.0;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                double step = columns ? light[j * n + (i + 1) % n] - light[j * n + i]
                    : light[((i + 1) % n) * n + j] - light[i * n + j];
                signed[i] += step;
                total[i] += Math.abs(step);
            }
            all += total[i];
        }
        double typical = all / ((double) n * n);
        double first = 0.0;
        double second = 0.0;
        for (int i = 0; i < n; i++) {
            double score = 0.0;
            if (total[i] > 1e-9 && typical > 1e-9) {
                double strength = Math.min(total[i] / n / typical, 3.0) / 3.0;
                score = Math.abs(signed[i]) / total[i] * strength;
            }
            if (score > first) {
                second = first;
                first = score;
            } else if (score > second) {
                second = score;
            }
        }
        return (first + second) / 2.0;
    }

    /**
     * Whether any of these names holds one of {@link #HINTS} after its namespace (the part before its first colon):
     * a namespace says which mod, not what the block is, and a mod's id may hold a hint's letters by chance. Nulls are
     * passed over.
     */
    public static boolean hinted(String... names) {
        return hint(names) != null;
    }

    /** The first hint any of these names holds, as {@link #hinted} reads them, or null. */
    public static String hint(String... names) {
        if (names == null) return null;
        for (String name : names) {
            if (name == null) continue;
            String own = name.substring(name.indexOf(':') + 1)
                .toLowerCase(Locale.ROOT);
            for (String hint : HINTS) {
                if (own.contains(hint)) return hint;
            }
        }
        return null;
    }

    /**
     * Whether a face turns where it is worn.
     *
     * @param override      a looks file's {@code turns} for the block, which wins over everything; null where none says
     * @param reading       the face's own measures, or {@link #UNREAD}
     * @param familyDefault the family's default, used only for a face that could not be read
     * @param names         the block's registry name and the face's texture name, for {@link #HINTS}
     */
    public static boolean turns(Boolean override, Reading reading, boolean familyDefault, String... names) {
        if (override != null) return override.booleanValue();
        if (hinted(names)) return false;
        if (reading == null || !reading.read) return familyDefault;
        return !reading.directional();
    }
}
