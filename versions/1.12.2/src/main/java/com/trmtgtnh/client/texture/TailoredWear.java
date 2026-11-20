package com.trmtgtnh.client.texture;

/**
 * Four looks made for the four families 0.9.229 brought in, each drawn as that material wears rather than borrowed
 * from a family it only resembles.
 *
 * <p>
 * Xep, 2026-11-12: "make 4 new wear patterns tailored to these families". Flesh, taint, sculk and cloud arrived wearing
 * smooth_dirt, crack_dirt, smooth_heavy and dirt_lite, each the nearest of the eleven looks then offered to what the
 * block does underfoot.
 * Nearest is not the same: a buffed rub reads as worn sand, not as something soft and wet that has been pressed, and a
 * crack reads as stone, not as a blight creeping over its own ruts. Each look here is chosen by what the block is.
 *
 * <p>
 * Every one keeps the property every older look is held to: it is worked from the block's own pixels and nothing else.
 * Where a look moves a pixel toward a color, that color is taken from the face it is handed - its own shadows, its
 * own brightest tones, its own quiet ground, lightened, darkened or greyed - so a resource pack that recolors flesh
 * gets bruises in its own colors, and any of the four set on a block nobody drew art for still comes out in that
 * block's palette. Nothing here is a palette and nothing names a block.
 *
 * <p>
 * Deterministic as the rest are: the same face, progress, strength and rotation always give the same pixels, so every
 * client draws the same wear without any of it being sent. The rotation seeds every placement, as it does for the
 * crack and the rub, so the four rotations a path lays side by side are four pictures and not one.
 *
 * <p>
 * Free of Minecraft types, like {@link WearCompositor} beside it, so it can be drawn to a contact sheet and tested
 * against known inputs. It is on {@code CoreStaysPortableTest.PORTABLE}, the same file in all eight editions.
 */
public final class TailoredWear {

    private TailoredWear() {}

    // ------------------------------------------------------------------
    // Flesh: bruised, then glossy and wet where trodden
    // ------------------------------------------------------------------

    /** How much of the face the trodden patch claims at the end of the run, before strength. */
    static final float FLESH_COVERAGE = 0.70f;

    /** How far a trodden pixel darkens, as a fraction of its own light, at the end of the run. */
    static final float FLESH_BRUISE = 0.42f;

    /** How far a trodden pixel's color is drawn toward the face's own shadow color: the bruise's hue. */
    static final float FLESH_HUE = 0.55f;

    /** How much a trodden pixel's own relief is closed up toward its neighbours': skin going wet and slick. */
    static final float FLESH_SLICK = 0.60f;

    /** How far a highlight goes from the bruised pixel toward the face's own brightest tone, lifted. */
    static final float FLESH_GLOSS = 0.60f;

    /** The share of the trodden patch's raised points that catch a wet highlight at the end of the run. */
    static final float FLESH_SHINE = 0.50f;

