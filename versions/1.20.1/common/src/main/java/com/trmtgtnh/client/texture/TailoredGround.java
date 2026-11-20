package com.trmtgtnh.client.texture;

import static com.trmtgtnh.client.texture.TailoredWear.clamp01;
import static com.trmtgtnh.client.texture.TailoredWear.hash;
import static com.trmtgtnh.client.texture.TailoredWear.lift;
import static com.trmtgtnh.client.texture.TailoredWear.lowFrequency;
import static com.trmtgtnh.client.texture.TailoredWear.lumas;
import static com.trmtgtnh.client.texture.TailoredWear.neighbourhood;
import static com.trmtgtnh.client.texture.TailoredWear.opaqueCopy;
import static com.trmtgtnh.client.texture.TailoredWear.pack;
import static com.trmtgtnh.client.texture.TailoredWear.quantile;
import static com.trmtgtnh.client.texture.TailoredWear.rankFraction;
import static com.trmtgtnh.client.texture.TailoredWear.toneColor;
import static com.trmtgtnh.client.texture.TailoredWear.wrap;

/**
 * Seven looks made for the seven older families that had only ever worn one of the operator looks: sand, gravel,
 * cobblestone, snow, ice, the Nether's ground and end stone, each drawn as that material wears.
 *
 * <p>
 * Xep, 2026-11-12, after choosing the four made for 0.9.229's new families as their defaults: new patterns for the
 * families that were already there, added beside the old ones and never in place of them, shown against each family's
 * current look on a comparison sheet so he can choose. Until he does, every one of those families keeps the look it
 * ships with; these are offered in the file, the picker and the settings screen as choices like any other.
 *
 * <p>
 * Held to everything {@link TailoredWear} is held to, and built from its arithmetic: every color comes from the face
 * handed in (its shadows, its brightest tones, its own average, lightened, darkened or greyed), so each is safe on any
 * block; the same face, progress, strength and rotation always give the same pixels; the rotation seeds every
 * placement. Most of the seven grow a trodden track on the rub's own ranking ({@link WearCompositor#sourceWearOrder})
 * and change what is inside it, because that is where a foot lands on any block; ice is the exception, because a
 * scuff on ice is a haze and a scratch rather than a track.
 *
 * <p>
 * Free of Minecraft types, like the file beside it, and to go on {@code CoreStaysPortableTest.PORTABLE} with it when
 * the ports carry them.
 */
public final class TailoredGround {

    private TailoredGround() {}

    /**
     * The trodden track most of these looks change, as the share of each pixel that is inside it: 1 inside, a soft
     * rim of up to six tenths fading over {@code rim} of the ranking beyond it, and 0 elsewhere. On the rub's ranking,
     * so it forms where a foot would land on any block.
     */
    static float[] track(int[] source, int size, float claimed, float rim, int seed) {
        float[] rank = rankFraction(WearCompositor.sourceWearOrder(source, size, seed));
        float[] pressed = new float[rank.length];
        for (int i = 0; i < rank.length; i++) {
            float r = rank[i];
            pressed[i] = r < claimed ? 1f : claimed <= 0f ? 0f : clamp01(1f - (r - claimed) / rim) * 0.6f;
        }
        return pressed;
    }

    private static float mean(float[] lum) {
        float total = 0f;
        for (float l : lum) total += l;
        return lum.length == 0 ? 0f : total / lum.length;
    }

    /** The largest share of a pixel's four neighbours that is inside the track. */
    private static float beside(float[] pressed, int size, int x, int y) {
        return Math.max(
            Math.max(pressed[wrap(x - 1, y, size)], pressed[wrap(x + 1, y, size)]),
            Math.max(pressed[wrap(x, y - 1, size)], pressed[wrap(x, y + 1, size)]));
    }

    /**
     * How far a pixel lies on a crack, 0 to 1: a fracture network {@code cells} cells across of the kind the crack
     * look is built on ({@link WearCompositor#fractureField}), warped as taint's veins are, a line {@code width} wide
     * in
     * field units, sampled across the pixel. Nothing at a width of nought.
     */
    static float fissure(int x, int y, int size, int cells, int seed, float width) {
        if (width <= 0f) return 0f;
        int field = TailoredWear.FIELD;
        float[] edges = TailoredWear.veins(cells, seed);
        int subs = Math.max(1, field / size);
        int warpX = Math.round((lowFrequency(x, y, size, 2, seed + 157) - 0.5f) * 5f);
        int warpY = Math.round((lowFrequency(x, y, size, 2, seed + 173) - 0.5f) * 5f);
        int fieldX = x * field / size;
        int fieldY = y * field / size;
        float total = 0f;
        for (int sy = 0; sy < subs; sy++) {
            int py = ((fieldY + sy + warpY) % field + field) % field;
            for (int sx = 0; sx < subs; sx++) {
                int px = ((fieldX + sx + warpX) % field + field) % field;
                total += TailoredWear.lineAmount(edges[py * field + px], width);
            }
        }
        return total / (subs * subs);
    }

    // ------------------------------------------------------------------
    // Sand: trodden firm, the grains pushed aside
    // ------------------------------------------------------------------

    /** How much of the face the trodden line claims at the end of the run, before strength. */
    static final float SAND_COVERAGE = 0.62f;

    /** How far the trodden line's light falls, as a fraction of its own, at the end of the run. */
    static final float SAND_FIRM = 0.24f;

    /** How much of a trodden pixel's own relief closes toward its neighbours': loose grains packed into a crust. */
    static final float SAND_PACK = 0.75f;

    /** How far a trodden pixel's color is drawn toward the face's own shadow color: firm sand, a shade damper. */
    static final float SAND_DAMP = 0.45f;

    /** How far a grain pushed up beside the line is lifted toward the face's brightest tone. */
    static final float SAND_PUSHED = 0.60f;

