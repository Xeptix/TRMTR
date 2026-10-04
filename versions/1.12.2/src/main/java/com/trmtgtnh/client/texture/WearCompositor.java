package com.trmtgtnh.client.texture;

import java.util.Arrays;

/**
 * The pixel arithmetic behind every generated wear texture.
 *
 * <p>
 * Kept free of Minecraft types on purpose. This is the part of the mod most likely to be
 * subtly wrong in a way nobody notices until a path looks like static, and separating it from
 * the resource manager means it can be tested against known inputs and rendered to a contact
 * sheet, rather than only inspected by launching the game and squinting at the ground.
 *
 * <p>
 * Everything here works in packed ARGB and is deterministic: the same inputs always give the
 * same pixels, which is what lets the same wear appear identically on every client without any
 * of it being sent over the wire.
 */
public final class WearCompositor {

    private WearCompositor() {}

    // ------------------------------------------------------------------
    /**
     * Lays one texture over another, honouring alpha, both already the same size.
     *
     * <p>
     * For a block that draws itself in more than one pass and cuts its own texture away so an
     * earlier pass shows through. A ghost draws once, so the pass underneath has to be baked in
     * or the hole is simply a hole.
     */
    public static int[] over(int[] under, int[] top, int size) {
        if (under == null) return top;
        if (top == null) return under;
        return overInto(new int[size * size], under, top, size);
    }

    /**
     * {@link #over}, written into a picture the caller holds rather than a new one, which is returned.
     *
     * <p>
     * For the same redraw as {@link #overThroughHolesInto}, and under the same rule: the first size squared pixels are
     * written and nothing else, and a picture handed in here must never be one compose returns.
     */
    public static int[] overInto(int[] out, int[] under, int[] top, int size) {
        if (under == null) return top;
        if (top == null) return under;
        int pixels = size * size;
        for (int i = 0; i < pixels && i < out.length && i < top.length && i < under.length; i++) {
            int alpha = (top[i] >>> 24) & 0xFF;
            if (alpha >= 250) {
                out[i] = top[i];
                continue;
            }
            if (alpha == 0) {
                out[i] = under[i];
                continue;
            }
            int inverse = 255 - alpha;
            int red = (((top[i] >> 16) & 0xFF) * alpha + ((under[i] >> 16) & 0xFF) * inverse) / 255;
            int green = (((top[i] >> 8) & 0xFF) * alpha + ((under[i] >> 8) & 0xFF) * inverse) / 255;
            int blue = ((top[i] & 0xFF) * alpha + (under[i] & 0xFF) * inverse) / 255;
            int keptAlpha = Math.max(alpha, (under[i] >>> 24) & 0xFF);
            out[i] = (keptAlpha << 24) | (red << 16) | (green << 8) | blue;
        }
        return out;
    }

    // Coverage: how grass wears away
    // ------------------------------------------------------------------

