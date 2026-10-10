package com.trmtgtnh.client.texture;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

import com.trmtgtnh.Trmt;

/**
 * Reads milkucha's authored wear art and hands it to {@link WearCompositor} as patterns that
 * can be re-applied to any block.
 *
 * <p>
 * Upstream ships one finished texture per stage, drawn against vanilla grass, dirt and sand.
 * That is exactly right for vanilla and exactly wrong for a pack with a dozen kinds of each,
 * so the art is decomposed rather than used directly: the grass art becomes a coverage
 * sequence, and the opaque dirt and sand art becomes a per-pixel ratio against the vanilla
 * texture it was drawn on.
 *
 * <p>
 * Only the first of those two is wired today. The eleven looks a family can be set to all work off
 * the block's own pixels - see {@link WearSprite#wearPass} and {@link WearSprite#grassPass} - and
 * of the art only the coverage sequence is still read, by the look that takes a cover off. The
 * ratio-transfer half is kept because it is the thing to reach for if a look ever wants authored
 * art again, and because it is what proves the decomposition works; nothing in the live pipeline
 * calls it.
 *
 * <p>
 * Everything is read through the resource manager, so a resource pack that retextures dirt
 * changes what worn ground looks like, and no Mojang art is ever redistributed.
 */
public final class WearPatterns {

    /** Authored art resolution. Patterns are scaled from here to whatever the block uses. */
    public static final int ART_SIZE = 16;

    /**
     * How much grass survives, as a curve the stage count is interpolated along.
     *
     * <p>
     * Upstream's own five stages ran 0.91, 0.76, 0.63, 0.41, 0.23. Two things were wrong with
     * borrowing them directly. The fall is steepest at the end, so most of the turf went in the
     * last couple of gradations and the earlier ones barely read as wear at all. And it stopped
     * at 0.23 - nearly a quarter of the face still green - immediately before the chain hands
     * over to bare earth, so the handover was a step rather than an arrival.
     *
     * <p>
     * This curve holds more grass through the early run, where the wear anyone actually watches
     * happens, gives up the middle steadily rather than all at once, and finishes close enough
     * to bare that the first earth gradation is the next thing you would have expected to see.
     */
    private static final float[] GRASS_COVERAGE = { 0.95f, 0.88f, 0.78f, 0.64f, 0.47f, 0.29f, 0.12f };

    /**
     * How much of a grass block's side fringe survives, as a curve the side's wear is sampled
     * along.
     *
     * <p>
     * Steeper than the top's own grass curve and reaching nothing, because the fringe is the one
     * green thing left on a side that is otherwise already earth: a residue of it hanging on while
     * the top has gone bald is what reads as wrong, so it is made to clear before the block's
     * appearance hands over to bare earth rather than after. The side's wear already lags the
     * floor by {@code sideWearFraction}, which is what keeps a barely-trodden block's fringe from
     * vanishing the moment a single footstep lands.
     */
    private static final float[] FRINGE_COVERAGE = { 1.0f, 0.9f, 0.72f, 0.52f, 0.34f, 0.18f, 0.06f, 0.0f };

    private static final int GRASS_ART_STAGES = 5;
    private static final int DIRT_ART_STAGES = 3;
    private static final int SAND_ART_STAGES = 5;

    public static final String PATTERN_GRASS = "grass";
    public static final String PATTERN_DIRT = "dirt";
    public static final String PATTERN_SAND = "sand";
    public static final String PATTERN_POLISH = "polish";

    /** Reuses the order cache; rotations are small non-negative ints, so this cannot collide. */
    private static final Integer POLISH_CACHE_KEY = Integer.valueOf(-1);

    /**
     * Decoded images and derived patterns, held only for the duration of one stitch.
     *
     * <p>
     * Several hundred sprites are generated per stitch and they all draw on the same handful
     * of PNGs, so without this the atlas would decode the same grass art a couple of thousand
     * times. Cleared when a stitch begins, and single-threaded because the atlas builds
     * sprites on the client thread.
     */
    private static final Map<String, BufferedImage> IMAGE_CACHE = new HashMap<String, BufferedImage>();
    private static final Map<Integer, float[]> ORDER_CACHE = new HashMap<Integer, float[]>();
    private static final Map<String, float[]> MODULATION_CACHE = new HashMap<String, float[]>();