    /**
     * Sand trodden into a firm line: darker, its grain packed down into a crust, with the loose grains a foot pushed
     * out of the way heaped in a lighter scatter along both its edges.
     *
     * <p>
     * The smoothed rub sand ships with buffs the whole face and then wears a track; on sand, a track is not worn away
     * but packed, and the difference shows at its edges. Inside, the grain closes up (each pixel's light pulled toward
     * its neighbourhood's) and the light falls by about a sixth toward the face's own shadow color, the color damp,
     * firm sand is. Outside, only a scatter of the pixels touching the line are lifted toward the face's brightest
     * tone: grains displaced rather than a clean rim, so the edge reads as loose. The rest of the face is left as it
     * was, because untrodden sand is untouched.
     *
     * @param coverage the floored progress the line grows with, 0 to 1
     * @param progress the eased progress the depth of every change follows, 0 to 1
     */
    public static int[] sand(int[] source, int size, float coverage, float progress, float strength, int seed) {
        int n = size * size;
        int[] out = opaqueCopy(source, n);
        float s = clamp01(strength);
        float depth = clamp01(progress) * s;
        if (depth <= 0f) return out;

        float[] lum = lumas(source, n);
        float[] shadow = toneColor(source, n, 0f, 0.25f);
        float[] bright = toneColor(source, n, 0.90f, 1f);
        float shadowLight = (shadow[0] + shadow[1] + shadow[2]) / 3f;
        float[] pressed = track(source, size, clamp01(coverage) * SAND_COVERAGE * s, 0.14f, seed);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int i = y * size + x;
                int pixel = source[i];
                float red = (pixel >>> 16) & 0xFF;
                float green = (pixel >>> 8) & 0xFF;
                float blue = pixel & 0xFF;
                float own = lum[i];
                float a = pressed[i] * depth;
                if (a > 0f) {
                    float light = own + (neighbourhood(lum, size, x, y) - own) * SAND_PACK * a;
                    light *= 1f - SAND_FIRM * a;
                    float scale = own <= 0.5f ? 0f : light / own;
                    red *= scale;
                    green *= scale;
                    blue *= scale;
                    float match = shadowLight <= 0.5f ? 0f : light / shadowLight;
                    float damp = SAND_DAMP * a;
                    red += (shadow[0] * match - red) * damp;
                    green += (shadow[1] * match - green) * damp;
                    blue += (shadow[2] * match - blue) * damp;
                } else {
                    float edge = beside(pressed, size, x, y);
                    if (edge > 0f && hash(x, y, 401 + seed * 31) < 0.65f) {
                        float rise = SAND_PUSHED * edge * depth;
                        red += (bright[0] - red) * rise;
                        green += (bright[1] - green) * rise;
                        blue += (bright[2] - blue) * rise;
                    }
                }
                out[i] = pack(red, green, blue);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Gravel and cobblestone share this: roughness from the first stages, and small dark patches that grow
    // ------------------------------------------------------------------

    /**
     * How soon the roughness of gravel and cobblestone shows: their grain, their uneven grime or grind and (on cobble)
     * the split between polished tops and ground joints follow the wear raised to this power rather than the wear
     * itself, about half their full strength at a fifth of the way, and all of it at the end, where the power changes
     * nothing. Xep, 2026-11-16, of the look these began as: "RD with a slight bit more roughness on the early stages,
     * so the early stages don't look indistinguishable from unworn"; 0.45 was that step, and 0.35 is the further one
     * he chose ("slightly more roughness").
     */
    static final float EARLY_ROUGH = 0.35f;

    /**
     * How far a dark patch pulls a pixel's wear forward, at most: a patch pixel shows up to two fifths of the wear it
     * has still to come, its grime and gaps or its dulling and joints, which is what makes it a little darker. The pull
     * fades with the run and is gone at its end, so the worn-out look is the one the patches grow into (Xep,
     * 2026-11-16: "maybe slight dark patches early on, with the dark patches being small and subtle and growing into
     * the current end wear look").
     */
    static final float PATCH_PULL = 0.40f;

    /** The share of the face a patch has claimed at the start of the run, and how much more it claims by the end. */
    static final float PATCH_START = 0.04f;

    static final float PATCH_GROWTH = 0.46f;

    /** The soft edge of a patch, in ranked share of the face. */
    static final float PATCH_EDGE = 0.06f;

    /** The weight the roughness takes at a pixel's wear: {@link #EARLY_ROUGH}'s earlier curve. */
    static float rough(float a) {
        return (float) Math.pow(a, EARLY_ROUGH);
    }

    /** Grain noise for a pixel, -1 to 1. */
    private static float grain(int x, int y, int salt) {
        return hash(x, y, salt) * 2f - 1f;
    }

    /**
     * The dark patches: each pixel's share of one, 0 to 1, at this depth. The face is ranked on a small blotchy field
     * the rotation seeds, and the patches claim it in that order: a few pixels at first, then more and larger, joined
     * up, as the run goes on.
     */
    static float[] patches(int size, float depth, int seed) {
        int n = size * size;
        float[] field = new float[n];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                field[y * size + x] = lowFrequency(x, y, size, 5, 571 + seed * 73) * 0.65f
                    + hash(x, y, 577 + seed * 79) * 0.35f;
            }
        }
        float[] rank = rankFraction(field);
        float share = PATCH_START + PATCH_GROWTH * depth;
        float[] patch = new float[n];
        for (int i = 0; i < n; i++) patch[i] = clamp01((share - rank[i]) / PATCH_EDGE);
        return patch;
    }

    /**
     * A pixel's wear, pulled forward where a patch lies: by half as much off the trodden ground as on it, and by
     * nothing at all at the end of the run.
     */
    private static float patched(float a, float[] patch, int i, float pressed, float depth) {
        if (patch == null || patch[i] <= 0f) return a;
        return a + (1f - a) * PATCH_PULL * patch[i] * (0.5f + 0.5f * pressed) * (1f - depth);
    }

    // ------------------------------------------------------------------
    // Gravel: the stones part, and dark gaps open between them
    // ------------------------------------------------------------------

    /** How much of the face the trodden ground claims at the end of the run, before strength. */
    static final float GRAVEL_COVERAGE = 0.74f;

    /** How far a gap between stones darkens, as a fraction of its own light, at the end of the run. */
    static final float GRAVEL_GAP = 0.64f;

    /** How far apart in light a gap and a stone are read against: smaller reads more of the face as gap. */
    static final float GRAVEL_SPREAD = 0.26f;

    /** Where along the run the stones' own edges start giving way to the gaps beside them. */
    static final float GRAVEL_PART = 0.15f;

    /** The share of the stones' edges that have given way by the end of the run. */
    static final float GRAVEL_PARTED_SHARE = 0.65f;

    /** How far the whole trodden ground grimes darker, as a fraction of its own light, at the end of the run. */
    static final float GRAVEL_GRIME = 0.30f;

    /** How uneven the grime is: the share of it a broad field adds in some places and takes away in others. */
    static final float GRAVEL_PATCHY = 0.55f;

    /** Grain: each trodden pixel's light knocked up or down by up to this share, never above its own. */
    static final float GRAVEL_GRAIN = 0.16f;

    /** How much further every trodden pixel is drawn toward the face's own shadow color, on top of the gaps' pull. */
    static final float GRAVEL_SOIL = 0.12f;

    /**
     * Loose stones shoved apart underfoot: the gaps between them open and go dark, and the whole trodden ground grimes
     * over, so the stones (the flint) stand out more sharply against their gaps while the ground darkens step by step.
     *
     * <p>
     * The rub gravel ships with wears a darker patch across stones and gaps alike. Gravel is not worn down; it is
     * displaced, and what that leaves is more gap and less stone. So the face is read as the two it is made of: a
     * pixel darker than its neighbourhood is gap, and one lighter is stone. Inside the trodden ground every gap
     * deepens toward the face's own darkest color, from the run's start, and from 15% of the way the stones'
     * edges (stone pixels touching a gap, and on their corners too once the parting is a quarter done) give way to it,
     * a scatter at a time, which is the stones parting in clumps. Over all of it lies grime, uneven on a broad field,
     * with grain through it: every trodden pixel darkens by up to three tenths and is drawn toward the face's own
     * shadow color, the stones less than the gaps, and no pixel ever ends lighter than it began. Small dark patches
     * open from the start, each a pixel's later wear pulled forward, and grow and join up until at the end they are the
     * grime and gaps themselves.
     *
     * <p>
     * Xep, 2026-11-12, of the first version, which lifted the stones cleaner: "The gravel looks too clean as it wears,
     * and it's harder to tell the wear apart. I do like that it makes the 'flint' in the gravel more defined with
     * wear." Then, 2026-11-15 and 16, from preview variants of that version: he asked for ones adding "more
     * 'roughness', 'darkness', 'both'", picked the rougher and darker one (RD) on both gravel and cobble, asked for its
     * early stages to read as worn, and of the variant here, with early roughness and small dark patches: "Let's do
     * RD+XE for both". Against the version before it, on vanilla's face, the change from unworn to 15% is about twice
     * as large, each step of the last third about a third larger, and the end about a quarter darker.
     *
     * @param coverage the floored progress the ground grows with, 0 to 1
     * @param progress the eased progress the depth of every change follows, 0 to 1
     */
    public static int[] gravel(int[] source, int size, float coverage, float progress, float strength, int seed) {
        return gravel(source, size, coverage, progress, strength, seed, true);
    }

    /** The look, with or without its dark patches: without them only so a test can see what they add. */
    static int[] gravel(int[] source, int size, float coverage, float progress, float strength, int seed,
        boolean withPatches) {
        int n = size * size;
        int[] out = opaqueCopy(source, n);
        float s = clamp01(strength);
        float depth = clamp01(progress) * s;
        if (depth <= 0f) return out;

        float[] lum = lumas(source, n);
        float[] shadow = toneColor(source, n, 0f, 0.10f);
        float shadowLight = (shadow[0] + shadow[1] + shadow[2]) / 3f;
        float spread = Math.max(6f, (quantile(lum, 0.9f) - quantile(lum, 0.1f)) * GRAVEL_SPREAD);
        float[] gap = new float[n];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int i = y * size + x;
                gap[i] = clamp01((neighbourhood(lum, size, x, y) - lum[i]) / spread);
            }
        }
        float parting = clamp01((depth - GRAVEL_PART) / (1f - GRAVEL_PART));
        float[] pressed = track(source, size, clamp01(coverage) * GRAVEL_COVERAGE * s, 0.12f, seed);
        float[] patch = withPatches ? patches(size, depth, seed) : null;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int i = y * size + x;
                int pixel = source[i];
                float red = (pixel >>> 16) & 0xFF;
                float green = (pixel >>> 8) & 0xFF;
                float blue = pixel & 0xFF;
                float own = lum[i];
                float a = patched(pressed[i] * depth, patch, i, pressed[i], depth);
                if (a <= 0f) {
                    out[i] = pack(red, green, blue);
                    continue;
                }
                float open = gap[i];
                if (open < 0.5f) {
                    // A stone's edge: it gives way to the gap beside it, in a scattered order, as the run goes on.
                    float near = Math.max(
                        Math.max(gap[wrap(x - 1, y, size)], gap[wrap(x + 1, y, size)]),
                        Math.max(gap[wrap(x, y - 1, size)], gap[wrap(x, y + 1, size)]));
                    if (parting > 0.25f) {
                        // Further on, the corners part too, so the gaps open in coarser clumps.
                        near = Math.max(
                            near,
                            Math.max(
                                Math.max(gap[wrap(x - 1, y - 1, size)], gap[wrap(x + 1, y + 1, size)]),
                                Math.max(gap[wrap(x + 1, y - 1, size)], gap[wrap(x - 1, y + 1, size)])));
                    }
                    if (near >= 0.5f && hash(x, y, 419 + seed * 37) < GRAVEL_PARTED_SHARE * parting) {
                        open = Math.max(open, 0.8f * parting);
                    }
                }
                float r = rough(a);
                float light = own * (1f - GRAVEL_GAP * open * a);
                // The grime, over stone and gap alike: what makes each gradation read as further on than the last.
                // Its evenness follows the wear, its unevenness the earlier roughness curve.
                float field = lowFrequency(x, y, size, 3, 541 + seed * 43);
                light *= 1f - GRAVEL_GRIME * a - GRAVEL_GRIME * GRAVEL_PATCHY * (2f * field - 1f) * r;
                // Grain: each pixel knocked up or down, never above its own light, so the stones never lighten.
                light = Math.min(own, light * (1f + GRAVEL_GRAIN * r * grain(x, y, 523 + seed * 29)));
                float scale = own <= 0.5f ? 0f : light / own;
                red *= scale;
                green *= scale;
                blue *= scale;
                float match = shadowLight <= 0.5f ? 0f : light / shadowLight;
                float hue = clamp01((0.6f * open + 0.3f * (1f - open)) * a + GRAVEL_SOIL * a);
                red += (shadow[0] * match - red) * hue;
                green += (shadow[1] * match - green) * hue;
                blue += (shadow[2] * match - blue) * hue;
                out[i] = pack(red, green, blue);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Cobblestone: the stone tops polish smooth, the mortar grinds darker
    // ------------------------------------------------------------------

    /** How much of the face the trodden way claims at the end of the run, before strength. */
    static final float COBBLE_COVERAGE = 0.80f;

    /** How much of a stone top's own relief is polished away, at the end of the run. */
    static final float COBBLE_POLISH = 0.58f;

    /** How far the whole trodden way dulls, as a fraction of its own light, at the end of the run. */
    static final float COBBLE_DULL = 0.30f;

    /** How far the mortar darkens, as a fraction of its own light, at the end of the run. */
    static final float COBBLE_GRIND = 0.64f;

    /** How uneven the mortar's grinding is along the joints: the share a broad field adds or takes away. */
    static final float COBBLE_PATCHY = 0.50f;

    /** How far the mortar is drawn toward the face's own shadow color, at the end of the run. */
    static final float COBBLE_SOIL = 0.45f;

    /** Grain: each trodden pixel's light knocked up or down by up to this share, never above its own. */
    static final float COBBLE_GRAIN = 0.14f;

    /** Where along the run the cracks start, how dark a crack runs at its deepest, and how wide it grows. */
    static final float COBBLE_CRACK_START = 0.25f;

    static final float COBBLE_CRACK = 0.68f;

    static final float COBBLE_CRACK_GROW = 0.046f;

    /**
     * Where along the run a second, finer network of cracks starts, how dark it runs at its deepest and how wide it
     * grows (Xep, 2026-11-12, of the second version: "I feel like the cobble should be darker when fully worn, perhaps
     * with slightly more cracking to it by 100%"; the dulling above was raised for the first half of that).
     */
    static final float COBBLE_FINE_START = 0.60f;

    static final float COBBLE_FINE = 0.60f;

    static final float COBBLE_FINE_GROW = 0.022f;

    /** The share of the trodden way that carries a scuff at the end of the run, and the light a scuff keeps. */
    static final float COBBLE_SCUFF = 0.20f;

    static final float COBBLE_SCUFF_LIGHT = 0.76f;

    /**
     * A cobbled way walked smooth and knocked about: the tops of the stones polish flat, so each stone's shape reads
     * more sharply against its joints, the mortar grinds down darker, and scuffs and cracks gather on them as the run
     * goes on.
     *
     * <p>
     * Which pixels are stone and which are mortar is read from the face: the darkest part of it is mortar, fading to
     * stone over the darker half. In the trodden way a stone's relief closes toward its own neighbourhood (polished
     * flat, keeping its own hue) and the mortar darkens unevenly toward the face's own shadow color, which together
     * define each stone's outline; that split, and a grain through the whole way, show from the first stages. The
     * whole way dulls, three tenths darker by the end, rather than brightening. From a quarter of the way along,
     * hairline cracks open across stone and joint alike on a fracture network of the kind the crack look draws,
     * widening and darkening with the run; from three fifths, a finer network of twice as many cells joins them, so a
     * worn-out way is broken up more than a half-worn one; and a growing scatter of scuffs (a pixel greyed and
     * darkened, past halfway spreading to the stone beside it) marks the stones. Small dark patches open from the
     * start, each a pixel's later wear pulled forward, and grow and join up until at the end they are the joints and
     * cracks themselves.
     *
     * <p>
     * Xep, 2026-11-12, of the first version, which lifted the polished tops toward the face's brightest tone: "The
     * cobble looks too bright as it wears, and also too 'perfect', where the old pattern added a lot of scuffs and
     * cracks the more worn it got. I do like how some of the aspects of the new one become more defined, though." So
     * the sheen is gone and the cracks and scuffs are here, but neither buries the stones in the cracked rub's dirt.
     * Then, 2026-11-15 and 16, from preview variants of that version: he asked for ones adding "more 'roughness',
     * 'darkness', 'both'", picked the rougher and darker one (RD) on both gravel and cobble, asked for its early stages
     * to read as worn, and of the variant here, with early roughness and small dark patches: "Let's do RD+XE for both".
     * Against the version before it, on vanilla's face, the change from unworn to 15% is about seven tenths larger,
     * each step of the last third about a quarter larger, and the end about a quarter darker.
     *
     * @param coverage the floored progress the way grows with, 0 to 1
     * @param progress the eased progress the depth of every change follows, 0 to 1
     */
    public static int[] cobble(int[] source, int size, float coverage, float progress, float strength, int seed) {
        return cobble(source, size, coverage, progress, strength, seed, true);
    }

    /** The look, with or without its dark patches: without them only so a test can see what they add. */
    static int[] cobble(int[] source, int size, float coverage, float progress, float strength, int seed,
        boolean withPatches) {
        int n = size * size;
        int[] out = opaqueCopy(source, n);
        float s = clamp01(strength);
        float depth = clamp01(progress) * s;
        if (depth <= 0f) return out;

        float[] lum = lumas(source, n);
        float[] shadow = toneColor(source, n, 0f, 0.10f);
        float shadowLight = (shadow[0] + shadow[1] + shadow[2]) / 3f;
        float cracking = clamp01((depth - COBBLE_CRACK_START) / (1f - COBBLE_CRACK_START));
        float crackWidth = cracking <= 0f ? 0f : 0.006f + COBBLE_CRACK_GROW * cracking;
        float fineCracking = clamp01((depth - COBBLE_FINE_START) / (1f - COBBLE_FINE_START));
        float fineWidth = fineCracking <= 0f ? 0f : 0.004f + COBBLE_FINE_GROW * fineCracking;
        float scuffShare = COBBLE_SCUFF * depth;
        float joint = quantile(lum, 0.40f);
        float floor = quantile(lum, 0.05f);
        float[] mortar = new float[n];
        for (int i = 0; i < n; i++) {
            mortar[i] = joint - floor <= 0.5f ? 0f : clamp01((joint - lum[i]) / (joint - floor));
        }
        float[] pressed = track(source, size, clamp01(coverage) * COBBLE_COVERAGE * s, 0.12f, seed);
        float[] patch = withPatches ? patches(size, depth, seed) : null;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int i = y * size + x;
                int pixel = source[i];
                float red = (pixel >>> 16) & 0xFF;
                float green = (pixel >>> 8) & 0xFF;
                float blue = pixel & 0xFF;
                float own = lum[i];
                float a = patched(pressed[i] * depth, patch, i, pressed[i], depth);
                if (a <= 0f) {
                    out[i] = pack(red, green, blue);
                    continue;
                }
                float joint01 = mortar[i];
                float stone = 1f - joint01;
                // The stone's own neighbourhood, counting only stone, so a top polishes toward itself and not toward
                // the joint beside it.
                float total = 0f;
                float weight = 0f;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int k = wrap(x + dx, y + dy, size);
                        float w = 1f - mortar[k];
                        total += lum[k] * w;
                        weight += w;
                    }
                }
                float top = weight <= 0.01f ? own : total / weight;
                // The polish-and-grind split and the grain follow the earlier roughness curve; the dulling does not.
                float r = rough(a);
                float light = own + (top - own) * COBBLE_POLISH * stone * r;
                light *= 1f - COBBLE_DULL * a;
                float field = lowFrequency(x, y, size, 3, 547 + seed * 47);
                float grind = COBBLE_GRIND * (1f - COBBLE_PATCHY + 2f * COBBLE_PATCHY * field);
                light *= 1f - grind * joint01 * r;
                // Grain: each pixel knocked up or down, so the polished tops and the joints read uneven.
                light = Math.min(own, light * (1f + COBBLE_GRAIN * r * grain(x, y, 529 + seed * 31)));
                // The cracks, over stone and joint alike, only where the way is trodden.
                float crack = fissure(x, y, size, 3, 71 + seed * 37, crackWidth) * cracking * pressed[i];
                light *= 1f - COBBLE_CRACK * crack;
                // The finer network, late in the run: the stones breaking up further as the way is worn out.
                float fine = fissure(x, y, size, 6, 79 + seed * 37, fineWidth) * fineCracking * pressed[i];
                light *= 1f - COBBLE_FINE * fine;
                crack = Math.max(crack, fine);
                boolean scuffed = hash(x, y, 487 + seed * 59) < scuffShare * stone;
                if (!scuffed && depth > 0.5f && stone > 0.5f) {
                    // Past halfway a scuff spreads to the stone pixel to its right or below, so it reads coarser.
                    int lx = (x + size - 1) % size;
                    int uy = (y + size - 1) % size;
                    scuffed = hash(lx, y, 487 + seed * 59) < scuffShare * (1f - mortar[y * size + lx])
                        || hash(x, uy, 487 + seed * 59) < scuffShare * (1f - mortar[uy * size + x]);
                }
                if (scuffed) light *= COBBLE_SCUFF_LIGHT;
                float scale = own <= 0.5f ? 0f : light / own;
                red *= scale;
                green *= scale;
                blue *= scale;
                float match = shadowLight <= 0.5f ? 0f : light / shadowLight;
                float dirt = COBBLE_SOIL * joint01 * a + 0.5f * crack;
                red += (shadow[0] * match - red) * dirt;
                green += (shadow[1] * match - green) * dirt;
                blue += (shadow[2] * match - blue) * dirt;
                if (scuffed) {
                    float grey = (red + green + blue) / 3f;
                    red += (grey - red) * 0.6f;
                    green += (grey - green) * 0.6f;
                    blue += (grey - blue) * 0.6f;
                }
                out[i] = pack(red, green, blue);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Snow: packed into a glossier, cooler, grey-blue track
    // ------------------------------------------------------------------

    /** How much of the face the packed track claims at the end of the run, before strength. */
    static final float SNOW_COVERAGE = 0.62f;

    /** How much of the track's relief is packed away, at the end of the run. */
    static final float SNOW_PACK = 0.85f;

    /** How far the track's light falls, as a fraction of its own, at the end of the run. */
    static final float SNOW_SINK = 0.18f;

    /** How far the track's color is drawn toward the face's own cool shadow, made bluer. */
    static final float SNOW_COOL = 0.70f;

    /** How far a glint on the packed track goes toward the face's brightest tone, lifted. */
    static final float SNOW_GLOSS = 0.55f;

    /** The share of the packed track that catches a glint at the end of the run. */
    static final float SNOW_SHINE = 0.16f;

    /**
     * How far the track darkens and greys where it is dirtiest, in patches, at the end of the run: walked in (Xep,
     * 2026-11-12, of the first version: "Can we make the snow just a hair more 'dirty'/'walked in'/closer to the old
     * one? Otherwise looks perfect.").
     */
    static final float SNOW_DIRT = 0.11f;

    /**
     * Snow packed underfoot into a track: denser and smoother, a little darker and cooler, the grey-blue of trodden
     * snow, with a few glints where it has gone glassy.
     *
     * <p>
     * The cracked rub snow ships with breaks the snow up; a path through snow is packed, not broken. Inside the
     * track its relief closes almost entirely toward the face's own average, and the light falls by about an eighth.
     * The color is drawn toward the face's own shadow color with its color pushed further out from grey: on snow that
     * shadow is a pale cyan, so packed snow comes out the grey-blue it is, and a snow texture with warm shadows packs
     * warm instead. Patches of it go a little darker and greyer, the dirt of a path in use. A share of the track,
     * growing over the run, catches a glint from the face's brightest tone lifted toward white, which is the glaze
     * packed snow takes on.
     *
     * @param coverage the floored progress the track grows with, 0 to 1
     * @param progress the eased progress the depth of every change follows, 0 to 1
     */
    public static int[] snow(int[] source, int size, float coverage, float progress, float strength, int seed) {
        int n = size * size;
        int[] out = opaqueCopy(source, n);
        float s = clamp01(strength);
        float depth = clamp01(progress) * s;
        if (depth <= 0f) return out;

        float[] lum = lumas(source, n);
        float average = mean(lum);
        float[] shadow = toneColor(source, n, 0f, 0.25f);
        float[] bright = toneColor(source, n, 0.90f, 1f);
        float shadowGrey = (shadow[0] + shadow[1] + shadow[2]) / 3f;
        float[] cool = new float[3];
        for (int c = 0; c < 3; c++) cool[c] = shadowGrey + (shadow[c] - shadowGrey) * 1.5f;
        float coolLight = (cool[0] + cool[1] + cool[2]) / 3f;
        float[] pressed = track(source, size, clamp01(coverage) * SNOW_COVERAGE * s, 0.14f, seed);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int i = y * size + x;
                int pixel = source[i];
                float red = (pixel >>> 16) & 0xFF;
                float green = (pixel >>> 8) & 0xFF;
                float blue = pixel & 0xFF;
                float own = lum[i];
                float a = pressed[i] * depth;
                if (a <= 0f) {
                    out[i] = pack(red, green, blue);
                    continue;
                }
                float light = (average + (own - average) * (1f - SNOW_PACK * a)) * (1f - SNOW_SINK * a);
                float scale = own <= 0.5f ? 0f : light / own;
                red *= scale;
                green *= scale;
                blue *= scale;
                float match = coolLight <= 0.5f ? 0f : light / coolLight;
                float chill = SNOW_COOL * a;
                red += (cool[0] * match - red) * chill;
                green += (cool[1] * match - green) * chill;
                blue += (cool[2] * match - blue) * chill;
                // Walked in: patches of the track darker and greyer, the trodden dirt of a path in use.
                float patch = lowFrequency(x, y, size, 4, 491 + seed * 61) * 0.7f + hash(x, y, 499 + seed * 7) * 0.3f;
                float dirty = SNOW_DIRT * a * patch;
                float grey = (red + green + blue) / 3f;
                red = (red + (grey - red) * dirty * 2f) * (1f - dirty);
                green = (green + (grey - green) * dirty * 2f) * (1f - dirty);
                blue = (blue + (grey - blue) * dirty * 2f) * (1f - dirty);
                if (pressed[i] >= 1f && hash(x, y, 433 + seed * 41) < SNOW_SHINE * depth) {
                    float glint = SNOW_GLOSS * depth;
                    red += (lift(bright[0]) - red) * glint;
                    green += (lift(bright[1]) - green) * glint;
                    blue += (lift(bright[2]) - blue) * glint;
                }
                out[i] = pack(red, green, blue);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Ice: scuffed and scratched, clouding toward white frost
    // ------------------------------------------------------------------

    /** How far the scuffed haze goes toward frost at its thickest, at the end of the run. */
    static final float ICE_HAZE = 0.50f;

    /** How many scratches a run lays down on a sixteen-pixel face, one after another. */
    static final int ICE_SCRATCHES = 26;

    /** How far a scratch goes toward frost. */
    static final float ICE_SCRATCH = 0.70f;

    /**
     * Where along the run the ice starts to crack, and how far a crack goes back toward the ice's own color, darkened,
     * at its deepest (Xep, 2026-11-12, of the first version: "Can we make the ice have some more defined cracks
     * showing in the >75% wear range? otherwise looks perfect.").
     */
    static final float ICE_CRACK_START = 0.74f;

    static final float ICE_CRACK = 0.95f;

    /** A crack's color: the ice's own, darkened to this share of its light. */
    static final float ICE_CRACK_DARK = 0.68f;

    /**
     * Ice scuffed dull: a frosted haze clouds it in patches that spread over the run, and fine scratches cross it
     * one after another, each a short white line, so clear ice goes milky where it is walked.
     *
     * <p>
     * The cracked rub ice ships with darkens it, which is the furthest fall of any family and the opposite of what a
     * scuffed clear surface does: scratched ice scatters light and goes white. So every change here is toward frost,
     * the face's own brightest tone lifted toward white and drained a little toward grey. The haze opens in blotches
     * on a broad field, every pixel clouding a little from the start, and the scratches are laid down in turn, at
     * scattered places and in a few directions set by the rotation, so the run is readable as a count of scratches
     * over a thickening haze. Up to three quarters of the way, nothing here ever darkens a pixel. Past it the ice
     * cracks: a fracture network opens and widens, each crack the ice's own color darkened by a third, so it reads
     * as a dark line through the frost, the one place a scuffed face goes darker.
     */
    public static int[] ice(int[] source, int size, float progress, float strength, int seed) {
        int n = size * size;
        int[] out = opaqueCopy(source, n);
        float s = clamp01(strength);
        float wear = clamp01(progress) * s;
        if (wear <= 0f) return out;

        float[] bright = toneColor(source, n, 0.92f, 1f);
        float[] frost = new float[3];
        float brightGrey = (bright[0] + bright[1] + bright[2]) / 3f;
        for (int c = 0; c < 3; c++) frost[c] = lift(bright[c] + (brightGrey - bright[c]) * 0.4f);
        float frostLight = (frost[0] + frost[1] + frost[2]) / 3f;
        float[] lum = lumas(source, n);
        float cracking = clamp01((wear - ICE_CRACK_START) / (1f - ICE_CRACK_START));
        float crackWidth = cracking <= 0f ? 0f : 0.008f + 0.034f * cracking;

        // The scratches, each a short straight line, laid in turn along the run.
        float[] scratch = new float[n];
        float scale = size / 16f;
        int[][] directions = { { 1, 0 }, { 1, 1 }, { 2, 1 }, { 1, -1 }, { 2, -1 }, { 0, 1 } };
        for (int k = 0; k < ICE_SCRATCHES; k++) {
            float shown = clamp01(wear * ICE_SCRATCHES * 1.05f - k);
            if (shown <= 0f) break;
            int startX = (int) (hash(k, 1, 461 + seed * 43) * size);
            int startY = (int) (hash(k, 2, 461 + seed * 43) * size);
            int[] direction = directions[(int) (hash(k, 3, 467 + seed * 47) * 3.999f) + (seed & 1) * 2];
            int length = Math.max(2, Math.round((3 + hash(k, 4, 461 + seed * 43) * 4f) * scale));
            float mark = shown * (0.75f + 0.25f * hash(k, 5, 461 + seed * 43));
            int steps = Math.max(Math.abs(direction[0]), Math.abs(direction[1]));
            for (int t = 0; t < length; t++) {
                int px = startX + Math.round(t * direction[0] / (float) steps);
                int py = startY + Math.round(t * direction[1] / (float) steps);
                int at = wrap(px, py, size);
                if (mark > scratch[at]) scratch[at] = mark;
            }
        }

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int i = y * size + x;
                int pixel = source[i];
                float red = (pixel >>> 16) & 0xFF;
                float green = (pixel >>> 8) & 0xFF;
                float blue = pixel & 0xFF;
                // A pixel already as light as the frost is frost already; a scuff never darkens ice.
                if (lum[i] < frostLight) {
                    float blot = lowFrequency(x, y, size, 3, 449 + seed * 17) * 0.8f
                        + hash(x, y, 457 + seed * 5) * 0.2f;
                    float reached = clamp01((wear * 1.25f - (1f - blot)) / 0.30f);
                    float haze = ICE_HAZE * (0.35f * wear + 0.65f * reached);
                    float cloud = 1f - (1f - haze) * (1f - ICE_SCRATCH * scratch[i]);
                    red += (frost[0] - red) * cloud;
                    green += (frost[1] - green) * cloud;
                    blue += (frost[2] - blue) * cloud;
                }
                float crack = fissure(x, y, size, 3, 83 + seed * 41, crackWidth) * Math.min(1f, 0.4f + cracking);
                if (crack > 0f) {
                    float deep = ICE_CRACK * crack;
                    red += (((pixel >>> 16) & 0xFF) * ICE_CRACK_DARK - red) * deep;
                    green += (((pixel >>> 8) & 0xFF) * ICE_CRACK_DARK - green) * deep;
                    blue += ((pixel & 0xFF) * ICE_CRACK_DARK - blue) * deep;
                }
                out[i] = pack(red, green, blue);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // The Nether's ground: charred, darker and flattened
    // ------------------------------------------------------------------

    /** How much of the face the charred track claims at the end of the run, before strength. */
    static final float NETHER_COVERAGE = 0.72f;

    /** How far the track's light falls, as a fraction of its own, at the end of the run. */
    static final float NETHER_CHAR = 0.45f;

    /** How much of the track's relief is flattened away, at the end of the run. */
    static final float NETHER_FLAT = 0.55f;

    /** How far the track's color is drained toward grey: soot. */
    static final float NETHER_SOOT = 0.40f;

    /** The share of the track that holds a fleck of pale ash at the end of the run. */
    static final float NETHER_ASH = 0.07f;

    /**
     * Nether ground trodden into a charred track: darker, flatter and sooty, with a few flecks of pale ash on it.
     *
     * <p>
     * Netherrack and nylium are all noise, which is why they ship with the smoothed crack: buffing first lets the
     * fissures read. What a road across burning ground does is char. Inside the track the relief flattens toward the
     * face's own average, the light falls toward the face's own darkest color and the color drains toward grey, so a
     * red face chars toward a dark brown-black and a teal nylium toward a dark slate, each from its own shadow. A small
     * share of the track, growing over the run, is left as ash: the face's own average, greyed and lifted, a fleck at
     * a time.
     *
     * @param coverage the floored progress the track grows with, 0 to 1
     * @param progress the eased progress the depth of every change follows, 0 to 1
     */
    public static int[] nether(int[] source, int size, float coverage, float progress, float strength, int seed) {
        return netherDraw(source, size, coverage, progress, strength, seed, null);
    }

    /**
     * The pixels of the nether look that glow: the ash flecks, each in the color {@link #nether} draws it in, and
     * everything else clear (alpha 0), at the same size. An overlay drawn from it at {@link #NETHER_GLOW_LEVEL} lights
     * the flecks and nothing else (Xep, 2026-11-15: "subtle real glow" on the nether's ash).
     */
    public static int[] netherGlow(int[] source, int size, float coverage, float progress, float strength, int seed) {
        float[] glow = new float[size * size];
        int[] worn = netherDraw(source, size, coverage, progress, strength, seed, glow);
        return glowPicture(worn, glow);
    }

    private static int[] netherDraw(int[] source, int size, float coverage, float progress, float strength, int seed,
        float[] glow) {
        int n = size * size;
        int[] out = opaqueCopy(source, n);
        float s = clamp01(strength);
        float depth = clamp01(progress) * s;
        if (depth <= 0f) return out;

        float[] lum = lumas(source, n);
        float average = mean(lum);
        float[] shadow = toneColor(source, n, 0f, 0.10f);
        float shadowLight = (shadow[0] + shadow[1] + shadow[2]) / 3f;
        float ash = Math.min(235f, average * 1.35f + 12f);
        float[] pressed = track(source, size, clamp01(coverage) * NETHER_COVERAGE * s, 0.14f, seed);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int i = y * size + x;
                int pixel = source[i];
                float red = (pixel >>> 16) & 0xFF;
                float green = (pixel >>> 8) & 0xFF;
                float blue = pixel & 0xFF;
                float own = lum[i];
                float a = pressed[i] * depth;
                if (a <= 0f) {
                    out[i] = pack(red, green, blue);
                    continue;
                }
                float light = (average + (own - average) * (1f - NETHER_FLAT * a)) * (1f - NETHER_CHAR * a);
                float scale = own <= 0.5f ? 0f : light / own;
                red *= scale;
                green *= scale;
                blue *= scale;
                float match = shadowLight <= 0.5f ? 0f : light / shadowLight;
                float burn = 0.5f * a;
                red += (shadow[0] * match - red) * burn;
                green += (shadow[1] * match - green) * burn;
                blue += (shadow[2] * match - blue) * burn;
                float grey = (red + green + blue) / 3f;
                float soot = NETHER_SOOT * a;
                red += (grey - red) * soot;
                green += (grey - green) * soot;
                blue += (grey - blue) * soot;
                if (pressed[i] >= 1f && hash(x, y, 479 + seed * 53) < NETHER_ASH * depth) {
                    float fleck = 0.7f * depth;
                    if (glow != null) glow[i] = 1f;
                    red += (ash - red) * fleck;
                    green += (ash - green) * fleck;
                    blue += (ash - blue) * fleck;
                }
                out[i] = pack(red, green, blue);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // End stone: bleached, chalky and polished pale
    // ------------------------------------------------------------------

    /** How much of the face the bleached track claims at the end of the run, before strength. */
    static final float END_COVERAGE = 0.70f;

    /** How far the track goes toward the face's palest tone, lifted, at the end of the run. */
    static final float END_BLEACH = 0.50f;

    /** How far the track's color is drained toward grey: bleached. */
    static final float END_DRAIN = 0.60f;

    /** How much of the track's relief is polished away, at the end of the run. */
    static final float END_POLISH = 0.65f;

    /**
     * How uneven the bleaching is (the share of it that a broad field takes away in places), and the share of the
     * track that holds a grain of grit at the end of the run (Xep, 2026-11-12, of the first version: "For the new
     * end, can we make it look slightly less clean/perfect?").
     */
    static final float END_UNEVEN = 0.40f;

    static final float END_GRIT = 0.16f;

    /**
     * Where along the run end stone starts to crack, and how far a crack's light falls at its deepest: a faint
     * network, finer and fainter than cobble's (Xep, 2026-11-12, of the second version: "I feel llike the end should
     * have some slight cracking to it also"). The start moved from two fifths to three tenths on 2026-11-15 (Xep: "make
     * the end wear pattern cracking start a little earlier").
     */
    static final float END_CRACK_START = 0.30f;

    static final float END_CRACK = 0.45f;

    /**
     * The color of the cracks: AF00DD, the midpoint of the two purples in the ender dragon's own eyes texture
     * ({@code dragon_eyes.png}: 9600BC and CC00FA), which Xep picked from three shades (2026-11-15, "Let's go with
     * between") to fit the End. Read as channels and as the mean light they are scaled against.
     */
    static final int END_CRACK_COLOR = 0xAF00DD;

    /**
     * The block and sky light, out of fifteen, a glow overlay is baked with: 9 is 60% of full on the end's cracks, 5 a
     * third on the nether's ash. Each renderer takes the brighter of this and the light the world gives the face, so by
     * day nothing changes and in the dark the glow shows.
     */
    public static final int END_GLOW_LEVEL = 9;

    public static final int NETHER_GLOW_LEVEL = 5;

    /** How much of a pixel the crack must cover for it to glow. */
    static final float END_GLOW_MIN = 0.30f;

    /** A light level as the game packs a lightmap coordinate: sky light in the high half, block light in the low. */
    public static int glowLight(int level) {
        return (level << 20) | (level << 4);
    }

    /** The worn picture where the mask is set, clear (alpha 0) everywhere else. */
    private static int[] glowPicture(int[] worn, float[] mask) {
        int[] out = new int[worn.length];
        for (int i = 0; i < out.length; i++) out[i] = mask[i] > 0f ? worn[i] : 0;
        return out;
    }

    private static final float CRACK_RED = (END_CRACK_COLOR >>> 16) & 0xFF;

    private static final float CRACK_GREEN = (END_CRACK_COLOR >>> 8) & 0xFF;

    private static final float CRACK_BLUE = END_CRACK_COLOR & 0xFF;

    private static final float CRACK_LIGHT = (CRACK_RED + CRACK_GREEN + CRACK_BLUE) / 3f;

    /**
     * End stone walked pale: a track bleached of its yellow, polished smooth and chalky, lighter than the stone
     * around it, scoured unevenly, with grit ground into it and a faint crack network late in the run.
     *
     * <p>
     * The smoothed crack end stone ships with buffs and splits it, and of every family it falls furthest. Nothing
     * weathers in the End, and a road there would scour rather than grime: so this is the one look of the seven that
     * goes lighter. Inside the track the relief closes toward the face's own average, the color drains toward grey
     * and the whole of it is drawn toward the face's own palest tone lifted toward white, which on end stone is a
     * chalky off-white and on any other block is that block's own palest. The bleaching is uneven, some of the track
     * scoured paler than the rest on a broad field, and a growing scatter of grit is ground into it, each grain the
     * face's own darkest color greyed and darkened, so the track reads as walked rather than as cleaned. From three
     * tenths of the way a faint crack network opens, four cells across and thin, each line a little under half
     * darker than the stone around it, and the color of the ender dragon's eyes, a purple: the scoured stone beginning
     * to split, never the buffed crack it ships with.
     *
     * @param coverage the floored progress the track grows with, 0 to 1
     * @param progress the eased progress the depth of every change follows, 0 to 1
     */
    public static int[] end(int[] source, int size, float coverage, float progress, float strength, int seed) {
        return endDraw(source, size, coverage, progress, strength, seed, null);
    }

    /**
     * The pixels of the end look that glow: the strong pixels of its purple cracks, each in the color {@link #end}
     * draws it in, and everything else clear (alpha 0), at the same size. A pixel glows where the crack covers at least
     * {@link #END_GLOW_MIN} of it, so the faint blended edges stay as they are and the lit line is the crack's core.
     * An overlay drawn from it at {@link #END_GLOW_LEVEL} lights the cracks and nothing else (Xep, 2026-11-15: "I'd
     * prefer a true glow", about 60%, less than the ender dragon's eyes).
     */
    public static int[] endGlow(int[] source, int size, float coverage, float progress, float strength, int seed) {
        float[] glow = new float[size * size];
        int[] worn = endDraw(source, size, coverage, progress, strength, seed, glow);
        return glowPicture(worn, glow);
    }

    private static int[] endDraw(int[] source, int size, float coverage, float progress, float strength, int seed,
        float[] glow) {
        int n = size * size;
        int[] out = opaqueCopy(source, n);
        float s = clamp01(strength);
        float depth = clamp01(progress) * s;
        if (depth <= 0f) return out;

        float[] lum = lumas(source, n);
        float average = mean(lum);
        float[] bright = toneColor(source, n, 0.90f, 1f);
        float[] pale = new float[3];
        float brightGrey = (bright[0] + bright[1] + bright[2]) / 3f;
        for (int c = 0; c < 3; c++) pale[c] = lift(bright[c] + (brightGrey - bright[c]) * 0.5f);
        float[] shadow = toneColor(source, n, 0f, 0.15f);
        float shadowLight = (shadow[0] + shadow[1] + shadow[2]) / 3f;
        float[] pressed = track(source, size, clamp01(coverage) * END_COVERAGE * s, 0.14f, seed);
        float cracking = clamp01((depth - END_CRACK_START) / (1f - END_CRACK_START));
        float crackWidth = cracking <= 0f ? 0f : 0.006f + 0.022f * cracking;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int i = y * size + x;
                int pixel = source[i];
                float red = (pixel >>> 16) & 0xFF;
                float green = (pixel >>> 8) & 0xFF;
                float blue = pixel & 0xFF;
                float own = lum[i];
                float a = pressed[i] * depth;
                if (a <= 0f) {
                    out[i] = pack(red, green, blue);
                    continue;
                }
                float light = average + (own - average) * (1f - END_POLISH * a);
                float scale = own <= 0.5f ? 0f : light / own;
                red *= scale;
                green *= scale;
                blue *= scale;
                float grey = (red + green + blue) / 3f;
                float drain = END_DRAIN * a;
                red += (grey - red) * drain;
                green += (grey - green) * drain;
                blue += (grey - blue) * drain;
                // Bleached unevenly: some of the track scoured paler than the rest.
                float scoured = lowFrequency(x, y, size, 4, 509 + seed * 71);
                float bleach = END_BLEACH * a * (1f - END_UNEVEN + END_UNEVEN * scoured);
                red += (pale[0] - red) * bleach;
                green += (pale[1] - green) * bleach;
                blue += (pale[2] - blue) * bleach;
                // Grit: a growing scatter of the track darkened toward the face's own darkest, ground in.
                if (hash(x, y, 503 + seed * 67) < END_GRIT * depth) {
                    float match = shadowLight <= 0.5f ? 0f : (red + green + blue) / 3f * 0.70f / shadowLight;
                    float grit = 0.80f * depth;
                    red += ((shadow[0] + shadowLight) * 0.5f * match - red) * grit;
                    green += ((shadow[1] + shadowLight) * 0.5f * match - green) * grit;
                    blue += ((shadow[2] + shadowLight) * 0.5f * match - blue) * grit;
                }
                // Faint cracks, late, finer and lighter than cobble's: the scoured stone only beginning to split.
                float crack = fissure(x, y, size, 4, 89 + seed * 43, crackWidth) * cracking * pressed[i];
                if (crack > 0f) {
                    float lightNow = (red + green + blue) / 3f;
                    float match = shadowLight <= 0.5f ? 0f : lightNow * (1f - END_CRACK) / shadowLight;
                    float line = crack;
                    if (glow != null && line >= END_GLOW_MIN) glow[i] = 1f;
                    // The crack's color: the ender dragon's purple, scaled to the light the crack would have had, so
                    // the
                    // crack keeps the shading of the stone it cuts and its blend with the pixels around it.
                    float want = lightNow * (1f - END_CRACK);
                    float k = want / CRACK_LIGHT;
                    red += (CRACK_RED * k - red) * line;
                    green += (CRACK_GREEN * k - green) * line;
                    blue += (CRACK_BLUE * k - blue) * line;
                }
                out[i] = pack(red, green, blue);
            }
        }
        return out;
    }
}