    /**
     * Builds a per-pixel wear order from the authored grass masks.
     *
     * <p>
     * Each mask is the alpha channel of one authored stage: opaque where grass survives. A
     * pixel's order is the earliest stage that drops it, plus a stable fractional tiebreak.
     * That turns five fixed masks into a continuous sequence, so any number of gradations can
     * be cut from it — and cutting exactly five reproduces the authored masks.
     *
     * @param masks      alpha values per authored stage, each {@code size * size}, ordered from
     *                   least to most worn
     * @param size       edge length of the masks
     * @param jitterSeed varies the tiebreak between rotations so they do not wear identically
     */
    public static float[] wearOrder(int[][] masks, int size, int jitterSeed) {
        float[] order = new float[size * size];
        // Anything no mask ever removes is grass right to the end of the sequence.
        Arrays.fill(order, masks.length);

        for (int stage = masks.length - 1; stage >= 0; stage--) {
            int[] mask = masks[stage];
            if (mask == null) continue;
            for (int i = 0; i < order.length && i < mask.length; i++) {
                if (mask[i] < 128) order[i] = stage;
            }
        }

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                order[y * size + x] += jitter(x, y, jitterSeed);
            }
        }
        return order;
    }

    /** Deterministic 0-1 tiebreak, so extra gradations scatter rather than band. */
    private static float jitter(int x, int y, int seed) {
        int h = (x * 374761393) ^ (y * 668265263) ^ (seed * 2147483647);
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h >>> 8) & 0xFFFF) / 65536.0f;
    }

    /**
     * The wear-order value at or above which a pixel is still grass, chosen so exactly
     * {@code coverage} of the block survives. Selecting by rank rather than by stage is what
     * decouples the number of gradations from the number of masks that were drawn.
     */
    public static float coverageThreshold(float[] order, float coverage) {
        float[] sorted = order.clone();
        Arrays.sort(sorted);
        int keep = Math.round(coverage * sorted.length);
        if (keep >= sorted.length) return Float.NEGATIVE_INFINITY; // all of it survives
        if (keep <= 0) return Float.POSITIVE_INFINITY; // none of it does
        return sorted[sorted.length - keep];
    }

    /**
     * Paints surviving grass back over the earth beneath it.
     *
     * @param earth   the block's own under-texture, {@code size * size} ARGB
     * @param grass   the block's own top texture, {@code size * size} ARGB
     * @param size    edge length of both
     * @param order   wear order at art resolution
     * @param artSize edge length of the order map
     * @param cut     threshold from {@link #coverageThreshold}
     */
    public static int[] applyCoverage(int[] earth, int[] grass, int size, float[] order, int artSize, float cut) {
        int[] out = new int[size * size];
        for (int y = 0; y < size; y++) {
            int artY = y * artSize / size;
            for (int x = 0; x < size; x++) {
                int artX = x * artSize / size;
                boolean stillGrass = order[artY * artSize + artX] >= cut;
                int source = stillGrass ? grass[y * size + x] : earth[y * size + x];
                out[y * size + x] = 0xFF000000 | (source & 0xFFFFFF);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Modulation: how everything else wears
    // ------------------------------------------------------------------

    /**
     * Extracts how much an authored stage darkened or lightened each pixel of the surface it
     * was drawn against.
     *
     * <p>
     * Dividing rather than subtracting is the point: a ratio transfers to a texture of a
     * different brightness and hue, so red sand wears red and Twilight Forest dirt wears in
     * its own colour, while a difference would drag everything toward vanilla's palette.
     *
     * @return {@code size * size * 3} channel ratios
     */
    public static float[] modulationRatio(int[] art, int[] reference, int size) {
        float[] ratio = new float[size * size * 3];
        for (int i = 0; i < size * size; i++) {
            int artPixel = art[i];
            int refPixel = reference[i];
            for (int channel = 0; channel < 3; channel++) {
                int shift = 16 - channel * 8;
                int artValue = (artPixel >>> shift) & 0xFF;
                int refValue = (refPixel >>> shift) & 0xFF;
                // A black reference pixel says nothing about the ratio, so leave it alone
                // rather than inventing a number that would blow up on multiply.
                ratio[i * 3 + channel] = refValue == 0 ? 1f : artValue / (float) refValue;
            }
        }
        return ratio;
    }

    /** Blends two ratio maps, for stages that fall between two authored ones. */
    public static float[] blendRatio(float[] low, float[] high, float amount) {
        if (low == null) return high;
        if (high == null) return low;
        float[] out = new float[low.length];
        for (int i = 0; i < out.length; i++) {
            out[i] = low[i] * (1f - amount) + high[i] * amount;
        }
        return out;
    }

    /** Scales a ratio map toward identity, for blending up from an unworn stage. */
    public static float[] towardIdentity(float[] map, float amount) {
        if (map == null) return null;
        float[] out = new float[map.length];
        for (int i = 0; i < out.length; i++) {
            out[i] = 1f + (map[i] - 1f) * amount;
        }
        return out;
    }

    /**
     * Multiplies a wear ratio into a block's own texture.
     *
     * @param strength how far to apply it, 0 leaving the texture untouched and 1 applying the
     *                 authored wear in full. Stone wants a light touch so a trail reads as
     *                 polish rather than damage.
     */
    public static int[] applyModulation(int[] base, int size, float[] ratio, int artSize, float strength) {
        int[] out = new int[size * size];
        for (int y = 0; y < size; y++) {
            int artY = y * artSize / size;
            for (int x = 0; x < size; x++) {
                int pixel = base[y * size + x];
                if (ratio == null || strength <= 0f) {
                    out[y * size + x] = 0xFF000000 | (pixel & 0xFFFFFF);
                    continue;
                }
                int artX = x * artSize / size;
                int ratioBase = (artY * artSize + artX) * 3;

                int result = 0xFF000000;
                for (int channel = 0; channel < 3; channel++) {
                    int shift = 16 - channel * 8;
                    int value = (pixel >>> shift) & 0xFF;
                    float factor = 1f + (ratio[ratioBase + channel] - 1f) * strength;
                    int worn = Math.round(value * factor);
                    if (worn < 0) worn = 0;
                    if (worn > 255) worn = 255;
                    result |= worn << shift;
                }
                out[y * size + x] = result;
            }
        }
        return out;
    }

    /**
     * Moves a colour toward white by the given amount.
     *
     * <p>
     * Used to weaken the biome tint on a worn grass block in proportion to how much bare earth
     * is showing. The tint applies to the whole face, so the more earth there is, the more of
     * the tint lands somewhere it does not belong; pulling it toward white as the grass thins
     * keeps the earth honest without ever pushing a colour anywhere it could not already go.
     * Unlike correcting the texture against a fixed reference green, this cannot overshoot in a
     * biome whose grass is a different colour from the one it was calibrated against.
     */
    /**
     * Eases a tint toward none, in proportion to how little of the face still wants it.
     *
     * <p>
     * Multiplicative rather than a straight blend, because a tint is a multiplier: raising each
     * channel's fraction to a power short of one weakens it evenly, where mixing toward white
     * would brighten the dark channels far faster than the light ones and drift the hue on the
     * way.
     */
    public static int easeTint(int colour, float worn) {
        if (worn <= 0f) return colour;
        if (worn >= 1f) return 0xFFFFFF;

        int result = 0;
        for (int channel = 0; channel < 3; channel++) {
            int shift = 16 - channel * 8;
            float fraction = ((colour >>> shift) & 0xFF) / 255f;
            int eased = Math.round((float) Math.pow(fraction, 1f - worn) * 255f);
            if (eased < 0) eased = 0;
            if (eased > 255) eased = 255;
            result |= eased << shift;
        }
        return result;
    }

    public static int lerpTowardWhite(int colour, float amount) {
        if (amount <= 0f) return colour;
        if (amount > 1f) amount = 1f;
        int result = 0;
        for (int channel = 0; channel < 3; channel++) {
            int shift = 16 - channel * 8;
            int value = (colour >>> shift) & 0xFF;
            result |= Math.round(value + (255 - value) * amount) << shift;
        }
        return result;
    }

    /** Multiplies a texture by a colour, as the game's biome tint would at render time. */
    public static int[] tint(int[] pixels, int colour) {
        int[] out = new int[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            int result = pixel & 0xFF000000;
            for (int channel = 0; channel < 3; channel++) {
                int shift = 16 - channel * 8;
                int value = (pixel >>> shift) & 0xFF;
                int factor = (colour >>> shift) & 0xFF;
                result |= ((value * factor) / 255) << shift;
            }
            out[i] = result;
        }
        return out;
    }

    /**
     * Pre-divides a texture by a colour so that multiplying it back by that colour restores
     * the original.
     *
     * <p>
     * The game tints a whole face at once, and a worn grass block's face is part grass and part
     * the earth showing through. The grass wants the tint; the earth does not, and applying it
     * anyway is what turns a brown path olive — badly so in a dark biome like a swamp. Dividing
     * the earth out in advance cancels the tint back off it, so it renders as earth again.
     *
     * <p>
     * Compensation is capped rather than allowed to overflow: a bright texture divided by a
     * dark tint would need values past 255, and clamping there simply leaves that channel
     * partly uncompensated, which is no worse than not compensating at all. That is why this
     * is safe to apply to a texture from a mod nobody has looked at.
     */
    public static int[] precompensate(int[] pixels, int colour, float strength) {
        int[] out = new int[pixels.length];
        float[] scale = new float[3];
        for (int channel = 0; channel < 3; channel++) {
            int factor = (colour >>> (16 - channel * 8)) & 0xFF;
            // Raising the correction to a power short of one splits the difference between the
            // biome this was calibrated for and every other one, so the earth is never far from
            // its true colour anywhere rather than exact in one biome and lurid in the rest.
            scale[channel] = factor == 0 ? 1f : (float) Math.pow(255.0 / factor, strength);
        }

        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            int result = pixel & 0xFF000000;
            for (int channel = 0; channel < 3; channel++) {
                int shift = 16 - channel * 8;
                int value = (pixel >>> shift) & 0xFF;
                int compensated = Math.round(value * scale[channel]);
                if (compensated > 255) compensated = 255;
                result |= compensated << shift;
            }
            out[i] = result;
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Polish: how hard surfaces wear
    // ------------------------------------------------------------------

    /**
     * Measures how much an authored stage flattened and darkened the surface it was drawn on.
     *
     * <p>
     * Ratio transfer works when the art is a modulation of its reference, which upstream's
     * sand is and upstream's dirt very nearly is not — the eroded dirt textures are a
     * rearrangement of dirt pixels at almost identical brightness, so dividing one by the
     * other yields noise rather than a wear pattern. Transferring that noise onto stone
     * produces stone with different noise, which is not what a trodden path looks like.
     *
     * <p>
     * What the sand art actually encodes is structural: a worn surface loses its speckle and
     * gets darker. Reduced to those two numbers it applies to any texture at any resolution
     * and needs no per-pixel correspondence at all.
     *
     * @return {@code {contrastRatio, lumaRatio}}, both relative to the reference
     */
    public static float[] measurePolish(int[] art, int[] reference) {
        float artMean = meanLuma(art);
        float refMean = meanLuma(reference);
        float artSpread = spread(art, artMean);
        float refSpread = spread(reference, refMean);

        float contrast = refSpread <= 0.001f ? 1f : artSpread / refSpread;
        float luma = refMean <= 0.001f ? 1f : artMean / refMean;
        return new float[] { contrast, luma };
    }

    private static float meanLuma(int[] pixels) {
        long total = 0;
        for (int pixel : pixels) {
            total += luma(pixel);
        }
        return pixels.length == 0 ? 0f : total / (float) pixels.length;
    }

    private static float spread(int[] pixels, float mean) {
        double total = 0;
        for (int pixel : pixels) {
            double delta = luma(pixel) - mean;
            total += delta * delta;
        }
        return pixels.length == 0 ? 0f : (float) Math.sqrt(total / pixels.length);
    }

    private static int luma(int pixel) {
        return (((pixel >>> 16) & 0xFF) + ((pixel >>> 8) & 0xFF) + (pixel & 0xFF)) / 3;
    }

    /**
     * Wears a surface by flattening it toward its own average colour and darkening it.
     *
     * <p>
     * Because it works from the texture's own mean, a path on granite comes out granite and a
     * path on gravel comes out gravel. Nothing of the surface it was measured from carries
     * across, which is exactly what makes it safe to apply to a block nobody drew art for.
     */
    // ------------------------------------------------------------------
    // Cracking
    // ------------------------------------------------------------------

    /**
     * The resolution the fracture fields are built at, whatever resolution the block's art is.
     *
     * <p>
     * Building the field at the texture's own size was the mistake that made the old cracks read
     * as static rather than as fissures. A sixteen-pixel texture gave a sixteen-pixel field, so a
     * lattice of cells four pixels apart had nowhere to put a line: every cell boundary landed on
     * a whole pixel or missed it entirely, and the result was a checkerboard of black pixels
     * scattered over an otherwise untouched surface. Fixing the field at sixty-four means the
     * boundary is always known to a quarter of a pixel and a sixteen-pixel crack can be a soft
     * two-thirds of one, which is what a hairline in a block texture has to be.
     */
    private static final int FIELD_SIZE = 64;

    /** How many cells the main fissures divide a face into, and how many the finer web does. */
    private static final int MAIN_CELLS = 3;
    private static final int WEB_CELLS = 6;

    /**
     * How much of a fissure's half-width is solid before the edge begins to fade.
     *
     * <p>
     * The old profile eased from the centre outward with nothing solid anywhere, so averaging it
     * over a pixel could never give a full-strength crack: the deepest a pixel sitting squarely
     * on a fissure could get was about a third. Giving the middle a flat core lets a crack be a
     * crack where it is one and still fade out over its outer edge, which is the only part that
     * wants softening.
     */
    private static final float CRACK_CORE = 0.40f;

    /** Half-width of the main fissure at the first gradation and at the last, as a fraction of a face. */
    private static final float MAIN_WIDTH_MIN = 0.016f;
    private static final float MAIN_WIDTH_MAX = 0.055f;

    /** The same for the finer web, which only starts once the face is nearly half gone. */
    private static final float WEB_WIDTH_MIN = 0.004f;
    private static final float WEB_WIDTH_MAX = 0.018f;
    private static final float WEB_START = 0.45f;
    private static final float WEB_DEPTH = 0.50f;

    /**
     * The crumbled ground either side of a fissure, as a multiple of its own half-width and a
     * fraction of its depth.
     *
     * <p>
     * A crack with a hard edge reads as a line somebody drew. A crack with a broad, shallow
     * shadow around it reads as stone that has been breaking up for years, and it is what lets
     * the fissure itself stay narrow — narrow enough not to swallow the face — while still being
     * something you can see from standing height.
     */
    private static final float HALO_SPAN = 2.8f;
    private static final float HALO_DEPTH = 0.45f;

    /**
     * How far below the surface's own dark end a fully cut pixel lands: at the first gradation,
     * at the deepest point of the run, and at the very end.
     *
     * <p>
     * Deepest in the middle rather than at the end, which is both what the ground does and what
     * fixes the complaint that worn stone stayed crisp forever. A crack opens, and then it fills:
     * grit and mud collect in it, and by the time the surface around it has gone the crack is a
     * dull seam rather than a sharp one. Everything else keeps darkening throughout, so the face
     * still gets steadily worse even as the fissure silts up.
     */
    private static final float DROP_START = 0.28f;
    private static final float DROP_PEAK = 0.72f;
    private static final float DROP_AT = 0.70f;
    private static final float DROP_END = 0.60f;

    /** Where the surface's own dark end is read from. Not the darkest pixel, which is often a one-off. */
    private static final float SHADOW_QUANTILE = 0.12f;

    /**
     * The three things that stop the fissure network looking like the lattice it is: how far the
     * sampling point is dragged about, how much the width swells and shrinks along a run, and how
     * far it pinches shut in places. All three are low-frequency, because a crack that wanders
     * over several pixels reads as a crack and one that wobbles pixel to pixel reads as noise.
     */
    private static final float WARP = 3.0f;
    private static final int WARP_CELLS = 2;
    private static final float WOBBLE = 0.50f;
    private static final int WOBBLE_CELLS = 5;
    private static final float BREAK_DEPTH = 0.42f;
    private static final int BREAK_CELLS = 4;

    /** How ragged the crack's own edge is, per pixel. The only part of it that should be noisy. */
    private static final float DITHER = 0.15f;

    /**
     * The four things that make a face muddy: how far each pixel is pulled toward its own
     * neighbours, how much of the face's overall contrast goes, how much darker the whole thing
     * settles, and how much of the colour goes with the dust.
     *
     * <p>
     * This is the part the old operator had none of, and its absence is what made worn stone look
     * like clean stone with cracks drawn on. A trodden surface loses its definition before it
     * loses anything else: the fine detail rubs off first, the light and dark of it close up, and
     * what is left is dirtier and greyer than it started. The blur is kept deliberately modest,
     * because past about a third the block stops being recognisably itself, which is the one
     * thing none of this may cost.
     */
    private static final float BLUR = 0.30f;
    private static final float FLATTEN = 0.24f;
    private static final float SETTLE = 0.20f;
    private static final float DUST = 0.12f;

    /** Ground-in dirt, in broad patches and as fine grit. Both only ever darken. */
    private static final float GRIME = 0.22f;
    private static final int GRIME_CELLS = 2;
    private static final float SPECK = 0.16f;

    /**
     * The fracture fields, built once each and kept for the life of the game.
     *
     * <p>
     * They depend on nothing but their own cell count and seed, so rebuilding them per sprite -
     * which is what used to happen, several hundred times a stitch - was pure waste, and dropping
     * it is why this operator is several times cheaper than the one it replaces despite doing more.
     * There are at most two per rotation and rotations are capped at four, so this holds eight
     * fields and then stops growing; it is never cleared, because nothing in a resource pack can
     * change what a Voronoi field of a given seed looks like. Unsynchronised for the same reason
     * the pattern caches are: the atlas builds sprites on the client thread and nothing else here
     * runs.
     */
    private static final java.util.Map<Long, float[]> FRACTURE_CACHE = new java.util.concurrent.ConcurrentHashMap<Long, float[]>();

    /**
     * Wears a hard surface by dulling it and cracking it open.
     *
     * <p>
     * Deepening the crevices a texture already has was the whole of this once, and on anything
     * smooth it did almost nothing - a dressed stone brick or a clean cobble has barely a crevice
     * to deepen, so every stage came out looking freshly laid. So the fissures are made rather
     * than found. A tileable fracture field decides where the stone splits, and the split widens
     * and spreads as the ground wears.
     *
     * <p>
     * The fissures are only half of it, and the smaller half. Before anything is cut, the face is
     * worn down the way a trodden surface actually goes: each pixel is drawn toward its
     * neighbours so the fine detail rubs off, the whole face's light and dark close up, dirt
     * collects in broad patches with grit scattered through it, and a little of the colour goes
     * with the dust. That is what makes a late gradation muddier than an early one rather than
     * merely darker, and it is why a stone brick still reads as a stone brick after all of it.
     *
     * <p>
     * A cut pixel is driven toward a tone taken from the surface's own dark end rather than
     * scaled by a flat fraction. Scaling by a fraction is how the fissures came out near black:
     * the same multiply that made a hairline visible on pale sandstone took a mortar joint to
     * nothing. Aiming at a tone the block already contains means a crack is as dark as that block
     * can plausibly be and no darker, on sandstone and on netherrack alike.
     *
     * <p>
     * The field is built at a fixed resolution and area-averaged down to whatever the block uses,
     * so the edge of a crack is a fraction of a pixel rather than all or nothing, and a
     * 128-pixel texture costs what a 16-pixel one does. Every pixel is scaled on all three
     * channels at once, so a crack is a shadow in the surface's own colour and never a grey line
     * painted over it.
     *
     * @param seed varies the whole network between rotations, so a path is not the same crack
     *             stamped over and over
     */
    /**
     * Builds every fracture field a stitch will ask for, before anything asks for it in parallel.
     *
     * <p>
     * The cache below is concurrent and would survive without this, but building each field twice
     * on two threads is work done twice for nothing, and the fields are the dearest thing the crack
     * operator does. Two per rotation - the main network and the finer web - so eight in all at the
     * shipped rotation count.
     */
    public static void primeFractures(int rotations) {
        for (int rotation = 0; rotation < Math.max(1, rotations); rotation++) {
            fracture(MAIN_CELLS, 1 + rotation * 31);
            fracture(WEB_CELLS, 2 + rotation * 31);
        }
    }

    public static int[] applyCrack(int[] source, int size, float coverage, float depth, int seed) {
        int[] out = new int[size * size];
        float wear = depth < 0f ? 0f : depth > 1f ? 1f : depth;
        if (wear <= 0f) {
            for (int i = 0; i < out.length && i < source.length; i++) {
                out[i] = 0xFF000000 | (source[i] & 0xFFFFFF);
            }
            return out;
        }

        float mean = meanLuma(source);
        float shadow = lumaQuantile(source, SHADOW_QUANTILE);
        float[] fissures = fracture(MAIN_CELLS, 1 + seed * 31);
        float[] web = fracture(WEB_CELLS, 2 + seed * 31);

        // How many field samples fall inside one output pixel. Four at vanilla resolution, which
        // is what turns a hard-edged line into a soft one; one on a texture finer than the field,
        // where the block's own detail is already doing that job.
        int subs = FIELD_SIZE / size;
        if (subs < 1) subs = 1;

        float webWear = wear <= WEB_START ? 0f : (wear - WEB_START) / (1f - WEB_START);
        float mainWidth = MAIN_WIDTH_MIN + (MAIN_WIDTH_MAX - MAIN_WIDTH_MIN) * wear;
        float webWidth = WEB_WIDTH_MIN + (WEB_WIDTH_MAX - WEB_WIDTH_MIN) * webWear;
        float drop = wear <= DROP_AT ? DROP_START + (DROP_PEAK - DROP_START) * (wear / DROP_AT)
            : DROP_PEAK + (DROP_END - DROP_PEAK) * ((wear - DROP_AT) / (1f - DROP_AT));

        float blur = BLUR * wear;
        float flatten = 1f - FLATTEN * wear;
        float settle = 1f - SETTLE * wear;
        float grime = GRIME * wear;
        float speck = SPECK * wear;
        float dust = DUST * wear;
        float spread = coverage < 0f ? 0f : coverage > 1f ? 1f : coverage;
        float pit = shadow * settle * (1f - drop);

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int index = y * size + x;
                if (index >= source.length) continue;
                int pixel = source[index];

                // Definition goes first. Wrapping, because a block texture tiles against copies
                // of itself and a face that blurred toward its own edges would show a seam.
                float red = (pixel >>> 16) & 0xFF;
                float green = (pixel >>> 8) & 0xFF;
                float blue = pixel & 0xFF;
                if (blur > 0f) {
                    int sumRed = 0;
                    int sumGreen = 0;
                    int sumBlue = 0;
                    for (int dy = -1; dy <= 1; dy++) {
                        int ny = ((y + dy) % size + size) % size;
                        for (int dx = -1; dx <= 1; dx++) {
                            int neighbour = source[ny * size + (((x + dx) % size + size) % size)];
                            sumRed += (neighbour >>> 16) & 0xFF;
                            sumGreen += (neighbour >>> 8) & 0xFF;
                            sumBlue += neighbour & 0xFF;
                        }
                    }
                    red += (sumRed / 9f - red) * blur;
                    green += (sumGreen / 9f - green) * blur;
                    blue += (sumBlue / 9f - blue) * blur;
                }
                float brightness = (red + green + blue) / 3f;

                // Then contrast, then dirt. The grime is broad and the grit is per pixel, which
                // between them give a face that is unevenly filthy rather than evenly dimmer -
                // and a uniformly dimmer block is the thing that reads as a lighting bug.
                float dirt = 1f - grime * lowFrequency(x, y, size, GRIME_CELLS, 41 + seed * 17)
                    - speck * hash(x, y, 97 + seed * 13);
                float base = (mean + (brightness - mean) * flatten) * settle * dirt;

                // The fissure's width swells, shrinks and pinches shut along its run, and the
                // point it is measured from is dragged about, so what comes out of a lattice of
                // cells does not look like one.
                float swell = 1f + (lowFrequency(x, y, size, WOBBLE_CELLS, 23 + seed * 7) - 0.5f) * 2f * WOBBLE;
                float pinch = 1f - BREAK_DEPTH * (1f - lowFrequency(x, y, size, BREAK_CELLS, 7 + seed * 11));
                if (pinch < 0f) pinch = 0f;
                float width = mainWidth * swell * pinch;
                float fineWidth = webWidth * swell;

                int warpX = Math.round((lowFrequency(x, y, size, WARP_CELLS, 53 + seed * 19) - 0.5f) * 2f * WARP);
                int warpY = Math.round((lowFrequency(x, y, size, WARP_CELLS, 71 + seed * 19) - 0.5f) * 2f * WARP);
                int fieldX = x * FIELD_SIZE / size;
                int fieldY = y * FIELD_SIZE / size;

                float cut = 0f;
                for (int sy = 0; sy < subs; sy++) {
                    int sampleY = ((fieldY + sy + warpY) % FIELD_SIZE + FIELD_SIZE) % FIELD_SIZE;
                    for (int sx = 0; sx < subs; sx++) {
                        int sampleX = ((fieldX + sx + warpX) % FIELD_SIZE + FIELD_SIZE) % FIELD_SIZE;
                        float edge = fissures[sampleY * FIELD_SIZE + sampleX];
                        float amount = lineAmount(edge, width);
                        float halo = lineAmount(edge, width * HALO_SPAN) * HALO_DEPTH;
                        if (halo > amount) amount = halo;
                        float fine = lineAmount(web[sampleY * FIELD_SIZE + sampleX], fineWidth) * WEB_DEPTH;
                        cut += amount > fine ? amount : fine;
                    }
                }
                cut /= subs * subs;
                // Only the edge is frayed. Fraying the middle as well is what would put the
                // pixel-to-pixel flicker back that made the old cracks look like static.
                if (cut > 0.02f) cut += (hash(x, y, 61 + seed * 3) - 0.5f) * DITHER;
                if (cut < 0f) cut = 0f;
                if (cut > 1f) cut = 1f;
                cut *= spread;

                float wanted = base + (pit - base) * cut;
                if (wanted > base) wanted = base; // a crack never brightens what it cuts
                if (wanted < 1f) wanted = 1f;
                float scale = brightness <= 0.5f ? 0f : wanted / brightness;

                float wornRed = red * scale;
                float wornGreen = green * scale;
                float wornBlue = blue * scale;
                // The last of it is dust, which is grey. Taken from the pixel's own brightness
                // rather than from any colour of ours, so nothing is invented: this can only ever
                // move a pixel toward a grey it is already exactly as light as.
                float grey = (wornRed + wornGreen + wornBlue) / 3f;
                int result = 0xFF000000 | (clampByte(wornRed + (grey - wornRed) * dust) << 16)
                    | (clampByte(wornGreen + (grey - wornGreen) * dust) << 8)
                    | clampByte(wornBlue + (grey - wornBlue) * dust);
                out[index] = result;
            }
        }
        return out;
    }

    /** The unseeded form, for callers with no rotation to hand. */
    public static int[] applyCrack(int[] source, int size, float coverage, float depth) {
        return applyCrack(source, size, coverage, depth, 0);
    }

    private static int clampByte(float value) {
        int rounded = Math.round(value);
        return rounded < 0 ? 0 : rounded > 255 ? 255 : rounded;
    }

    /**
     * How much of a crack is at this pixel: solid through the middle, easing to nothing at
     * {@code width}.
     */
    private static float lineAmount(float edge, float width) {
        if (width <= 0f || edge >= width) return 0f;
        float across = edge / width;
        if (across <= CRACK_CORE) return 1f;
        float fade = (1f - across) / (1f - CRACK_CORE);
        return fade * fade;
    }

    /**
     * A smooth low-frequency value between 0 and 1, {@code cells} blobs across {@code span},
     * wrapping at the edges so it tiles the way the texture it is used on has to.
     */
    private static float lowFrequency(int x, int y, int span, int cells, int seed) {
        float acrossX = x * cells / (float) span;
        float acrossY = y * cells / (float) span;
        // A plain truncation, because the only caller walks a pixel grid and never goes negative.
        int cellX = (int) acrossX;
        int cellY = (int) acrossY;
        float withinX = acrossX - cellX;
        float withinY = acrossY - cellY;
        // Smoothstep, so neighbouring cells blend instead of showing their edges.
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

    /** A fracture field at the fixed working resolution, built once per cell count and seed. */
    private static float[] fracture(int cells, int seed) {
        Long key = Long.valueOf(((long) cells << 32) ^ (seed & 0xFFFFFFFFL));
        float[] cached = FRACTURE_CACHE.get(key);
        if (cached != null) return cached;
        float[] built = fractureField(FIELD_SIZE, cells, seed);
        FRACTURE_CACHE.put(key, built);
        return built;
    }

    /**
     * A tileable fracture field: per pixel, the gap between the nearest and second-nearest seed.
     *
     * <p>
     * Zero along the boundary between two cells, rising away from it - which is exactly where a
     * brittle surface splits, so thresholding it gives a network of closed, branching cracks
     * rather than scratches. The seeds wrap, so the field tiles against copies of itself the way
     * a block texture must.
     */
    static float[] fractureField(int size, int cells, int seed) {
        float[] field = new float[size * size];
        for (int y = 0; y < size; y++) {
            float py = (y + 0.5f) / size;
            for (int x = 0; x < size; x++) {
                float px = (x + 0.5f) / size;
                float best = Float.MAX_VALUE;
                float second = Float.MAX_VALUE;
                for (int cellY = -1; cellY <= cells; cellY++) {
                    for (int cellX = -1; cellX <= cells; cellX++) {
                        int wrapX = ((cellX % cells) + cells) % cells;
                        int wrapY = ((cellY % cells) + cells) % cells;
                        float seedX = (cellX + hash(wrapX, wrapY, seed)) / cells;
                        float seedY = (cellY + hash(wrapX, wrapY, seed + 977)) / cells;
                        float dx = px - seedX;
                        float dy = py - seedY;
                        float distance = dx * dx + dy * dy;
                        if (distance < best) {
                            second = best;
                            best = distance;
                        } else if (distance < second) {
                            second = distance;
                        }
                    }
                }
                field[y * size + x] = (float) (Math.sqrt(second) - Math.sqrt(best));
            }
        }
        return field;
    }

    /**
     * How far the plain smoothed look closes a face's light and dark up, and how far it darkens
     * what is left.
     *
     * <p>
     * Named rather than typed out because three looks now pass them - the smoothing itself, and
     * both compounds that buff a face before doing something else to it. Three copies of a pair of
     * magic numbers is three chances for two of them to drift apart, and the drift would show as
     * one row of the picker quietly not matching the two beside it.
     */
    public static final float POLISH_CONTRAST = 0.6f;

    public static final float POLISH_LUMA = 0.9f;

    /**
     * The same two for the heavier smoothing, which is a different pair of numbers rather than the
     * same pair applied harder.
     *
     * <p>
     * Applying it harder would mean multiplying the strength, and the strength is the family's own
     * wearStrength: a family set to 1.0 would quietly be running 1.8 and the setting would be lying
     * about itself. A look lives in its parameters, so this is where it changes.
     *
     * <p>
     * About a third of the relief survives, and it is not an arbitrary third. {@link #applyOverlay}
     * claims roughly a third of the face untouched at full wear and calls that surviving relief the
     * whole difference between worn cobble and a grey smear; the same budget is what keeps a buffed
     * cobble a cobble. Below about 0.30 a sixteen-pixel face sits within a few luma levels of its
     * own mean and is a grey square, so this is a floor rather than a preference. The darkening is
     * deliberately far gentler than the flattening, because buffed means a loss of texture and not
     * a loss of light.
     */
    public static final float HEAVY_CONTRAST = 0.32f;

    public static final float HEAVY_LUMA = 0.80f;

    public static int[] applyPolish(int[] base, int size, float contrast, float luma, float strength) {
        int[] out = new int[size * size];
        if (strength <= 0f) {
            for (int i = 0; i < base.length && i < out.length; i++) {
                out[i] = 0xFF000000 | (base[i] & 0xFFFFFF);
            }
            return out;
        }

        float effectiveContrast = 1f + (contrast - 1f) * strength;
        float effectiveLuma = 1f + (luma - 1f) * strength;
        float meanBrightness = meanLuma(base);

        for (int i = 0; i < out.length; i++) {
            int pixel = base[i];
            float brightness = luma(pixel);
            float wanted = (meanBrightness + (brightness - meanBrightness) * effectiveContrast) * effectiveLuma;
            if (wanted < 0f) wanted = 0f;

            // Every channel moves by the same factor, which is the whole point: the pixel gets
            // darker and flatter without its colour changing at all. Working channel by channel
            // against a per-channel mean, as this used to, pulls each one a different distance
            // and quietly drains the colour out — which is why worn sand came out pale next to
            // the real thing, and why worn stone had no stone left in it.
            float scale = brightness <= 0.5f ? 0f : wanted / brightness;
            int result = 0xFF000000;
            for (int channel = 0; channel < 3; channel++) {
                int shift = 16 - channel * 8;
                int worn = Math.round(((pixel >>> shift) & 0xFF) * scale);
                if (worn < 0) worn = 0;
                if (worn > 255) worn = 255;
                result |= worn << shift;
            }
            out[i] = result;
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Overlay wear
    // ------------------------------------------------------------------

    /**
     * Wears a surface by darkening a growing patch of it, and leaving the rest alone.
     *
     * <p>
     * This replaces an operator that scaled the whole texture down as it wore. That is not what
     * worn ground looks like: a path is not a darker version of the field beside it, it is a
     * place where some of the surface has been rubbed away and what is underneath shows through.
     * A uniformly darker block reads as a lighting bug, which is how it looked.
     *
     * <p>
     * So two things happen and only two. A mask decides which pixels are worn, and it grows with
     * coverage. Those pixels are recoloured toward a tone the block already contains. Every other
     * pixel comes out bit-identical to the source, which is what keeps a cobble looking like
     * cobble rather than a grey smear: at full wear a third of the face is still untouched, and
     * that surviving relief is the whole difference.
     *
     * <p>
     * The recolour is one scale applied to all three channels. Hue and saturation are ratios
     * between channels, so scaling them together cannot shift either. Red sand wears to darker
     * red sand and stone wears to stone's own shadow, with no palette to look up and no list of
     * which blocks are which colour.
     *
     * @param source   the block's own pixels
     * @param size     edge length of the square
     * @param order    per-pixel wear ranking, in art space
     * @param artSize  edge length that ranking was built at
     * @param coverage fraction of the face that has worn, 0 to 1
     * @param depth    how far the worn pixels have gone, 0 to 1
     */
    public static int[] applyOverlay(int[] source, int size, float[] order, int artSize, float coverage, float depth) {
        int[] out = new int[size * size];
        if (coverage <= 0f || order == null) {
            for (int i = 0; i < source.length && i < out.length; i++) {
                out[i] = 0xFF000000 | (source[i] & 0xFFFFFF);
            }
            return out;
        }

        float cut = coverageThreshold(order, coverage);
        // Tones the block itself uses. Taking the worn colour from the source's own dark end is
        // what makes this work on a block nobody has ever heard of: there is nothing to look up,
        // because the answer is already in the texture.
        float shadow = lumaQuantile(source, 0.15f);
        // A guard against a worn pixel going black, not a target. Sitting it AT the block's own
        // darkest tone was a mistake that cost a whole round of tuning: nearly every worn pixel
        // clamped onto it, so the worn patch came out one flat colour and no amount of adjusting
        // the flattening changed anything, because the flattening was never what decided it.
        float floor = lumaQuantile(source, 0.03f) * (1f - 0.35f * depth);
        // Enough to see. These were gentle enough that a worn patch on a low-contrast block -
        // most modded stone - was indistinguishable from a clean one at any distance.
        float settle = 1f - 0.38f * depth;
        float flatten = 1f - 0.60f * depth;

        for (int y = 0; y < size; y++) {
            int artY = y * artSize / size;
            for (int x = 0; x < size; x++) {
                int index = y * size + x;
                int pixel = source[index];
                if (order[artY * artSize + (x * artSize / size)] < cut) {
                    out[index] = 0xFF000000 | (pixel & 0xFFFFFF);
                    continue;
                }

                float brightness = luma(pixel);
                float target = (shadow + (brightness - shadow) * flatten) * settle;
                if (target < floor) target = floor;
                if (target > brightness) target = brightness;

                float scale = brightness <= 0.5f ? 0f : target / brightness;
                int result = 0xFF000000;
                for (int channel = 0; channel < 3; channel++) {
                    int shift = 16 - channel * 8;
                    int worn = Math.round(((pixel >>> shift) & 0xFF) * scale);
                    if (worn < 0) worn = 0;
                    if (worn > 255) worn = 255;
                    result |= worn << shift;
                }
                out[index] = result;
            }
        }
        return out;
    }

    /** The luma below which the given fraction of a source's pixels sit. */
    static float lumaQuantile(int[] pixels, float fraction) {
        if (pixels.length == 0) return 0f;
        float[] sorted = new float[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            sorted[i] = luma(pixels[i]);
        }
        java.util.Arrays.sort(sorted);
        int index = Math.round(fraction * (sorted.length - 1));
        if (index < 0) index = 0;
        if (index >= sorted.length) index = sorted.length - 1;
        return sorted[index];
    }

    /**
     * Ranks a block's own pixels by which wear away first, highest first.
     *
     * <p>
     * The bright parts go first, because those are the raised parts catching the traffic. Ranking
     * raw brightness alone picks out single pixels scattered across the face, which reads as
     * static rather than as a worn patch, so each pixel is judged partly on its neighbours and a
     * raised cobble wears as one lump the way a real one does. The patch term has a floor under
     * it so a texture with almost no relief still wears in patches instead of dissolving into
     * speckle, which is the case that matters for smooth modded stone.
     */
    public static float[] sourceWearOrder(int[] pixels, int size, int seed) {
        float[] order = new float[size * size];
        float[] brightness = new float[size * size];
        float total = 0f;
        for (int i = 0; i < pixels.length && i < brightness.length; i++) {
            brightness[i] = luma(pixels[i]);
            total += brightness[i];
        }
        float mean = brightness.length == 0 ? 0f : total / brightness.length;

        float variance = 0f;
        for (int i = 0; i < brightness.length; i++) {
            float delta = brightness[i] - mean;
            variance += delta * delta;
        }
        float spread = brightness.length == 0 ? 0f : (float) Math.sqrt(variance / brightness.length);
        float patchAmount = Math.max(2.0f * spread, 6.0f);

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                // Wrapping, because a block texture tiles against copies of itself.
                float neighbourhood = 0f;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = ((x + dx) % size + size) % size;
                        int ny = ((y + dy) % size + size) % size;
                        neighbourhood += brightness[ny * size + nx];
                    }
                }
                float smoothed = brightness[y * size + x] * 0.45f + (neighbourhood / 9f) * 0.55f;
                order[y * size + x] = smoothed + patchAmount * (patch(x, y, size, seed) - 0.5f)
                    + hash(x, y, seed) * 0.25f;
            }
        }
        return order;
    }

    /** A smooth low-frequency field, so wear arrives in patches rather than as speckle. */
    private static float patch(int x, int y, int size, int seed) {
        int cell = Math.max(2, size / 2);
        int cx = x / cell;
        int cy = y / cell;
        float fx = (x % cell) / (float) cell;
        float fy = (y % cell) / (float) cell;
        // Smoothstep, so neighbouring cells blend instead of showing their edges.
        fx = fx * fx * (3f - 2f * fx);
        fy = fy * fy * (3f - 2f * fy);

        float topEdge = hash(cx, cy, seed) * (1f - fx) + hash(cx + 1, cy, seed) * fx;
        float bottomEdge = hash(cx, cy + 1, seed) * (1f - fx) + hash(cx + 1, cy + 1, seed) * fx;
        return topEdge * (1f - fy) + bottomEdge * fy;
    }

    private static float hash(int x, int y, int seed) {
        int h = x * 374761393 + y * 668265263 + seed * 1442695040;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535f;
    }

    /**
     * Buffs a face flat and then splits it open.
     *
     * <p>
     * Two operators that already exist, in the one order that works, and the order is a property of
     * the operators rather than a preference. Everything {@link #applyCrack} aims at is read out of
     * the pixels it is handed: the mean, and the shadow taken at the shadow quantile, are what a cut
     * pixel is driven toward. {@link #applyPolish} does the opposite - it pulls every pixel toward
     * the mean by a fixed fraction, and a fissure is by construction the pixel furthest from it. So
     * polishing afterwards is a crack-removal operator in a polish's clothes: it spends the whole
     * crack pass drawing lines and then rubs most of them out again. Polishing first hands the crack
     * a flatter surface to measure, and because the crack aims at a fraction of that surface's own
     * dark end rather than at an absolute tone, a slightly raised shadow costs it almost nothing.
     *
     * <p>
     * Both halves are needed for the picture this look is for. The buffing is what separates it from
     * a plain crack, whose ground between the fissures is merely dirty where this one's is dead; the
     * crack is what separates it from a plain smoothing, which has no lines in it at all.
     *
     * <p>
     * That the reverse fails is the claim here most likely to be undone by a later tidy-up, so it is
     * pinned in the test rather than only written down.
     *
     * @param smoothing how far the face is buffed before anything is cut, 0 to 1
     * @param seed      varies the whole network between rotations, as {@link #applyCrack} does
     */
    public static int[] applySmoothedCrack(int[] source, int size, float coverage, float depth, float smoothing,
        int seed) {
        return applyCrack(
            applyPolish(source, size, POLISH_CONTRAST, POLISH_LUMA, smoothing),
            size,
            coverage,
            depth,
            seed);
    }

    /**
     * Buffs a face flat and then wears a track across part of it.
     *
     * <p>
     * The same order as {@link #applySmoothedCrack} and mostly the same argument for it: the tones
     * {@link #applyOverlay} aims at are read off whatever it is handed, so the buffing has to come
     * first or both of them are guards on an intermediate that something else then moves. There is a
     * second reason here that the crack does not have. applyOverlay leaves every unworn pixel
     * bit-identical to its input, and that is the whole of what the operator is for; polish
     * afterwards and no pixel on the face is untouched, the surviving relief goes, and what is left
     * is a plain smoothing with a slightly darker patch on it.
     *
     * <p>
     * The one thing read before the buffing is the wear order, and it is read from the original
     * pixels. Tones after, geometry before: where a track forms is a question about the block's
     * shape, which is the block's own and not a function of how much it has been buffed. The ranking
     * barely moves either way, so the reason is legibility rather than fidelity - taking it from the
     * original puts this look's track in the same place on the same block as the plain rub's, which
     * is what makes those two rows of the picker comparable rather than merely similar.
     *
     * @param smoothing how far the face is buffed before anything is worn, 0 to 1
     * @param seed      varies where the track forms, as {@link #sourceWearOrder} does
     */
    public static int[] applySmoothedRub(int[] source, int size, float coverage, float depth, float smoothing,
        int seed) {
        float[] order = sourceWearOrder(source, size, seed);
        return applyOverlay(
            applyPolish(source, size, POLISH_CONTRAST, POLISH_LUMA, smoothing),
            size,
            order,
            size,
            coverage,
            depth);
    }

    /**
     * Splits a face open and then wears a track through what it has split.
     *
     * <p>
     * The third compound and the first whose order does not follow from the two above it. Both of
     * those buff first because {@link #applyPolish} pulls every pixel toward the mean and would
     * therefore undo whatever had just been drawn away from it. Neither half here is a tone operator
     * of that kind, so that argument says nothing about this pair and the order had to be measured.
     * It was, at the end of a full run, on eight vanilla faces - stone, cobblestone, dirt, sand,
     * gravel, netherrack, end stone and snow - at all four rotations.
     *
     * <p>
     * Cracking first wins on both halves at once, which is what makes it an answer rather than a
     * preference. What makes a track a track is how far the worn part sits below the part left
     * alone: a plain rub separates them by 41.98 colour levels, this order keeps 30.29 of that and
     * the reverse keeps 10.61. The reason is in {@link #applyCrack} rather than in taste - it blurs
     * across a three by three window, and a track's edge is a per-pixel ranked mask, so a crack run
     * afterwards smears out the very boundary that makes a track a track.
     *
     * <p>
     * The rub keeps its own promise this way round and cannot keep it the other. Every pixel the
     * track does not claim comes back exactly as {@link #applyOverlay} was handed it, which is the
     * whole of what that operator is for: seventy-two pixels of a sixteen-square face at the plain
     * rub's own coverage, the same seventy-two it leaves against a bare block, on every face at
     * every rotation. Those pixels are a plain crack pixel for pixel, so the fracture network over
     * that part of the face is at full strength by construction rather than by measurement.
     * Cracking afterwards instead leaves none at all - not fewer, none - because the crack's blur,
     * flattening, settling and grime reach every pixel it is handed whatever its coverage says; only
     * the fissures themselves are masked.
     *
     * <p>
     * One figure is given up for that, and it is given up on purpose. Inside the track the fissures
     * read 4.03 levels against the ground beside them, where a plain crack's read 16.36 and where
     * cracking last would have kept 10.62. A fissure inside a trodden path has been walked flat, and
     * a path whose cracks are as sharp as the ones beside it is a path nobody has walked on.
     *
     * <p>
     * The ranking is read from the original pixels, as {@link #applySmoothedRub} reads it - tones
     * after, geometry before - and here that is load-bearing rather than a matter of legibility. A
     * fissure is the darkest thing on a cracked face and the rub claims the brightest pixels first,
     * so ranking the cracked image makes the track form in the plates between the fissures and avoid
     * them: nearly a third of the face changes side, the track's separation falls to 18.01 levels
     * and the network over the whole face to 4.19 from 12.83. A path that avoids its own cracks is a
     * prettier idea than it is a picture.
     *
     * <p>
     * That the reverse fails is the claim here most likely to be undone by a later tidy-up, so it is
     * pinned in the test rather than only written down.
     *
     * @param coverage      how much of the face the track claims, 0 to 1
     * @param depth         how far the track has worn into what it claims, 0 to 1
     * @param crackCoverage how much of the fracture network has opened, before anything is worn
     * @param crackDepth    how far the cracking has gone, 0 to 1
     * @param seed          varies the network and where the track forms alike, as the halves do
     */
    public static int[] applyCrackedRub(int[] source, int size, float coverage, float depth, float crackCoverage,
        float crackDepth, int seed) {
        float[] order = sourceWearOrder(source, size, seed);
        return applyOverlay(
            applyCrack(source, size, crackCoverage, crackDepth, seed),
            size,
            order,
            size,
            coverage,
            depth);
    }

    /**
     * The same, but only where the shell was cut clean away.
     *
     * <p>
     * {@link #over} keeps the greater of the two alphas, which is right for everything that shares
     * it: what comes out is a picture drawn in the solid pass, where alpha is a test rather than a
     * blend, so the value only has to clear a threshold. It is wrong the moment the picture is drawn
     * in the blended pass instead. A carved shell's own pixels are mostly not fully opaque - a
     * chaotic waterstone has ten of two hundred and fifty-six at full - so keeping the greater alpha
     * would hand the whole face the water's floor of a hundred and seventy and make the stone
     * see-through along with the water. Measured, that is ninety-six per cent of that face.
     *
     * <p>
     * So the shell is forced solid and only a hole keeps what the layer put there. Three populations
     * go in and two come out: where the shell was solid the result is opaque, where it was partial
     * the stone is blended over the layer here on the processor and the result is opaque as well,
     * and where it was a hole the layer's own pixel passes through untouched, alpha and all. The
     * middle case is the one worth arguing: those pixels are already a mixture of stone and water,
     * and a mixture that is part stone should not be a window.
     *
     * <p>
     * Forced rather than passed through at the top, so that the finished sprite carries exactly two
     * populations and anything downstream can ask whether a pixel is fully opaque rather than
     * whether it clears some threshold. Fifty-two pixels across the waterstone carvings sit between
     * two hundred and fifty and two hundred and fifty-four today, and this is what tidies them.
     */
    public static int[] overThroughHoles(int[] under, int[] top, int size) {
        if (under == null) return top;
        if (top == null) return under;
        return overThroughHolesInto(new int[size * size], under, top, size);
    }

    /**
     * {@link #overThroughHoles}, written into a picture the caller holds rather than a new one, which is returned.
     *
     * <p>
     * For the render thread's redraw of a moving layer, which lays the same shell over a new frame every time the layer
     * moves and hands the result straight to an upload that copies it and keeps nothing. Only the first size squared
     * pixels are written, and nothing past them. Never for compose, whose result becomes a picture: a worker that
     * wrote into a shared picture would be one sprite drawing another's pixels.
     */
    public static int[] overThroughHolesInto(int[] out, int[] under, int[] top, int size) {
        if (under == null) return top;
        if (top == null) return under;
        int pixels = size * size;
        for (int i = 0; i < pixels && i < out.length && i < top.length && i < under.length; i++) {
            int alpha = (top[i] >>> 24) & 0xFF;
            if (alpha >= 250) {
                out[i] = 0xFF000000 | (top[i] & 0xFFFFFF);
                continue;
            }
            if (alpha == 0) {
                out[i] = under[i];
                continue;
            }
            int inverse = 255 - alpha;
            int red = (((top[i] >> 16) & 0xFF) * alpha + ((under[i] >> 16) & 0xFF) * inverse) / 255;
            int green = (((top[i] >> 8) & 0xFF) * alpha + ((under[i] >> 8) & 0xFF) * inverse) / 255;
            int blue = ((top[i] & 0xFF) * alpha + (under[i] & 0xFF) * inverse) / 255;
            out[i] = 0xFF000000 | (red << 16) | (green << 8) | blue;
        }
        return out;
    }

    /**
     * Composed pixels with the source's own transparency put back, channel for channel.
     *
     * <p>
     * Every operator in this file writes a fully opaque pixel, and each is right to: the colour
     * arithmetic they do - flattening towards a quantile, scaling three channels by one factor,
     * blending a fracture field - is defined on a colour and says nothing about how much of that
     * colour there is. Teaching nine operators about transparency would risk moving what a worn
     * cobble looks like for the sake of a channel none of them ever touched, so the channel is
     * restored afterwards instead and the colour arithmetic stays byte for byte what it was.
     *
     * <p>
     * Applied to every worn face rather than only to the see-through ones, and that is deliberate. A
     * solid block's texture is already opaque, so this changes nothing for it; and the solid pass
     * draws with an alpha test and no blending, so even a stray value below full is drawn exactly as
     * it is drawn today. What a face is drawn THROUGH is settled by which stand-in is painted over
     * it, not by what its pixels happen to carry - which is the only way it can be settled, because
     * one sprite set serves every block reporting the same face of the same family.
     */
    public static int[] withSourceAlpha(int[] composed, int[] source) {
        if (composed == null || source == null || composed.length != source.length) return composed;
        for (int i = 0; i < composed.length; i++) {
            composed[i] = (source[i] & 0xFF000000) | (composed[i] & 0xFFFFFF);
        }
        return composed;
    }

    // ------------------------------------------------------------------
    // Grass side wear
    // ------------------------------------------------------------------

    /**
     * Thins a grey grass-side overlay so its hanging fringe recedes upward as the ground wears.
     *
     * <p>
     * The overlay is the separately tinted strip the renderer lays over a grass block's sides -
     * mostly transparent, with an opaque band a few pixels deep along the top that gives the side
     * its green fringe. Coverage is measured against that band alone, not the whole face, so a
     * coverage of a half leaves half the fringe rather than half of a mostly-empty texture.
     *
     * <p>
     * The band frays from the bottom up: the dangling lower pixels of the fringe go first and the
     * topmost row survives longest, which reads as grass dying back to its roots rather than the
     * whole strip fading at once. A stable per-pixel jitter ragged the edge so it does not recede
     * as a straight line. Nothing is recoloured - a pixel is either the overlay's own colour or
     * cleared to nothing - because the render pass tints what survives.
     *
     * @param overlay  the grey overlay's own pixels, ARGB, its transparent parts already clear
     * @param size     edge length of the square
     * @param coverage fraction of the original fringe to keep, 1 leaving it whole and 0 clearing it
     * @param seed     varies the frayed edge between rotations
     */
    public static int[] applyFringe(int[] overlay, int size, float coverage, int seed) {
        int[] out = new int[size * size];
        if (overlay == null) return out;
        if (coverage >= 1f) {
            System.arraycopy(overlay, 0, out, 0, Math.min(overlay.length, out.length));
            return out;
        }

        // The foot of the fringe, so the upward bias is measured from the band's own bottom edge.
        int bandBottom = 0;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (((overlay[y * size + x] >>> 24) & 0xFF) >= 16 && y > bandBottom) bandBottom = y;
            }
        }

        float[] order = new float[size * size];
        int opaque = 0;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int i = y * size + x;
                int alpha = (overlay[i] >>> 24) & 0xFF;
                if (alpha < 16) {
                    order[i] = Float.NEGATIVE_INFINITY; // no fringe here to begin with
                    continue;
                }
                // Bottom of the band first, the top row last, with a ragged frazzle from a stable
                // jitter that can just cross a row so the receding edge is not a straight line.
                order[i] = (bandBottom - y) * 3f + (alpha / 255f) * 1.5f + hash(x, y, seed) * 2.4f;
                opaque++;
            }
        }
        if (opaque == 0) return out;

        // Ranked against the fringe alone, so coverage is a fraction of the band and not the face.
        float[] bandOrders = new float[opaque];
        int at = 0;
        for (int i = 0; i < order.length; i++) {
            if (order[i] != Float.NEGATIVE_INFINITY) bandOrders[at++] = order[i];
        }
        float cut = coverageThreshold(bandOrders, coverage);

        for (int i = 0; i < out.length && i < overlay.length; i++) {
            boolean keep = order[i] != Float.NEGATIVE_INFINITY && order[i] >= cut;
            // Cleared to alpha zero where the fringe has gone; the colour is left under it so the
            // atlas's mipmaps have something to average rather than bleeding black into the edge.
            out[i] = keep ? overlay[i] : (overlay[i] & 0x00FFFFFF);
        }
        return out;
    }

    /**
     * Replaces a grass side's green-tinted or cut-away top edge with the earth just below it.
     *
     * <p>
     * A grass block's side is dirt for most of its height with a few rows along the top baked a
     * dull green, and a modded loamy grass cuts those same rows away to a hole for a pass
     * underneath to show through. Neither belongs on a worn side meant to read as bare earth, and
     * the green is untinted so it never fades with the biome the way the fringe over it does. Each
     * such pixel is filled down from the first clean, opaque, un-green row beneath it in its own
     * column, which leaves a uniform earth wall with no green and no hole - and the block's own
     * loam, not vanilla soil, because the fill comes from the texture itself.
     */
    public static int[] degreenTopEdge(int[] side, int size) {
        if (side == null) return null;
        int[] out = side.clone();
        for (int x = 0; x < size; x++) {
            int cleanRow = -1;
            for (int y = 0; y < size; y++) {
                if (isCleanEarth(side[y * size + x])) {
                    cleanRow = y;
                    break;
                }
            }
            if (cleanRow <= 0) continue; // top row already earth, or the column is earth all down
            // A grass cap is a few rows deep. Anything deeper than that is not a cap over earth -
            // it is a texture that is coloured most of the way down, and filling it from the first
            // row that happens to read as earth would smear that row across half the face.
            if (cleanRow > Math.max(1, size / 4)) continue;
            int fill = 0xFF000000 | (side[cleanRow * size + x] & 0xFFFFFF);
            for (int y = 0; y < cleanRow; y++) {
                if (!isCleanEarth(side[y * size + x])) out[y * size + x] = fill;
            }
        }
        return out;
    }

    /** Opaque and not green-biased: an earth pixel rather than the grassy top edge or a hole. */
    private static boolean isCleanEarth(int pixel) {
        if (((pixel >>> 24) & 0xFF) < 250) return false; // a cut-away hole
        int r = (pixel >>> 16) & 0xFF;
        int g = (pixel >>> 8) & 0xFF;
        int b = pixel & 0xFF;
        return !(g > r + 12 && g > b + 12);
    }

    // ------------------------------------------------------------------
    // Stage curves
    // ------------------------------------------------------------------

    /**
     * Interpolates a value along an authored curve for stage {@code index} of {@code count}.
     * With {@code count} equal to the curve's length this returns the authored values exactly.
     */
    public static float alongCurve(float[] curve, int index, int count) {
        if (curve.length == 0) return 0f;
        if (count <= 1) return curve[curve.length - 1];
        float position = (index / (float) (count - 1)) * (curve.length - 1);
        int low = (int) Math.floor(position);
        int high = Math.min(low + 1, curve.length - 1);
        float blend = position - low;
        return curve[low] * (1f - blend) + curve[high] * blend;
    }
}