    private WearPatterns() {}

    /** Drops everything cached for a stitch. */
    public static void clearCaches() {
        IMAGE_CACHE.clear();
        ORDER_CACHE.clear();
        MODULATION_CACHE.clear();
    }

    // ------------------------------------------------------------------
    // Patterns
    // ------------------------------------------------------------------

    /** Per-pixel wear order for one rotation of the grass art: lower values wear away first. */
    /**
     * Fills the wear-order cache for every rotation, so nothing has to fill it later.
     *
     * <p>
     * Called on the render thread before any sprite is composed, because the map below is a plain
     * one and the composing now happens on several threads at once. Priming rather than locking:
     * there are four entries, they never change within a stitch, and a cache that is finished being
     * written before anybody reads it needs no synchronisation at all - the submit that hands work
     * to a worker publishes everything written before it.
     */
    public static void primeOrders(IResourceManager resources, int rotations) {
        for (int rotation = 0; rotation < Math.max(1, rotations); rotation++) {
            grassWearOrder(resources, rotation);
        }
    }

    /**
     * The primed orders, one per rotation, for handing to a worker.
     *
     * <p>
     * Read out here rather than looked up inside the composing, so that what crosses to another
     * thread is an array rather than a map somebody might extend.
     */
    public static float[][] orders(int rotations) {
        int turns = Math.max(1, rotations);
        float[][] out = new float[turns][];
        for (int rotation = 0; rotation < turns; rotation++) {
            out[rotation] = ORDER_CACHE.get(Integer.valueOf(rotation));
        }
        return out;
    }

    /** Whether the missing-art warning has been given this session. Once is enough to find it. */
    private static volatile boolean artMissingSaid;

    public static float[] grassWearOrder(IResourceManager resources, int rotation) {
        Integer cacheKey = Integer.valueOf(rotation);
        float[] cached = ORDER_CACHE.get(cacheKey);
        if (cached != null) return cached;

        int[][] masks = new int[GRASS_ART_STAGES][];
        for (int stage = 0; stage < GRASS_ART_STAGES; stage++) {
            BufferedImage art = read(resources, "grass_top_" + stage + "_r" + rotation);
            if (art == null) continue;
            int[] alpha = new int[ART_SIZE * ART_SIZE];
            for (int y = 0; y < ART_SIZE; y++) {
                for (int x = 0; x < ART_SIZE; x++) {
                    alpha[y * ART_SIZE + x] = (sample(art, x, y, ART_SIZE) >>> 24) & 0xFF;
                }
            }
            masks[stage] = alpha;
        }

        int missing = 0;
        for (int[] mask : masks) {
            if (mask == null) missing++;
        }
        if (missing > 0 && !artMissingSaid) {
            artMissingSaid = true;
            Trmt.LOG.warn(
                "{} of the {} grass wear pattern masks for turn {} could not be read (trmtgtnh:textures/blocks/grass_top_<stage>_r<turn>.png), so worn grass is drawn with a random pattern in their place; a jar or resource pack without them will look like this",
                Integer.valueOf(missing),
                Integer.valueOf(GRASS_ART_STAGES),
                Integer.valueOf(rotation));
        }

        float[] order = WearCompositor.wearOrder(masks, ART_SIZE, rotation);
        ORDER_CACHE.put(cacheKey, order);
        return order;
    }

    /** Target grass coverage for a stage, interpolated along upstream's curve. */
    public static float grassCoverage(int stage, int count) {
        return WearCompositor.alongCurve(GRASS_COVERAGE, stage, count);
    }

    /** How much of a grass side's fringe survives at a stage, interpolated along its own curve. */
    public static float fringeCoverage(int stage, int count) {
        return WearCompositor.alongCurve(FRINGE_COVERAGE, stage, count);
    }

