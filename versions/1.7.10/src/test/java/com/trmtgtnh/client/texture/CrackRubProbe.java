package com.trmtgtnh.client.texture;

import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

/**
 * Throwaway harness for the rub-and-crack compound. Not part of the shipped suite.
 *
 * <p>
 * Measures each order against the defining property of the half it is composed of, sweeps the two
 * scaling parameters, sweeps the exponent at both gradation counts, and works out the picker
 * geometry at every interface scale.
 */
class CrackRubProbe {

    private static final String ROOT = "D:/Projects/TRMT Backport 1.7.10/trmtgtnh/build/rfg/minecraft-src/resources/assets/minecraft/textures/blocks/";

    private static final String[] FACES = { "stone", "cobblestone", "dirt", "sand", "gravel", "snow", "ice",
        "netherrack" };

    private static final int SIZE = 16;

    private static int[] face(String name) throws Exception {
        BufferedImage art = ImageIO.read(new File(ROOT + name + ".png"));
        int w = art.getWidth();
        int[] px = new int[SIZE * SIZE];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                px[y * SIZE + x] = 0xFF000000 | (art.getRGB(x * w / SIZE, y * w / SIZE) & 0xFFFFFF);
            }
        }
        return px;
    }

    private static int[][] loadFaces() throws Exception {
        int[][] faces = new int[FACES.length][];
        for (int i = 0; i < FACES.length; i++) faces[i] = face(FACES[i]);
        return faces;
    }

    private static double luma(int p) {
        return (((p >>> 16) & 0xFF) + ((p >>> 8) & 0xFF) + (p & 0xFF)) / 3.0;
    }

    private static double delta(int[] a, int[] b) {
        double t = 0;
        for (int i = 0; i < a.length; i++) t += Math.abs(luma(a[i]) - luma(b[i]));
        return t / a.length;
    }

    private static String f2(double v) {
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }

    // ------------------------------------------------------------------
    // The pieces, exactly as wearPass hands them over
    // ------------------------------------------------------------------

    private static int[] crackOnly(int[] src, float eased, int rot, float scale) {
        float c = eased * scale;
        return WearCompositor.applyCrack(src, SIZE, c * 0.9f, c, rot);
    }

    /** The same crack with its coverage at zero: the dulling, and none of the fissures. */
    private static int[] crackGroundOnly(int[] src, float eased, int rot, float scale) {
        return WearCompositor.applyCrack(src, SIZE, 0f, eased * scale, rot);
    }

    private static int[] rubOnly(int[] src, float eased, int rot, float scale) {
        float[] order = WearCompositor.sourceWearOrder(src, SIZE, rot);
        return WearCompositor.applyOverlay(src, SIZE, order, SIZE, eased * 0.72f * scale, eased);
    }

    /** Rub then crack. */
    private static int[] rubCrack(int[] src, float eased, int rot, float track, float run) {
        return crackFrom(rubOnly(src, eased, rot, track), eased, rot, run);
    }

    private static int[] crackFrom(int[] input, float eased, int rot, float run) {
        float c = eased * run;
        return WearCompositor.applyCrack(input, SIZE, c * 0.9f, c, rot);
    }

    /** Crack then rub, ranked off the source pixels: tones after, geometry before. */
    private static int[] crackRub(int[] src, float eased, int rot, float track, float run) {
        float[] order = WearCompositor.sourceWearOrder(src, SIZE, rot);
        int[] cracked = crackOnly(src, eased, rot, run);
        return WearCompositor.applyOverlay(cracked, SIZE, order, SIZE, eased * 0.72f * track, eased);
    }

    /** Crack then rub, ranked off the cracked pixels. */
    private static int[] crackRubLateRank(int[] src, float eased, int rot, float track, float run) {
        int[] cracked = crackOnly(src, eased, rot, run);
        float[] order = WearCompositor.sourceWearOrder(cracked, SIZE, rot);
        return WearCompositor.applyOverlay(cracked, SIZE, order, SIZE, eased * 0.72f * track, eased);
    }

    // ------------------------------------------------------------------
    // Measures
    // ------------------------------------------------------------------

    /**
     * The pixels the crack alone puts a fissure on: darkened by six levels or more against the same
     * crack with its fissures switched off. A fixed set of pixels, so it can be looked for in a
     * picture some other operator has been over afterwards.
     */
    private static boolean[] fissureSet(int[] src, float eased, int rot, float run) {
        int[] withFissures = crackOnly(src, eased, rot, run);
        int[] without = crackGroundOnly(src, eased, rot, run);
        boolean[] set = new boolean[src.length];
        for (int i = 0; i < src.length; i++) set[i] = luma(without[i]) - luma(withFissures[i]) >= 6.0;
        return set;
    }

    private static boolean[] trackSet(float[] order, float coverage) {
        float cut = WearCompositor.coverageThreshold(order, coverage);
        boolean[] mask = new boolean[order.length];
        for (int i = 0; i < order.length; i++) mask[i] = order[i] >= cut;
        return mask;
    }

    /**
     * How far a fissure reads against the ground immediately around it: per fissure pixel, the mean
     * luma of the non-fissure pixels within two, less its own. The face-wide contrast is the wrong
     * measure once something has darkened part of the ground, because an eye reads a line against
     * what it sits in and not against the average of the square.
     */
    private static double localFissureContrast(int[] image, boolean[] fissures) {
        double total = 0;
        int counted = 0;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int index = y * SIZE + x;
                if (!fissures[index]) continue;
                double around = 0;
                int n = 0;
                for (int dy = -2; dy <= 2; dy++) {
                    for (int dx = -2; dx <= 2; dx++) {
                        int nx = ((x + dx) % SIZE + SIZE) % SIZE;
                        int ny = ((y + dy) % SIZE + SIZE) % SIZE;
                        if (fissures[ny * SIZE + nx]) continue;
                        around += luma(image[ny * SIZE + nx]);
                        n++;
                    }
                }
                if (n == 0) continue;
                total += around / n - luma(image[index]);
                counted++;
            }
        }
        return counted == 0 ? 0 : total / counted;
    }

    private static double meanLuma(int[] image) {
        double total = 0;
        for (int p : image) total += luma(p);
        return total / image.length;
    }

    /** Mean luma outside a set less mean luma inside it: how far the set reads as darker. */
    private static double contrast(int[] image, boolean[] set) {
        double in = 0, out = 0;
        int ins = 0, outs = 0;
        for (int i = 0; i < image.length; i++) {
            if (set[i]) {
                in += luma(image[i]);
                ins++;
            } else {
                out += luma(image[i]);
                outs++;
            }
        }
        if (ins == 0 || outs == 0) return 0;
        return out / outs - in / ins;
    }

    private static int untouched(int[] before, int[] after) {
        int same = 0;
        for (int i = 0; i < before.length; i++) {
            if ((before[i] & 0xFFFFFF) == (after[i] & 0xFFFFFF)) same++;
        }
        return same;
    }

    private static double darkest(int[] image) {
        double low = 255;
        for (int p : image) low = Math.min(low, luma(p));
        return low;
    }

    // ------------------------------------------------------------------
    // a. Which order
    // ------------------------------------------------------------------

    private static final String[] ARRANGEMENTS = { "plain rub (reference)", "plain crack (reference)", "rub then crack",
        "crack then rub, source rank", "crack then rub, cracked rank" };

    @Test
    void order() throws Exception {
        int[][] faces = loadFaces();
        StringBuilder out = new StringBuilder();
        out.append("=== ORDER: each half measured by its own defining property, 8 faces x 4 rotations ===\n");
        out.append("bitIdentical% is against what THAT operator was handed. fissureLocal is how far a\n");
        out.append("fissure reads against the ground within two pixels of it. Both halves run full.\n\n");

        for (float eased : new float[] { 1.0f, 0.5f }) {
            out.append("--- eased = ")
                .append(eased)
                .append(" ---\n");
            out.append(
                String.format(
                    java.util.Locale.ROOT,
                    "%-30s %13s %14s %13s %14s %12s %10s %12s%n",
                    "arrangement",
                    "bitIdentical%",
                    "trackContrast",
                    "fissureFace",
                    "fissureLocal",
                    "fissureFloor",
                    "meanLuma",
                    "trackMoved%"));

            double[][] tally = new double[5][7];
            int n = 0;
            for (int[] src : faces) {
                for (int rot = 0; rot < 4; rot++) {
                    float[] srcOrder = WearCompositor.sourceWearOrder(src, SIZE, rot);
                    boolean[] srcTrack = trackSet(srcOrder, eased * 0.72f);
                    boolean[] fissures = fissureSet(src, eased, rot, 1f);

                    int[] rub = rubOnly(src, eased, rot, 1f);
                    int[] crack = crackOnly(src, eased, rot, 1f);
                    int[] rc = rubCrack(src, eased, rot, 1f, 1f);
                    int[] cr = crackRub(src, eased, rot, 1f, 1f);
                    int[] crLate = crackRubLateRank(src, eased, rot, 1f, 1f);
                    float[] lateOrder = WearCompositor.sourceWearOrder(crack, SIZE, rot);
                    boolean[] lateTrack = trackSet(lateOrder, eased * 0.72f);
                    int moved = 0;
                    for (int i = 0; i < src.length; i++) {
                        if (lateTrack[i] != srcTrack[i]) moved++;
                    }

                    row(
                        tally[0],
                        untouched(src, rub) * 100.0 / src.length,
                        contrast(rub, srcTrack),
                        contrast(rub, fissures),
                        localFissureContrast(rub, fissures),
                        darkest(rub),
                        meanLuma(rub),
                        0);
                    row(
                        tally[1],
                        0,
                        contrast(crack, srcTrack),
                        contrast(crack, fissures),
                        localFissureContrast(crack, fissures),
                        darkest(crack),
                        meanLuma(crack),
                        0);
                    row(
                        tally[2],
                        untouched(rub, rc) * 100.0 / src.length,
                        contrast(rc, srcTrack),
                        contrast(rc, fissures),
                        localFissureContrast(rc, fissures),
                        darkest(rc),
                        meanLuma(rc),
                        0);
                    row(
                        tally[3],
                        untouched(crack, cr) * 100.0 / src.length,
                        contrast(cr, srcTrack),
                        contrast(cr, fissures),
                        localFissureContrast(cr, fissures),
                        darkest(cr),
                        meanLuma(cr),
                        0);
                    row(
                        tally[4],
                        untouched(crack, crLate) * 100.0 / src.length,
                        contrast(crLate, lateTrack),
                        contrast(crLate, fissures),
                        localFissureContrast(crLate, fissures),
                        darkest(crLate),
                        meanLuma(crLate),
                        moved * 100.0 / src.length);
                    n++;
                }
            }

            for (int i = 0; i < 5; i++) {
                out.append(
                    String.format(
                        java.util.Locale.ROOT,
                        "%-30s %13.1f %14.2f %13.2f %14.2f %12.1f %10.1f %12.1f%n",
                        ARRANGEMENTS[i],
                        tally[i][0] / n,
                        tally[i][1] / n,
                        tally[i][2] / n,
                        tally[i][3] / n,
                        tally[i][4] / n,
                        tally[i][5] / n,
                        tally[i][6] / n));
            }
            double sourceMean = 0;
            for (int[] src : faces) sourceMean += meanLuma(src);
            out.append("  (the eight faces themselves mean ")
                .append(f2(sourceMean / faces.length))
                .append(")\n\n");
        }
        System.out.println(out);
    }

    private static void row(double[] into, double a, double b, double c, double d, double e, double f, double g) {
        into[0] += a;
        into[1] += b;
        into[2] += c;
        into[3] += d;
        into[4] += e;
        into[5] += f;
        into[6] += g;
    }

    // ------------------------------------------------------------------
    // b. The parameters
    // ------------------------------------------------------------------

    @Test
    void parameters() throws Exception {
        int[][] faces = loadFaces();
        StringBuilder out = new StringBuilder();
        out.append("=== PARAMETERS: crack then rub, source rank, track scale x crack run, at eased 1.0 ===\n");
        out.append(
            String.format(
                java.util.Locale.ROOT,
                "%6s %6s %14s %13s %14s %13s %10s %16s%n",
                "track",
                "run",
                "bitIdentical%",
                "fissureFace",
                "fissureLocal",
                "fissureFloor",
                "meanLuma",
                "fissuresInTrack%"));
        float[] tracks = { 1.00f, 0.90f, 0.85f, 0.80f, 0.72f };
        float[] runs = { 1.00f, 0.90f, 0.85f, 0.80f };
        for (float track : tracks) {
            for (float run : runs) {
                double bit = 0, ff = 0, fl = 0, floor = 0, mean = 0, inTrack = 0;
                int n = 0;
                for (int[] src : faces) {
                    for (int rot = 0; rot < 4; rot++) {
                        float[] order = WearCompositor.sourceWearOrder(src, SIZE, rot);
                        boolean[] trackMask = trackSet(order, 0.72f * track);
                        boolean[] fissures = fissureSet(src, 1f, rot, run);
                        int[] cracked = crackOnly(src, 1f, rot, run);
                        int[] full = crackRub(src, 1f, rot, track, run);
                        bit += untouched(cracked, full) * 100.0 / src.length;
                        ff += contrast(full, fissures);
                        fl += localFissureContrast(full, fissures);
                        floor += darkest(full);
                        mean += meanLuma(full);
                        int both = 0, all = 0;
                        for (int i = 0; i < src.length; i++) {
                            if (!fissures[i]) continue;
                            all++;
                            if (trackMask[i]) both++;
                        }
                        inTrack += all == 0 ? 0 : both * 100.0 / all;
                        n++;
                    }
                }
                out.append(
                    String.format(
                        java.util.Locale.ROOT,
                        "%6.2f %6.2f %14.1f %13.2f %14.2f %13.1f %10.1f %16.1f%n",
                        track,
                        run,
                        bit / n,
                        ff / n,
                        fl / n,
                        floor / n,
                        mean / n,
                        inTrack / n));
            }
        }

        double rubBit = 0, rubTrack = 0, crackFace = 0, crackLocal = 0, crackMean = 0, rubMean = 0;
        int n = 0;
        for (int[] src : faces) {
            for (int rot = 0; rot < 4; rot++) {
                float[] order = WearCompositor.sourceWearOrder(src, SIZE, rot);
                boolean[] trackMask = trackSet(order, 0.72f);
                boolean[] fissures = fissureSet(src, 1f, rot, 1f);
                int[] rub = rubOnly(src, 1f, rot, 1f);
                int[] crack = crackOnly(src, 1f, rot, 1f);
                rubBit += untouched(src, rub) * 100.0 / src.length;
                rubTrack += contrast(rub, trackMask);
                rubMean += meanLuma(rub);
                crackFace += contrast(crack, fissures);
                crackLocal += localFissureContrast(crack, fissures);
                crackMean += meanLuma(crack);
                n++;
            }
        }
        out.append(
            String.format(
                java.util.Locale.ROOT,
                "%nplain rub:   bitIdentical %.1f%%, trackContrast %.2f, meanLuma %.1f%n",
                rubBit / n,
                rubTrack / n,
                rubMean / n));
        out.append(
            String.format(
                java.util.Locale.ROOT,
                "plain crack: fissureFace %.2f, fissureLocal %.2f, meanLuma %.1f%n",
                crackFace / n,
                crackLocal / n,
                crackMean / n));
        out.append(
            String.format(
                java.util.Locale.ROOT,
                "the shipped smoothed rub at 0.85 leaves %.1f%% of its own input bit-identical%n",
                smoothedRubUntouched(faces)));
        System.out.println(out);
    }

    /** What SMOOTHED_RUB_TRACK actually buys, in the currency this compound is being judged in. */
    private static double smoothedRubUntouched(int[][] faces) {
        double total = 0;
        int n = 0;
        for (int[] src : faces) {
            for (int rot = 0; rot < 4; rot++) {
                int[] polished = WearCompositor
                    .applyPolish(src, SIZE, WearCompositor.POLISH_CONTRAST, WearCompositor.POLISH_LUMA, 1f);
                int[] worn = WearCompositor.applySmoothedRub(src, SIZE, 0.72f * 0.85f, 1f, 1f, rot);
                total += untouched(polished, worn) * 100.0 / src.length;
                n++;
            }
        }
        return total / n;
    }

    // ------------------------------------------------------------------
    // c. The curve
    // ------------------------------------------------------------------

    private interface Look {

        int[] at(int[] src, float eased, int rot);
    }

    private static double worstStep(Look look, float exponent, int gradations, int[][] faces) {
        double worst = Double.MAX_VALUE;
        for (int[] src : faces) {
            for (int rot = 0; rot < 4; rot++) {
                int[] previous = null;
                for (int i = 0; i < gradations; i++) {
                    float progress = i / (float) (gradations - 1);
                    float eased = progress <= 0f ? 0f : (float) Math.pow(progress, exponent);
                    int[] now = look.at(src, eased, rot);
                    if (previous != null) {
                        double d = delta(previous, now);
                        if (d < worst) worst = d;
                    }
                    previous = now;
                }
            }
        }
        return worst;
    }

    /** Fewest distinct pictures in a run, and the longest run of identical ones, worst face. */
    private static int[] distinct(Look look, float exponent, int gradations, int[][] faces) {
        int fewest = Integer.MAX_VALUE;
        int longestRun = 1;
        for (int[] src : faces) {
            for (int rot = 0; rot < 4; rot++) {
                int seen = 1;
                int run = 1;
                int[] previous = null;
                for (int i = 0; i < gradations; i++) {
                    float progress = i / (float) (gradations - 1);
                    float eased = progress <= 0f ? 0f : (float) Math.pow(progress, exponent);
                    int[] now = look.at(src, eased, rot);
                    if (previous != null) {
                        if (java.util.Arrays.equals(now, previous)) {
                            run++;
                            if (run > longestRun) longestRun = run;
                        } else {
                            seen++;
                            run = 1;
                        }
                    }
                    previous = now;
                }
                if (seen < fewest) fewest = seen;
            }
        }
        return new int[] { fewest, longestRun };
    }

    private static final float TRACK = 0.85f;

    @Test
    void curve() throws Exception {
        final int[][] faces = loadFaces();
        StringBuilder out = new StringBuilder();

        Look candidate = new Look() {

            public int[] at(int[] s, float e, int r) {
                return crackRub(s, e, r, TRACK, 1f);
            }
        };
        Look bothFull = new Look() {

            public int[] at(int[] s, float e, int r) {
                return crackRub(s, e, r, 1f, 1f);
            }
        };

        out.append("=== CURVE: crack then rub, source rank, track scaled to ")
            .append(TRACK)
            .append(", crack run full ===\n");
        for (int gradations : new int[] { 16, 80 }) {
            out.append(gradations)
                .append(" gradations, worst adjacent step over 8 faces x 4 rotations:\n  ");
            for (float e = 0.60f; e <= 1.305f; e += 0.05f) {
                out.append(
                    String
                        .format(java.util.Locale.ROOT, "%.2f=%s  ", e, f2(worstStep(candidate, e, gradations, faces))));
            }
            out.append('\n');
        }
        out.append("finer, 16 gradations, 0.90 to 1.10:\n  ");
        for (float e = 0.90f; e <= 1.105f; e += 0.025f) {
            out.append(String.format(java.util.Locale.ROOT, "%.3f=%s  ", e, f2(worstStep(candidate, e, 16, faces))));
        }
        out.append("\nfiner, 80 gradations, 0.90 to 1.10:\n  ");
        for (float e = 0.90f; e <= 1.105f; e += 0.025f) {
            out.append(String.format(java.util.Locale.ROOT, "%.3f=%s  ", e, f2(worstStep(candidate, e, 80, faces))));
        }
        out.append('\n');

        out.append("\nthe same sweep with both halves full, to show the track scale does not move the peak:\n  ");
        for (float e = 0.90f; e <= 1.105f; e += 0.05f) {
            out.append(String.format(java.util.Locale.ROOT, "%.2f=%s  ", e, f2(worstStep(bothFull, e, 16, faces))));
        }
        out.append('\n');

        out.append("\ndistinct pictures, worst of eight faces and four rotations:\n");
        for (float e : new float[] { 0.80f, 0.90f, 1.00f, 1.10f }) {
            int[] at16 = distinct(candidate, e, 16, faces);
            int[] at80 = distinct(candidate, e, 80, faces);
            out.append(
                String.format(
                    java.util.Locale.ROOT,
                    "  exponent %.2f: %d distinct of 16 (longest run %d), %d distinct of 80 (longest run %d)%n",
                    e,
                    at16[0],
                    at16[1],
                    at80[0],
                    at80[1]));
        }

        out.append("\nthe two parents at 80, on their own curves, for the same measure:\n");
        Look crack = new Look() {

            public int[] at(int[] s, float e, int r) {
                return crackOnly(s, e, r, 1f);
            }
        };
        Look rub = new Look() {

            public int[] at(int[] s, float e, int r) {
                return rubOnly(s, e, r, 1f);
            }
        };
        out.append("  crack @1.00: 16 -> ")
            .append(f2(worstStep(crack, 1.00f, 16, faces)))
            .append(", 80 -> ")
            .append(f2(worstStep(crack, 1.00f, 80, faces)))
            .append(", distinct at 80 ")
            .append(java.util.Arrays.toString(distinct(crack, 1.00f, 80, faces)))
            .append('\n');
        out.append("  rub   @0.60: 16 -> ")
            .append(f2(worstStep(rub, 0.60f, 16, faces)))
            .append(", 80 -> ")
            .append(f2(worstStep(rub, 0.60f, 80, faces)))
            .append(", distinct at 80 ")
            .append(java.util.Arrays.toString(distinct(rub, 0.60f, 80, faces)))
            .append('\n');

        out.append("\nclient.wearCurve multiplies the chosen exponent; worst step at 16 gradations:\n  ");
        for (float tilt : new float[] { 0.2f, 0.5f, 0.8f, 1.0f, 1.3f, 1.6f, 2.0f, 3.0f }) {
            out.append(
                String.format(
                    java.util.Locale.ROOT,
                    "x%.1f=%s  ",
                    tilt,
                    f2(worstStep(candidate, 1.00f * tilt, 16, faces))));
        }
        out.append('\n');

        System.out.println(out);
    }

    // ------------------------------------------------------------------
    // e. The picker
    // ------------------------------------------------------------------

    private static int scaleFactor(int width, int height, int guiScale) {
        int factor = 1;
        int k = guiScale == 0 ? 1000 : guiScale;
        while (factor < k && width / (factor + 1) >= 320 && height / (factor + 1) >= 240) factor++;
        return factor;
    }

    private static int scaledHeight(int width, int height, int guiScale) {
        return (int) Math.ceil(height / (double) scaleFactor(width, height, guiScale));
    }

    private static final int LOOK_ROW = 22;

    private static final int LOOK_ROW_TIGHT = 15;

    private static final int LOOK_ART = 16;

    private static int shippedPitch(int band, int rows) {
        return 8 + rows * LOOK_ROW <= band ? LOOK_ROW : LOOK_ROW_TIGHT;
    }

    private static int derivedPitch(int band, int rows, int floor) {
        int pitch = (band - 8) / rows;
        if (pitch > LOOK_ROW) pitch = LOOK_ROW;
        if (pitch < floor) pitch = floor;
        return pitch;
    }

    @Test
    void picker() throws Exception {
        StringBuilder out = new StringBuilder();
        int[][] screens = { { 1280, 720 }, { 1366, 768 }, { 1600, 900 }, { 1920, 1080 }, { 2560, 1440 }, { 3440, 1440 },
            { 3840, 2160 }, { 1024, 768 }, { 854, 480 }, { 640, 480 }, { 800, 600 }, { 640, 400 } };
        int[] scales = { 0, 1, 2, 3 };

        int worstBand = Integer.MAX_VALUE;
        for (int[] screen : screens) {
            for (int scale : scales) {
                worstBand = Math.min(worstBand, scaledHeight(screen[0], screen[1], scale) - 92);
            }
        }
        out.append("worst band over every screen and interface scale in the table: ")
            .append(worstBand)
            .append(" px\n\n");

        out.append("=== HOW MANY ROWS FIT IN THAT BAND, by rule, floor 12 ===\n");
        out.append(
            String.format(
                java.util.Locale.ROOT,
                "%6s %10s %8s %6s %11s %8s %6s %6s%n",
                "looks",
                "shippedPx",
                "pitch",
                "fits",
                "derivedPx",
                "pitch",
                "art",
                "fits"));
        for (int rows = 8; rows <= 14; rows++) {
            int shipped = shippedPitch(worstBand, rows);
            int shippedHeight = 8 + rows * shipped;
            int derived = derivedPitch(worstBand, rows, 12);
            int derivedHeight = 8 + rows * derived;
            out.append(
                String.format(
                    java.util.Locale.ROOT,
                    "%6d %10d %8d %6s %11d %8d %6d %6s%n",
                    rows,
                    shippedHeight,
                    shipped,
                    shippedHeight <= worstBand ? "yes" : "NO",
                    derivedHeight,
                    derived,
                    Math.min(LOOK_ART, derived - 2),
                    derivedHeight <= worstBand ? "yes" : "NO"));
        }

        out.append("\n=== THE SAME AT A FLOOR OF 11 ===\n");
        for (int rows = 10; rows <= 14; rows++) {
            int derived = derivedPitch(worstBand, rows, 11);
            int derivedHeight = 8 + rows * derived;
            out.append(
                String.format(
                    java.util.Locale.ROOT,
                    "%6d looks: pitch %d, %d px, %s%n",
                    rows,
                    derived,
                    derivedHeight,
                    derivedHeight <= worstBand ? "fits" : "DOES NOT FIT"));
        }

        out.append("\n=== TEN ROWS, DERIVED PITCH, AT EVERY SCREEN AND SCALE ===\n");
        out.append(
            String.format(
                java.util.Locale.ROOT,
                "%12s %6s %6s %6s %5s %6s %12s %12s%n",
                "screen",
                "gui",
                "band",
                "pitch",
                "art",
                "fits",
                "artRealPx",
                "rowRealPx"));
        int worstArtReal = Integer.MAX_VALUE;
        String worstArtAt = "";
        for (int[] screen : screens) {
            for (int scale : scales) {
                int factor = scaleFactor(screen[0], screen[1], scale);
                int band = scaledHeight(screen[0], screen[1], scale) - 92;
                int pitch = derivedPitch(band, 10, 12);
                int art = Math.min(LOOK_ART, pitch - 2);
                if (art * factor < worstArtReal) {
                    worstArtReal = art * factor;
                    worstArtAt = screen[0] + "x" + screen[1] + " gui " + (scale == 0 ? "auto" : String.valueOf(scale));
                }
                out.append(
                    String.format(
                        java.util.Locale.ROOT,
                        "%12s %6s %6d %6d %5d %6s %12d %12d%n",
                        screen[0] + "x" + screen[1],
                        scale == 0 ? "auto" : String.valueOf(scale),
                        band,
                        pitch,
                        art,
                        8 + 10 * pitch <= band ? "yes" : "NO",
                        art * factor,
                        pitch * factor));
            }
        }
        out.append("smallest preview anywhere in that table: ")
            .append(worstArtReal)
            .append(" real screen pixels, at ")
            .append(worstArtAt)
            .append('\n');

        out.append("\n=== WHAT TODAY'S TWO-PITCH RULE DOES WITH TEN ROWS ===\n");
        for (int[] screen : screens) {
            for (int scale : scales) {
                int band = scaledHeight(screen[0], screen[1], scale) - 92;
                int height = 8 + 10 * shippedPitch(band, 10);
                if (height > band) {
                    out.append(
                        String.format(
                            java.util.Locale.ROOT,
                            "  CLIPS at %s, gui %s: band %d, picker %d, over by %d px%n",
                            screen[0] + "x" + screen[1],
                            scale == 0 ? "auto" : String.valueOf(scale),
                            band,
                            height,
                            height - band));
                }
            }
        }

        out.append("\n=== AND WHAT THE SHIPPED NINE ROWS ALREADY DO ===\n");
        for (int[] screen : new int[][] { { 2560, 1440 }, { 1280, 720 }, { 1920, 1080 } }) {
            int band = scaledHeight(screen[0], screen[1], 0) - 92;
            int height = 8 + 9 * shippedPitch(band, 9);
            out.append(
                String.format(
                    java.util.Locale.ROOT,
                    "  %s auto: band %d, picker %d, %s, slack %d px%n",
                    screen[0] + "x" + screen[1],
                    band,
                    height,
                    height <= band ? "fits" : "CLIPS",
                    band - height));
        }

        System.out.println(out);
    }
}
