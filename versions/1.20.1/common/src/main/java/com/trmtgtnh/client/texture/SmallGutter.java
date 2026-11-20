package com.trmtgtnh.client.texture;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * This mod's own sprites under Angelica: a small gutter, priced as it really is, and each distinct picture stored
 * once (0.9.229, public issue #3; Xep, 2026-11-15: "Switch, test borders", over a separate worn atlas, because map
 * mods and shader packs must keep working).
 *
 * <p>
 * Angelica 2.2.43 reserves {@code 1 << mipmapLevels} pixels round every block-atlas sprite, sixteen at the game's four
 * levels, so a sixteen-pixel wear picture took a 48-pixel holder and GT New Horizons 2.9.0's block atlas reached
 * 32768. The gutter is held per sprite (SpriteExtension.angelica$setGutterWidth), and everything that reads it reads
 * that sprite's own value, read from Angelica 2.2.43's classes on 2026-11-15: the stitcher's holder
 * (MixinStitcherHolder,
 * width and height plus twice the gutter), the UV inset (the mipmaps MixinTextureAtlasSprite moves initSprite's
 * origin in by it), the padded upload (MixinTextureMap.angelica$uploadPaddedSprite, and AtlasAssembler.collect for the
 * batched upload, in its Java 17 and 21 forms alike) and an animated sprite's upload. So a smaller value for this
 * mod's sprites alone is drawn as consistently as Angelica's own.
 *
 * <p>
 * What a small gutter costs is the coarse mip levels. The padded upload gives level {@code l} a gutter of
 * {@code gutter >> l}, so a 4-pixel gutter has none from level 3 on, and lwjgl3ify's STB packer places each holder at
 * any pixel, so a 24-pixel holder lands on whole pixels of levels 0 to 3 only ({@link #alignedLevels}). Distant ground
 * at the coarsest level can therefore take color from the sprite beside it, which is what client.angelicaWearGutter's
 * candidates are photographed for. A sprite of another mod's beside one of these keeps its own sixteen-pixel gutter;
 * where this mod's narrower holders move it off the sixteen-pixel grid, its coarsest levels shift by part of a pixel
 * within that gutter.
 */
public final class SmallGutter {

    private SmallGutter() {}

    /** client.angelicaWearGutter's "auto": the largest gutter at which everything asked for fits. */
    public static final int AUTO = -1;

    /**
     * client.angelicaWearGutter's "atLeast2" (Xep, 2026-11-18, "Drop-down + minimum (Recommended)"): auto's choice,
     * never below {@link #FLOOR} pixels while that keeps every face auto would.
     */
    public static final int AT_LEAST_TWO = -2;

    /** The narrowest gutter "atLeast2" takes while it keeps every face. */
    public static final int FLOOR = 2;

    /**
     * The values the settings screen offers, in its order: Automatic, Automatic at least 2 pixels, and a fixed 1, 2,
     * 4, 8 or 16. The file goes on reading "0" and any other number as it did before, held to 0 to 16.
     */
    public static final String[] SETTINGS = { "auto", "atLeast2", "1", "2", "4", "8", "16" };

    /** The smaller gutters auto tries after Angelica's own, largest first. */
    private static final int[] SMALLER = { 8, 4, 2, 1, 0 };

    /**
     * The share of the target square the holders may be predicted to fill: nine tenths. lwjgl3ify's STB packer
     * packed every replay of GT New Horizons 2.9.0's holders at its starting size, the fullest at 97.4% of it
     * (SmallGutterTest), so nine tenths leaves the packer room for a pack whose sizes are less even.
     */
    static final int FILL_TENTHS = 9;

    /**
     * lwjgl3ify's STB stitcher's share of the square in thousandths, for a plan priced in exact pixels (0.9.229,
     * glow-pixels): nine tenths, as above. GT New Horizons 2.9.0's holders with the glow overlays, reshaped to a
     * 4-pixel gutter (192.5 M pixels, 71.7% of a 16384 square), packed there too, at 2 and 8 pixels alike.
     */
    public static final int STB_FILL_PER_MILLE = FILL_TENTHS * 100;

    /**
     * Vanilla 1.7.10's Stitcher's share of the square in thousandths: 45%. It never grows the atlas taller than half
     * its
     * largest side: to grow the height it checks the width plus the holder against the largest width, which it has
     * already reached, so a 16384 square holds at most 16384 by 8192 (read from the patched game's Stitcher, and run on
     * the real class: 3,000 holders of 256 pixels stopped at 2,048 in 16384 by 8192). Replayed over GT New Horizons
     * 2.9.0's holders at gutters 0, 1, 2, 4 and 8, at one to four rotations, with and without the glow overlays, and
     * over three earlier captures, every pack stopped between 49.69% and 50.00% of the square (VanillaStitchReplay);
     * 45% is nine tenths of what it can reach, as STB's is.
     */
    public static final int VANILLA_FILL_PER_MILLE = 450;

    /**
     * The setting read: {@link #AUTO}, {@link #AT_LEAST_TWO}, or a fixed gutter held to 0 to 16. Anything else is
     * auto.
     */
    public static int fixedGutter(String setting) {
        if (setting == null) return AUTO;
        if (SETTINGS[1].equals(setting.trim())) return AT_LEAST_TWO;
        try {
            // "auto" is not a number, and comes out as auto with everything else that is not.
            return Math.max(0, Math.min(16, Integer.parseInt(setting.trim())));
        } catch (NumberFormatException notANumber) {
            return AUTO;
        }
    }

    /**
     * The gutters to try, largest first (Xep, 2026-11-15: "higher where it can be, and lower as it needs to be"):
     * under auto and "atLeast2", Angelica's own and then 8, 4, 2, 1 and 0 below it; a fixed setting first, never
     * above Angelica's, then those below it, which are taken only where it cannot keep every face (2026-11-18,
     * {@link #choose(AtlasPlan[], long[], int, int, int)}); none at a fixed 0, or without Angelica's gutter.
     */
    public static int[] candidates(int angelicaGutter, int fixed) {
        if (angelicaGutter <= 0) return new int[] { 0 };
        int widest = fixed == AUTO || fixed == AT_LEAST_TWO ? angelicaGutter
            : Math.max(0, Math.min(angelicaGutter, fixed));
        int[] out = new int[SMALLER.length + 1];
        int count = 0;
        out[count++] = widest;
        for (int gutter : SMALLER) {
            if (gutter < widest) out[count++] = gutter;
        }
        return Arrays.copyOf(out, count);
    }

    /**
     * The narrowest candidate the setting chooses down to before anything narrower is a fallback: the last under
     * auto, the narrowest of 2 pixels or more under "atLeast2" (the last where none is), the first for a fixed one.
     */
    public static int floorIndex(int[] gutters, int fixed) {
        int last = gutters.length - 1;
        if (fixed == AUTO) return last;
        if (fixed != AT_LEAST_TWO) return 0;
        int floor = -1;
        for (int i = 0; i < gutters.length; i++) {
            if (gutters[i] >= FLOOR) floor = i;
        }
        return floor < 0 ? last : floor;
    }

    /** Whether the gutter taken is narrower than the setting's floor: a fallback, which the gutter line says. */
    public static boolean fellBack(int at, int floor) {
        return at > floor;
    }

    /**
     * The atlas side lwjgl3ify's StbStitcher starts at for this much holder area: the power of two above its square
     * root. In every replay it packed there (SmallGutterTest), so this is the side the stitch will come to.
     */
    public static long predictedSide(long pixels) {
        long side = (long) Math.ceil(Math.sqrt((double) Math.max(1L, pixels)));
        while (side * side < pixels) side++;
        long power = 1L;
        while (power < side) power <<= 1;
        return power;
    }

    /**
     * This mod's holder pixels for a whole plan, exactly: the surface sets', the family fallbacks' and the fringe's
     * pixels for one gradation at one rotation, at every gradation and rotation, and the side sprites once.
     */
    public static long ownPixels(long setPixels, long fallbackPixels, long fringePixels, long sidePixels,
        int gradations, int rotations) {
        return ownPixels(setPixels, 0L, fallbackPixels, 0L, fringePixels, sidePixels, gradations, rotations);
    }

    /**
     * The same, where client.wearTurns' turn only draws part of the sets and the fallbacks with one pattern rather than
     * one per rotation (0.9.229): {@code setOnePixels} of the set pixels and {@code fallbackOnePixels} of the fallback
     * pixels are priced at one rotation.
     */
    public static long ownPixels(long setPixels, long setOnePixels, long fallbackPixels, long fallbackOnePixels,
        long fringePixels, long sidePixels, int gradations, int rotations) {
        long setOne = Math.max(0L, Math.min(setPixels, setOnePixels));
        long fallbackOne = Math.max(0L, Math.min(fallbackPixels, fallbackOnePixels));
        long everyTurn = setPixels - setOne + fallbackPixels - fallbackOne + fringePixels;
        return (everyTurn * Math.max(0, rotations) + setOne + fallbackOne) * Math.max(0, gradations) + sidePixels;
    }

    /** Whether this much holder area fits the target square inside the margin. */
    public static boolean fits(long pixels, int target) {
        return pixels * 10L <= (long) target * target * FILL_TENTHS;
    }

    /**
     * The same, inside a measured stitcher's share of the square in thousandths (StitcherKind); nought, a stitcher
     * nobody measured, is nine tenths as before.
     */
    public static boolean fits(long pixels, int target, int fillPerMille) {
        if (fillPerMille <= 0) return fits(pixels, target);
        return pixels * 1000L <= (long) target * target * fillPerMille;
    }

    /** The share fits() holds the holders to, in thousandths. */
    public static int fillPerMille(int fillPerMille) {
        return fillPerMille > 0 ? fillPerMille : FILL_TENTHS * 100;
    }

    /**
     * Which of the candidates auto takes, given each one's plan as AtlasPlan.planForBorderBudget makes it at that
     * gutter (null where not even the shared tier fits) and its holder pixels predicted at that plan, the candidates
     * largest first: the widest whose holders fit inside the margin and whose plan keeps as much as the narrowest's;
     * where none does, the narrowest.
     *
     * <p>
     * Judged against the narrowest rather than against everything asked, because some limits are the same at every
     * gutter. On GT New Horizons 2.9.0 (2026-11-17) the glow overlays took the sprites past client.maxWearSprites,
     * which counts sprites and not pixels: no gutter kept all 80 gradations, so auto fell through to 0 pixels with
     * the atlas 42% full, though 79 gradations at four rotations were the plan at the wider gutters too
     * (GutterChoiceTest).
     */
    public static int choose(AtlasPlan[] plans, long[] pixels, int target) {
        return choose(plans, pixels, target, 0);
    }

    /** The same, the holders held to a measured stitcher's share of the square in thousandths (fits). */
    public static int choose(AtlasPlan[] plans, long[] pixels, int target, int fillPerMille) {
        return choose(plans, pixels, target, fillPerMille, plans.length - 1);
    }

    /**
     * The same, chosen down to the setting's floor ({@link #floorIndex}) as auto chooses down to the narrowest: the
     * widest above it whose plan keeps as much as the floor's, or the floor itself where its plan keeps every face the
     * narrowest's does and its holders fit (2026-11-18). There patterns have given way first and then stages, in the
     * plan's own order. Where the floor's plan cannot keep those faces even at one pattern and the fewest stages, or
     * its holders do not fit, the widest below it that can, and the narrowest where none can.
     */
    public static int choose(AtlasPlan[] plans, long[] pixels, int target, int fillPerMille, int floor) {
        int narrowest = plans.length - 1;
        int at = Math.max(0, Math.min(narrowest, floor));
        boolean floorKeeps = at == narrowest
            || keepsEveryFace(plans[at], plans[narrowest]) && fits(pixels[at], target, fillPerMille);
        if (floorKeeps) {
            for (int i = 0; i < at; i++) {
                if (keepsAsMuch(plans[i], plans[at]) && fits(pixels[i], target, fillPerMille)) return i;
            }
            return at;
        }
        for (int i = at + 1; i < narrowest; i++) {
            if (keepsEveryFace(plans[i], plans[narrowest]) && fits(pixels[i], target, fillPerMille)) return i;
        }
        return Math.max(0, narrowest);
    }

    /** Whether a plan keeps as many faces in their own colors as the narrowest's, at whatever patterns and stages. */
    public static boolean keepsEveryFace(AtlasPlan plan, AtlasPlan narrowest) {
        if (plan == null) return false;
        return narrowest == null || plan.surfaceSetsKept >= narrowest.surfaceSetsKept;
    }

    /**
     * Whether a wider gutter's plan keeps as much as the narrowest's: as many pictures in each ramp, as many
     * rotations, and as many faces in their own colors, of which one shared look per family keeps none.
     */
    public static boolean keepsAsMuch(AtlasPlan plan, AtlasPlan narrowest) {
        if (plan == null) return false;
        if (narrowest == null) return true;
        return plan.gradations >= narrowest.gradations && plan.wish.rotations >= narrowest.wish.rotations
            && plan.surfaceSetsKept >= narrowest.surfaceSetsKept;
    }

    /** Whether a plan keeps everything asked: every gradation, rotation and face, under the face ceiling asked. */
    public static boolean keepsEverything(AtlasPlan plan, int gradations, int rotations, int surfaceCeiling) {
        return plan != null && plan.gradations >= gradations
            && plan.wish.rotations >= rotations
            && plan.surfaceSetsKept == plan.surfaceSetsConsidered
            && plan.wish.surfaceCeiling >= surfaceCeiling;
    }

    /** The square the block atlas is held to: the card's largest, never past 16384, 8192 where the card did not say. */
    public static int target(int glMaximum) {
        return glMaximum <= 0 ? AtlasPlan.LARGEST_SIDE : Math.min(glMaximum, AtlasPlan.LARGER_SIDE);
    }

    /** The gutter this mod's sprites take: the one asked for, never more than Angelica's, none without Angelica's. */
    public static int ownGutter(int angelicaGutter, int asked) {
        if (angelicaGutter <= 0) return 0;
        return Math.max(0, Math.min(angelicaGutter, asked));
    }

    /** Pixels one holder takes: the edge as the stitcher rounds it to its mip stride, plus the gutter each side. */
    public static long holderPixels(int edge, int mipmapLevels, int gutter) {
        long side = AtlasPlan.mipmapDimension(Math.max(1, edge), mipmapLevels) + 2L * Math.max(0, gutter);
        return side * side;
    }

    /** Whole sixteen-pixel slots for a summed area, rounded up once over the sum rather than once a holder. */
    public static long slots(long pixels) {
        long cell = (long) AtlasPlan.CELL * AtlasPlan.CELL;
        return (Math.max(0L, pixels) + cell - 1L) / cell;
    }

    /** The mip levels whose pixels a holder of this edge starts and ends on, wherever on the grid it is placed. */
    public static int alignedLevels(int holderEdge) {
        return holderEdge <= 0 ? 0 : Integer.numberOfTrailingZeros(holderEdge);
    }

    /**
     * For each picture, the first eligible picture with the same size and pixels, or itself. A picture that is not
     * eligible - one whose layer moves, which is redrawn in its own place each frame, or one that could not be
     * built - is its own, and so is one with no pixels.
     */
    public static int[] canonical(int[][] pictures, int[] widths, int[] heights, boolean[] eligible) {
        int[] canonical = new int[pictures.length];
        Map<Key, Integer> first = new HashMap<Key, Integer>();
        for (int i = 0; i < pictures.length; i++) {
            canonical[i] = i;
            if (!eligible[i] || pictures[i] == null) continue;
            Key key = new Key(pictures[i], widths[i], heights[i]);
            Integer seen = first.get(key);
            if (seen == null) {
                first.put(key, Integer.valueOf(i));
            } else {
                canonical[i] = seen.intValue();
            }
        }
        return canonical;
    }

    /** How many pictures share another's place. */
    public static int shared(int[] canonical) {
        int shared = 0;
        for (int i = 0; i < canonical.length; i++) {
            if (canonical[i] != i) shared++;
        }
        return shared;
    }

    private static final class Key {

        final int[] pixels;
        final int width;
        final int height;
        final int hash;

        Key(int[] pixels, int width, int height) {
            this.pixels = pixels;
            this.width = width;
            this.height = height;
            this.hash = (Arrays.hashCode(pixels) * 31 + width) * 31 + height;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Key)) return false;
            Key that = (Key) other;
            return width == that.width && height == that.height
                && hash == that.hash
                && Arrays.equals(pixels, that.pixels);
        }
    }
}