    /**
     * Per-pixel channel ratios describing how one authored stage wore the surface it was drawn
     * against, or null when the art or its reference texture is unreadable.
     *
     * @param pattern {@code dirt} or {@code sand}, choosing both art set and reference texture
     * @param stage   index into that pattern's authored stages; below zero means "unworn"
     */
    public static float[] modulation(IResourceManager resources, String pattern, int stage, int rotation) {
        if (stage < 0) return null; // unworn: identity, so the caller skips the multiply

        String cacheKey = pattern + stage + "_" + rotation;
        if (MODULATION_CACHE.containsKey(cacheKey)) return MODULATION_CACHE.get(cacheKey);

        boolean sand = PATTERN_SAND.equals(pattern);
        int stages = sand ? SAND_ART_STAGES : DIRT_ART_STAGES;
        if (stage >= stages) stage = stages - 1;

        BufferedImage art = read(resources, (sand ? "sand_top_" : "dirt_all_") + stage + "_r" + rotation);
        BufferedImage reference = readVanilla(resources, sand ? "sand" : "dirt");
        if (art == null || reference == null) {
            MODULATION_CACHE.put(cacheKey, null);
            return null;
        }

        float[] ratio = WearCompositor
            .modulationRatio(toPixels(art, ART_SIZE), toPixels(reference, ART_SIZE), ART_SIZE);
        MODULATION_CACHE.put(cacheKey, ratio);
        return ratio;
    }

    /** How many stages a pattern was drawn with, for interpolating an arbitrary stage count. */
    public static int artStageCount(String pattern) {
        if (PATTERN_SAND.equals(pattern) || PATTERN_POLISH.equals(pattern)) return SAND_ART_STAGES;
        if (PATTERN_GRASS.equals(pattern)) return GRASS_ART_STAGES;
        return DIRT_ART_STAGES;
    }

    /**
     * How much each authored sand stage flattened and darkened the sand it was drawn on.
     *
     * <p>
     * This is the wear curve for surfaces nobody drew art for. Sand is the right source for it
     * because its stages are a genuine modulation of the vanilla texture, going from speckled
     * to smooth and progressively darker; the dirt art, by contrast, is a rearrangement at
     * almost identical brightness and says nothing transferable about wearing down.
     *
     * @return one {@code {contrast, luma}} pair per authored stage
     */
    public static float[][] polishCurve(IResourceManager resources) {
        float[] cached = ORDER_CACHE.get(POLISH_CACHE_KEY);
        if (cached != null) return unflatten(cached);

        // Authored rather than measured. This curve used to be read off the sand artwork, on the
        // reasoning that whatever wear looks like on sand should transfer. It does not: the
        // artwork's total contrast rises over its first four stages, because it trades fine
        // speckle for broader blotches, so stone came out noisier than clean stone for most of
        // its chain and only settled at the very end. Stone that is being walked smooth should
        // get flatter and darker at every step, without exception, and it should be visible from
        // the first one - a couple of percent, which is what the measured curve gave, is not.
        float[] flat = { 0.92f, 0.93f, 0.82f, 0.86f, 0.70f, 0.79f, 0.58f, 0.72f, 0.45f, 0.66f };
        ORDER_CACHE.put(POLISH_CACHE_KEY, flat);
        return unflatten(flat);
    }