    /**
     * Soft ground that has been pressed rather than carried away: it bruises, and it goes wet.
     *
     * <p>
     * Three things happen inside a growing trodden patch and nothing outside it. The patch darkens and its color is
     * drawn toward the face's own darkest tones, which on flesh is a deep blood red and is what a bruise is: the same
     * material, darker and more saturated, never a color brought in from outside. Its relief closes up, each pixel
     * pulled toward its neighbours, because a pressed wet surface has lost its grain. And a scatter of pixels in the
     * patch catch a highlight taken from the face's own brightest tone, lifted toward white, which is the one thing
     * that tells wet from merely dark. A soft rim around the patch is bruised at half the depth, so it fades into the
     * skin around it rather than stopping at a pixel's edge.
     *
     * <p>
     * Where the patch forms is the rub's own ranking ({@link WearCompositor#sourceWearOrder}), so on any block it
     * forms where the raised parts would catch a foot, and on flesh in the same places the old smoothed rub put its
     * track.
     *
     * @param coverage the floored progress the patch grows with, 0 to 1
     * @param progress the eased progress the depth of every change follows, 0 to 1
     */
    public static int[] flesh(int[] source, int size, float coverage, float progress, float strength, int seed) {
        int n = size * size;
        int[] out = opaqueCopy(source, n);
        float depth = clamp01(progress) * clamp01(strength);
        if (depth <= 0f) return out;

        float[] rank = rankFraction(WearCompositor.sourceWearOrder(source, size, seed));
        float claimed = clamp01(coverage) * FLESH_COVERAGE * clamp01(strength);
        float[] shadow = toneColor(source, n, 0f, 0.15f);
        float[] bright = toneColor(source, n, 0.95f, 1f);
        float[] lum = lumas(source, n);

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int i = y * size + x;
                float r = rank[i];
                float in = r < claimed ? 1f : claimed <= 0f ? 0f : 0.5f * (1f - (r - claimed) / 0.10f);
                if (in <= 0f) continue;
                float a = in * depth;

                int pixel = source[i];
                float red = (pixel >>> 16) & 0xFF;
                float green = (pixel >>> 8) & 0xFF;
                float blue = pixel & 0xFF;
                float own = lum[i];

                // Slick: the pixel's light pulled toward its neighbourhood's, its color kept.
                float around = neighbourhood(lum, size, x, y);
                float light = own + (around - own) * FLESH_SLICK * a;
                // Bruised: darker, by a fraction of its own light, never below the face's own floor.
                light *= 1f - FLESH_BRUISE * a;
                float scale = own <= 0.5f ? 0f : light / own;
                red *= scale;
                green *= scale;
                blue *= scale;
                // The bruise's hue: toward the shadow color, at the light this pixel now has.
                float shadowLight = (shadow[0] + shadow[1] + shadow[2]) / 3f;
                float match = shadowLight <= 0.5f ? 0f : light / shadowLight;
                float hue = FLESH_HUE * a;
                red += (shadow[0] * match - red) * hue;
                green += (shadow[1] * match - green) * hue;
                blue += (shadow[2] * match - blue) * hue;

                // Wet: a highlight on the raised points of the patch proper - a pixel lighter than every
                // neighbour - and only on some of them, the share growing with the run.
                if (in >= 1f && own >= peak(lum, size, x, y) && hash(x, y, 311 + seed * 29) < FLESH_SHINE * depth) {
                    float shine = FLESH_GLOSS * depth;
                    red += (lift(bright[0]) - red) * shine;
                    green += (lift(bright[1]) - green) * shine;
                    blue += (lift(bright[2]) - blue) * shine;
                }
                out[i] = pack(red, green, blue);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Taint: the blight darkens in blotches and veins over its ruts
    // ------------------------------------------------------------------

    /** The fracture fields' resolution, as {@link WearCompositor}'s crack builds its own. */
    static final int FIELD = 64;

    /** Cells across the main vein network, and across the fine one that follows it. */
    private static final int VEIN_CELLS = 4;

    private static final int FINE_CELLS = 8;

    /** Where along the run the fine veins start, and how wide either network grows, in field units. */
    static final float FINE_START = 0.40f;

    private static final float VEIN_WIDTH_MIN = 0.008f;

    private static final float VEIN_WIDTH_MAX = 0.040f;

    private static final float FINE_WIDTH_MAX = 0.016f;

    /** How dark the blighted ground goes, and how much of its relief it keeps, at the end of the run. */
    static final float TAINT_DARKEN = 0.60f;

    private static final float TAINT_FLATTEN = 0.45f;

    /** How far the face's own lighter strands are drawn toward the fibres' color at the end of the run. */
    static final float TAINT_SWELL = 0.85f;

    /** The dark groove each vein sits in: how wide, as a multiple of the vein, and how deep. */
    private static final float GROOVE_SPAN = 2.6f;

    private static final float GROOVE_DEPTH = 0.30f;

    /**
     * How late the blotches open: the share of the ranked ground they have claimed is the wear raised to this power,
     * so the last of it is reached only at the very end of the run.
     *
     * <p>
     * Xep, 2026-11-15, of the look as it first shipped: "the ending half is harder to tell apart, and it's like the
     * wear tapers off too fast". Its blotches opened on a threshold that had reached nearly the whole face by two
     * thirds of the way, so the blight stopped spreading and the last third barely moved. Shown a milder and a bolder
     * remedy side by side, he chose the milder ("T aint A"): this, and the late features below. Above one, the
     * spread is held back against taint's front-loaded curve; at 1.67 it would cancel that curve outright, which was
     * the bolder one.
     */
    static final float TAINT_BLOT_LAG = 1.35f;

    /** The soft edge of the spreading blight, as a share of the ranked ground: how far ahead of it a pixel starts. */
    private static final float TAINT_BLOT_EDGE = 0.22f;

    /**
     * Where the late features start, in eased wear: 0.77 is about 65% of the run on taint's curve (0.65 to the power
     * 0.6), the point at which the look as it first shipped had stopped changing. From here the main veins widen and
     * brighten and the deepest ground keeps sinking, so the contrast keeps climbing to the end instead of levelling
     * off (Xep's milder choice of 2026-11-15, above).
     */
    static final float TAINT_LATE_START = 0.77f;

    /** How much wider the main veins grow over the late stretch, in field units, on top of their steady widening. */
    private static final float TAINT_LATE_WIDTH = 0.014f;

    /** How much more strongly the drawn veins take the fibres' color over the late stretch. */
    private static final float TAINT_LATE_GLOW = 0.20f;

    /** How far the fibres' color itself lifts toward a highlight over the late stretch. */
    private static final float TAINT_LATE_LIFT = 0.30f;

    /**
     * How much further the deepest blighted ground, below the face's middle and off its strands, darkens over the
     * late stretch: the crust's pits going toward black while its fibres lighten, which is the look's contrast
     * carried on rather than a new one.
     */
    private static final float TAINT_PIT = 0.45f;

    private static final java.util.Map<Long, float[]> VEINS = new java.util.concurrent.ConcurrentHashMap<Long, float[]>();

    /**
     * A blight that grows back over the ground it is walked on: the trodden crust darkens in blotches, and veins of
     * the block's own brightest color creep across it.
     *
     * <p>
     * The opposite of a crack, which is the look taint borrowed. A crack cuts dark lines into a face and dirties the
     * ground between them; taint's fibres stand out lighter than the crust they grow over. So the network here is
     * drawn in the face's own top tones, saturated and lifted a little, each fibre sitting in a narrow dark groove so
     * it reads as raised. The ground darkens behind them in blotches that spread over the run rather than as a track,
     * because taint is not worn away but spoiled where it is trodden. The veins come first and the dark follows: early
     * on a few fibres cross an unchanged crust, and by the end they run across a crust gone nearly to its own shadow.
     * The blotches keep spreading to the very end, and over the last third the main veins widen and brighten while
     * the deepest ground sinks further, so every step of the late run is its own picture (Xep, 2026-11-15).
     *
     * <p>
     * The fibres are a cell network of the kind the crack is built on ({@link WearCompositor#fractureField}), at its
     * resolution and with its warp, so they are soft at sixteen pixels and do not read as a lattice; but four and
     * eight cells across rather than the crack's three and six, finer and more branched, as a mycelium is.
     */
    public static int[] taint(int[] source, int size, float progress, float strength, int seed) {
        int n = size * size;
        int[] out = opaqueCopy(source, n);
        float s = clamp01(strength);
        float wear = clamp01(progress) * s;
        if (wear <= 0f) return out;

        float[] lum = lumas(source, n);
        float shadow = quantile(lum, 0.12f);
        float middle = quantile(lum, 0.5f);
        float top = quantile(lum, 1f);
        float[] bright = toneColor(source, n, 0.90f, 1f);
        float[] veins = veins(VEIN_CELLS, 5 + seed * 37);
        float[] fine = veins(FINE_CELLS, 6 + seed * 37);
        int subs = Math.max(1, FIELD / size);

        // The late stretch: nothing until about two thirds of the way, all of it at the end.
        float late = clamp01((wear - TAINT_LATE_START) / (1f - TAINT_LATE_START));
        float fineWear = wear <= FINE_START ? 0f : (wear - FINE_START) / (1f - FINE_START);
        float width = VEIN_WIDTH_MIN + (VEIN_WIDTH_MAX - VEIN_WIDTH_MIN) * wear + TAINT_LATE_WIDTH * late;
        float fineWidth = FINE_WIDTH_MAX * fineWear;
        float[] blight = blight(size, wear, seed);
        // The fibres' color: the face's brightest tone made vivid, lifting toward a highlight over the late stretch.
        float[] fibre = new float[3];
        for (int c = 0; c < 3; c++) {
            fibre[c] = vivid(bright, c);
            fibre[c] += (lift(fibre[c]) - fibre[c]) * TAINT_LATE_LIFT * late;
        }

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int i = y * size + x;
                int pixel = source[i];
                float red = (pixel >>> 16) & 0xFF;
                float green = (pixel >>> 8) & 0xFF;
                float blue = pixel & 0xFF;
                float own = lum[i];

                // How far this pixel belongs to the face's own lighter network rather than its ground: nothing at
                // the middle of its light and below, all of it at the face's brightest.
                float strand = top - middle <= 0.5f ? 0f : clamp01((own - middle) / (top - middle));

                // The blight: blotches that open in order of a broad field, each darkening the ground as it is
                // reached. The face's own strands resist it, so they are left standing out of the dark.
                float reached = blight[i] * (1f - 0.8f * strand);
                float light = (shadow + (own - shadow) * (1f - TAINT_FLATTEN * reached))
                    * (1f - TAINT_DARKEN * reached);
                if (light > own) light = own;
                // The pits: the deepest blighted ground keeps sinking through the late stretch.
                float pit = middle - shadow <= 0.5f ? 0f : clamp01((middle - own) / (middle - shadow));
                light *= 1f - TAINT_PIT * late * pit * blight[i] * (1f - strand);

                // The fibres, sampled across the pixel as the crack samples its fissures.
                int warpX = Math.round((lowFrequency(x, y, size, 2, 157 + seed * 19) - 0.5f) * 5f);
                int warpY = Math.round((lowFrequency(x, y, size, 2, 173 + seed * 19) - 0.5f) * 5f);
                int fieldX = x * FIELD / size;
                int fieldY = y * FIELD / size;
                float vein = 0f;
                float groove = 0f;
                for (int sy = 0; sy < subs; sy++) {
                    int py = ((fieldY + sy + warpY) % FIELD + FIELD) % FIELD;
                    for (int sx = 0; sx < subs; sx++) {
                        int px = ((fieldX + sx + warpX) % FIELD + FIELD) % FIELD;
                        float edge = veins[py * FIELD + px];
                        float main = lineAmount(edge, width);
                        float thin = fineWidth > 0f ? lineAmount(fine[py * FIELD + px], fineWidth) * 0.7f : 0f;
                        vein += main > thin ? main : thin;
                        groove += lineAmount(edge, width * GROOVE_SPAN);
                    }
                }
                vein /= subs * subs;
                groove /= subs * subs;

                light *= 1f - GROOVE_DEPTH * groove * wear;
                float scale = own <= 0.5f ? 0f : light / own;
                red *= scale;
                green *= scale;
                blue *= scale;

                // The face's own strands swell toward the fibres' color, and the drawn fibres grow over all of it,
                // taking it more strongly through the late stretch.
                float veinGrowth = 0.25f + 0.45f * wear + TAINT_LATE_GLOW * late;
                float grown = Math.max(strand * TAINT_SWELL * wear, vein * veinGrowth * s);
                if (grown > 0f) {
                    red += (fibre[0] - red) * grown;
                    green += (fibre[1] - green) * grown;
                    blue += (fibre[2] - blue) * grown;
                }
                out[i] = pack(red, green, blue);
            }
        }
        return out;
    }

    /**
     * How far the blight has reached each pixel of the ground, 0 to 1, before the face's own strands resist it.
     *
     * <p>
     * The ground is ranked by a broad blotch field with a little grain in it, so the dark opens in blotches rather
     * than as a track, and claimed in that order up to a share that grows as {@link #TAINT_BLOT_LAG} sets: the whole
     * face only at the end of the run, each pixel easing in over {@link #TAINT_BLOT_EDGE} of the ranking ahead of the
     * front.
     *
     * @param wear the eased progress times strength, 0 to 1
     */
    static float[] blight(int size, float wear, int seed) {
        int n = size * size;
        float[] blots = new float[n];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                blots[y * size + x] = lowFrequency(x, y, size, 3, 131 + seed * 17) * 0.85f
                    + hash(x, y, 149 + seed * 5) * 0.15f;
            }
        }
        float[] rank = rankFraction(blots);
        float front = (float) Math.pow(wear, TAINT_BLOT_LAG);
        float[] reach = new float[n];
        for (int i = 0; i < n; i++) {
            reach[i] = clamp01((front * (1f + TAINT_BLOT_EDGE) - rank[i]) / TAINT_BLOT_EDGE);
        }
        return reach;
    }

    // ------------------------------------------------------------------
    // Sculk: the glints go out, then the skin goes smooth and dead
    // ------------------------------------------------------------------

    /** The span of the run over which the glints go out, one by one, and how long each one takes. */
    static final float GLINTS_OUT_BY = 0.90f;

    private static final float GLINT_FADE = 0.12f;

    /** The share of the face, from its quietest, that can never be a glint. */
    private static final float GLINT_SHARE = 0.75f;

    /** How lively a pixel has to be to be counted as a glint and given a turn of its own. */
    private static final float GLINT_COUNTED = 0.34f;

    /** How far every glint has dimmed together by the end, under the one-by-one outages. */
    private static final float GLINT_DIM = 0.40f;

    /** Where the deadening starts, and how far it flattens, greys and lifts the face by the end. */
    static final float DEAD_START = 0.25f;

    private static final float DEAD_CONTRAST = 0.45f;

    private static final float DEAD_GREY = 0.40f;

    private static final float DEAD_ASH = 0.18f;

    /**
     * The share of the counted glints that never go out: about a fifth.
     *
     * <p>
     * Xep, 2026-11-15: "I like how it wears but I want to add a glow effect like the new nether/end - it should keep a
     * few of the sculk bits glowing that green-ish blue tint. So most of the 'stars' in the sculk texture go dim at
     * full wear, but a few are still showing." Shown a tenth and a fifth side by side, he chose the fifth: eleven of
     * the fifty-three glints on vanilla's sixteen-pixel sculk. These survivors are also what a glow at night would
     * light ({@link #sculkSurvivors}), so a worn path keeps a few points of the deep dark's own color in the dark.
     */
    static final float SCULK_SURVIVE = 0.20f;

    /**
     * How far a survivor dims toward the quiet ground by the end, at its full loudness: a little, so it reads as a
     * light holding on rather than one untouched, and never out.
     */
    static final float SCULK_SURVIVOR_DIM = 0.15f;

    /**
     * How much chance weighs against loudness in choosing the survivors: the loudest glints are preferred, not
     * certain, so the four rotations of a path each keep a different few.
     */
    private static final float SCULK_SURVIVOR_CHANCE = 0.6f;

    /**
     * A living skin trodden still: its glints go out one at a time, then the whole face goes smooth, grey and dead.
     *
     * <p>
     * Sculk's character is its specks - cyan points on a near-black ground - and the heavy buff it borrowed takes
     * them all down together, so every gradation is the same picture a little flatter. Here they go out one by one,
     * in a scattered order set by the rotation, each pulled down to the face's own quiet ground over a short span, so
     * most of the run is a count of lights going dark that can be read step by step, every glint also dimming a little
     * from the first step so that no gradation repeats the one before it. Which pixels are glints is read from the
     * face: the liveliest quarter, those that stand furthest above its ground in light and color together. On a block
     * with no specks this half moves almost nothing, and the deadening does the work alone.
     *
     * <p>
     * From a quarter of the way along the face deadens: its relief closes to about a third, its color drains toward
     * grey, and it lifts slightly toward an ash a little lighter than its own average. That last is what makes it
     * read as dead rather than only dark - living sculk is black because it is wet and alive, and what is left when
     * it is not is dull and grey.
     *
     * <p>
     * A fifth of the glints never go out ({@link #SCULK_SURVIVE}; Xep, 2026-11-15). They dim only a little, and they
     * are left out of the deadening altogether, so at the end of the run a few cyan points still stand on the grey.
     */
    public static int[] sculk(int[] source, int size, float progress, float strength, int seed) {
        int n = size * size;
        int[] out = opaqueCopy(source, n);
        float s = clamp01(strength);
        float wear = clamp01(progress) * s;
        if (wear <= 0f) return out;

        float[] glow = liveliness(source, n);
        float quiet = quantile(glow, 0.5f);
        float[] ground = new float[3];
        int counted = 0;
        for (int i = 0; i < n; i++) {
            if (glow[i] > quiet) continue;
            ground[0] += (source[i] >>> 16) & 0xFF;
            ground[1] += (source[i] >>> 8) & 0xFF;
            ground[2] += source[i] & 0xFF;
            counted++;
        }
        for (int c = 0; c < 3; c++) ground[c] /= Math.max(1, counted);
        float groundLight = (ground[0] + ground[1] + ground[2]) / 3f;

        // Each glint's turn to go out: a scattered order set by the rotation, spaced evenly along the span, so the
        // lights go out at a steady count rather than in clumps with gaps between. A survivor's turn passes without
        // it going out; taking survivors out of the order measured no steadier (the longest wait for the next light,
        // six gradations of eighty against seven), so the order is the one every glint had before.
        float[] loudness = loudness(glow);
        boolean[] survivor = survivors(loudness, size, seed);
        float[] draw = new float[n];
        int glints = 0;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int i = y * size + x;
                draw[i] = loudness[i] >= GLINT_COUNTED ? hash(x, y, 211 + seed * 23) : -1f;
                if (loudness[i] >= GLINT_COUNTED) glints++;
            }
        }
        float[] turn = rankFraction(draw);

        float dead = clamp01((wear - DEAD_START) / (1f - DEAD_START));
        float[] dimmed = new float[n * 3];
        float total = 0f;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int i = y * size + x;
                int pixel = source[i];
                float red = (pixel >>> 16) & 0xFF;
                float green = (pixel >>> 8) & 0xFF;
                float blue = pixel & 0xFF;
                if (survivor[i]) {
                    // A survivor dims a little toward the ground and keeps the rest of its color.
                    float out01 = SCULK_SURVIVOR_DIM * wear * loudness[i];
                    red += (ground[0] - red) * out01;
                    green += (ground[1] - green) * out01;
                    blue += (ground[2] - blue) * out01;
                } else if (loudness[i] > 0f) {
                    // A glint goes out in its turn; a faint one, too faint to be counted, fades across the span.
                    float own = loudness[i] >= GLINT_COUNTED
                        ? clamp01((wear - turn[i] * n / glints * (GLINTS_OUT_BY - GLINT_FADE)) / GLINT_FADE)
                        : clamp01(wear / GLINTS_OUT_BY);
                    // Every glint dims a little from the first step, so no gradation repeats the one before it
                    // while the next light waits its turn.
                    float out01 = Math.max(own, GLINT_DIM * wear) * loudness[i];
                    // Down to the ground's color, at a light near the ground's, a little of its own kept.
                    float keep = 0.85f + 0.3f * hash(x, y, 223 + seed * 23);
                    red += (ground[0] * keep - red) * out01;
                    green += (ground[1] * keep - green) * out01;
                    blue += (ground[2] * keep - blue) * out01;
                }
                dimmed[i * 3] = red;
                dimmed[i * 3 + 1] = green;
                dimmed[i * 3 + 2] = blue;
                total += (red + green + blue) / 3f;
            }
        }
        float mean = total / n;
        float contrast = 1f + (DEAD_CONTRAST - 1f) * dead;
        float ash = Math.max(mean, groundLight) * 1.6f + 6f;
        for (int i = 0; i < n; i++) {
            float red = dimmed[i * 3];
            float green = dimmed[i * 3 + 1];
            float blue = dimmed[i * 3 + 2];
            if (survivor[i]) {
                // A survivor is kept out of the deadening: not flattened, not lifted toward ash, not greyed.
                out[i] = pack(red, green, blue);
                continue;
            }
            float own = (red + green + blue) / 3f;
            float light = mean + (own - mean) * contrast;
            light += (ash - light) * DEAD_ASH * dead;
            float scale = own <= 0.5f ? 1f : light / own;
            red *= scale;
            green *= scale;
            blue *= scale;
            float grey = (red + green + blue) / 3f;
            float drain = DEAD_GREY * dead;
            out[i] = pack(red + (grey - red) * drain, green + (grey - green) * drain, blue + (grey - blue) * drain);
        }
        return out;
    }

    /**
     * Which of a sculk face's pixels are the glints that never go out, the same at every point of the run for a given
     * rotation: what a glow at night would light, so a worn path keeps a few points of its own color in the dark.
     *
     * <p>
     * About a fifth of the counted glints ({@link #SCULK_SURVIVE}), the loudest preferred, picked by a hash the
     * rotation seeds. Read from the face alone, like the rest of the look: a face with no glints has no survivors.
     *
     * @param seed the rotation, as handed to {@link #sculk}
     */
    public static boolean[] sculkSurvivors(int[] source, int size, int seed) {
        return survivors(loudness(liveliness(source, size * size)), size, seed);
    }

    /**
     * The block and sky light, out of fifteen, sculk's glow is baked with: 7 is about 45% of full (Xep, 2026-11-15,
     * "sculk B"), between the nether's ash and the end's cracks.
     */
    public static final int SCULK_GLOW_LEVEL = 7;

    /** How far along the run the survivors start to glow: the first third, where the other glints are going out. */
    static final float SCULK_GLOW_START = 0.35f;

    /**
     * The pixels of the sculk look that glow: the survivors, the fifth of the counted glints that never go out, each in
     * the color {@link #sculk} draws it in, and everything else clear (alpha 0), at the same size. They glow from
     * {@link #SCULK_GLOW_START} of the way along the run, so a worn path keeps a few points of the deep dark's own
     * color
     * in the dark.
     *
     * @param progress the eased progress, as handed to {@link #sculk}
     * @param seed     the rotation, as handed to {@link #sculk}
     */
    public static int[] sculkGlow(int[] source, int size, float progress, float strength, int seed) {
        int n = size * size;
        int[] out = new int[n];
        if (clamp01(progress) * clamp01(strength) < SCULK_GLOW_START) return out;
        int[] worn = sculk(source, size, progress, strength, seed);
        boolean[] survivor = sculkSurvivors(source, size, seed);
        for (int i = 0; i < n; i++) {
            if (survivor[i]) out[i] = worn[i];
        }
        return out;
    }

    /** How far each pixel stands above the quiet ground, in light and color together. */
    private static float[] liveliness(int[] source, int n) {
        float[] glow = new float[n];
        float[] lum = lumas(source, n);
        for (int i = 0; i < n; i++) {
            glow[i] = lum[i] + 1.5f * chroma(source[i]);
        }
        return glow;
    }

    /**
     * Each pixel's loudness as a glint, 0 to 1: nothing below the liveliest quarter, so the count of lights going out
     * is a count of lights, and all of it a third of the way from there to the loudest.
     */
    private static float[] loudness(float[] glow) {
        float loud = quantile(glow, 1f);
        float lively = quantile(glow, GLINT_SHARE);
        float[] loudness = new float[glow.length];
        for (int i = 0; i < glow.length; i++) {
            loudness[i] = loud - lively <= 0.5f ? 0f : clamp01((glow[i] - lively) / (loud - lively) * 3f);
        }
        return loudness;
    }

    private static boolean[] survivors(float[] loudness, int size, int seed) {
        int n = size * size;
        boolean[] survivor = new boolean[n];
        float[] pick = new float[n];
        int glints = 0;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int i = y * size + x;
                boolean counted = loudness[i] >= GLINT_COUNTED;
                pick[i] = counted ? loudness[i] + SCULK_SURVIVOR_CHANCE * hash(x, y, 241 + seed * 23) : -1f;
                if (counted) glints++;
            }
        }
        int keep = Math.max(1, Math.round(glints * SCULK_SURVIVE));
        float[] place = rankFraction(pick);
        for (int i = 0; i < n; i++) {
            if (pick[i] >= 0f && Math.round(place[i] * n) < keep) survivor[i] = true;
        }
        return survivor;
    }

    // ------------------------------------------------------------------
    // Cloud: pressed into a flatter, greyer, denser white
    // ------------------------------------------------------------------

    /** How much of the face the pressed track claims at the end of the run, before strength. */
    static final float CLOUD_COVERAGE = 0.58f;

    /** How far the pressed track's light falls, as a fraction of its own, at the end of the run. */
    static final float CLOUD_PRESS = 0.11f;

    /** How far the whole face's light falls as it packs down, at the end of the run. */
    static final float CLOUD_SETTLE = 0.05f;

    /** How much darker the trough's inner wall is than its floor, at the end of the run. */
    static final float CLOUD_WALL = 0.07f;

    /** How much of the whole face's relief is left at the end of the run: fluff packed down. */
    private static final float CLOUD_PACK = 0.45f;

    /**
     * A soft white solid pressed down underfoot: denser, flatter and a little greyer where trodden, its fluff pushed
     * up into a brighter rim round the track.
     *
     * <p>
     * The lightest rub it borrowed darkens part of the face toward its own shadow, which on a face with ten levels of
     * relief is a smudge. Snow showed that a full rub takes white to grey; this goes a little way there and no
     * further. The whole face packs down first, its relief closing to a little under half, because a cloud walked on
     * is a cloud compressed. Then a track, ranked as the rub ranks it, goes denser: its relief closes almost entirely
     * and its light falls by about a sixth with the packing, toward the face's own shadow color, which on a cloud is a
     * cool grey.
     * And the pixels round the track's edge are lifted toward the face's brightest tone, which is the fluff the foot
     * pushed aside. That rim is what makes a white track legible at all; without it a pressed cloud is only a faintly
     * darker cloud.
     *
     * @param coverage the floored progress the track grows with, 0 to 1
     * @param progress the eased progress the depth of every change follows, 0 to 1
     */
    public static int[] cloud(int[] source, int size, float coverage, float progress, float strength, int seed) {
        int n = size * size;
        int[] out = opaqueCopy(source, n);
        float s = clamp01(strength);
        float depth = clamp01(progress) * s;
        if (depth <= 0f) return out;

        float[] lum = lumas(source, n);
        float mean = 0f;
        for (float l : lum) mean += l;
        mean /= n;
        float[] shadow = toneColor(source, n, 0f, 0.20f);
        float[] bright = toneColor(source, n, 0.90f, 1f);
        float[] rank = rankFraction(WearCompositor.sourceWearOrder(source, size, seed));
        float claimed = clamp01(coverage) * CLOUD_COVERAGE * s;

        float[] pressed = new float[n];
        for (int i = 0; i < n; i++) {
            float r = rank[i];
            pressed[i] = r < claimed ? 1f : claimed <= 0f ? 0f : clamp01(1f - (r - claimed) / 0.16f) * 0.6f;
        }
        float packed = 1f + (CLOUD_PACK - 1f) * depth;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int i = y * size + x;
                int pixel = source[i];
                float red = (pixel >>> 16) & 0xFF;
                float green = (pixel >>> 8) & 0xFF;
                float blue = pixel & 0xFF;
                float own = lum[i];
                float a = pressed[i] * depth;

                float beside = Math.max(
                    Math.max(pressed[wrap(x - 1, y, size)], pressed[wrap(x + 1, y, size)]),
                    Math.max(pressed[wrap(x, y - 1, size)], pressed[wrap(x, y + 1, size)]));
                float least = Math.min(
                    Math.min(pressed[wrap(x - 1, y, size)], pressed[wrap(x + 1, y, size)]),
                    Math.min(pressed[wrap(x, y - 1, size)], pressed[wrap(x, y + 1, size)]));

                float relief = packed * (1f - 0.8f * a);
                float light = (mean + (own - mean) * relief) * (1f - CLOUD_SETTLE * depth) * (1f - CLOUD_PRESS * a);
                // The trough's inner wall: trodden, beside something that is not, in its own shade.
                if (least < pressed[i]) light *= 1f - CLOUD_WALL * (pressed[i] - least) * depth;
                float scale = own <= 0.5f ? 0f : light / own;
                red *= scale;
                green *= scale;
                blue *= scale;
                if (a > 0f) {
                    float shadowLight = (shadow[0] + shadow[1] + shadow[2]) / 3f;
                    float match = shadowLight <= 0.5f ? 0f : light / shadowLight;
                    float cool = 0.6f * a;
                    red += (shadow[0] * match - red) * cool;
                    green += (shadow[1] * match - green) * cool;
                    blue += (shadow[2] * match - blue) * cool;
                } else if (beside > 0f) {
                    // The rim: untrodden, beside something trodden, pushed up and catching the light.
                    float rise = 0.8f * beside * depth;
                    red += (lift(bright[0]) - red) * rise;
                    green += (lift(bright[1]) - green) * rise;
                    blue += (lift(bright[2]) - blue) * rise;
                }
                out[i] = pack(red, green, blue);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Shared arithmetic
    // ------------------------------------------------------------------

    static int[] opaqueCopy(int[] source, int n) {
        int[] out = new int[n];
        for (int i = 0; i < n && i < source.length; i++) {
            out[i] = 0xFF000000 | (source[i] & 0xFFFFFF);
        }
        return out;
    }

    static float[] lumas(int[] source, int n) {
        float[] lum = new float[n];
        for (int i = 0; i < n && i < source.length; i++) {
            int p = source[i];
            lum[i] = (((p >>> 16) & 0xFF) + ((p >>> 8) & 0xFF) + (p & 0xFF)) / 3f;
        }
        return lum;
    }

    static float chroma(int p) {
        int r = (p >>> 16) & 0xFF;
        int g = (p >>> 8) & 0xFF;
        int b = p & 0xFF;
        return Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b));
    }

    /** The value below which the given fraction of the values sit. */
    static float quantile(float[] values, float fraction) {
        if (values.length == 0) return 0f;
        float[] sorted = values.clone();
        java.util.Arrays.sort(sorted);
        int index = Math.round(clamp01(fraction) * (sorted.length - 1));
        return sorted[index];
    }

    /** The mean color of the pixels whose light falls between two fractions of the face's, darkest first. */
    static float[] toneColor(int[] source, int n, float from, float to) {
        Integer[] byLight = new Integer[n];
        final float[] lum = lumas(source, n);
        for (int i = 0; i < n; i++) byLight[i] = Integer.valueOf(i);
        java.util.Arrays.sort(byLight, new java.util.Comparator<Integer>() {

            @Override
            public int compare(Integer a, Integer b) {
                int byValue = Float.compare(lum[a.intValue()], lum[b.intValue()]);
                return byValue != 0 ? byValue : a.compareTo(b);
            }
        });
        int start = Math.min(n - 1, Math.max(0, (int) Math.floor(from * n)));
        int end = Math.max(start + 1, Math.min(n, (int) Math.ceil(to * n)));
        float[] color = new float[3];
        for (int k = start; k < end; k++) {
            int p = source[byLight[k].intValue()];
            color[0] += (p >>> 16) & 0xFF;
            color[1] += (p >>> 8) & 0xFF;
            color[2] += p & 0xFF;
        }
        for (int c = 0; c < 3; c++) color[c] /= end - start;
        return color;
    }

    /** Each pixel's place in a wear order, 0 for the first to go and just under 1 for the last. */
    static float[] rankFraction(final float[] order) {
        int n = order.length;
        Integer[] byOrder = new Integer[n];
        for (int i = 0; i < n; i++) byOrder[i] = Integer.valueOf(i);
        java.util.Arrays.sort(byOrder, new java.util.Comparator<Integer>() {

            @Override
            public int compare(Integer a, Integer b) {
                int byValue = Float.compare(order[b.intValue()], order[a.intValue()]);
                return byValue != 0 ? byValue : a.compareTo(b);
            }
        });
        float[] rank = new float[n];
        for (int k = 0; k < n; k++) rank[byOrder[k].intValue()] = k / (float) n;
        return rank;
    }

    static float neighbourhood(float[] lum, int size, int x, int y) {
        float total = 0f;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                total += lum[wrap(x + dx, y + dy, size)];
            }
        }
        return total / 9f;
    }

    /** The lightest of a pixel's eight neighbours, wrapping. */
    private static float peak(float[] lum, int size, int x, int y) {
        float most = 0f;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0) continue;
                most = Math.max(most, lum[wrap(x + dx, y + dy, size)]);
            }
        }
        return most;
    }

    static int wrap(int x, int y, int size) {
        return ((y % size + size) % size) * size + ((x % size + size) % size);
    }

    /** A highlight's channel: the face's own brightest tone, lifted a third of the way to white. */
    static float lift(float channel) {
        return channel + (255f - channel) * 0.35f;
    }

    /** A fibre's channel: the face's brightest tone with its color pushed out from its grey, and a little lighter. */
    static float vivid(float[] color, int channel) {
        float grey = (color[0] + color[1] + color[2]) / 3f;
        return (grey + (color[channel] - grey) * 1.35f) * 1.20f;
    }

    static float lineAmount(float edge, float width) {
        if (width <= 0f || edge >= width) return 0f;
        float across = edge / width;
        if (across <= 0.35f) return 1f;
        float fade = (1f - across) / 0.65f;
        return fade * fade;
    }

    static float[] veins(int cells, int seed) {
        Long key = Long.valueOf(((long) cells << 32) ^ (seed & 0xFFFFFFFFL));
        float[] held = VEINS.get(key);
        if (held != null) return held;
        float[] built = WearCompositor.fractureField(FIELD, cells, seed);
        VEINS.put(key, built);
        return built;
    }

    static float lowFrequency(int x, int y, int span, int cells, int seed) {
        float acrossX = x * cells / (float) span;
        float acrossY = y * cells / (float) span;
        int cellX = (int) acrossX;
        int cellY = (int) acrossY;
        float withinX = acrossX - cellX;
        float withinY = acrossY - cellY;
        withinX = withinX * withinX * (3f - 2f * withinX);
        withinY = withinY * withinY * (3f - 2f * withinY);
        int leftX = ((cellX % cells) + cells) % cells;
        int rightX = ((cellX + 1) % cells + cells) % cells;
        int topY = ((cellY % cells) + cells) % cells;
        int bottomY = ((cellY + 1) % cells + cells) % cells;
        float top = hash(leftX, topY, seed) * (1f - withinX) + hash(rightX, topY, seed) * withinX;
        float bottom = hash(leftX, bottomY, seed) * (1f - withinX) + hash(rightX, bottomY, seed) * withinX;
        return top * (1f - withinY) + bottom * withinY;
    }

    static float hash(int x, int y, int seed) {
        int h = x * 374761393 + y * 668265263 + seed * 1442695040;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535f;
    }

    static float clamp01(float value) {
        return value < 0f ? 0f : value > 1f ? 1f : value;
    }

    static int pack(float red, float green, float blue) {
        return 0xFF000000 | (channel(red) << 16) | (channel(green) << 8) | channel(blue);
    }

    private static int channel(float value) {
        int rounded = Math.round(value);
        return rounded < 0 ? 0 : rounded > 255 ? 255 : rounded;
    }
}