    private static float[][] unflatten(float[] flat) {
        float[][] out = new float[flat.length / 2][];
        for (int i = 0; i < out.length; i++) {
            out[i] = new float[] { flat[i * 2], flat[i * 2 + 1] };
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Resource access
    // ------------------------------------------------------------------

    private static BufferedImage read(IResourceManager resources, String name) {
        return readLocation(resources, new ResourceLocation(Trmt.MODID, "textures/blocks/" + name + ".png"));
    }

    private static BufferedImage readVanilla(IResourceManager resources, String name) {
        return readLocation(resources, new ResourceLocation("minecraft", "textures/blocks/" + name + ".png"));
    }

    /**
     * Reads any block texture by its icon name, from the file the block atlas itself loads for that name.
     *
     * <p>
     * This used to split the name at its colon by hand and keep the domain as written. Resource packs only
     * ever hold lower-case domains and the resource manager looks them up by exact case, so a face
     * registered as MyMod:turf_bottom read nothing here while its header was read, and priced, from
     * mymod's file; the sprite then fell back to vanilla dirt at the resource pack's size, far past the
     * price. Reading through {@link #blockTextureFile} keeps the price and the pixels on the same file.
     */
    public static BufferedImage readIcon(IResourceManager resources, String iconName) {
        if (iconName == null) return null;
        ResourceLocation file;
        try {
            file = blockTextureFile(iconName);
        } catch (RuntimeException unnamable) {
            return null;
        }
        return readLocation(resources, file);
    }

    /**
     * The file the block atlas loads for a registered sprite name, built the way 1.12.2's TextureMap builds
     * it: {@code textures/} and the name's own path. A 1.12.2 name carries its folder - {@code
     * minecraft:blocks/dirt} - and is taken as it is. A bare name in the other edition's form - {@code
     * dirt}, which is what FaceRules still hands out - is a block texture and gets the folder added.
     * Shared by readIcon and headerWidth, so that what a sprite is built from and what it is priced from
     * cannot drift apart again.
     */
    static ResourceLocation blockTextureFile(String iconName) {
        ResourceLocation named = new ResourceLocation(iconName);
        String path = named.getPath();
        if (path.indexOf('/') < 0) path = "blocks/" + path;
        return new ResourceLocation(named.getNamespace(), "textures/" + path + ".png");
    }

    private static BufferedImage readLocation(IResourceManager resources, ResourceLocation location) {
        String key = location.toString();
        if (IMAGE_CACHE.containsKey(key)) return IMAGE_CACHE.get(key);
        BufferedImage image = decode(resources, location);
        IMAGE_CACHE.put(key, image);
        return image;
    }

    private static BufferedImage decode(IResourceManager resources, ResourceLocation location) {
        InputStream stream = null;
        try {
            IResource resource = resources.getResource(location);
            stream = resource.getInputStream();
            return ImageIO.read(stream);
        } catch (IOException missing) {
            return null;
        } catch (RuntimeException broken) {
            Trmt.LOG.debug("Could not read {} for wear textures", location, broken);
            return null;
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException ignored) {
                    // The image was either read or it was not; nothing useful to do here.
                }
            }
        }
    }

    /**
     * The widths read from the headers of the block atlas's files this stitch, by the location the atlas loads each
     * from.
     *
     * <p>
     * Kept apart from the caches above, and not dropped by clearCaches, because it is filled before the stitch event
     * that clears those: the measuring injection reads every texture the atlas has gathered, and the planner then
     * prices every face from the same table. Dropped when the stitch reports on itself, and at the start of a stitch
     * the measure never reached.
     */
    private static final Map<String, Integer> HEADER_WIDTHS = new HashMap<String, Integer>();

    /** Drops every width read from a header. */
    public static void clearHeaderWidths() {
        HEADER_WIDTHS.clear();
    }

    /**
     * The width of the texture the block atlas loads for a registered icon name, read from the header of its file
     * without decoding a pixel, or nought where it cannot be read.
     *
     * <p>
     * The location is the one TextureMap loads, from blockTextureFile, which readIcon reads through as well: the
     * atlas lower-cases the domain and takes a name whose colon comes first or second as vanilla's, so a sprite
     * registered under a domain in capitals is loaded, priced and built from the lower-case domain's file. A PNG's
     * width comes from its first twenty-four bytes; anything else is asked of the image reader vanilla would load it
     * with, because vanilla chooses that reader by the file's content rather than its name. Nought covers a file
     * that is missing, one no image reader recognises, and a name whose sprite is read by a loader of its own and
     * has no file; the caller decides what each of those is priced at.
     *
     * <p>
     * Where the texture has a metadata file, the resource manager has already opened it along with the image, and
     * only reading that metadata closes it again, so it is read here for that reason alone. Left open, a pack in a
     * folder would hold a file handle for every animated texture until the collector came round.
     */
    public static int headerWidth(IResourceManager resources, String iconName) {
        if (resources == null || iconName == null) return 0;
        ResourceLocation file;
        try {
            file = blockTextureFile(iconName);
        } catch (RuntimeException unnamable) {
            return 0;
        }
        String key = file.toString();
        Integer known = HEADER_WIDTHS.get(key);
        if (known != null) return known.intValue();
        int width = readHeaderWidth(resources, file);
        HEADER_WIDTHS.put(key, Integer.valueOf(width));
        return width;
    }

    private static int readHeaderWidth(IResourceManager resources, ResourceLocation location) {
        InputStream stream = null;
        try {
            IResource resource = resources.getResource(location);
            stream = resource.getInputStream();
            if (resource.hasMetadata()) {
                try {
                    resource.getMetadata("animation");
                } catch (RuntimeException unparsable) {
                    // Closed either way; a broken metadata file is vanilla's to report when it loads the sprite.
                }
            }
            return AtlasPlan.fileWidth(stream);
        } catch (IOException missing) {
            return 0;
        } catch (RuntimeException broken) {
            return 0;
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException ignored) {
                    // The width was either read or it was not; nothing useful to do here.
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Sampling
    // ------------------------------------------------------------------

    /**
     * How many frames a vertical strip holds.
     *
     * <p>
     * The declared frame height wins where a pack gives one, because a frame is allowed not to be
     * square and a pack using one would otherwise be counted as having several times too many
     * frames. Where nothing is declared a frame is as tall as the image is wide, which is the rule
     * the game falls back on itself.
     */
    public static int frameCount(BufferedImage image, int declaredFrameHeight) {
        if (image == null) return 0;
        int frameHeight = declaredFrameHeight > 0 ? declaredFrameHeight : image.getWidth();
        if (frameHeight <= 0) return 0;
        return Math.max(1, image.getHeight() / frameHeight);
    }

    /**
     * One frame of a vertical strip, flattened to a square of the given edge.
     *
     * <p>
     * Deliberately not {@link #sample}, which clamps height to width so that an animated texture
     * reads as its first frame. That is what every other caller wants; this is the one caller that
     * wants the rest of them.
     */
    public static int[] framePixels(BufferedImage image, int frameIndex, int declaredFrameHeight, int size) {
        if (image == null || size <= 0) return null;
        int frameHeight = declaredFrameHeight > 0 ? declaredFrameHeight : image.getWidth();
        if (frameHeight <= 0) return null;
        int width = image.getWidth();
        int top = frameIndex * frameHeight;
        int[] out = new int[size * size];
        for (int y = 0; y < size; y++) {
            int sy = top + (size == frameHeight ? y : y * frameHeight / size);
            if (sy >= image.getHeight()) sy = image.getHeight() - 1;
            for (int x = 0; x < size; x++) {
                int sx = size == width ? x : x * width / size;
                out[y * size + x] = image.getRGB(Math.min(sx, width - 1), sy);
            }
        }
        return out;
    }

    /** Flattens an image into a square ARGB array of the given edge length. */
    public static int[] toPixels(BufferedImage image, int size) {
        int[] out = new int[size * size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                out[y * size + x] = sample(image, x, y, size);
            }
        }
        return out;
    }

    /**
     * Samples an image at a given output resolution, scaling by nearest neighbour.
     *
     * <p>
     * Height is clamped to width because animated textures are vertical strips of frames; only
     * the first frame is wanted, and a worn path does not need to animate.
     */
    public static int sample(BufferedImage image, int x, int y, int size) {
        int width = image.getWidth();
        int height = Math.min(image.getHeight(), width);
        int sx = size == width ? x : x * width / size;
        int sy = size == height ? y : y * height / size;
        return image.getRGB(Math.min(sx, width - 1), Math.min(sy, height - 1));
    }

    /** Edge length to generate at: the block's own resolution, so HD packs stay sharp. */
    public static int squareSize(BufferedImage image) {
        return image == null ? ART_SIZE : image.getWidth();
    }
}
