package com.trmtgtnh.client.texture;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.config.FamilySettings;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.dataload.BlockLooks;
import com.trmtgtnh.erosion.ErosionChain;
import com.trmtgtnh.surface.SurfaceFamily;
import com.trmtgtnh.surface.SurfaceRegistry;
import com.trmtgtnh.surface.WearScale;

/**
 * Every generated wear texture, and how to find the right one for a position.
 *
 * <p>
 * Two tiers. A small fallback tier, one set per family, built from the vanilla textures the
 * art was drawn against - always present, always correct enough. Then a per-surface tier
 * that gives individual blocks their own color-matched wear, bounded by the room the block
 * atlas actually has - measured at every stitch and priced by {@link AtlasPlan} - and by
 * {@code surfaces.maxTexturedSurfaces} on top of that, so a pack with a hundred kinds of dirt
 * cannot quietly blow up the atlas.
 *
 * <p>
 * A block's own wear is filed under the block object and its metadata, never under its numeric
 * id. Forge renumbers modded blocks whenever a world is opened or a server joined, and a table
 * filed under the ids in force at the stitch kept them for as long as the atlas lasted, which
 * outlives the visit: in a world that numbered two blocks the other way round, each wore the
 * other's pictures, and went on doing so in the next world after a stitch made in that one. The
 * object is the one key no move changes, and a registry name is not even readable while the ids
 * are being moved. The painter still remembers the block it painted over as a number, and that
 * number is turned back into a block under the ids in force before anything here is asked.
 *
 * <p>
 * Lookups happen once per rendered face on Celeritas' worker threads, so everything they read
 * hangs off one holder, built whole at the end of registration and read once per lookup: a flat
 * array of icon references indexed by arithmetic, and filings probed by object identity, whose
 * hash every block already carries. Nothing here allocates or locks after stitching.
 */
public final class WearTextures {

    private static final int FAMILIES = SurfaceFamily.values().length;
    /**
     * How many pictures one surface's run is drawn with: client.wearGradations, held to the counted steps and read
     * from config at each stitch, then settled by the plan, which draws fewer where the atlas has no room for them.
     *
     * <p>
     * Read once when planning begins rather than per sprite, because it sizes the icon table and every index in it;
     * changing it half way through a stitch would scramble the lookup. The figure written here is only what it holds
     * before the first stitch.
     */
    private static int spriteLayers = 8;

    /**
     * How many turns of each wear pattern are built per look: client.wearRotations, one to four, read from config at
     * each stitch. A square's rotation picks one of four, and with fewer built the lookup folds the rest back onto
     * them, so a path laid in a line repeats sooner; each turn costs a whole set of sprites, which is atlas room the
     * plan would otherwise give to more faces worn from their own pixels.
     */
    private static int rotations = 2;

    /**
     * client.wearTurns as this stitch read it ({@link WearTurns}), held for the whole stitch like the rotations,
     * because under turn only it decides how many patterns a set that turns is planned with.
     */
    private static String turnsMode = WearTurns.BOTH;

    /**
     * Which sets this stitch turns, by set index: the family fallbacks at their family's ordinal, then each surface set
     * as the real walk makes it ({@link FaceDirection}, read in {@link FacePricing#turns}). Stitch thread only;
     * published to the lookup as a copy.
     */
    private static final java.util.BitSet TURNED = new java.util.BitSet();

    /** The first {@code FAMILIES} sets are the vanilla-derived fallbacks, one per family. */
    private static final int FIRST_SURFACE_SET = FAMILIES;

    // The table this stitch is filling, like the gradations and rotations above and the fringes
    // below: working state of the stitch thread alone. No lookup reads any of them. A resource
    // reload replaces them while chunk-meshing worker threads are drawing, and those read the
    // holder published once registration is done, so they see either the old table or the whole
    // new one, never a half-filled one, nor one table measured by another stitch's ramp.
    private static IIcon[] icons = new IIcon[0];

    /**
     * The glow overlay of each wear picture this stitch is registering, indexed as {@link #icons} is, or null where the
     * surface's look has no glow (real glow). Every overlay is also in {@link #REGISTERED}, so it is composed with the
     * rest; one that has nothing to light is never offered the atlas.
     */
    private static IIcon[] glowIcons = new IIcon[0];

    private static int setCount = FIRST_SURFACE_SET;

    /**
     * Everything a lookup reads, built whole at the end of registration and never written again.
     *
     * <p>
     * One holder rather than a field apiece, because a lookup that read the table, the ramp, the
     * rotations and the set filing one field at a time could straddle a re-stitch between two of
     * them and index one stitch's table by the other's arithmetic. It reads this once and works
     * off what it read.
     */
    private static final class Lookup {

        static final Lookup EMPTY = new Lookup(
            new IIcon[0],
            new IIcon[0],
            new IIcon[0],
            8,
            2,
            StateFiling.<Block, Integer>empty(),
            StateFiling.<Block, IIcon>empty(),
            StateFiling.<Block, IIcon>empty(),
            null,
            StateFiling.<Block, Boolean>empty(),
            new int[0],
            AtlasPlan.pictureMap(WearScale.COUNTED_STEPS, 8, 8),
            new java.util.BitSet(),
            WearTurns.RESEED);

        final IIcon[] icons;

        /** The glow overlay of each of {@link #icons}, null where it has none (real glow). */
        final IIcon[] glowIcons;

        /** The receding grass-side fringes, indexed {@code stage * rotations + rotation}. */
        final IIcon[] fringes;

        final int layers;

        final int rotations;

        /** Each block state with wear of its own, to the set holding it. */
        final StateFiling<Block, Integer> sets;

        /** Mended side textures, for the covered blocks that draw themselves in more than one pass. */
        final StateFiling<Block, IIcon> mended;

        /** De-greened grass side walls, one per grass block state. */
        final StateFiling<Block, IIcon> walls;

        /** The wall a grass origin with none of its own falls back on, or null when it could not be registered. */
        final IIcon wallFallback;

        /**
         * Every state the three filings above hold, each once, and the id each of its blocks had
         * at the stitch, in the order they were first filed. Read by the line written when the ids
         * move and by nothing else: a block that is filed for its sets and its wall as well is one
         * block that moved, not two, and nothing drawn ever reads these numbers.
         */
        final StateFiling<Block, Boolean> filed;

        final int[] filedNumbers;

        /** The picture each counted stage draws; see AtlasPlan.pictureMap. */
        final int[] stages;

        /** Which sets turn, by set index; see {@link #TURNED}. */
        final java.util.BitSet turned;

        /** client.wearTurns as the stitch read it. */
        final String mode;

        Lookup(IIcon[] icons, IIcon[] glowIcons, IIcon[] fringes, int layers, int rotations,
            StateFiling<Block, Integer> sets, StateFiling<Block, IIcon> mended, StateFiling<Block, IIcon> walls,
            IIcon wallFallback, StateFiling<Block, Boolean> filed, int[] filedNumbers, int[] stages,
            java.util.BitSet turned, String mode) {
            this.icons = icons;
            this.glowIcons = glowIcons;
            this.fringes = fringes;
            this.layers = layers;
            this.rotations = rotations;
            this.sets = sets;
            this.mended = mended;
            this.walls = walls;
            this.wallFallback = wallFallback;
            this.filed = filed;
            this.filedNumbers = filedNumbers;
            this.stages = stages;
            this.turned = turned;
            this.mode = mode;
        }
    }

    private static volatile Lookup lookup = Lookup.EMPTY;

    /** No safe block-atlas plan kept every face; draw each covered block's own icon instead. */
    private static volatile boolean capacityDisabled;

    public static boolean capacityDisabled() {
        return capacityDisabled;
    }

    /**
     * How the ids in force number a block, and which block an id names. The one place this class
     * reads a block id, and only to say in the log whether the filed blocks moved; a lookup keyed
     * by these numbers is exactly what went wrong.
     */
    private static final StateFiling.Numbering<Block> BLOCK_NUMBERING = new StateFiling.Numbering<Block>() {

        @Override
        public int idOf(Block block) {
            return Block.getIdFromBlock(block);
        }

        @Override
        public Block keyAt(int id) {
            return id < 0 ? null : Block.getBlockById(id);
        }
    };

    /** How many blocks the line written when the ids move names by way of example. */
    private static final int AUDIT_EXAMPLES = 5;

    /**
     * How many mended sides and walls have been named this stitch. Their sprite names are numbered
     * within the stitch rather than by the block's id: a name is only ever registered, never looked
     * up again, and an id in it said nothing true once the ids moved.
     */
    private static int sideSpritesNamed;

    private WearTextures() {}

    private static int slot(int set, SurfaceFamily appearance, int stage, int rotation) {
        return slot(set, appearance, stage, rotation, spriteLayers, rotations);
    }

    private static int slot(int set, SurfaceFamily appearance, int stage, int rotation, int layers, int turns) {
        return ((set * FAMILIES + appearance.ordinal()) * layers + stage) * turns + rotation;
    }

    // ------------------------------------------------------------------
    // Registration
    // ------------------------------------------------------------------

    /**
     * Every wear sprite registered this stitch, which the sprite pass builds and the count of pictures behind a moving
     * layer walks.
     */
    private static final List<WearSprite> REGISTERED = new ArrayList<WearSprite>();

    /**
     * The receding grass-side fringes this stitch is registering, indexed
     * {@code stage * rotations + rotation}.
     *
     * <p>
     * Universal, not per-origin: the overlay is grey and the same for every grass block, tinted
     * per biome only at render time. One thinned copy per gradation and rotation is all it needs.
     *
     * <p>
     * Mended sides and de-greened walls have no field here. Mended sides serve only the handful of
     * covered blocks that draw themselves in more than one pass and cut their own side away so an
     * earlier pass shows through (see {@link WearSprite}'s mendSide); walls are the block's own side
     * with its green top edge and cut-away holes filled down to earth, so a worn grass block's
     * flanks read as soil under a receding fringe, and cover every grass block. Both are filed by
     * block while registering and reach lookups only through the holder.
     */
    private static IIcon[] overlaySprites = new IIcon[0];

    /**
     * How many sprites are composed before any of them are installed.
     *
     * <p>
     * Not a bound on the pass's peak memory, and not a tuning knob. Install keeps the composed picture as its
     * sprite's frame, and every installed picture stays with its sprite, mipmaps and all, until the atlas uploads it
     * after the stitch, so by the end of the pass every wear picture in the atlas is held at once whether they were
     * installed a batch at a time or all together. A batch bounds only how far installing falls behind composing: at
     * most four thousand-odd finished pictures wait for their install, four megabytes at sixteen pixels, and a batch
     * is still far more work than enough to keep every thread busy. Until 0.9.212 each sprite also held the picture
     * load had composed for it until its replacement was installed, so composing the whole atlas before installing
     * any of it held a second full set beside the first, at eighty gradations the better part of two hundred
     * megabytes; that is what this bound was first written against.
     */
    private static final int COMPOSE_BATCH = 4096;

    /** What the pass has to say afterwards, gathered on the render thread only. */
    static final class Tally {

        int built; // installed
        int guessed; // installed and improvised
        int failed; // compose returned null, or install refused
        int unreadable; // sprites whose source could not be read
        int surfaces; // harvests made
        int scaled; // surfaces whose Source.scaled is true
        int resized; // sprites whose composed picture was not getIconWidth() squared
        final Set<String> improvised = new java.util.TreeSet<String>();
        final Set<String> scaledNames = new java.util.TreeSet<String>();
        final Set<String> resizedNames = new java.util.TreeSet<String>();
    }

    /**
     * Composes every wear sprite, once, across threads, and installs it.
     *
     * <p>
     * Run after loading and before stitching, which is the one window that works. Sprites are loaded in the order a
     * hash map happens to iterate, so during loading roughly half of them have their pixels and the rest do not; and
     * immediately after each sprite is uploaded its pixels are thrown away. Between those two, everything is loaded
     * and nothing is discarded yet. A wear sprite's own load reads nothing and composes nothing: it takes the edge its
     * plan priced as its size, which is all the stitcher needs of it, and this is the only place its picture is made.
     *
     * <p>
     * Three phases, and the split between them is the whole of what makes threading safe here rather
     * than merely faster. Everything that touches a resource manager, a block's own code or another
     * sprite's pixels is done first, on this thread, and once per surface rather than once per
     * sprite - six hundred-odd reads serving nearly two hundred thousand sprites, which is what
     * keeps the serial half flat as the gradation count rises. What crosses to a worker is arrays
     * and numbers, and the only code a worker runs is pixel arithmetic that allocates its own output
     * and mutates nothing it was handed. Everything that writes a sprite, regenerates its mipmaps or
     * says anything to the log comes back here afterwards - the mipmaps because vanilla's generator
     * blends through a single shared static buffer, and the log because a stitch that logged from
     * several threads would produce one nobody could read.
     *
     * <p>
     * Forge's skipped first stitch at start-up loads nothing, and this returns before reading anything, so that stitch
     * reads no face, composes no picture, and leaves the see-through filing and the animation budget as the last
     * stitch that loaded them left them.
     */

    public static void buildFromLoadedSprites(TextureMap map, IResourceManager manager, int mipmapLevels) {
        // Noted before the early return rather than after it, because the two say different
        // things: nothing registered is an ordinary quiet afternoon, and the hook never firing
        // is the whole mod silently doing nothing.
        buildHookRan = true;
        if (REGISTERED.isEmpty()) return;

        // Taken whatever this stitch turns out to be, so a count never carries into the next one.
        int loaded = WearSprite.takeLoads();
        long loadMillis = WearSprite.takeLoadMillis();
        // The test noteStitched makes: every sprite the atlas loads is given a size, so none having one is a stitch
        // that loaded nothing, which is Forge's skipped first stitch at start-up. Nothing is read, composed, primed or
        // published for it, so the see-through filing and the budget stand as the last stitch that loaded left them.
        if (!anySized()) {
            Trmt.LOG.info(LINE_NOTHING_LOADED, Integer.valueOf(REGISTERED.size()));
            return;
        }
        Trmt.LOG.info(LINE_SIZED, Integer.valueOf(loaded), Long.valueOf(loadMillis));
        // A stitch that loaded this mod's sprites composes from here: nought until the pass says how many it built, so
        // a pass that stops part way is judged as having built none (PicturesComposed).
        builtThisStitch = 0;

        // Every cache a worker could otherwise write to, filled here instead. After these two lines
        // the pattern orders and the fracture fields are read-only for the rest of the pass, and the
        // submit that starts each batch publishes them.
        WearPatterns.primeOrders(manager, rotations);
        WearCompositor.primeFractures(rotations);
        InnerLayers.prime();
        // Counted before anything is harvested, because the budget prices a surface whole and only this walk
        // can say how many pictures will share its layer; each is at its sprite's edge, which is the edge its
        // frames are cut to.
        Map<Block, Map<String, MovingLayerLedger.Pictures>> layerPictures = countLayerPictures();
        // One reader for the whole pass, so a face a block shares across its appearances, its wall and its mended side
        // is read once, and let go of with the pass.
        FaceSource faces = new FaceSource(manager);

        // Filed under the block object first and the rest of the key second, rather than under one
        // string that spelt the block as its id. A number read while the ids were being moved on
        // another thread could give two blocks one key, and the second would be built from the
        // first one's pixels; two objects are never one key.
        Map<Block, Map<String, WearSprite.Source>> sourcesByBlock = new IdentityHashMap<Block, Map<String, WearSprite.Source>>();
        Map<String, WearSprite.Source> sourcesWithoutBlock = new HashMap<String, WearSprite.Source>();
        List<WearSprite> pending = new ArrayList<WearSprite>(COMPOSE_BATCH);
        List<WearSprite.Source> ingredients = new ArrayList<WearSprite.Source>(COMPOSE_BATCH);

        long began = System.nanoTime();
        ExecutorService pool = WearGeneration.newPool();
        Tally tally = new Tally();
        try {
            for (WearSprite sprite : REGISTERED) {
                // A sprite the atlas never sized is never installed. Skipped in countLayerPictures as well, so the
                // count and the build describe the same surfaces.
                if (sprite.getIconWidth() <= 0) continue;

                // Phase one, on this thread: the resource manager, the block's own getIcon, and the
                // pixels of whatever sprite it is already drawing with. Shared by every gradation
                // and rotation of the same surface, which is what makes reading cheap.
                Block origin = sprite.origin();
                Map<String, WearSprite.Source> sources;
                if (origin == null) {
                    sources = sourcesWithoutBlock;
                } else {
                    sources = sourcesByBlock.get(origin);
                    if (sources == null) {
                        sources = new HashMap<String, WearSprite.Source>();
                        sourcesByBlock.put(origin, sources);
                    }
                }
                String key = sprite.sourceKey();
                WearSprite.Source source = sources.get(key);
                if (source == null) {
                    // Held open round the harvest, so the one call in it that asks the budget prices every picture of
                    // the surface at once. No harvest runs anywhere else.
                    InnerLayers.openAsk(picturesOf(layerPictures, origin, key), mipmapLevels);
                    try {
                        source = sprite.harvest(faces, manager);
                    } catch (Throwable awkwardBlock) {
                        // One block refusing to name or hand over its texture is not worth the rest
                        // of the atlas, and it is third-party code either way.
                        Trmt.LOG
                            .debug("Could not read the source of wear texture {}", sprite.getIconName(), awkwardBlock);
                        source = WearSprite.Source.unreadable();
                    } finally {
                        InnerLayers.closeAsk();
                    }
                    sources.put(key, source);
                    tally.surfaces++;
                    if (source.scaled) {
                        tally.scaled++;
                        tally.scaledNames.add(sprite.originName());
                    }
                }
                // Marked where it is found, so the lookup skips the placeholder load stitched it with for whatever
                // lies below this sprite, which markUnusable sets out, and counted in this pass's own line.
                if (source.isUnreadable()) {
                    sprite.markUnusable();
                    tally.unreadable++;
                    continue;
                }

                pending.add(sprite);
                ingredients.add(source);
                if (pending.size() >= COMPOSE_BATCH) {
                    WearGeneration.composeAndInstall(pool, pending, ingredients, mipmapLevels, tally);
                }
            }
            WearGeneration.composeAndInstall(pool, pending, ingredients, mipmapLevels, tally);
        } finally {
            // Published here, once every harvest that notes a see-through layer is over, and whatever
            // became of the pass: the painter reads one whole filing, and a stitch that stopped part
            // way still replaces the one an earlier stitch made about sprites registered afresh since. Every
            // state sharing a see-through set is a window first, not only the one its pixels were read from.
            InnerLayers.spreadWindows(lookup.sets);
            InnerLayers.publishWindows();
            WearGeneration.shutDown(pool);
        }

        Trmt.LOG.info(
            LINE_BUILT,
            new Object[] { Integer.valueOf(tally.built), Long.valueOf((System.nanoTime() - began) / 1000000L),
                Integer.valueOf(tally.surfaces), Integer.valueOf(tally.guessed), Integer.valueOf(tally.unreadable),
                Integer.valueOf(tally.failed) });
        builtThisStitch = tally.built;
        if (!tally.improvised.isEmpty()) {
            // Counted and sampled rather than handed over whole.
            //
            // This line used to pass the set itself, and log4j stringifies a collection parameter by
            // walking it. On 1.16.5, where a stitch composes forty-four thousand pictures from a
            // hundred and fifty-six surfaces, that walk threw OutOfMemoryError with a StringBuilder
            // past two gigabytes - inside TextureAtlas.reload, which took the whole resource reload
            // down with it and left Indigo tessellating blocks with a null model. The game did not
            // start. The other two editions have small enough packs that it never showed.
            //
            // The count is what anybody reads anyway; the names are a sample off a snapshot, so the
            // line is bounded whatever the set is doing. A log line may not be able to stop a game.
            Trmt.LOG.info(
                "Wearing a family stand-in rather than their own pixels: {}",
                com.trmtgtnh.util.LogSample.of(tally.improvised));
        }
        reportFaces(faces);
        if (tally.scaled > 0) {
            Trmt.LOG
                .info(LINE_SCALED, Integer.valueOf(tally.scaled), com.trmtgtnh.util.LogSample.of(tally.scaledNames));
        }
        if (tally.resized > 0) {
            Trmt.LOG
                .warn(LINE_RESIZED, Integer.valueOf(tally.resized), com.trmtgtnh.util.LogSample.of(tally.resizedNames));
        }
        InnerLayers.report();
        InnerLayers.reportAnimation();
        InnerLayers.reportWindows();
        // After the pass rather than as the stitch is planned, because only now is a fallback that could not be built
        // unusable, which is half of what this looks for.
        reportFallbackGaps();
        shareIdenticalPictures();
    }

    /**
     * Each picture this stitch made, mapped to the earlier one with the same pixels whose place in the atlas it takes
     * (0.9.229, public issue #3: "Each distinct picture is stored once"). Stitch thread only.
     */
    private static final Map<TextureAtlasSprite, TextureAtlasSprite> TWINS = new IdentityHashMap<TextureAtlasSprite, TextureAtlasSprite>();

    /**
     * The glow overlays this stitch composed with nothing to light, which are never offered the atlas (real glow).
     * Stitch thread only.
     */
    private static final Set<TextureAtlasSprite> EMPTY_GLOWS = java.util.Collections
        .newSetFromMap(new IdentityHashMap<TextureAtlasSprite, Boolean>());

    static final String LINE_SHARED = "Stored {} of this mod's {} block-atlas pictures once each: {} had the same pixels as another and share its place.";

    static final String LINE_HOLDERS = "Going to the stitcher for the block atlas: this mod's {} holders take {} pixels and everything else's {} take {}, together {}% of a 16384-pixel square's pixels before the packer's slack.";

    static final String LINE_GLOWS = "Registered {} glow overlays beside the worn faces of the looks that glow, end_bleached's cracks drawn at light {} and nether_charred's ash at light {} of 15 wherever the world is darker; the plan prices each as large as its picture, and one with nothing to light is never stitched.";

    /** How many glow siblings this stitch registered; written by registration, said once it is done. */
    private static int glowsRegistered;

    static final String LINE_LEFT_OUT = "Left {} of this mod's pictures that share another's place out of the block atlas's stitch.";

    static final String LINE_GUTTER = "Gave {} of this mod's block-atlas sprites a {}-pixel Angelica gutter rather than Angelica's {}, so each sixteen-pixel picture takes a {}-pixel holder rather than {}; and left {} pictures that share another's place out of the stitch.";

    /**
     * Finds the pictures whose pixels are another's, once every picture is composed, so only the first of each goes
     * to the stitcher. A moving layer is never shared, because it is redrawn in its own place, and nor is a picture
     * that could not be built. Their mip levels are made from their pixels, so the same pixels are the same picture
     * at every level.
     */
    private static void shareIdenticalPictures() {
        TWINS.clear();
        EMPTY_GLOWS.clear();
        List<TextureAtlasSprite> sprites = new ArrayList<TextureAtlasSprite>(REGISTERED.size() + overlaySprites.length);
        for (WearSprite sprite : REGISTERED) {
            // A glow overlay with nothing to light is neither offered nor shared: it is left out of the stitch whole.
            if (sprite.isGlow() && !sprite.isUsable()) {
                EMPTY_GLOWS.add(sprite);
                continue;
            }
            sprites.add(sprite);
        }
        for (IIcon fringe : overlaySprites) {
            if (fringe instanceof FringeSprite) sprites.add((FringeSprite) fringe);
        }
        int count = sprites.size();
        int[][] pictures = new int[count][];
        int[] widths = new int[count];
        int[] heights = new int[count];
        boolean[] eligible = new boolean[count];
        for (int i = 0; i < count; i++) {
            TextureAtlasSprite sprite = sprites.get(i);
            widths[i] = sprite.getIconWidth();
            heights[i] = sprite.getIconHeight();
            boolean usable = sprite instanceof WearSprite
                ? ((WearSprite) sprite).isUsable() && !((WearSprite) sprite).moves()
                : ((FringeSprite) sprite).isUsable();
            eligible[i] = usable && widths[i] > 0 && !sprite.hasAnimationMetadata() && sprite.getFrameCount() == 1;
            if (eligible[i]) {
                int[][] levels = sprite.getFrameTextureData(0);
                pictures[i] = levels == null || levels.length == 0 ? null : levels[0];
                // A glow overlay is compared with the other overlays and with nothing else: one more value than any
                // picture of the same pixels, so it is never filed as a wear picture's twin, or the other way about.
                if (pictures[i] != null && sprite instanceof WearSprite && ((WearSprite) sprite).isGlow()) {
                    pictures[i] = glowKey(pictures[i]);
                }
            }
        }
        int[] canonical = SmallGutter.canonical(pictures, widths, heights, eligible);
        for (int i = 0; i < count; i++) {
            if (canonical[i] != i) TWINS.put(sprites.get(i), sprites.get(canonical[i]));
        }
        Trmt.LOG.info(
            LINE_SHARED,
            new Object[] { Integer.valueOf(count - TWINS.size()), Integer.valueOf(count),
                Integer.valueOf(TWINS.size()) });
    }

    /**
     * Called at the head of the block atlas's stitch, after Angelica has given every sprite its gutter and before
     * the stitcher reads a holder's size: gives this mod's sprites the small gutter, and takes out of the stitch every
     * picture that shares another's place. The holder's size is read live from the sprite's gutter (Angelica's
     * MixinStitcherHolder), so the gutter set here is the one the stitcher, the UV inset and the padded upload use.
     */
    public static void beforeStitch(java.util.Set<?> stitching) {
        if (stitching == null || stitching.isEmpty()) return;
        int gutter = ownGutter();
        boolean angelica = holderGutter > 0;
        int bordered = 0;
        int left = 0;
        boolean ours = false;
        for (java.util.Iterator<?> holders = stitching.iterator(); holders.hasNext();) {
            Object each = holders.next();
            if (!(each instanceof net.minecraft.client.renderer.texture.Stitcher.Holder)) continue;
            TextureAtlasSprite sprite = ((net.minecraft.client.renderer.texture.Stitcher.Holder) each).getAtlasSprite();
            if (!(sprite instanceof WearSprite || sprite instanceof FringeSprite)) continue;
            ours = true;
            if (TWINS.containsKey(sprite) || EMPTY_GLOWS.contains(sprite)) {
                holders.remove();
                left++;
                continue;
            }
            if (angelica && AngelicaGutter.set(sprite, gutter)) bordered++;
        }
        if (!ours) return;
        if (angelica && gutter < holderGutter) {
            // What the stitcher will place, read from each holder as it will read it: the sprite's edge at the mip
            // stride plus the gutter now set on it (Angelica's MixinStitcherHolder adds it live).
            long ownPixels = 0L;
            long otherPixels = 0L;
            int own = 0;
            int other = 0;
            for (Object each : stitching) {
                if (!(each instanceof net.minecraft.client.renderer.texture.Stitcher.Holder)) continue;
                net.minecraft.client.renderer.texture.Stitcher.Holder holder = (net.minecraft.client.renderer.texture.Stitcher.Holder) each;
                long pixels = (long) holder.getWidth() * holder.getHeight();
                TextureAtlasSprite sprite = holder.getAtlasSprite();
                if (sprite instanceof WearSprite || sprite instanceof FringeSprite) {
                    own++;
                    ownPixels += pixels;
                } else {
                    other++;
                    otherPixels += pixels;
                }
            }
            long square = (long) AtlasPlan.LARGER_SIDE * AtlasPlan.LARGER_SIDE;
            Trmt.LOG.info(
                LINE_HOLDERS,
                new Object[] { Integer.valueOf(own), Long.valueOf(ownPixels), Integer.valueOf(other),
                    Long.valueOf(otherPixels), Long.valueOf((ownPixels + otherPixels) * 100L / square) });
        }
        if (!angelica) {
            // No Angelica, no gutter to speak of: only the pictures left out for sharing another's place.
            Trmt.LOG.info(LINE_LEFT_OUT, Integer.valueOf(left));
            return;
        }
        Trmt.LOG.info(
            LINE_GUTTER,
            new Object[] { Integer.valueOf(bordered), Integer.valueOf(gutter), Integer.valueOf(holderGutter),
                Integer.valueOf(16 + 2 * gutter), Integer.valueOf(16 + 2 * holderGutter), Integer.valueOf(left) });
    }

    /**
     * Called once the block atlas is stitched and uploaded: every picture that shared another's place takes that
     * place, and lets go of its own pixels, which nothing will upload. Vanilla has by then given each of them
     * missingno's place, as it does every sprite that was not stitched.
     */
    public static void afterStitch() {
        if (TWINS.isEmpty()) return;
        for (Map.Entry<TextureAtlasSprite, TextureAtlasSprite> pair : TWINS.entrySet()) {
            TextureAtlasSprite twin = pair.getKey();
            twin.copyFrom(pair.getValue());
            twin.clearFramesTextureData();
        }
    }

    /**
     * Every picture that could take a moving layer, by its block and the key the sprite pass files its sources under.
     *
     * <p>
     * From the sprites actually registered, each at the size it was stitched at, rather than from the plan's
     * gradations and rotations, so that no copy of the plan's arithmetic has to be kept in step here. Every sprite of
     * one filing is stitched at one edge, the edge is part of the key, and the frames its surface is harvested with
     * are cut at that edge, so a count here is one edge a surface. Filed under the same block object and the same
     * sourceKey as the pass files sources, and skipping the same sprites, so the count and the build describe the
     * same surfaces; if they ever do not, the ask finds no count and the log warns. A block is asked about its layer
     * once for each run of its sprites rather than once a sprite, and nothing is walked at all while no layer
     * can move.
     *
     * <p>
     * Walked before the pass's own guards are up, so nothing in it may throw: the one question that leaves this mod,
     * whether a block has a layer, is asked inside a catch of its own.
     */
    private static Map<Block, Map<String, MovingLayerLedger.Pictures>> countLayerPictures() {
        Map<Block, Map<String, MovingLayerLedger.Pictures>> byBlock = new IdentityHashMap<Block, Map<String, MovingLayerLedger.Pictures>>();
        if (!InnerLayers.layersMayMove()) return byBlock;
        Block askedOf = null;
        int askedMeta = 0;
        boolean layered = false;
        for (WearSprite sprite : REGISTERED) {
            // A glow holds no layer: it is drawn over its base, whose shell the layer moves under.
            if (sprite.getIconWidth() <= 0 || sprite.isGlow()) continue;
            Block origin = sprite.origin();
            // A fallback set has no block, so nothing can name a layer behind it.
            if (origin == null) continue;
            int meta = sprite.originMeta();
            if (origin != askedOf || meta != askedMeta) {
                askedOf = origin;
                askedMeta = meta;
                try {
                    layered = InnerLayers.textureFor(origin, meta) != null;
                } catch (Throwable awkwardBlock) {
                    // The question goes to the block registry by the block object, and so into a mod's own code,
                    // and the harvest asks it inside a catch for that reason. Out here an exception would end the
                    // whole stitch. A block that cannot be asked is counted as having no layer: its harvest then
                    // fails the same way and asks for nothing, or answers that time and is warned about as an ask
                    // with no count behind it.
                    Trmt.LOG.debug(
                        "Could not ask whether wear texture {} has a layer behind it",
                        sprite.getIconName(),
                        awkwardBlock);
                    layered = false;
                }
            }
            if (!layered) continue;
            Map<String, MovingLayerLedger.Pictures> row = byBlock.get(origin);
            if (row == null) {
                row = new HashMap<String, MovingLayerLedger.Pictures>();
                byBlock.put(origin, row);
            }
            String key = sprite.sourceKey();
            MovingLayerLedger.Pictures counted = row.get(key);
            if (counted == null) {
                counted = new MovingLayerLedger.Pictures();
                row.put(key, counted);
            }
            counted.add(sprite.getIconWidth());
        }
        return byBlock;
    }

    /** The count for one surface, or null where it has no layer behind it. */
    private static MovingLayerLedger.Pictures picturesOf(Map<Block, Map<String, MovingLayerLedger.Pictures>> byBlock,
        Block origin, String key) {
        if (origin == null) return null;
        Map<String, MovingLayerLedger.Pictures> row = byBlock.get(origin);
        return row == null ? null : row.get(key);
    }

    /** Whether any wear sprite registered this stitch has a size, which every one the atlas loads is given. */
    private static boolean anySized() {
        for (WearSprite sprite : REGISTERED) {
            if (sprite.getIconWidth() > 0) return true;
        }
        return false;
    }

    /**
     * Says where the pass's faces came from and how many had the filtering border taken off, every stitch that loads,
     * and warns where filtering is on and not one face read out of the atlas was marked as loaded with it.
     */
    private static void reportFaces(FaceSource faces) {
        String line = LINE_FACES;
        List<Object> args = new ArrayList<Object>();
        args.add(Integer.valueOf(faces.atlasFaces()));
        args.add(Integer.valueOf(faces.fileFaces()));
        args.add(Integer.valueOf(faces.croppedFaces()));
        if (faces.unborderedFaces() > 0) {
            line = line + LINE_FACES_UNBORDERED;
            args.add(Integer.valueOf(faces.unborderedFaces()));
        }
        Trmt.LOG.info(line, args.toArray());
        AtlasPlan plan = lastPlan;
        if (plan != null && plan.measure.anisotropic
            && faces.atlasFaces() > 0
            && faces.croppedFaces() + faces.unborderedFaces() == 0) {
            Trmt.LOG.warn(LINE_NO_BORDERS, Integer.valueOf(faces.atlasFaces()));
        }
    }

    // Word for word what the sprite pass writes, kept in one place so a line found in latest.log leads straight back
    // to the branch that wrote it.
    private static final String LINE_NOTHING_LOADED = "None of this mod's {} wear sprites had been loaded when the block atlas went to the stitcher, which is what Forge's skipped first stitch at start-up does, loading nothing, so no face was read and no picture composed for this stitch; the stitch that loads the atlas composes them, and says so.";

    private static final String LINE_SIZED = "Sized {} wear sprites for the stitcher in {} ms, each at the edge its plan priced it at, without reading a face or composing a pixel; the sprite pass below composes each of them once.";

    private static final String LINE_BUILT = "Composed {} wear textures once each in {} ms, from {} surfaces read once apiece, each picture at the size the stitcher reserved for it; {} had to be guessed at, {} had nothing readable to be made from and {} could not be composed or installed. Of those last two, a block's own wear falls through to its family's fallback, a side wall to the fallback wall and a mended side to vanilla grass's side; a family's fallback and the fallback wall have nothing below them, and a family's fallback missing at its first gradation is named in a warning below.";

    private static final String LINE_FACES = "Read {} block faces out of the atlas for the wear textures and {} from their files; {} of those read out of the atlas had the sixteen-pixel border anisotropic filtering adds taken off first, so that worn ground draws each of them at its own scale rather than with the border squeezed into the face's own edge, which puts a sixteen-pixel face at half its scale, twice across, and a thirty-two-pixel face at two thirds, one and a half times across.";

    private static final String LINE_FACES_UNBORDERED = " {} others read out of the atlas were marked as loaded with that border but did not carry it as the game wraps it; the game draws only the middle of a marked texture whatever it holds, so they were cropped to that middle all the same, save any no wider than the border's sixteen pixels, which were used as they were loaded. A loader of their own that fills a sprite's frames after the game has marked it, or a mark left from an earlier load, is the likely cause.";

    private static final String LINE_NO_BORDERS = "Anisotropic filtering is on, and not one of the {} block faces read out of the atlas for the wear textures was marked as loaded with the border it adds, so none was cropped. The game marks every texture it loads from a file that way, so something in the pack loads the block atlas's textures by a route of its own; if that route still widens them, worn ground draws each face with the sixteen-pixel border squeezed into the face's own edge, a sixteen-pixel face at half its scale, twice across, and a thirty-two-pixel face at two thirds, one and a half times across, which a screenshot of worn stone beside unworn stone shows at once.";

    private static final String LINE_SCALED = "{} surfaces were drawn from a face of another size than the edge their plan priced, and the face was scaled to that edge as it was read, so their wear is at another resolution from the block itself, on these blocks: {}. Each is a block whose face's file could not be read while the stitch was planned, whose face a loader of its own draws at another size from its file, or whose icon another mod registered after this mod's planner had run; the plan can price only what it can read before anything is loaded, and a picture larger than its price would take room given to other faces.";

    private static final String LINE_RESIZED = "{} wear textures were composed at another size from the one the stitcher reserved for them and were scaled to fit, on these blocks: {}. Every picture is composed at the edge its plan priced, which is the edge its sprite was stitched at, so this is a fault in this mod and not in any setting: the size a sprite is stitched at and the size it is composed at have come apart.";

    // ------------------------------------------------------------------
    // Whether the hooks that build all of this actually ran
    // ------------------------------------------------------------------

    /** Whether the injection that measures the atlas reached this class during the current stitch. */
    private static boolean roomHookRan;

    /** Whether the injection that builds every worn picture reached it. */
    private static boolean buildHookRan;

    /**
     * The pictures the sprite pass composed this stitch, or PicturesComposed.SKIPPED for a stitch that loaded none of
     * this mod's sprites (Forge's skipped first stitch at start-up) or never reached the pass.
     */
    private static int builtThisStitch = PicturesComposed.SKIPPED;

    /** Whether the injection that counts what went to the stitcher reached this class during the current stitch. */
    private static boolean stitchCounted;

    /**
     * Whether the line saying what went to the stitcher was written this stitch, which the count alone does not
     * promise: Forge's skipped first stitch at start-up is counted, finds none of this mod's sprites loaded, and
     * writes nothing, so the end of that stitch must not point at a line above it.
     */
    private static boolean stitchReported;

    /** What this mod registered this stitch: its wear sprites and its grass-side fringes. */
    private static int registeredThisStitch;

    /** Fringes registered this stitch, which REGISTERED does not hold. */
    private static int fringesRegistered;

    /** The plan this stitch was built to, kept until the stitch reports on itself. */
    private static AtlasPlan lastPlan;

    /**
     * Says so, loudly, when the two injections everything here depends on did not fire.
     *
     * <p>
     * Both of them carry {@code require = 0}, and that is deliberate: a mod that has replaced
     * {@code TextureMap} outright should leave this one drawing plain ground rather than refuse to
     * start the game at all. What it costs is the only thing worth attention here. An injection with
     * {@code require = 0} and no {@code expect} that fails to bind logs nothing whatever - not a
     * warning, not a debug line, nothing - so this mod comprehensively not working looks exactly
     * like this mod working, and the sole visible difference is ground that never wears however far
     * it is walked. That failure has shipped before, and it took three versions to find, because
     * there was nothing to search the log for.
     *
     * <p>
     * Said from {@code TextureStitchEvent.Post}, which is a Forge event rather than an injection and
     * therefore cannot go missing the same way. The flags are cleared as they are read so that a
     * later stitch - a resource reload, a pack change - is judged on its own rather than on the
     * first one's luck.
     */
    public static void reportStitchHooks() {
        reportStitchSearch(stitchReported || !stitchCounted && lastPlan != null);
        if (!buildHookRan) {
            Trmt.error(
                "The injection that builds every worn picture never ran, so no ground will show any wear at all however far it is walked. Either something has replaced net.minecraft.client.renderer.texture.TextureMap, or this mod's mixins did not apply; look earlier in this log for a mixin config that failed to load.");
        } else if (!roomHookRan) {
            // The pointer at the stitcher's line is given only where that line was written. A stitch that
            // counted but wrote nothing loaded none of this mod's sprites, which is Forge's skipped first
            // stitch; one that never counted is reported by LINE_I below, which can only say that the
            // counting injection never ran, not why.
            if (lastPlan == null) {
                Trmt.LOG.warn(LINE_J_NO_PLAN);
            } else if (stitchReported) {
                Trmt.LOG.warn(LINE_J + LINE_J_REPORTED, Integer.valueOf(lastPlan.side));
            } else if (stitchCounted) {
                Trmt.LOG.warn(LINE_J + LINE_J_LOADED_NOTHING, Integer.valueOf(lastPlan.side));
            } else {
                Trmt.LOG.warn(LINE_J + ".", Integer.valueOf(lastPlan.side));
            }
        }
        if (buildHookRan && !stitchCounted) Trmt.LOG.warn(LINE_I);
        // Kept for the harness, which judges the walk by it: the line above is said to a log, and the flags it reads
        // are cleared below (0.9.229, the walk that passed under FastCraft with no picture composed).
        if (!REGISTERED.isEmpty()) PicturesComposed.stitchEnded(buildHookRan, builtThisStitch);
        builtThisStitch = PicturesComposed.SKIPPED;
        roomHookRan = false;
        buildHookRan = false;
        stitchCounted = false;
        stitchReported = false;
        packDemand = 0;
        packRead = 0;
        packReadCells = 0L;
        packReadMillis = 0L;
        glMaximum = 0;
        packAnisotropic = false;
        holderGutter = 0;
        stitcherLevels = -1;
        WearPatterns.clearHeaderWidths();
        lastPlan = null;
    }

    /**
     * Says what the stitcher took over the block atlas and what its shortcut spared it (AtlasPlan.Search), for a
     * stitch that carried this mod's sprites: one the count reported on, or one the count never reached that planned
     * them, since OptiFine on 1.12.2 replaces the method the count waits in and the stitcher's own figures are then the
     * only ones there are. Read either way, so a stitch that carried none - Forge's skipped first
     * one at start-up - does not leave its figures for the next report.
     */
    private static void reportStitchSearch(boolean carriedOurs) {
        long[] report = AtlasPlan.Search.take();
        if (!carriedOurs || report[0] == 0L) return;
        if (report[2] == 0L && report[0] >= SEARCH_HEARD_FROM) {
            Trmt.LOG.warn(LINE_SEARCH_UNSPARED, Long.valueOf(report[0]), Long.valueOf(report[1]));
        } else {
            Trmt.LOG.info(LINE_SEARCH, Long.valueOf(report[0]), Long.valueOf(report[1]), Long.valueOf(report[2]));
        }
    }

    /** Sprites past which a stitch that turned no search back can only mean the shortcut never bound. */
    private static final int SEARCH_HEARD_FROM = 1000;

    private static final String LINE_SEARCH = "The stitcher placed the block atlas's {} sprites in {} ms, and turned back {} searches at slots it already knew were too full for the sprite in hand. Each sprite is where vanilla's own search would put it, which walks each of those slots to the bottom for every sprite and with sprites of mixed sizes took minutes - short of a sprite vanilla's would lay over others, which this puts somewhere free.";

    private static final String LINE_SEARCH_UNSPARED = "The stitcher placed the block atlas's {} sprites in {} ms without turning back a single search, so this mod's shortcut through its slot search never ran: another mod has replaced Stitcher.Slot.addSlot, or this mod's mixins did not apply - look earlier in this log for a mixin config that failed to load. Nothing is wrong with the atlas. With many sprites of mixed sizes it is only slow, because vanilla's search walks every filled slot for every sprite.";

    private static final String LINE_I = "The injection that counts what went to the stitcher never ran, although the one that builds the wear textures did, so this stitch cannot say what went to the stitcher against what the plan priced. Both wait for the same call to Stitcher.doStitch, or to FastCraft's hook where FastCraft has put it in doStitch's place, in the same mixin, so this should not happen, and an optional injection writes nothing to this log when it does not run, so no earlier line will say why.";

    /** Line J, one placeholder, ended by exactly one of the three tails below or a full stop. */
    private static final String LINE_J = "The injection that measures the atlas never ran, so the wear textures were planned as though the rest of the pack had taken half of a {}-pixel square, rather than from what it had actually taken. The ramp may be coarser than it needed to be, or, on a pack that fills more than half, the atlas may not fit; the line from planning above gives the figures";

    private static final String LINE_J_REPORTED = ", and the line written as the atlas went to the stitcher says what the pack really took.";

    private static final String LINE_J_LOADED_NOTHING = ". None of this mod's sprites had been loaded when the atlas went to the stitcher, which is what Forge's skipped first stitch at start-up does, loading nothing, so there was nothing to hold to the plan and no line says what went to the stitcher; the next stitch plans again, and its own lines say what the pack really took.";

    /**
     * Line J where no plan reached the end of the stitch. The plan is kept in the last lines of the Pre handler, and
     * an exception out of that handler ends the stitch before this event, so a missing plan here means the handler
     * never ran, or stopped part way inside a TextureMap that swallowed the exception.
     */
    private static final String LINE_J_NO_PLAN = "The injection that measures the atlas never ran, and no plan for the wear textures reached the end of this stitch: this mod's handler for TextureStitchEvent.Pre either never ran or stopped before it finished, so nothing this mod registers can be counted on from this stitch, and ground may show no wear however far it is walked. Most likely something has rewritten net.minecraft.client.renderer.texture.TextureMap.loadTextureAtlas so that it no longer fires that event through ForgeHooksClient.onTextureStitchedPre, although it still stitches and fires the event that follows; an error earlier in this log would mean planning stopped part way instead.";

    /** One picture per gradation a record can hold. Below this, consecutive gradations share again. */
    private static final int MIN_GRADATIONS = SurfaceFamily.MAX_STAGES;

    /** What the stitcher was carrying before this mod added anything, and what the card will hold. */
    private static volatile int packDemand;

    private static volatile int glMaximum;

    private static volatile boolean packAnisotropic;

    /** Per-side block-sprite gutter assigned by Angelica at stitch time, zero in older packs. */
    private static volatile int holderGutter;

    /**
     * How many of the atlas's textures had a readable header, the slots those came to, and how long reading them took.
     */
    private static volatile int packRead;

    private static volatile long packReadCells;

    private static volatile long packReadMillis;

    /** The mipmap levels the stitcher was built with, or -1 when nothing measured the atlas this stitch. */
    private static volatile int stitcherLevels = -1;

    /**
     * The most surfaces.maxTexturedSurfaces accepts, which TrmtConfig reads with a range of nought to
     * 4096; asked only so the ceiling line names a figure the setting will take.
     */
    private static final int MOST_TEXTURED_SURFACES = 4096;

    /** Count and area budgets below the 8192 atlas's packing cliff measured in GTNH. */

    /**
     * Which texturable surfaces are natural ground, offered the textured-surface places first: vanilla's own, by the
     * namespace of the registry name. Vanilla's texturable faces are its generated ground and few, 36 on 1.7.10 to 62
     * on 26.2 in the rig's plain games (2026-11-08), so they never take more than a few dozen of the places. A mod's
     * natural ground has no mark at the stitch that every edition can read alike: biomes name their top and filler
     * blocks to 1.16.5, and from 1.18 the surface rules that replaced them arrive with a world, after this stitch. It
     * keeps its place by the ceiling's default, which holds the largest pack measured whole.
     */
    private static final AtlasPlan.Ground<SurfaceRegistry.SurfaceState> NATURAL_GROUND = new AtlasPlan.Ground<SurfaceRegistry.SurfaceState>() {

        @Override
        public boolean natural(SurfaceRegistry.SurfaceState state) {
            return AtlasPlan.vanilla(SurfaceRegistry.registryName(state.block));
        }
    };

    /**
     * Reported by {@code MixinTextureMap} from the call that fires the stitch event, which lands
     * after the atlas has gathered every block and item icon and before any handler of that event
     * has run - this mod's planner included, and every other mod's with it. A sprite another mod
     * adds from its own handler is therefore not in this count: room the plan left unused absorbs
     * those first, and the sixteenth held back for the stitcher only once that is spent, and the line
     * written as the atlas goes to the stitcher says how many there were.
     *
     * <p>
     * Every texture gathered is priced from its own file, its width read from the header without
     * decoding it. Every name rather than a sample, for two reasons. On a stitch that loads its sprites
     * the cost is a subset of work that stitch does a moment later: for a name vanilla loads from a file
     * it is the same resource lookup and the same opened stream, without the decode or the pixel copy,
     * and with the metadata parsed only for a texture that has any, because parsing it is what closes
     * it; only a name with a loader of its own costs a lookup vanilla does not make. Forge's skipped
     * first stitch at start-up loads nothing, so there the whole pass is extra, and what it reads is
     * dropped before the real stitch reads it all again; telling that stitch apart needs Forge's private
     * skipFirst, which MixinTextureMap does not shadow. And a pack's total is ruled
     * by its few largest files, which is exactly what a sample estimates worst - one 512-pixel texture
     * is 1,024 slots, and a one-in-thirty-two sample scales it into 32,768 found or missed, twice
     * everything held back for the stitcher. None of the figures can be guessed at from here: the
     * textures are the pack's, the maximum the driver's, the filtering the player's, and the mipmap
     * levels are the atlas's own, which it may lower on its field before it stitches.
     */
    public static void noteAtlasRoom(IResourceManager manager, Map<?, ?> registered, int maximum, boolean anisotropic,
        int mipmapLevels) {
        roomHookRan = true;
        glMaximum = maximum;
        packAnisotropic = anisotropic;
        holderGutter = angelicaHolderGutter(mipmapLevels);
        stitcherLevels = mipmapLevels;
        WearPatterns.clearHeaderWidths();
        long began = System.nanoTime();
        Object[] names = registered.keySet()
            .toArray();
        int read = 0;
        long cells = 0L;
        try {
            for (Object name : names) {
                if (!(name instanceof String)) continue;
                int width = WearPatterns.headerWidth(manager, (String) name);
                if (width <= 0) continue;
                read++;
                cells += AtlasPlan.spriteCells(width + 2 * holderGutter, anisotropic);
            }
        } catch (RuntimeException awkwardPack) {
            // This runs inside the stitch itself, and one resource pack refusing a question must not
            // take the whole atlas with it; what was read by then still prices the pack, and the rest
            // at their average.
            Trmt.LOG.debug("Stopped reading the block atlas's texture headers part way", awkwardPack);
        }
        packDemand = names.length;
        packRead = read;
        packReadCells = cells;
        packReadMillis = (System.nanoTime() - began) / 1000000L;
    }

    /**
     * The gutter this mod's own sprites take under Angelica at this stitch, chosen as it was planned (registerAll,
     * SmallGutter.candidates): never more than Angelica's own, none without it.
     */
    private static volatile int chosenGutter;

    static int ownGutter() {
        return SmallGutter.ownGutter(holderGutter, chosenGutter);
    }

    /**
     * client.angelicaWearGutter as read: {@link SmallGutter#AUTO} or a fixed gutter. -Dtrmt.wearGutter lets the rig
     * force one without touching the shared settings fixture, as -Dtrmt.dumpStitchRects lets it capture the holders;
     * a player's game never sets it.
     */
    private static String gutterSetting() {
        String forced = System.getProperty("trmt.wearGutter");
        return forced != null ? forced : TrmtConfig.angelicaWearGutter;
    }

    static final String LINE_CHOSEN_GUTTER = "Angelica gutter for this mod's sprites: {} pixels, {}.";

    static final String LINE_STITCHER_SHARE = "Planned this mod's sprites and the pack's into {}% of the {}-pixel block atlas, priced in exact pixels, because {}.";

    /** The gutter a stitched sprite is counted at: this mod's own at the small one, everything else at Angelica's. */
    private static int gutterOf(TextureAtlasSprite sprite) {
        return sprite instanceof WearSprite || sprite instanceof FringeSprite ? ownGutter() : holderGutter;
    }

    /** The mip levels the stitcher rounds holders at, as measured, or the game's four where nothing measured. */
    static int pricedLevels() {
        return stitcherLevels >= 0 ? stitcherLevels : 4;
    }

    private static int angelicaHolderGutter(int mipmapLevels) {
        try {
            Class<?> sprite = Class.forName("com.gtnewhorizons.angelica.rendering.celeritas.SpriteExtension");
            sprite.getMethod("angelica$getGutterWidth");
            Class<?> padding = Class.forName("com.gtnewhorizons.angelica.utils.SpritePadding");
            return Math.max(
                0,
                ((Integer) padding.getMethod("gutterFor", int.class, boolean.class)
                    .invoke(null, Integer.valueOf(mipmapLevels), Boolean.TRUE)).intValue());
        } catch (ReflectiveOperationException unavailable) {
            return 0;
        } catch (LinkageError unavailable) {
            return 0;
        }
    }

    /**
     * Reported by {@code MixinTextureMap} as the atlas goes to the stitcher: every sprite it holds by
     * then, which is what the plan's prices can be held to.
     *
     * <p>
     * Counted at the mipmap levels the stitcher was built with, which the measure noted. The atlas's
     * own field may have dropped below those since, when a texture narrower than the mipmap stride
     * loaded, and is used only when nothing measured it, which the line then says. Forge's skipped first
     * stitch at start-up loads nothing, so every sprite of this mod's still has no size and that stitch
     * writes no line here; the lines written while planning point here only for a stitch that loads its
     * textures, and the end of an unmeasured stitch says when none was written. A sprite vanilla refused
     * this stitch can keep the size it was given before it was refused and is counted anyway, which errs
     * high; missingno is added, at the size vanilla gives it.
     */
    public static void noteStitched(Map<?, ?> registered, int mipmapLevels) {
        stitchCounted = true;
        AtlasPlan plan = lastPlan;
        if (plan == null) return;
        boolean stitcherLevelsKnown = roomHookRan && stitcherLevels >= 0;
        AtlasPlan.Stitched stitched = new AtlasPlan.Stitched(stitcherLevelsKnown ? stitcherLevels : mipmapLevels);
        int widest = 0;
        int tallest = 0;
        double longestAspect = 0.0D;
        int nonSquare = 0;
        String widestName = "";
        String tallestName = "";
        String aspectName = "";
        int aspectWidth = 0;
        int aspectHeight = 0;
        for (Object value : registered.values()) {
            if (!(value instanceof TextureAtlasSprite)) continue;
            TextureAtlasSprite sprite = (TextureAtlasSprite) value;
            int width = sprite.getIconWidth();
            int height = sprite.getIconHeight();
            if (width > widest) {
                widest = width;
                widestName = sprite.getIconName();
            }
            if (height > tallest) {
                tallest = height;
                tallestName = sprite.getIconName();
            }
            if (width > 0 && height > 0) {
                if (width != height) nonSquare++;
                double aspect = (double) Math.max(width, height) / Math.min(width, height);
                if (aspect > longestAspect) {
                    longestAspect = aspect;
                    aspectWidth = width;
                    aspectHeight = height;
                    aspectName = sprite.getIconName();
                }
            }
            stitched.add(
                sprite instanceof WearSprite || sprite instanceof FringeSprite,
                width > 0 ? width + 2 * gutterOf(sprite) : width,
                height > 0 ? height + 2 * gutterOf(sprite) : height);
        }
        if (stitched.ownSprites() == 0) return;
        Trmt.LOG.warn(
            "Raw block-atlas sprite sizes before the {}-pixel Angelica gutter: widest {} pixels ({}), tallest {} pixels ({}), {} non-square, largest aspect {}:1 at {}x{} ({}).",
            Integer.valueOf(holderGutter),
            Integer.valueOf(widest),
            widestName,
            Integer.valueOf(tallest),
            tallestName,
            Integer.valueOf(nonSquare),
            Double.valueOf(longestAspect),
            Integer.valueOf(aspectWidth),
            Integer.valueOf(aspectHeight),
            aspectName);
        int missing = WearPatterns.ART_SIZE + (plan.measure.anisotropic ? AtlasPlan.ANISOTROPIC_GROWTH : 0);
        stitched.add(false, missing + 2 * holderGutter, missing + 2 * holderGutter);
        int late = plan.measure.taken ? Math.max(0, registered.size() - plan.measure.packSprites - registeredThisStitch)
            : 0;
        // Under the small gutter this count is not the stitch's: the pictures that share another's place leave the
        // stitch after it, and AtlasPlan.Stitched rounds a whole holder, gutter and all, to the mip stride, which
        // makes a 24-pixel holder 32. The holders themselves are counted at the head of the stitch instead
        // (beforeStitch, LINE_HOLDERS), where they are what the stitcher will place.
        if (ownGutter() < holderGutter) return;
        reportStitched(plan, stitched, late, stitcherLevelsKnown);
    }

    /**
     * Says what went to the stitcher against what the plan priced, every stitch that loaded this mod's
     * sprites, and warns once everything that went has begun to spend the sixteenth held back for the
     * stitcher.
     *
     * <p>
     * Judged on the whole rather than half by half, as AtlasPlan.overdrawn is: a slot is a slot, and the
     * stitcher sorts its holders by size and never tells this mod's sprites from another's, so what one
     * half came in under its price absorbs what the other ran past its own, and so does room the plan
     * never used. How far each half ran past its price is still given, as a description of where the
     * slots went rather than as the test. Only a whole past the square itself is said to risk an atlas
     * larger than planned, or 'Unable to fit'; short of that the reserve is being spent, which a stitcher
     * that packs onto shelves may or may not get away with.
     */
    private static void reportStitched(AtlasPlan plan, AtlasPlan.Stitched stitched, int late,
        boolean stitcherLevelsKnown) {
        stitchReported = true;
        long total = stitched.ownCells() + stitched.otherCells();
        StringBuilder text = new StringBuilder(
            "Went to the stitcher for the block atlas: this mod's {} sprites in {} slots, against the {} its plan priced them at, and everything else, {} sprites in {} slots, against the {} ");
        List<Object> args = new ArrayList<Object>();
        args.add(Integer.valueOf(stitched.ownSprites()));
        args.add(Long.valueOf(stitched.ownCells()));
        args.add(Long.valueOf(plan.plannedCells));
        args.add(Integer.valueOf(stitched.otherSprites()));
        args.add(Long.valueOf(stitched.otherCells()));
        args.add(Long.valueOf(plan.packCells));
        if (plan.measure.taken) {
            text.append("priced for the pack from its files, {} of them added after the atlas was measured");
            args.add(Integer.valueOf(late));
        } else {
            text.append("taken as the pack's, half the square, because nothing measured the atlas");
        }
        text.append(
            ". Together that is {} of the {} slots in a {}-pixel square, {} of them held back for the stitcher, ");
        args.add(Long.valueOf(total));
        args.add(Long.valueOf(plan.squareCells));
        args.add(Integer.valueOf(plan.side));
        args.add(Long.valueOf(plan.reserveCells));
        if (stitcherLevelsKnown) {
            text.append("counted as the stitcher rounds each sprite, at {} mipmap levels.");
        } else {
            text.append(
                "counted at the {} mipmap levels the atlas held as it stitched, which may be fewer than its stitcher rounds each sprite at, where a texture narrower than the mipmap stride made the atlas lower them; the slot figures may then be low, and a stitch that has spent the reserve may not be warned of.");
        }
        args.add(Integer.valueOf(stitched.levels()));
        if (!plan.overdrawn(stitched)) {
            Trmt.LOG.info(text.toString(), args.toArray());
            return;
        }
        long spent = plan.reserveSpent(stitched);
        if (total > plan.squareCells) {
            text.append(
                " Together they want {} of the {} slots held back for the stitcher, more than there are, and more than a {}-pixel square holds, so the atlas may come out larger than that square, or stop with 'Unable to fit' on a card that will not address a larger one.");
            args.add(Long.valueOf(spent));
            args.add(Long.valueOf(plan.reserveCells));
            args.add(Integer.valueOf(plan.side));
        } else {
            text.append(
                " That spends {} of the {} slots held back for the stitcher, which packs onto shelves rather than perfectly and may not reach every one of them, though the square itself has room for the whole.");
            args.add(Long.valueOf(spent));
            args.add(Long.valueOf(plan.reserveCells));
        }
        // Where the halves went, not whether it fits: that was settled on the whole above.
        long ownPast = plan.ownPast(stitched);
        long restPast = plan.restPast(stitched);
        if (ownPast + restPast > 0L) {
            text.append(
                " Against their prices, this mod's sprites ran {} slots past and everything else {}; a half that came in under its price, and room the plan never used, absorb what the other ran over, so it is the two together that are held to the square less what is held back.");
            args.add(Long.valueOf(ownPast));
            args.add(Long.valueOf(restPast));
        } else {
            // Each half within its price and the whole still into the reserve can only mean the plan itself
            // was past the room, which only the mandatory tier brings about, and line F said so while planning.
            text.append(
                " Neither half ran past its price: the plan itself was larger than the room, as the line from planning warned.");
        }
        Trmt.LOG.warn(text.toString(), args.toArray());
    }

    /**
     * The edge dirt and stone are drawn at. It prices a face whose file cannot be read, where the face paired with it
     * is no larger, and the pack's own textures when not one of their files could be read; every other sprite is
     * priced at the edge of the file it is drawn from. Probed from two textures rather than one, because a pack may
     * redraw stone and leave dirt alone. Both reads are cached for the rest of the stitch, and the fallbacks read the
     * same files anyway.
     */
    private static int wearEdge(IResourceManager manager) {
        int edge = WearPatterns.ART_SIZE;
        edge = Math.max(edge, WearPatterns.squareSize(WearPatterns.readIcon(manager, "dirt")));
        edge = Math.max(edge, WearPatterns.squareSize(WearPatterns.readIcon(manager, "stone")));
        return edge;
    }

    /**
     * How many appearances the fallback tier plans, asked of the helper planChain plans them with,
     * so the price and the plan cannot differ. The tier is never cut by any limit: truncating it
     * does not give a surface coarser art, it gives it none at all - it is the last thing every
     * lookup falls through to - and the ground simply stops wearing part way along its run.
     */
    private static int fallbackAppearances() {
        int total = 0;
        for (SurfaceFamily family : SurfaceFamily.values()) {
            if (family.staged) total += pricedAppearances(appearancesFor(null, family));
        }
        return total;
    }

    /**
     * How many pictures a run is being drawn with this stitch, which is not always what was asked
     * for.
     *
     * <p>
     * Read by {@code GhostRendering.eased}, which stops rationing pictures among chain steps once
     * there are enough of them to go round. The answer is whatever survived the config, the atlas
     * and the pre-flight, so it is asked of the table rather than of the setting.
     */
    /**
     * Whether the wear pictures have been composed yet.
     *
     * <p>
     * Not the same question as {@link #drawnGradations()}, which answers eight before a single sprite
     * exists because the empty table is built with the shipped default in it. Anything waiting for the
     * stitch has to ask this instead - the verification harness does, because a world opened before
     * the pictures are installed is rendered against a half-built atlas, and on 1.16.5 Fabric that is
     * a crash in the renderer rather than a missing texture.
     */
    public static boolean composed() {
        return lookup != Lookup.EMPTY;
    }

    public static int drawnGradations() {
        return lookup.layers;
    }

    /** Rotations actually stitched, which may be fewer than the requested count. */
    public static int drawnRotations() {
        return lookup.rotations;
    }

    /** Called from {@code TextureStitchEvent.Pre} for the block atlas. */
    public static void registerAll(final TextureMap map) {
        // First, before a single wear sprite of this stitch is registered or loaded, so none of them has yet been
        // handed a placeholder. The placeholder chains and the scratch pictures a moving layer redraws into are held
        // one per edge in static maps, and nothing needs one once its stitch is over: every sprite the last stitch
        // built has frames of its own, every one it could not build was uploaded and let go of its frames, and vanilla
        // empties its list of moving sprites before this event, so no old sprite asks for a scratch picture again.
        // Left alone, a pack tried once at a large edge would keep that edge's chains for the rest of the session. A
        // sprite still holding an old placeholder keeps its own reference to the array, so nothing drawn changes.
        WearSprite.forgetPlaceholders();
        TWINS.clear();
        EMPTY_GLOWS.clear();
        sideSpritesNamed = 0;

        // Taken once, here, and held for the whole stitch. It sizes the icon table and every
        // index into it, so a value that changed part way through would scramble the lookup.
        // The floor stays at two rather than rising to the slider's sixteen. Forge's ranged getInt
        // clamps client.wearGradations to sixteen or more whenever the file is read, but choosing
        // custom on the quality chooser restores the numbers presets.remembered holds straight into
        // the setting, without that range, so a hand-edited remembered string can still hand this
        // fewer than sixteen until the config is next read.
        spriteLayers = Math.max(2, Math.min(WearScale.COUNTED_STEPS, TrmtConfig.wearGradations));
        rotations = Math.max(1, Math.min(4, TrmtConfig.wearRotations));
        turnsMode = WearTurns.mode(TrmtConfig.wearTurns);
        TURNED.clear();

        // Dropped before anything is measured rather than after it. This method runs again on
        // every re-stitch now, and the pre-flight below reads dirt and stone back through the very
        // same cache: left where it was, a second stitch would size the whole atlas budget from the
        // pack loaded at the previous one. The header widths are not dropped here, because the
        // measure filled them a moment ago for this very stitch - unless the measure never ran, in
        // which case whatever they hold is an earlier stitch's.
        WearPatterns.clearCaches();
        if (!roomHookRan) WearPatterns.clearHeaderWidths();
        // The enabled packs' looks files, read again before anything is planned (W3 T5): the game stitches this atlas
        // before any mod's reload listener runs, so the pack reader's own listener would hand this stitch the looks of
        // the packs before the change. A reload that changed no pack's data re-merges nothing.
        com.trmtgtnh.client.PackData.beforeStitch();
        // This stitch's layers behind blocks, before the sets are planned: a set is keyed on the layer as well
        // as the face and the family. See planSurfaceSets.
        InnerLayers.readTable();

        IResourceManager manager = Minecraft.getMinecraft()
            .getResourceManager();
        AtlasPlan.Measure measure = measureThisStitch();
        int edge = wearEdge(manager);
        // Automatic, Automatic at least 2 pixels, or a fixed border (Xep, 2026-11-18): each walks the narrower gutters
        // after its own, and chooses down to its floor before a narrower one is a fallback (SmallGutter.floorIndex).
        int gutterMode = SmallGutter.fixedGutter(gutterSetting());
        int[] gutters = SmallGutter.candidates(holderGutter, gutterMode);
        int floor = SmallGutter.floorIndex(gutters, gutterMode);
        chosenGutter = gutters[0];
        FacePricing pricing = new FacePricing(manager, edge, chosenGutter);
        // Read once, and every walk below takes this one list. The surface table is rebuilt whenever
        // the ids move, on whichever thread moved them, and walks that each read it afresh could hand
        // the census one table and the gate another. Natural ground first (AtlasPlan.naturalFirst), so
        // a limit that cuts faces cuts decorative variants before the ground a path is worn across.
        List<SurfaceRegistry.SurfaceState> surfaces = AtlasPlan
            .naturalFirst(SurfaceRegistry.texturableStates(), NATURAL_GROUND);
        StateFiling.Builder<Block, Boolean> mendsWanted = mendedSidesWanted(surfaces);
        StateFiling.Builder<Block, Boolean> wallsWanted = grassWallsWanted(surfaces);
        // Every edge worked out once, here, and used both to price the plan and to register the sprites, so what goes
        // to the stitcher is what the plan priced rather than a second reading of the same files.
        int[][] fallbackEdges = pricing.fallbackEdges();
        StateFiling.Builder<Block, Integer> mendEdges = pricing.sideEdges(mendsWanted);
        StateFiling.Builder<Block, Integer> wallEdges = pricing.sideEdges(wallsWanted);
        int fallbackWallEdge = pricing.fallbackWallEdge();
        int fringeEdge = pricing.fringeEdge();
        boolean perSurface = TrmtConfig.perSurfaceTextures && TrmtConfig.maxTexturedSurfaces > 0;

        // Walked once without planning anything, so that the plan prices the sets this walk will
        // actually make - one per face and family, a lawn with its earth, each at the size of the face
        // it is built from - rather than one per block, which is what let the pre-flight hand the
        // planner a ramp the atlas could not hold. Walked whenever per-surface textures are on, at a
        // ceiling of zero as well, so that the log can say how many faces that ceiling turned away, and
        // every set it records is priced, past the ceiling too, so that the log can say what raising
        // the ceiling would keep; the plan prices none past it, so what that costs is the walk and a
        // header already read. The walk asks nothing but block icons, the registry and those headers,
        // and the second walk below is held by its gate to what this one recorded.
        // Under Angelica the gutter gives way first (Xep, 2026-11-15: "higher where it can be, and lower as it needs
        // to be"): each candidate, largest first, is priced and walked, and the first at which the plain plan keeps
        // every face at every rotation and gradation asked for, with the holders predicted inside nine tenths of the
        // target square, is the gutter. Where none is, the smallest is, and the plan below gives way in its own
        // order. Without Angelica, or with a gutter set by hand, there is one candidate and nothing is tried.
        // Every candidate is walked and planned, and SmallGutter.choose takes the widest whose plan keeps as much as
        // the
        // narrowest's (2026-11-17): a limit every gutter meets alike, client.maxWearSprites, had taken GT New Horizons
        // 2.9.0 to 0 pixels with the atlas 42% full. The six walks of that pack take under a second together.
        AtlasPlan.Census census = null;
        AtlasPlan.Wish wish = null;
        AtlasPlan[] gutterPlans = new AtlasPlan[gutters.length];
        long[] predicted = new long[gutters.length];
        FacePricing[] pricings = new FacePricing[gutters.length];
        AtlasPlan.Census[] censuses = new AtlasPlan.Census[gutters.length];
        AtlasPlan.Wish[] wishes = new AtlasPlan.Wish[gutters.length];
        String gutterWhy = gutters.length == 1 ? (holderGutter > 0 ? "set by client.angelicaWearGutter" : null)
            : "the smallest tried, because every wider gutter keeps less than it does or takes more than "
                + SmallGutter.fillPerMille(measure.fillPerMille) / 10
                + "% of the "
                + SmallGutter.target(glMaximum)
                + "-pixel atlas for "
                + stitcher.said
                + ", so the plan gives way after it";
        for (int tried = 0; tried < gutters.length; tried++) {
            chosenGutter = gutters[tried];
            pricing = new FacePricing(manager, edge, chosenGutter);
            census = new AtlasPlan.Census();
            if (TrmtConfig.perSurfaceTextures) {
                planSurfaceSets(
                    surfaces,
                    StateFiling.<Block, Integer>builder(),
                    null,
                    FIRST_SURFACE_SET,
                    census,
                    pricing);
            }
            long setPixels = pricing.pricedPixels();
            long setOnePixels = pricing.pricedOnePixels();
            // The family fallbacks client.wearTurns' turn only draws with one pattern: those whose own face turns.
            int fallbackOneAppearances = 0;
            long fallbackOneCells = 0L;
            long fallbackOnePixels = 0L;
            if (WearTurns.TURN.equals(turnsMode)) {
                for (SurfaceFamily family : SurfaceFamily.values()) {
                    if (!family.staged || !pricing.fallbackTurns(family)) continue;
                    int[] row = fallbackEdges[family.ordinal()];
                    for (int each : row) if (each > 0) fallbackOneAppearances++;
                    fallbackOneCells += pricing.holderCellsOf(new int[][] { row });
                    fallbackOnePixels += pricing.pixelsOf(row);
                }
            }
            wish = new AtlasPlan.Wish(
                edge,
                spriteLayers,
                MIN_GRADATIONS,
                rotations,
                fallbackAppearances(),
                pricing.holderCellsOf(fallbackEdges),
                pricing.singleHolderCells(fringeEdge),
                mendsWanted.states() + wallsWanted.states() + 1, // + the fallback wall
                pricing.holderCellsOf(mendEdges) + pricing.holderCellsOf(wallEdges)
                    + pricing.singleHolderCells(fallbackWallEdge),
                census.sets(),
                census.cells(),
                TrmtConfig.maxTexturedSurfaces,
                TrmtConfig.maxWearSprites,
                fallbackOneAppearances,
                fallbackOneCells,
                census.ones(),
                census.glows(),
                census.glowLit(),
                census.glowCells(),
                fallbackGlows(),
                fallbackGlowLit(),
                pricing.glowCellsOf(fallbackEdges))
                    // The same prices in exact pixels, which the plan uses where the measure names a measured
                    // stitcher (0.9.229, glow-pixels; StitcherKind), and ignores otherwise.
                    .withPixels(
                        census.pixels(),
                        census.glowPixels(),
                        pricing.pixelsOf(fallbackEdges),
                        fallbackOnePixels,
                        pricing.glowPixelsOf(fallbackEdges),
                        pricing.pixelsOf(new int[] { fringeEdge }),
                        pricing.pixelsOf(mendEdges) + pricing.pixelsOf(wallEdges)
                            + pricing.pixelsOf(new int[] { fallbackWallEdge }));
            if (gutters.length == 1) break;
            // The plan the stitch would make at this gutter, which gives way in its own order where anything is short.
            AtlasPlan whole = AtlasPlan.planForBorderBudget(measure, wish);
            gutterPlans[tried] = whole;
            pricings[tried] = pricing;
            censuses[tried] = census;
            wishes[tried] = wish;
            if (whole == null) continue;
            // Judged on exact pixels at that plan's gradations and rotations: the plan's slots round each set's
            // one-gradation unit up to whole slots before multiplying it by every gradation and rotation, which is
            // right for its budget and too much for a fit the real packer is held to
            // (SmallGutterTest.the_prediction_sums_exact_holder_pixels).
            predicted[tried] = SmallGutter.ownPixels(
                setPixels,
                setOnePixels,
                pricing.pixelsOf(fallbackEdges),
                fallbackOnePixels,
                pricing.pixelsOf(new int[] { fringeEdge }),
                pricing.pixelsOf(mendEdges) + pricing.pixelsOf(wallEdges)
                    + pricing.pixelsOf(new int[] { fallbackWallEdge }),
                whole.gradations,
                whole.wish.rotations) + whole.packCells * AtlasPlan.CELL * AtlasPlan.CELL;
        }
        if (gutters.length > 1) {
            int at = SmallGutter
                .choose(gutterPlans, predicted, SmallGutter.target(glMaximum), measure.fillPerMille, floor);
            chosenGutter = gutters[at];
            pricing = pricings[at];
            census = censuses[at];
            wish = wishes[at];
            AtlasPlan kept = gutterPlans[at];
            long pixels = predicted[at];
            String share = pixels + " holder pixels predicted, "
                + (pixels * 100L / ((long) SmallGutter.target(glMaximum) * SmallGutter.target(glMaximum)))
                + "% of the "
                + SmallGutter.target(glMaximum)
                + "-pixel atlas, at most "
                + SmallGutter.fillPerMille(measure.fillPerMille) / 10
                + "%"
                + (measure.fillPerMille > 0 ? " for " + stitcher.said + ", priced in exact pixels"
                    : " for " + stitcher.said + ", priced in whole slots");
            String widest = java.util.Arrays.toString(java.util.Arrays.copyOf(gutters, floor + 1));
            String how = gutterMode == SmallGutter.AUTO ? "chosen automatically as the widest of "
                : "chosen automatically, at least " + SmallGutter.FLOOR + " pixels, as the widest of ";
            if (SmallGutter.fellBack(at, floor)) {
                // The border asked cannot keep every face even at one rotation and one gradation, or its holders are
                // past the stitcher's share: the widest narrower one that can (2026-11-18).
                gutterWhy = "narrower than the " + gutters[floor]
                    + " pixels client.angelicaWearGutter asks, because at "
                    + gutters[floor]
                    + " not every textured face fits even at one rotation and one gradation, or the holders take more than "
                    + SmallGutter.fillPerMille(measure.fillPerMille) / 10
                    + "% of the atlas: the widest of "
                    + java.util.Arrays.toString(java.util.Arrays.copyOfRange(gutters, floor + 1, gutters.length))
                    + " that keeps them, or the smallest; "
                    + keptSaid(kept, wish)
                    + "; "
                    + share;
            } else if (gutterMode >= 0) {
                gutterWhy = "set by client.angelicaWearGutter, rotations giving way first and then gradations: "
                    + keptSaid(kept, wish)
                    + "; "
                    + share;
            } else if (SmallGutter.keepsEverything(kept, wish.gradations, wish.rotations, wish.surfaceCeiling)
                && SmallGutter.fits(pixels, SmallGutter.target(glMaximum), measure.fillPerMille)) {
                    gutterWhy = how + widest
                        + " at which all "
                        + kept.surfaceSetsConsidered
                        + " textured faces, "
                        + wish.rotations
                        + " rotations and "
                        + wish.gradations
                        + " gradations fit: "
                        + share;
                } else if (at < floor) {
                    gutterWhy = how + widest
                        + " that keeps as much as the smallest does, since the plan gives way alike at every gutter: "
                        + keptSaid(kept, wish)
                        + "; "
                        + share;
                } else if (gutterMode == SmallGutter.AT_LEAST_TWO) {
                    gutterWhy = how + widest
                        + ", the narrowest it takes while every face fits, rotations giving way first and then gradations: "
                        + keptSaid(kept, wish)
                        + "; "
                        + share;
                }
        }
        if (gutterWhy != null) Trmt.LOG.info(LINE_CHOSEN_GUTTER, Integer.valueOf(chosenGutter), gutterWhy);

        // Price every Holder before registration. The safety cap reduces rotations, then gradations
        // down to one while retaining every face; shared family art is the last resort.
        AtlasPlan plan = AtlasPlan.planForBorderBudget(measure, wish);
        if (plan == null) {
            capacityDisabled = true;
            lookup = Lookup.EMPTY;
            REGISTERED.clear();
            fringesRegistered = 0;
            registeredThisStitch = 0;
            lastPlan = null;
            Trmt.error(
                "Worn art cannot fit even its shared family tier in the {}-pixel block atlas with Angelica's {}-pixel gutters; covered blocks keep their own icons.",
                Integer.valueOf(Math.min(glMaximum > 0 ? glMaximum : AtlasPlan.LARGEST_SIDE, AtlasPlan.LARGER_SIDE)),
                Integer.valueOf(holderGutter));
            return;
        }
        capacityDisabled = false;
        // Without Angelica's gutter the gutter line above is not written, so a plan held to the stitcher's own share
        // says so here (0.9.229, stitch-cap).
        if (plan.pixels && holderGutter <= 0) {
            Trmt.LOG.info(
                LINE_STITCHER_SHARE,
                Integer.valueOf(measure.fillPerMille / 10),
                Integer.valueOf(measure.stitchSide),
                stitcher.why);
        }
        if (plan.wish.surfaceCeiling == 0 && TrmtConfig.maxTexturedSurfaces > 0 && census.sets().length > 0) {
            Trmt.LOG.warn(
                "Atlas room cannot hold all {} textured faces even at one gradation and one rotation; using one shared worn look per family.",
                Integer.valueOf(census.sets().length));
        }
        spriteLayers = plan.gradations;
        rotations = plan.wish.rotations;
        if (!plan.measure.taken) warnUnmeasured(plan);

        StateFiling.Builder<Block, Integer> states = StateFiling.<Block, Integer>builder();
        REGISTERED.clear();
        final StateFiling.Builder<Block, IIcon> mended = StateFiling.<Block, IIcon>builder();
        List<SpritePlan> plans = new ArrayList<SpritePlan>();

        // Fallbacks first. They are what every surface set falls through to for an appearance its
        // own set does not hold - which is now every appearance a family wears through INTO, since
        // there is nothing in a granite block to make cobble out of and those live once per family
        // rather than once per block. Planned last, as they were, they were the first thing to be
        // dropped when the budget ran out, and dropping them does not degrade a surface to generic
        // art: it leaves it with no art at all, because there is nothing further to fall through
        // to. A stone road stopped showing anything at all past its sixteenth gradation.
        //
        // A fallback appearance at eighty gradations and four rotations is three hundred and twenty
        // sprites. Out of the box the families come to eleven appearances - one for each staged
        // family, and one more for the earth under grass, which general.grassWearsThroughToDirt ships
        // switched on - so three thousand five hundred and twenty sprites: about one and a third per
        // cent of an 8192 square at sixteen pixels and about five and a third at thirty-two.
        // general.wearThroughToOtherSurfaces ships switched off; turned on, or on a server whose rules
        // turn it on, every family a chain then wears through into adds one, seventeen at the defaults,
        // five thousand four hundred and forty sprites, about two per cent and about eight. Everything
        // else in the atlas depends on them, which is why the plan prices them first and never cuts them.
        for (SurfaceFamily family : SurfaceFamily.values()) {
            if (!family.staged) continue;
            boolean turns = pricing.fallbackTurns(family);
            if (turns) TURNED.set(family.ordinal());
            planChain(plans, family.ordinal(), null, 0, family, fallbackEdges[family.ordinal()], turns);
        }

        int nextSet = FIRST_SURFACE_SET;
        if (perSurface) nextSet = planSurfaceSets(surfaces, states, plans, nextSet, plan.gate(), pricing);

        setCount = nextSet;
        icons = new IIcon[setCount * FAMILIES * spriteLayers * rotations];
        glowIcons = new IIcon[icons.length];
        overlaySprites = new IIcon[spriteLayers * rotations];
        final StateFiling.Builder<Block, IIcon> walls = StateFiling.<Block, IIcon>builder();

        glowsRegistered = 0;
        for (SpritePlan planned : plans) {
            register(map, planned);
        }
        Trmt.LOG.info(
            LINE_GLOWS,
            Integer.valueOf(glowsRegistered),
            Integer.valueOf(TailoredGround.END_GLOW_LEVEL),
            Integer.valueOf(TailoredGround.NETHER_GLOW_LEVEL));

        // One more per covered block that draws itself and cuts its own side away for a pass
        // underneath. Cheap - a handful of blocks in a pack, one sprite each - and it is the
        // difference between a worn Biomes O' Plenty lawn showing its own loam and showing
        // vanilla soil.
        mendEdges.forEach(new StateFiling.Visitor<Block, Integer>() {

            @Override
            public void visit(Block block, int meta, Integer sideEdge) {
                registerMendedSide(map, mended, block, meta, SurfaceFamily.GRASS, sideEdge.intValue());
            }
        });

        // The grass-side wear set, generated whether or not the feature is switched on so a
        // mid-session toggle takes without a re-stitch. A grey receding fringe per gradation and
        // rotation, and one de-greened wall per grass block plus a fallback for an unknown origin.
        fringesRegistered = 0;
        for (int stage = 0; stage < spriteLayers; stage++) {
            for (int rotation = 0; rotation < rotations; rotation++) {
                registerFringe(map, stage, rotation);
            }
        }
        wallEdges.forEach(new StateFiling.Visitor<Block, Integer>() {

            @Override
            public void visit(Block block, int meta, Integer sideEdge) {
                registerGrassWall(map, walls, block, meta, sideEdge.intValue());
            }
        });
        IIcon fallbackWall = registerGrassWallFallback(map, fallbackWallEdge);

        // Published whole, once everything it holds is registered. The ids beside the filings are
        // read here, where they are still the ids the stitch was made under, and serve only the line
        // written when they next move.
        StateFiling<Block, Integer> sets = states.build();
        StateFiling<Block, IIcon> mendedBuilt = mended.build();
        StateFiling<Block, IIcon> wallsBuilt = walls.build();
        StateFiling<Block, Boolean> filed = everyStateFiled(sets, mendedBuilt, wallsBuilt);
        lookup = new Lookup(
            icons,
            glowIcons,
            overlaySprites,
            spriteLayers,
            rotations,
            sets,
            mendedBuilt,
            wallsBuilt,
            fallbackWall,
            filed,
            filed.numbersNow(BLOCK_NUMBERING),
            plan.pictureMap(WearScale.COUNTED_STEPS),
            (java.util.BitSet) TURNED.clone(),
            turnsMode);
        reportFiling(sets, mendedBuilt, wallsBuilt);
        reportTurns(setCount, pricing);

        registeredThisStitch = REGISTERED.size() + fringesRegistered;
        lastPlan = plan;
        reportPlan(plan, plans.size(), setCount, registeredThisStitch, TrmtConfig.perSurfaceTextures);
    }

    /**
     * The stitcher this stitch's plan was made for (0.9.229, glow-pixels). Under Angelica's gutter the plan prices
     * this mod's holders in exact pixels against the share of the square that stitcher was measured to fill; without
     * that gutter, or for a stitcher nobody measured, in whole slots as before.
     */
    private static volatile StitcherKind stitcher = StitcherKind.UNMEASURED;

    /**
     * What the measuring injection noted this stitch or, when it never ran, the card's size and the filtering setting
     * and nothing else, which AtlasPlan then treats as half taken. Filtering is read either way, because the line
     * written as the atlas goes to the stitcher adds missingno at the size filtering gives it, and the sprite pass
     * warns by it where not one face read out of the atlas was marked as loaded with the border, measured or not.
     * Gated on the flag rather than on the figures, because an earlier stitch's figures are not this one's.
     *
     * <p>
     * No bound on the atlas's sprites is passed ({@code Measure.spriteRoom}, 1.0.0, canvas-sprites): that bound is for
     * Canvas for 1.16.5, which keeps a texture with a row for every sprite of the atlas and cannot upload one taller
     * than the card addresses. None of this edition's renderers, vanilla, OptiFine and Angelica, keeps such a texture.
     */
    private static AtlasPlan.Measure measureThisStitch() {
        if (roomHookRan) {
            int maximum = glMaximum > 0 ? glMaximum : AtlasPlan.LARGEST_SIDE;
            int stitchSide = holderGutter > 0 ? Math.min(maximum, AtlasPlan.LARGER_SIDE) : AtlasPlan.side(glMaximum);
            stitcher = StitcherKind.detect();
            return new AtlasPlan.Measure(
                true,
                packDemand,
                packRead,
                packReadCells,
                glMaximum,
                packAnisotropic,
                stitchSide,
                holderGutter,
                stitcher.fillPerMille(glMaximum, stitchSide, holderGutter > 0));
        }
        Minecraft game = Minecraft.getMinecraft();
        return new AtlasPlan.Measure(
            false,
            0,
            0,
            0L,
            Minecraft.getGLMaximumTextureSize(),
            game != null && game.gameSettings != null && game.gameSettings.anisotropicFiltering > 1);
    }

    /**
     * The first sentence of the plan's line, which reads the same whether or not the atlas was
     * measured; only its clause about faces turns on client.perSurfaceTextures.
     */
    private static final String LINE_A = "Registered {} wear textures across {} surface sets, and {} sprites in all once the grass fringes and side walls are counted: {} gradations at {} rotations, ";

    private static final String LINE_A_FACES = "with {} of {} textured faces wearing their own pixels. ";

    private static final String LINE_A_NO_FACES = "with no face wearing its own pixels, because client.perSurfaceTextures is off, which the potato quality rung also does. ";

    /**
     * The measured clause of line A in three parts, four placeholders, then one or none, then three; the middle part
     * turns on whether any of the pack's files could be read, because where none could the pack was priced at dirt
     * and stone's edge, as line P_NONE has just said, and not from its files.
     */
    private static final String LINE_A_MEASURED = "They were planned into {} of the {} block atlas slots left free, measured with {} textures already registered and priced at {} slots ";

    private static final String LINE_A_FROM_FILES = "from their own files";

    private static final String LINE_A_FROM_EDGE = "because none of their files could be read, each at dirt and stone's {}-pixel edge";

    private static final String LINE_A_CARD = ", on a card that addresses {} pixels, to a {}-pixel square, with anisotropic filtering {}; ";

    private static final String LINE_A_UNMEASURED = "They were planned into {} of the {} slots left in a {}-pixel square once a sixteenth is held back for the stitcher and half of it is taken as the rest of the pack's, because nothing measured the atlas this stitch; ";

    private static final String LINE_A_PRICED = "every wear sprite is priced at the size of the texture it is made from, and one made from a face whose file cannot be read at the {}-pixel edge of dirt and stone; each is stitched and composed at exactly the size it was priced at.";

    private static final String LINE_P_ALL = "Read the widths of all {} of the block atlas's textures from the headers of their files in {} ms, without decoding any: they come to {} slots, each edge rounded up to whole sixteen-pixel slots";

    private static final String LINE_P_SOME = "Read the widths of {} of the block atlas's {} textures from the headers of their files in {} ms, without decoding any: {} slots, each edge rounded up to whole sixteen-pixel slots";

    private static final String LINE_P_GROWN = " after sixteen pixels are added to its width and height for anisotropic filtering";

    private static final String LINE_P_REST = ". The other {} could not be read - a sprite with a loader of its own and no file at its name, a missing file, or a file no image reader recognises - and are priced at the average of those that could, which puts the pack at {} slots.";

    private static final String LINE_P_NONE = "None of the block atlas's {} textures could be read from the headers of their files, so each is priced at dirt and stone's {} pixels{}, {} slots in all, which may be far from what the pack really takes; on a stitch that loads its textures, which is every one but Forge's skipped first stitch at start-up, the line written as the atlas goes to the stitcher says what it did take.";

    /**
     * Says what the plan came to, every stitch, whether or not anything had to give. The second
     * sentence is the proof the measured cap ran at all: nothing that planned without the measure
     * could print it.
     */
    private static void reportPlan(AtlasPlan plan, int plannedSprites, int sets, int registered, boolean perSurface) {
        AtlasPlan.Measure measure = plan.measure;
        if (measure.taken && measure.packSprites > 0) reportPackRead(plan);

        StringBuilder text = new StringBuilder(LINE_A);
        List<Object> args = new ArrayList<Object>();
        args.add(Integer.valueOf(plannedSprites));
        args.add(Integer.valueOf(sets));
        args.add(Integer.valueOf(registered));
        args.add(Integer.valueOf(plan.gradations));
        args.add(Integer.valueOf(plan.wish.rotations));
        if (perSurface) {
            text.append(LINE_A_FACES);
            args.add(Integer.valueOf(plan.surfaceSetsKept));
            args.add(Integer.valueOf(plan.wish.surfaceSetCount()));
        } else {
            text.append(LINE_A_NO_FACES);
        }
        if (measure.taken) {
            text.append(LINE_A_MEASURED);
            args.add(Long.valueOf(plan.plannedCells));
            args.add(Long.valueOf(plan.room));
            args.add(Integer.valueOf(measure.packSprites));
            args.add(Long.valueOf(plan.packCells));
            // The same test reportPackRead makes for line P_NONE, so the two lines of one stitch agree.
            if (measure.packSprites > 0 && measure.packRead <= 0) {
                text.append(LINE_A_FROM_EDGE);
                args.add(Integer.valueOf(plan.wish.wearEdge));
                if (measure.anisotropic) text.append(" grown by sixteen for anisotropic filtering");
            } else {
                text.append(LINE_A_FROM_FILES);
            }
            text.append(LINE_A_CARD);
            args.add(Integer.valueOf(measure.glMaximum));
            args.add(Integer.valueOf(plan.side));
            args.add(measure.anisotropic ? "on" : "off");
        } else {
            text.append(LINE_A_UNMEASURED);
            args.add(Long.valueOf(plan.plannedCells));
            args.add(Long.valueOf(plan.room));
            args.add(Integer.valueOf(plan.side));
        }
        text.append(LINE_A_PRICED);
        args.add(Integer.valueOf(plan.wish.wearEdge));
        Trmt.LOG.info(text.toString(), args.toArray());

        if (plan.wish.surfaceCeiling == 0 && TrmtConfig.maxTexturedSurfaces > 0) {
            if (plan.mandatoryOverruns()) warnFallbacksPastRoom(plan, perSurface);
            return;
        }

        if (plan.mandatoryOverruns()) {
            warnFallbacksPastRoom(plan, perSurface);
        } else if (plan.stoppedBy == AtlasPlan.Limit.ROOM) {
            warnRoomStopped(plan);
        }
        int requestedRotations = Math.max(1, Math.min(4, TrmtConfig.wearRotations));
        boolean safetyTurned = plan.wish.rotations < requestedRotations;
        if (holderGutter > 0 && plan.wish.surfaceCeiling > 0 && plan.gradations < TrmtConfig.wearGradations) {
            Trmt.LOG.warn(
                "Atlas room reduced worn gradations from {} to {} and rotations from {} to {}; all {} textured faces retain their own colors.",
                Integer.valueOf(TrmtConfig.wearGradations),
                Integer.valueOf(plan.gradations),
                Integer.valueOf(requestedRotations),
                Integer.valueOf(plan.wish.rotations),
                Integer.valueOf(plan.surfaceSetsKept));
        } else if (safetyTurned && plan.wish.surfaceCeiling > 0 && holderGutter == 0) {
            Trmt.LOG.warn(
                "Drawing {} rotations rather than {} to keep every textured face inside the {}-pixel block atlas; {} textured faces keep their own colors at {} gradations.",
                Integer.valueOf(plan.wish.rotations),
                Integer.valueOf(requestedRotations),
                Integer.valueOf(plan.side),
                Integer.valueOf(plan.surfaceSetsKept),
                Integer.valueOf(plan.gradations));
        } else if (safetyTurned && plan.wish.surfaceCeiling > 0) {
            Trmt.LOG.warn(
                "Drawing {} rotations rather than {} to keep this mod's sprites, at a {}-pixel Angelica gutter (client.angelicaWearGutter; the pack's own keep Angelica's {}), inside the {}-pixel block atlas; {} textured faces keep their own colors at {} gradations.",
                Integer.valueOf(plan.wish.rotations),
                Integer.valueOf(requestedRotations),
                Integer.valueOf(ownGutter()),
                Integer.valueOf(holderGutter),
                Integer.valueOf(plan.side),
                Integer.valueOf(plan.surfaceSetsKept),
                Integer.valueOf(plan.gradations));
        }
        if (plan.gradations < plan.wish.gradations && !plan.floorHeld() && !safetyTurned) {
            warnRampCoarsened(plan, perSurface);
        }
        if (plan.stoppedBy == AtlasPlan.Limit.CEILING) reportCeiling(plan);
        if (plan.stoppedBy == AtlasPlan.Limit.SPRITES) warnSpriteCeiling(plan);
    }

    /** Says how the pack's own textures were read and what they came to, before the plan's own line. */
    private static void reportPackRead(AtlasPlan plan) {
        AtlasPlan.Measure measure = plan.measure;
        if (measure.packRead <= 0) {
            Trmt.LOG.warn(
                LINE_P_NONE,
                new Object[] { Integer.valueOf(measure.packSprites), Integer.valueOf(plan.wish.wearEdge),
                    measure.anisotropic ? ", grown by sixteen for anisotropic filtering" : "",
                    Long.valueOf(plan.packCells) });
        } else if (measure.packUnread() == 0) {
            Trmt.LOG.info(
                LINE_P_ALL + (measure.anisotropic ? LINE_P_GROWN : "") + ".",
                new Object[] { Integer.valueOf(measure.packSprites), Long.valueOf(packReadMillis),
                    Long.valueOf(measure.packReadCells) });
        } else {
            Trmt.LOG.info(
                LINE_P_SOME + (measure.anisotropic ? LINE_P_GROWN : "") + LINE_P_REST,
                new Object[] { Integer.valueOf(measure.packRead), Integer.valueOf(measure.packSprites),
                    Long.valueOf(packReadMillis), Long.valueOf(measure.packReadCells),
                    Integer.valueOf(measure.packUnread()), Long.valueOf(plan.packCells) });
        }
    }

    /**
     * Line F: the fallbacks alone are past the room, and what, if anything, brings them back inside it. With
     * client.perSurfaceTextures off the census never ran, so no count of textured faces is given.
     */
    private static void warnFallbacksPastRoom(AtlasPlan plan, boolean perSurface) {
        boolean filtered = plan.measure.anisotropic;
        long spare = plan.squareCells - plan.reserveCells;
        boolean pastSquare = plan.mandatoryCells > spare;
        StringBuilder text = new StringBuilder(
            "The block atlas has {} slots free and the wear textures every surface falls back on need {} of them even at {} gradations, so no textured face gets wear of its own and this stitch may not fit at all. ");
        List<Object> args = new ArrayList<Object>();
        args.add(Long.valueOf(plan.room));
        args.add(Long.valueOf(plan.mandatoryCells));
        args.add(Integer.valueOf(plan.gradations));
        if (pastSquare) {
            text.append(
                "The family fallbacks, the grass fringe and the side walls alone want more than the {} slots a {}-pixel square has once a sixteenth is held back for the stitcher, whatever the rest of the pack takes. ");
            args.add(Long.valueOf(spare));
            args.add(Integer.valueOf(plan.side));
        } else if (plan.measure.taken) {
            text.append(
                "The pack's own {} textures were priced at {} slots of the {} a {}-pixel square has, and a sixteenth of that is held back for the stitcher. ");
            args.add(Integer.valueOf(plan.measure.packSprites));
            args.add(Long.valueOf(plan.packCells));
            args.add(Long.valueOf(plan.squareCells));
            args.add(Integer.valueOf(plan.side));
        } else {
            text.append(
                "Nothing measured the pack's own textures this stitch, so half of the {}-pixel square the atlas is planned to was taken as theirs; the room above is what that guess and the sixteenth held back for the stitcher leave, and the pack may really take more or less. ");
            args.add(Integer.valueOf(plan.side));
        }
        text.append(
            "The fallbacks are planned regardless, because without them worn ground has nothing to draw, and client.wearGradations cannot take the ramp any lower. If the game stops loading with 'Unable to fit', ");
        boolean tail = true;
        if (plan.wish.rotations > 1) {
            AtlasPlan atOne = plan.planAt(plan.wish.gradations, 1);
            if (atOne.mandatoryOverruns()) {
                text.append(
                    "setting client.wearRotations to one brings what they need down to {} slots, still more than the room; past that, ");
                args.add(Long.valueOf(atOne.mandatoryCells));
            } else {
                text.append("set client.wearRotations to one, which fits them in the room at {} gradations");
                args.add(Integer.valueOf(atOne.gradations));
                if (perSurface) {
                    // Off, the census never ran, and a nought of nought faces would read as detection
                    // finding none.
                    text.append(" and gives {} of the {} textured faces their own pixels");
                    args.add(Integer.valueOf(atOne.surfaceSetsKept));
                    args.add(Integer.valueOf(plan.wish.surfaceSetCount()));
                }
                text.append(".");
                tail = false;
            }
        } else {
            text.append("client.wearRotations is already at one, so ");
        }
        if (tail && pastSquare) {
            text.append(
                "only drawing what they are made from at a lower resolution makes them smaller: the vanilla ground textures the fallbacks and the fringe are read from, and each grass block's own side for its wall or mended side");
            text.append(".");
        } else if (tail && !plan.measure.taken) {
            text.append(
                "find out first why nothing measured the atlas, which the warning above describes, because the real room may be larger or smaller than this guess.");
        } else if (tail) {
            text.append(
                "the room has to grow: a resource pack that draws blocks at a lower resolution gives it where they are drawn larger than sixteen pixels, and so do fewer block textures");
            text.append(
                filtered
                    ? ", and so does turning anisotropic filtering off, which widens every block texture loaded from a file by sixteen pixels."
                    : ".");
        }
        Trmt.LOG.warn(text.toString(), args.toArray());
    }

    /** Line B: the room turned faces away with the ramp held at its floor, and what could bring them back. */
    private static void warnRoomStopped(AtlasPlan plan) {
        boolean belowFloor = plan.gradations < plan.wish.floor;
        StringBuilder text = new StringBuilder(
            belowFloor
                ? "Holding at {} gradations per run, the count client.wearGradations holds, which only a hand-edited presets.remembered quality string can put below sixteen, although the room left in the block atlas pays for only {}, so {} of {} textured faces wear their family's generic art rather than their own pixels. "
                : "Holding at {} gradations per run although the room left in the block atlas pays for only {}, so {} of {} textured faces wear their family's generic art rather than their own pixels. ");
        List<Object> args = new ArrayList<Object>();
        args.add(Integer.valueOf(plan.gradations));
        args.add(Long.valueOf(plan.affordable));
        args.add(Integer.valueOf(plan.surfacesDropped()));
        args.add(Integer.valueOf(plan.wish.surfaceSetCount()));
        text.append(
            "Priced at the size each is drawn at, one gradation of the family fallbacks, the grass fringe and every face within surfaces.maxTexturedSurfaces takes {} slots at {} rotations, the side walls {} more, and {} were free. ");
        args.add(Long.valueOf(plan.cellsPerGradation));
        args.add(Integer.valueOf(plan.wish.rotations));
        args.add(Long.valueOf(plan.sideCells));
        args.add(Long.valueOf(plan.room));
        text.append(
            belowFloor
                ? "Below sixteen the plan never draws fewer pictures than that setting asks for, so it is the faces last in the plan's order, vanilla's ground first and the rest by registry name, that gave way rather than the ramp. "
                : "Sixteen is the fewest pictures a run can be drawn with before consecutive gradations share one again, so it is the faces last in the plan's order, vanilla's ground first and the rest by registry name, that gave way rather than the ramp, and client.wearGradations cannot take it lower. ");
        AtlasPlan fewer = plan.fewerRotations();
        if (fewer != null && fewer.surfaceSetsKept > plan.surfaceSetsKept) {
            // Named against every textured face, because the figure is every face kept at that count,
            // not how many of those that gave way come back.
            text.append(
                "Setting client.wearRotations to {} gives {} of the {} textured faces their own pixels, {} more than now; raising surfaces.maxTexturedSurfaces or client.maxWearSprites cannot bring any back.");
            args.add(Integer.valueOf(fewer.wish.rotations));
            args.add(Integer.valueOf(fewer.surfaceSetsKept));
            args.add(Integer.valueOf(plan.wish.surfaceSetCount()));
            args.add(Integer.valueOf(fewer.surfaceSetsKept - plan.surfaceSetsKept));
        } else {
            text.append(
                fewer != null ? "No lower client.wearRotations brings any of them back, "
                    : "client.wearRotations is already at one, ");
            text.append(
                "and raising surfaces.maxTexturedSurfaces or client.maxWearSprites cannot either; only more room in the block atlas does, which a resource pack that draws blocks at a lower resolution gives where they are drawn larger than sixteen pixels, and so do fewer block textures");
            text.append(
                plan.measure.anisotropic
                    ? ", and so does turning anisotropic filtering off, which widens every block texture loaded from a file by sixteen pixels."
                    : ".");
        }
        Trmt.LOG.warn(text.toString(), args.toArray());
    }

    /**
     * Line C: the room drew a coarser ramp than was asked for, and which settings would move it. With
     * client.perSurfaceTextures off no face is priced or kept, so the line says the fallbacks alone set the
     * ramp rather than that faces still wear their own colors, which line A of the same stitch denies.
     */
    private static void warnRampCoarsened(AtlasPlan plan, boolean perSurface) {
        StringBuilder text = new StringBuilder(
            "Drawing {} gradations per run rather than {}. Priced at the size each is drawn at, {} appearances - ");
        text.append(
            perSurface
                ? "the family fallbacks, every textured face within surfaces.maxTexturedSurfaces and the grass fringe"
                : "the family fallbacks and the grass fringe");
        text.append(
            " - take {} slots a gradation at {} rotations, and with the side walls would want {} of the {} slots left in the block atlas at the gradations asked for. ");
        text.append(
            perSurface
                ? "Every face the ceilings admit still wears in its own colors; the ramp between its gradations is coarser."
                : "No face wears its own pixels, because client.perSurfaceTextures is off; it is the family fallbacks, the grass fringe and the side walls alone that the room could not hold at the gradations asked for, so only the ramp between their gradations is coarser.");
        List<Object> args = new ArrayList<Object>();
        args.add(Integer.valueOf(plan.gradations));
        args.add(Integer.valueOf(plan.wish.gradations));
        args.add(Long.valueOf(plan.pricedAppearances));
        args.add(Long.valueOf(plan.cellsPerGradation));
        args.add(Integer.valueOf(plan.wish.rotations));
        args.add(Long.valueOf(plan.cellsWanted));
        args.add(Long.valueOf(plan.room));
        if (plan.wish.rotations > 1) {
            text.append(
                " Setting client.wearRotations to one draws {} gradations, with fewer wear patterns across neighbouring blocks.");
            args.add(Integer.valueOf(plan.planAt(plan.wish.gradations, 1).gradations));
        } else {
            text.append(" client.wearRotations is already at one.");
        }
        if (plan.surfaceSetsConsidered > 0) {
            AtlasPlan bare = plan.withSettings(plan.wish.gradations, plan.wish.rotations, 0, plan.wish.spriteCeiling);
            if (bare.gradations > plan.gradations) {
                text.append(
                    " Lowering surfaces.maxTexturedSurfaces buys gradations back for fewer faces, the last in the plan's order, which puts vanilla's ground first, up to {} at zero, which is also what switching client.perSurfaceTextures off gives after a restart, with every face on its family's generic art.");
                args.add(Integer.valueOf(bare.gradations));
            }
        }
        if (plan.gradations > plan.wish.floor && plan.stoppedBy != AtlasPlan.Limit.SPRITES) {
            text.append(
                perSurface
                    ? " Lowering client.wearGradations brings no face back, only a coarser ramp, and set anywhere from {} to {} it changes nothing."
                    : " Lowering client.wearGradations only makes the ramp coarser, and set anywhere from {} to {} it changes nothing.");
            args.add(Integer.valueOf(plan.gradations));
            args.add(Integer.valueOf(plan.wish.gradations));
        }
        Trmt.LOG.warn(text.toString(), args.toArray());
    }

    /** Line D: surfaces.maxTexturedSurfaces turned faces away, and what raising it would actually keep. */
    private static void reportCeiling(AtlasPlan plan) {
        StringBuilder text = new StringBuilder(
            "Reached the textured-surface ceiling of {}; {} more faces use their family's shared fallback textures.");
        List<Object> args = new ArrayList<Object>();
        args.add(Integer.valueOf(plan.wish.surfaceCeiling));
        args.add(Integer.valueOf(plan.surfacesDropped()));
        int most = Math.min(plan.wish.surfaceSetCount(), MOST_TEXTURED_SURFACES);
        if (most <= plan.wish.surfaceCeiling) {
            text.append(" surfaces.maxTexturedSurfaces is already at its most, {}.");
            args.add(Integer.valueOf(MOST_TEXTURED_SURFACES));
        } else {
            AtlasPlan raised = plan
                .withSettings(plan.wish.gradations, plan.wish.rotations, most, plan.wish.spriteCeiling);
            int more = raised.surfaceSetsKept - plan.surfaceSetsKept;
            if (more > 0) {
                text.append(" Setting surfaces.maxTexturedSurfaces to {} gives {} more of them their own pixels");
                args.add(Integer.valueOf(most));
                args.add(Integer.valueOf(more));
                if (raised.gradations < plan.gradations) {
                    text.append(
                        ", drawn with {} gradations rather than {}, because the block atlas has not the room for more");
                    args.add(Integer.valueOf(raised.gradations));
                    args.add(Integer.valueOf(plan.gradations));
                }
                text.append(".");
            } else if (raised.stoppedBy == AtlasPlan.Limit.SPRITES) {
                text.append(
                    " Raising surfaces.maxTexturedSurfaces brings none of them in, because client.maxWearSprites turns the next one away.");
            } else {
                text.append(
                    " Raising surfaces.maxTexturedSurfaces brings none of them in, because the room left in the block atlas has no space for the next one even with the ramp held at {}.");
                args.add(Integer.valueOf(raised.gradations));
            }
        }
        Trmt.LOG.info(text.toString(), args.toArray());
    }

    /** Line E: client.maxWearSprites turned faces away, and which other settings would keep more of them. */
    private static void warnSpriteCeiling(AtlasPlan plan) {
        boolean tierPastCeiling = plan.mandatorySprites > plan.wish.spriteCeiling;
        boolean roomSetRamp = plan.gradations < plan.wish.gradations && !plan.floorHeld();
        StringBuilder text = new StringBuilder();
        List<Object> args = new ArrayList<Object>();
        if (tierPastCeiling) {
            text.append(
                "client.maxWearSprites is set to {}, but the family fallbacks, grass fringes and side walls come to {} sprites on their own at {} gradations and {} rotations, and those are planned whatever it says, so none of the {} textured faces wears its own pixels, and a ceiling below that figure saves nothing further. Setting client.maxWearSprites to {} lets the first of them in. ");
            args.add(Integer.valueOf(plan.wish.spriteCeiling));
            args.add(Long.valueOf(plan.mandatorySprites));
            args.add(Integer.valueOf(plan.gradations));
            args.add(Integer.valueOf(plan.wish.rotations));
            args.add(Integer.valueOf(plan.wish.surfaceSetCount()));
            // Set 0 exists and passed the room: SPRITES is only reported from inside the set loop,
            // after the room check.
            args.add(
                Long.valueOf(
                    plan.mandatorySprites + (long) plan.wish.surfaceSet(0) * plan.gradations * plan.wish.rotations));
        } else {
            text.append(
                "Stopped planning wear textures at {} sprites, within the {} client.maxWearSprites allows once the family fallbacks, grass fringes and side walls are counted, so {} of {} textured faces use their family's shared fallback textures. Raising client.maxWearSprites fits more of them in, up to the room the block atlas has. ");
            args.add(Long.valueOf(plan.plannedSprites));
            args.add(Integer.valueOf(plan.wish.spriteCeiling));
            args.add(Integer.valueOf(plan.surfacesDropped()));
            args.add(Integer.valueOf(plan.wish.surfaceSetCount()));
        }
        if (plan.gradations > plan.wish.floor) {
            AtlasPlan atFloor = plan.planAt(plan.wish.floor, plan.wish.rotations);
            if (atFloor.surfaceSetsKept > plan.surfaceSetsKept) {
                text.append(
                    "Setting client.wearGradations to sixteen gives {} of the {} textured faces their own pixels");
                args.add(Integer.valueOf(atFloor.surfaceSetsKept));
                args.add(Integer.valueOf(plan.wish.surfaceSetCount()));
                if (roomSetRamp) {
                    text.append(
                        ", though set anywhere from the {} drawn now up to the {} asked for it changes nothing, because the room set the ramp there");
                    args.add(Integer.valueOf(plan.gradations));
                    args.add(Integer.valueOf(plan.wish.gradations));
                }
                text.append(". ");
            } else {
                text.append("No client.wearGradations down to sixteen gives more of them their own pixels. ");
            }
        } else {
            text.append("client.wearGradations cannot take the ramp below the {} it is drawn with. ");
            args.add(Integer.valueOf(plan.gradations));
        }
        AtlasPlan fewer = plan.fewerRotations();
        if (fewer == null) {
            text.append("client.wearRotations is already at one.");
        } else if (fewer.surfaceSetsKept > plan.surfaceSetsKept) {
            text.append(
                "Setting client.wearRotations to {} gives {} of the {} textured faces their own pixels, {} more than now.");
            args.add(Integer.valueOf(fewer.wish.rotations));
            args.add(Integer.valueOf(fewer.surfaceSetsKept));
            args.add(Integer.valueOf(plan.wish.surfaceSetCount()));
            args.add(Integer.valueOf(fewer.surfaceSetsKept - plan.surfaceSetsKept));
        } else if (roomSetRamp) {
            text.append(
                "No lower client.wearRotations gives more of them their own pixels, because what a rotation saves goes into finer gradations first, up to the {} asked for.");
            args.add(Integer.valueOf(plan.wish.gradations));
        } else {
            text.append("No lower client.wearRotations gives more of them their own pixels.");
        }
        if (plan.floorHeld() && plan.gradations < plan.wish.gradations) {
            text.append(
                " The room left in the block atlas pays for only {} gradations, so the ramp is held at {} rather than the {} client.wearGradations asks for.");
            args.add(Long.valueOf(plan.affordable));
            args.add(Integer.valueOf(plan.gradations));
            args.add(Integer.valueOf(plan.wish.gradations));
        }
        Trmt.LOG.warn(text.toString(), args.toArray());
    }

    /** Line G: nothing measured the atlas, so the plan guessed at half, and where to look for why. */
    private static void warnUnmeasured(AtlasPlan plan) {
        Trmt.LOG.warn(
            "Nothing measured the block atlas for this stitch, so the wear textures are planned as though the rest of the pack had already taken half of the {}-pixel square the atlas is planned to. That is a guess, and a cautious one: the ramp may come out coarser than this pack needs, and a pack that really fills more than half may still not fit; on a stitch that loads its textures, which is every one but Forge's skipped first stitch at start-up, the line written as the atlas goes to the stitcher says what it really took. If the end of this stitch also says the injection that builds every worn picture never ran, none of this mod's injections into net.minecraft.client.renderer.texture.TextureMap found anything to attach to, and that line says where to look. Otherwise they did, and something else in the pack has rewritten TextureMap.loadTextureAtlas so that its call to ForgeHooksClient.onTextureStitchedPre is not where vanilla puts it; the measuring injection is optional so that such a pack still starts, and an optional injection that cannot find its call writes nothing to this log, so no earlier line will name the cause.",
            Integer.valueOf(plan.side));
    }

    /**
     * Says which family fallbacks came out unusable, because nothing else can.
     *
     * <p>
     * A fallback set is the last thing a lookup falls through to: if the slot for an appearance is
     * empty or holds a sprite whose source could not be read, that appearance simply does not draw
     * anywhere in the world, and the only symptom is ground that stops wearing partway along its
     * run. That happened once already and took a long time to find from the outside, so it says so
     * from the inside now.
     *
     * <p>
     * Said as the sprite pass ends, where a fallback that could not be built has been marked unusable. Until 0.9.212
     * it was said as the stitch was planned, before any sprite was built, while every sprite still counted as usable,
     * so it could only ever name an empty slot.
     */
    private static void reportFallbackGaps() {
        IIcon[] table = icons;
        StringBuilder gaps = new StringBuilder();
        for (SurfaceFamily base : SurfaceFamily.values()) {
            if (!base.staged) continue;
            // Every chain of the family's ground, the blocks' own as well (W3 T9).
            for (SurfaceFamily appearance : ErosionChain.appearancesOf(base)) {
                int at = slot(base.ordinal(), appearance, 0, 0);
                if (at >= 0 && at < table.length && usable(table[at])) continue;
                String pair = base.key() + "->" + appearance.key();
                if (gaps.indexOf(pair) >= 0) continue;
                if (gaps.length() > 0) gaps.append(", ");
                gaps.append(pair);
            }
        }
        if (gaps.length() == 0) return;
        Trmt.LOG.warn(
            "These appearances have no usable fallback texture and will not draw at all: {}. "
                + "Ground of those families stops showing wear at the point its chain reaches them.",
            gaps);
    }

    /**
     * Says what this stitch filed by block, every stitch, beside the plan's own line. Figures above
     * nought on a pack with modded surfaces are the proof the filing built is the one by block.
     */
    private static void reportFiling(StateFiling<Block, Integer> sets, StateFiling<Block, IIcon> mended,
        StateFiling<Block, IIcon> walls) {
        Trmt.LOG.info(
            "Filed wear by block rather than by block id: {} states of {} blocks wear pictures of their own, with {} mended sides and {} side walls, so a world or server that numbers blocks differently still finds each block's own.",
            new Object[] { Integer.valueOf(sets.states()), Integer.valueOf(sets.keys()),
                Integer.valueOf(mended.states()), Integer.valueOf(walls.states()) });
    }

    /**
     * Every state the three filings hold, each once, in the order they were filed: the sets, then
     * the mended sides, then the walls. What the line written when the ids move counts, so that a
     * grass block filed for its set, its wall and its mended side is one block and each of its
     * states one state. The see-through layers are not added, because every one of them is noted by
     * a sprite registered from a state these three already hold.
     */
    private static StateFiling<Block, Boolean> everyStateFiled(StateFiling<Block, Integer> sets,
        StateFiling<Block, IIcon> mended, StateFiling<Block, IIcon> walls) {
        final StateFiling.Builder<Block, Boolean> out = StateFiling.<Block, Boolean>builder();
        sets.forEach(new StateFiling.Visitor<Block, Integer>() {

            @Override
            public void visit(Block block, int meta, Integer set) {
                out.file(block, meta, Boolean.TRUE);
            }
        });
        StateFiling.Visitor<Block, IIcon> side = new StateFiling.Visitor<Block, IIcon>() {

            @Override
            public void visit(Block block, int meta, IIcon icon) {
                out.file(block, meta, Boolean.TRUE);
            }
        };
        mended.forEach(side);
        walls.forEach(side);
        return out.build();
    }

    /**
     * Says, each time the block ids move, how many blocks with wear of their own now carry an id other
     * than the one they had when the atlas was stitched, and whether every one still reads back as itself
     * through the ids now in force. The count is against the stitch, not the move before, so a move back
     * to the ids the atlas was stitched under reports none. Client thread only,
     * queued behind the rebuild of the surface table, so this line follows the one saying the ids
     * changed.
     *
     * <p>
     * Nothing is rebuilt and nothing re-stitched, because the atlas is filed by block object and no
     * move renumbers an object; this line is the evidence. Each filed block is numbered under the
     * ids in force and the number turned back into a block, exactly as a painted position is before
     * it is drawn, and that has to give back the same object. A block with no id at all is counted
     * apart from one whose id names another block: joining a server without a mod leaves that mod's
     * blocks unnumbered for the whole visit, and a block Forge substitutes another for in a world loses
     * its number the same way, even in single player; both are ordinary and warn of nothing, where a
     * number naming the wrong block is not ordinary at all.
     */
    public static void auditAfterIdMove() {
        Lookup current = lookup;
        StateFiling.Audit<Block> audit = current.filed.audit(current.filedNumbers, BLOCK_NUMBERING, AUDIT_EXAMPLES);

        StringBuilder text = new StringBuilder(
            "Block ids changed: {} of the {} blocks with wear of their own now carry a different id from the one they had when the atlas was stitched");
        List<Object> args = new ArrayList<Object>();
        args.add(Integer.valueOf(audit.moved));
        args.add(Integer.valueOf(audit.keys));
        if (!audit.movedExamples.isEmpty()) {
            text.append(" (for example {})");
            args.add(movedExamples(audit.movedExamples));
        }
        if (audit.misread > 0) {
            text.append(
                ", leaving out {} that read back as another block whatever id they carry, as the warning below says");
            args.add(Integer.valueOf(audit.misread));
        } else if (audit.absent > 0) {
            text.append(", and every one that has an id still reads back as itself");
        } else {
            text.append(", and every one still reads back as itself");
        }
        if (audit.absent > 0) {
            text.append(
                "; {} of them, with {} states, have no id under the ids in force, which a server without their mod leaves, or a block Forge has substituted in this world, and cannot appear in it");
            args.add(Integer.valueOf(audit.absent));
            args.add(Integer.valueOf(audit.absentStates));
        }
        text.append(".");
        Trmt.LOG.info(text.toString(), args.toArray());

        if (audit.misreadStates > 0) {
            Trmt.LOG.warn(
                "Block ids changed, and {} filed block states did not read back as their own block through the ids in force ({}). Nothing drawn looks them up by id, but ground painted over those blocks would be taken for another block; outside a proxy switching servers mid-session this should not happen.",
                Integer.valueOf(audit.misreadStates),
                blockNames(audit.misreadExamples));
        }
    }

    /** Renumbered blocks as {@code chisel:marble 2001 -> 2002}, one after another. */
    /** What a gutter's plan keeps of what was asked, for the gutter line: or that not even the shared tier fits. */
    private static String keptSaid(AtlasPlan kept, AtlasPlan.Wish wish) {
        if (kept == null) return "not even one shared look per family fits there";
        return kept.gradations + " of "
            + wish.gradations
            + " gradations at "
            + kept.wish.rotations
            + " of "
            + wish.rotations
            + " rotations, with "
            + kept.surfaceSetsKept
            + " of "
            + kept.surfaceSetsConsidered
            + " textured faces";
    }

    private static String movedExamples(List<StateFiling.Moved<Block>> moved) {
        StringBuilder out = new StringBuilder();
        for (StateFiling.Moved<Block> example : moved) {
            if (out.length() > 0) out.append(", ");
            out.append(nameOf(example.key));
            out.append(' ');
            out.append(example.from);
            out.append(" -> ");
            out.append(example.to);
        }
        return out.toString();
    }

    private static String blockNames(List<Block> blocks) {
        StringBuilder out = new StringBuilder();
        for (Block block : blocks) {
            if (out.length() > 0) out.append(", ");
            out.append(nameOf(block));
        }
        return out.toString();
    }

    /** A block's registry name, or whatever the block says of itself where the registry has none for it. */
    private static String nameOf(Block block) {
        String name = SurfaceRegistry.registryName(block);
        return name != null ? name : String.valueOf(block);
    }

    /**
     * Allocates one set per distinct face and family, for as long as the gate grants them, each priced
     * at the size of the face it is built from; with no plans list it only walks, for the census. Walks
     * the one list of surfaces the stitch read, and files each state under its block object.
     */
    private static int planSurfaceSets(List<SurfaceRegistry.SurfaceState> surfaces,
        StateFiling.Builder<Block, Integer> states, List<SpritePlan> plans, int nextSet, AtlasPlan.SetGate gate,
        FacePricing pricing) {

        // Across every block, not within one. A set is generated entirely from one face's pixels
        // and one family's chain, so two blocks reporting the same face and the same family
        // produce byte-for-byte the same sprites - and a stone stair, a stone slab, a doubled
        // stone slab and plain stone all report the face "stone". Scoped to a single block, as
        // this was, each of those claimed a set of its own and sixty-four sprites with it, which
        // is how letting shapes wear took the atlas from thirty-nine thousand sprites to
        // forty-six. Sharing costs nothing at all: the pixels are identical either way.
        //
        // Keyed on the family as well as the face, because the family decides which appearances a
        // set holds. Two blocks that look alike and erode differently must not share.
        //
        // And on the layer innerLayerTextures names behind the block, because that is painted into
        // every picture of the set: the same face with lava behind it and with water behind it are two
        // different sets of pictures. GTNH's Chisel gives its lavastone and waterstone faces of their
        // own, so this never met it; Chisel for 1.12.2 cuts both from one set of carvings, and keyed on
        // the face alone every waterstone there shared its lavastone's set - lava in its holes, and no
        // window filed. Found on 1.12.2 on 2026-10-08 and carried here to keep the rule one rule.
        Map<String, Integer> byFace = new HashMap<String, Integer>();

        for (SurfaceRegistry.SurfaceState state : surfaces) {
            if (gate.shut()) break;

            // Metadata values that draw the same face share one set of wear textures.
            //
            // A set used to be allocated per metadata value, which is only right for a block
            // whose subtypes actually look different. Most do not - sixteen variants of one
            // texture is common - and each of them was eating a slot out of the ceiling. The
            // budget ran out part way down a list sorted by registry name, so mods early in the
            // alphabet got their own textures and everything after fell back to a vanilla one,
            // which is why one pack's stone wore correctly and another's wore into plain stone.
            //
            // Which values to plan, and what each of them is made of, are both asked of the
            // registry rather than of the block, because a block is not a reliable witness about
            // itself. A doubled slab's getSubBlocks refuses the doubled item outright and hands
            // back an empty list, so all eight of its materials collapsed onto metadata zero and
            // every one of them wore the smooth white top of a stone slab - which is what a worn
            // nether brick road came out as. The registry worked out the family of each of the
            // sixteen values while it was classifying them, and that per-value answer is what
            // this loop wanted all along.
            //
            // Declared values first and merely claimed ones after, so that when the ceiling bites
            // it is the states a block admits to that keep their own pixels, and the ones only
            // detection believes in that fall back on a sibling.
            Integer[] firstOfFamily = new Integer[FAMILIES];
            boolean full = false;
            int[] declared = metasOf(state.block, plans != null);
            for (int pass = 0; pass < 2 && !full; pass++) {
                int count = pass == 0 ? declared.length : 16;
                for (int index = 0; index < count; index++) {
                    int meta = (pass == 0 ? declared[index] : index) & 0xF;
                    SurfaceFamily family = SurfaceRegistry.familyOf(state.block, meta);
                    if (family == null || !family.staged) continue;

                    if (states.has(state.block, meta)) continue;

                    // A value whose block will not name its face is left to the rescue below,
                    // which gives it one set per family rather than one per value. Taking a set
                    // here instead would cost one for every claimed value of a block that throws
                    // on an undeclared metadata - a common enough shape - and the sprite would
                    // then fail the same call at build time and draw the family stand-in anyway.
                    String raw = FaceSource.iconName(state.block, meta, 1);
                    // Unless a looks file supplies the face, or its own pictures (Codex review R18): then the file's
                    // face is what the set is filed and drawn by, and the block's silence does not matter.
                    if (raw == null) raw = FaceSource.lookOf(state.block, meta)
                        .planFace();
                    if (raw == null) continue;

                    // And by the underside where the pictures are made of it (FaceRules.setKey, 0.9.223): keyed on the
                    // top alone, vanilla grass shared Biomes O' Plenty's loamy grass's set and wore into its loamy
                    // dirt.
                    String behind = InnerLayers.textureFor(state.block, meta);
                    // And by the block's own look where a looks file gives one (W3 T5): its look, strength, face and
                    // authored pictures are painted into every picture of the set, so only blocks with the same look
                    // share. Empty for a block no file names, which keys every set as before.
                    String face = FaceRules.setKey(
                        family,
                        raw,
                        FaceSource.iconName(state.block, meta, 0),
                        behind,
                        FaceSource.coverLook(TrmtConfig.family(family), state.block, meta))
                        + FaceSource.lookOf(state.block, meta)
                            .setKey();
                    // And by whether the face turns (0.9.229): two blocks drawing one face share its pixels whether or
                    // not they turn, but under turn only one is planned with one pattern and the other with all of
                    // them, and a name hint or a looks file can part blocks the pixels alone would not.
                    boolean turns = pricing.turns(state.block, meta, family);
                    if (turns) face += " turns";
                    Integer shared = byFace.get(face);
                    if (shared != null) {
                        states.file(state.block, meta, shared);
                        continue;
                    }

                    // The gate is asked per variant, not only once per block, because a block
                    // routinely brings sixteen variants - Chisel gives every chiselled face its own
                    // set - and the plan priced each set on its own. It grants exactly the sets the
                    // plan kept, at the slots it priced them, and refuses everything after its first
                    // refusal. Stopping here hands the rest of this block's variants to the fill
                    // below, which points them at a sibling of the same material: one gradation
                    // coarser, rather than a stranger's texture.
                    Set<SurfaceFamily> appearances = appearancesFor(state.block, family);
                    int[] edges = pricing.setEdges(state.block, meta, family, appearances);
                    boolean one = WearTurns.seeds(turnsMode, turns, rotations) == 1 && rotations > 1;
                    if (!gate.take(
                        pricedAppearances(appearances),
                        pricing.holderCellsOf(edges, one),
                        one,
                        glowCount(appearances),
                        glowLit(appearances),
                        pricing.glowCellsOf(edges),
                        pricing.setPixels(edges),
                        pricing.setGlowPixels(edges))) {
                        full = true;
                        break;
                    }
                    states.file(state.block, meta, Integer.valueOf(nextSet));
                    byFace.put(face, Integer.valueOf(nextSet));
                    if (turns && plans != null) TURNED.set(nextSet);
                    if (plans != null) planChain(plans, nextSet, state.block, meta, family, edges, turns);
                    nextSet++;
                }
            }

            for (int meta = 0; meta < 16; meta++) {
                SurfaceFamily family = SurfaceRegistry.familyOf(state.block, meta);
                if (family == null || !family.staged) continue;
                Integer set = states.get(state.block, meta);
                if (set != null && firstOfFamily[family.ordinal()] == null) {
                    firstOfFamily[family.ordinal()] = set;
                }
            }

            // A family of this block whose every value refused to name a face still gets a set,
            // as such a block always has: the sprite resolves the block's own pixels later, once
            // the atlas has finished, and frequently succeeds where the call above did not. What
            // it does not get any more is one set per value - sixteen claimed values of a
            // refusing block would take sixteen sets where the planner used to take one, and the
            // pixels behind them are the same block and the same family either way.
            for (int meta = 0; meta < 16 && !full; meta++) {
                SurfaceFamily family = SurfaceRegistry.familyOf(state.block, meta);
                if (family == null || !family.staged) continue;
                if (firstOfFamily[family.ordinal()] != null) continue;
                Set<SurfaceFamily> appearances = appearancesFor(state.block, family);
                int[] edges = pricing.setEdges(state.block, meta, family, appearances);
                // A block that names no face: nothing to read, so its names, a looks file and its family decide.
                boolean turns = pricing.turns(state.block, meta, family);
                boolean one = WearTurns.seeds(turnsMode, turns, rotations) == 1 && rotations > 1;
                if (!gate.take(
                    pricedAppearances(appearances),
                    pricing.holderCellsOf(edges, one),
                    one,
                    glowCount(appearances),
                    glowLit(appearances),
                    pricing.glowCellsOf(edges),
                    pricing.setPixels(edges),
                    pricing.setGlowPixels(edges))) {
                    full = true;
                    break;
                }
                states.file(state.block, meta, Integer.valueOf(nextSet));
                if (turns && plans != null) TURNED.set(nextSet);
                if (plans != null) planChain(plans, nextSet, state.block, meta, family, edges, turns);
                firstOfFamily[family.ordinal()] = Integer.valueOf(nextSet);
                nextSet++;
            }

            // Detection claims all sixteen metadata values; a block only declares the ones it
            // actually uses; and the world may hold either. Chisel's snakestone declares 1 and
            // 13 and nothing else, so a lookup at metadata 0 found no set and fell back to the
            // family's plain vanilla texture - which is why worn snakestone came out as stone
            // over genuine snakestone sides. Every metadata this block did not plan for itself
            // now points at a set it did. No extra sprites: the table simply stops having holes.
            //
            // At a sibling of the SAME family, and that is the whole of the difference. A vanilla
            // doubled slab is eight materials under one id, so the old fill - the first set
            // found, whatever it happened to be made of - pointed worn nether brick at its
            // block's stone sibling and drew smooth white stone over it. Where no sibling shares
            // the family, the value is left out entirely and the lookup falls through to that
            // family's own generic wear: coarser art, but the right material, which is the trade
            // that tier exists for.
            for (int meta = 0; meta < 16; meta++) {
                SurfaceFamily family = SurfaceRegistry.familyOf(state.block, meta);
                if (family == null || !family.staged) continue;
                if (states.has(state.block, meta)) continue;
                Integer set = firstOfFamily[family.ordinal()];
                if (set != null) states.file(state.block, meta, set);
            }
        }
        return nextSet;
    }

    /**
     * Plans one sprite per distinct look a surface of this family can take.
     *
     * <p>
     * Per <em>look</em>, not per step of the chain, and the difference is the whole reason this
     * comment exists. A chain is eighty steps long, but a step is an appearance, a visual layer
     * and a depth, and only the first two decide what gets drawn - the depth changes the shape of
     * the block, not its pixels. Layer three of bare earth is the same picture whether the ground
     * has dropped one pixel or eight, so it occurs eight times on the chain and needs exactly one
     * sprite.
     *
     * <p>
     * Planning it eight times instead put thirty-two thousand sprites into the block atlas, which
     * is more than it can hold: everything past the limit came out with meaningless coordinates
     * and rendered as flat black cubes.
     */
    private static void planChain(List<SpritePlan> plans, int set, Block origin, int meta, SurfaceFamily originFamily,
        int[] edges, boolean turns) {
        Set<SurfaceFamily> appearances = appearancesFor(origin, originFamily);
        // One pattern for a set that turns under client.wearTurns' turn only, which the plan priced (WearTurns.seeds).
        int seeds = WearTurns.seeds(turnsMode, turns, rotations);

        // A full run of gradations for each, rather than only the ones the chain visits. What is
        // drawn is not tied to a step any more: a sunken block is shown further along its run
        // than its own layer count would reach, so the gradations above that have to exist.
        for (SurfaceFamily appearance : appearances) {
            for (int stage = 0; stage < spriteLayers; stage++) {
                int stageCount = spriteLayers;
                for (int rotation = 0; rotation < seeds; rotation++) {
                    plans.add(
                        new SpritePlan(
                            set,
                            origin,
                            meta,
                            originFamily,
                            appearance,
                            stage,
                            stageCount,
                            rotation,
                            edges == null || appearance.ordinal() >= edges.length ? 0 : edges[appearance.ordinal()],
                            turns));
                }
            }
        }
    }

    /**
     * The appearances a set for this origin holds: every family on the chain for a fallback set,
     * and for a particular block only its own family, with the earth under a lawn. Asked by
     * planChain, which plans them, and by the census and the fallback count, which price them, so
     * the plan and the price cannot come apart.
     */
    private static Set<SurfaceFamily> appearancesFor(Block origin, SurfaceFamily originFamily) {
        // Every chain of the family's ground, its blocks' own as well (W3 T9): a block a data file gives a list of its
        // own may wear through to a surface the family's chain never reaches, and its fallback is planned here.
        Set<SurfaceFamily> onChain = new HashSet<SurfaceFamily>(ErosionChain.appearancesOf(originFamily));

        // A surface set is built from one block's own pixels, and a successor is a different
        // material: there is nothing in a granite block to make cobble out of. So a set for a
        // particular block plans only what can honestly be drawn from that block, and the
        // successors live once in the family's fallback set, which every surface falls through to
        // for an appearance its own set does not hold. That is the difference between six extra
        // appearance sets and one per stony block in the pack - three hundred and eighty-four
        // sprites against twenty-odd thousand, on an atlas with two thousand to spare.
        //
        // Grass is the exception, and it is not an inconsistency: a lawn's earth is on the block's
        // own underside, so a Biomes O' Plenty grass wears through to its own loam rather than to
        // vanilla dirt. That is why it was worth having in the first place.
        Set<SurfaceFamily> appearances;
        if (origin == null) {
            appearances = onChain;
        } else {
            appearances = new HashSet<SurfaceFamily>();
            if (onChain.contains(originFamily)) appearances.add(originFamily);
            if (originFamily == SurfaceFamily.GRASS && onChain.contains(SurfaceFamily.DIRT)) {
                appearances.add(SurfaceFamily.DIRT);
            }
        }
        return appearances;
    }

    /**
     * The edge every sprite this stitch registers is drawn at, and what that costs in slots.
     *
     * <p>
     * One per stitch, serving the census and the real walk alike, and both read the same table of widths, so the two
     * walks work out the same edge for the same set; the gate holds a walk that answers differently to fewer slots.
     * The edge given here is the size the atlas stitches the sprite at and the size the pass composes it at, so the
     * price is not an estimate of the sprite but the sprite itself. Every rule it applies is FaceRules', and every
     * name and width FaceSource's. Anisotropic filtering grows none of them: the game widens the pack's own textures,
     * and a wear sprite is made from the face with that border taken off.
     */
    static final class FacePricing {

        private final IResourceManager manager;

        private final int wearEdge;

        private final int gutter;

        FacePricing(IResourceManager manager, int wearEdge, int gutter) {
            this.manager = manager;
            this.wearEdge = wearEdge;
            this.gutter = gutter;
        }

        /**
         * Under Angelica's gutter, this mod's holders are priced by the pixels they really take, the edge rounded to
         * the stitcher's mip stride and the small gutter on each side, summed and then rounded up to whole slots once:
         * AtlasPlan.cellsForHolder rounds each holder up to whole sixteen-pixel slots, which priced a 24-pixel holder
         * at 32 (four slots for 2.25) and would have planned the small gutter into fewer rotations than fit. Without
         * Angelica's gutter the pricing is exactly what it was.
         */
        int holderCellsOf(int[] set) {
            // A set's glow overlays priced beside its pictures, each as large as its picture (real glow).
            int[] edges = pricedEdges(set);
            long exact = pixelsOf(edges);
            priced += exact;
            if (gutter > 0) {
                long pixels = 0L;
                for (int edge : edges) if (edge > 0) pixels += SmallGutter.holderPixels(edge, pricedLevels(), gutter);
                return (int) Math.min(Integer.MAX_VALUE, SmallGutter.slots(pixels));
            }
            int cells = 0;
            for (int edge : edges) if (edge > 0) cells += AtlasPlan.cellsForHolder(edge, gutter);
            return cells;
        }

        /**
         * The exact holder pixels of every set priced through {@link #holderCellsOf(int[])} so far: what the census
         * walk costs at one gradation and one rotation, without the slot rounding the plan's budget uses (auto's fit
         * test, SmallGutter.fits).
         */
        private long priced;

        long pricedPixels() {
            return priced;
        }

        /** Of {@link #pricedPixels}, those of sets drawn with one pattern (client.wearTurns' turn only). */
        private long pricedOne;

        long pricedOnePixels() {
            return pricedOne;
        }

        /** {@link #holderCellsOf(int[])}, noting the set's pixels as drawn with one pattern where {@code one}. */
        int holderCellsOf(int[] edges, boolean one) {
            if (one) pricedOne += pixelsOf(edges);
            return holderCellsOf(edges);
        }

        /** Each face's three measures, read once a stitch by its name ({@link FaceDirection}). */
        private final Map<String, FaceDirection.Reading> readings = new HashMap<String, FaceDirection.Reading>();

        /** A face's measures from its file, or UNREAD where the file cannot be read or decoded. */
        FaceDirection.Reading reading(String face) {
            if (face == null) return FaceDirection.UNREAD;
            FaceDirection.Reading known = readings.get(face);
            if (known != null) return known;
            FaceDirection.Reading read = FaceDirection.UNREAD;
            try {
                java.awt.image.BufferedImage image = WearPatterns.readIcon(manager, face);
                if (image != null) {
                    int width = image.getWidth();
                    int height = image.getHeight();
                    read = FaceDirection.read(image.getRGB(0, 0, width, height, null, 0, width), width, height);
                }
            } catch (RuntimeException unreadable) {
                read = FaceDirection.UNREAD;
            }
            readings.put(face, read);
            return read;
        }

        /**
         * Whether a block state's worn top turns with its position (0.9.229): a looks file's {@code turns}, else no
         * where the block's or its face's name holds a hint, else the face's own pixels, else, for a face that cannot
         * be read, its family's default (FamilySettings.turnsByDefault). The face is the looks file's where it gives
         * one, as the set is drawn from it, else the block's own top.
         */
        boolean turns(Block block, int meta, SurfaceFamily family) {
            BlockLooks.Look look = FaceSource.lookOf(block, meta);
            String face = look.face != null ? look.face : FaceSource.iconName(block, meta, 1);
            if (face == null) face = look.planFace();
            String registry;
            try {
                registry = SurfaceRegistry.registryName(block);
            } catch (RuntimeException unregistrable) {
                registry = null;
            }
            if (Boolean.FALSE.equals(look.turns) || look.turns == null && FaceDirection.hinted(registry, face))
                vetoed.add(registry + ":" + (meta & 0xF));
            return FaceDirection
                .turns(look.turns, reading(face), FamilySettings.turnsByDefault(family), registry, face);
        }

        /**
         * Block states kept still by a looks file or a name hint, each once however many walks ask, for the line
         * reportTurns writes.
         */
        final Set<String> vetoed = new HashSet<String>();

        /** Whether a family's fallback set turns: the vanilla face it is made of, judged as any other face. */
        boolean fallbackTurns(SurfaceFamily family) {
            String face = FaceSource.readable(manager, FaceRules.vanillaName(family, 1));
            return FaceDirection.turns(null, reading(face), FamilySettings.turnsByDefault(family), face);
        }

        /** Exact holder pixels of these edges at this pricing's gutter, an edge of nought holding nothing. */
        long pixelsOf(int[] set) {
            long pixels = 0L;
            for (int edge : pricedEdges(set))
                if (edge > 0) pixels += SmallGutter.holderPixels(edge, pricedLevels(), gutter);
            return pixels;
        }

        /**
         * The slots one gradation of a set's glow overlays takes at one rotation at this pricing's gutter, rounded
         * down (GlowRoomTest): the planner takes these off for the stages that cannot light, and rounded down it never
         * takes off more than they hold.
         */
        int glowCellsOf(int[] set) {
            return (int) (glowPixelsOf(set) / ((long) AtlasPlan.CELL * AtlasPlan.CELL));
        }

        /**
         * The exact holder pixels one gradation of a set's glow overlays takes at one rotation at this pricing's
         * gutter (0.9.229, glow-pixels): what a plan priced in pixels takes off for the stages that cannot light.
         */
        long glowPixelsOf(int[] set) {
            if (set.length != FAMILIES) return 0L;
            SurfaceFamily[] families = SurfaceFamily.values();
            long pixels = 0L;
            for (int at = 0; at < set.length; at++) {
                if (set[at] > 0 && glows(families[at]))
                    pixels += SmallGutter.holderPixels(set[at], pricedLevels(), gutter);
            }
            return pixels;
        }

        /** The same over every fallback appearance. */
        long glowCellsOf(int[][] rows) {
            long cells = 0L;
            for (int[] row : rows) cells += glowCellsOf(row);
            return cells;
        }

        /** The glow pixels over every fallback appearance. */
        long glowPixelsOf(int[][] rows) {
            long pixels = 0L;
            for (int[] row : rows) pixels += glowPixelsOf(row);
            return pixels;
        }

        /** A set's exact holder pixels, glows and all, as one figure a census takes. */
        int setPixels(int[] set) {
            return (int) Math.min(Integer.MAX_VALUE, pixelsOf(set));
        }

        /** A set's glow overlays' exact holder pixels, as one figure a census takes. */
        int setGlowPixels(int[] set) {
            return (int) Math.min(Integer.MAX_VALUE, glowPixelsOf(set));
        }

        long pixelsOf(int[][] edges) {
            long pixels = 0L;
            for (int[] row : edges) pixels += pixelsOf(row);
            return pixels;
        }

        long pixelsOf(StateFiling.Builder<Block, Integer> edges) {
            final long[] pixels = { 0L };
            edges.forEach(new StateFiling.Visitor<Block, Integer>() {

                @Override
                public void visit(Block block, int meta, Integer edge) {
                    pixels[0] += SmallGutter.holderPixels(edge.intValue(), pricedLevels(), gutter);
                }
            });
            return pixels[0];
        }

        /** One holder of this edge, priced as {@link #holderCellsOf(int[])} prices a set. */
        int singleHolderCells(int edge) {
            return holderCellsOf(new int[] { edge });
        }

        long holderCellsOf(int[][] edges) {
            long cells = 0L;
            for (int[] row : edges) cells += holderCellsOf(row);
            return cells;
        }

        long holderCellsOf(StateFiling.Builder<Block, Integer> edges) {
            final long[] cells = { 0L };
            edges.forEach(new StateFiling.Visitor<Block, Integer>() {

                @Override
                public void visit(Block block, int meta, Integer edge) {
                    cells[0] += gutter > 0 ? SmallGutter.holderPixels(edge.intValue(), pricedLevels(), gutter)
                        : AtlasPlan.cellsForHolder(edge.intValue(), gutter);
                }
            });
            return gutter > 0 ? SmallGutter.slots(cells[0]) : cells[0];
        }

        /**
         * The edge of every appearance of one surface set, by the appearance's ordinal, nought for an appearance the
         * set does not hold. Every appearance a block's own set holds is made of the block's family - grass keeps
         * grass for its earth, and every other family holds only itself - so the chain of names ends at that family's
         * vanilla texture. The underside is read only where an appearance is drawn from it, or where the top cannot be
         * read and harvest would put the underside in its place.
         */
        int[] setEdges(Block block, int meta, SurfaceFamily family, Set<SurfaceFamily> appearances) {
            int top = FaceSource.pricedWidth(manager, block, meta, 1, family);
            boolean wantsBottom = top <= 0;
            for (SurfaceFamily appearance : appearances) {
                if (cover(family, appearance, block, meta) || FaceRules.revealsEarth(family, appearance)) {
                    wantsBottom = true;
                }
            }
            int bottom = wantsBottom ? FaceSource.pricedWidth(manager, block, meta, 0, family) : 0;
            int topEdge = FaceRules.faceEdge(top, bottom, wearEdge);
            int bottomEdge = FaceRules.faceEdge(bottom, top, wearEdge);
            int[] edges = new int[FAMILIES];
            for (SurfaceFamily appearance : appearances) {
                edges[appearance.ordinal()] = FaceRules.appearanceEdge(
                    cover(family, appearance, block, meta),
                    FaceRules.revealsEarth(family, appearance),
                    topEdge,
                    bottomEdge);
            }
            return edges;
        }

        /**
         * Mended sides or grass walls, one sprite per block state, each at the edge of its own side face, in filing
         * order.
         */
        StateFiling.Builder<Block, Integer> sideEdges(StateFiling.Builder<Block, Boolean> wanted) {
            final StateFiling.Builder<Block, Integer> edges = StateFiling.<Block, Integer>builder();
            wanted.forEach(new StateFiling.Visitor<Block, Boolean>() {

                @Override
                public void visit(Block block, int meta, Boolean value) {
                    int width = FaceSource.pricedWidth(manager, block, meta, 2, SurfaceFamily.GRASS);
                    edges.file(block, meta, Integer.valueOf(FaceRules.faceEdge(width, 0, wearEdge)));
                }
            });
            return edges;
        }

        /** The wall an unknown grass falls back on, read from vanilla dirt. */
        int fallbackWallEdge() {
            return FaceRules.fileEdge(FaceSource.vanillaWidth(manager, SurfaceFamily.GRASS, SurfaceFamily.GRASS, 2), 0);
        }

        /** One grass-side fringe, read from the overlay. Priced only: FringeSprite sizes itself from the same file. */
        int fringeEdge() {
            return FaceRules.fileEdge(WearPatterns.headerWidth(manager, "grass_side_overlay"), 0);
        }

        /**
         * Every fallback appearance's edge, by family ordinal and then appearance ordinal, nought where none is
         * planned.
         */
        int[][] fallbackEdges() {
            int[][] edges = new int[FAMILIES][FAMILIES];
            for (SurfaceFamily family : SurfaceFamily.values()) {
                if (!family.staged) continue;
                for (SurfaceFamily appearance : appearancesFor(null, family)) {
                    int top = FaceSource.vanillaWidth(manager, family, appearance, 1);
                    int bottom = FaceSource.vanillaWidth(manager, family, appearance, 0);
                    edges[family.ordinal()][appearance.ordinal()] = FaceRules.appearanceEdge(
                        cover(family, appearance),
                        FaceRules.revealsEarth(family, appearance),
                        FaceRules.fileEdge(top, bottom),
                        FaceRules.fileEdge(bottom, top));
                }
            }
            return edges;
        }

        /** The slots one gradation of a set takes at one rotation, its glow overlays' included. */
        static int cellsOf(int[] edges) {
            return pricedCells(edges);
        }

        /** The slots one gradation of every fallback appearance takes at one rotation. */
        static long cellsOf(int[][] edges) {
            long cells = 0L;
            for (int[] row : edges) {
                cells += cellsOf(row);
            }
            return cells;
        }

        /** The slots a filing of side sprites takes, once each. */
        static long cellsOf(StateFiling.Builder<Block, Integer> edges) {
            final long[] cells = { 0L };
            edges.forEach(new StateFiling.Visitor<Block, Integer>() {

                @Override
                public void visit(Block block, int meta, Integer edge) {
                    cells[0] += AtlasPlan.cellsFor(edge.intValue());
                }
            });
            return cells[0];
        }

        /** Whether an appearance is drawn as a cover, asked of its look as it stands while the stitch is planned. */
        private static boolean cover(SurfaceFamily family, SurfaceFamily appearance) {
            return FaceRules.drawsCover(family, appearance, FaceSource.coverLook(TrmtConfig.family(appearance)));
        }

        /** The same, for one block, whose own look a looks file may give (W3 T5), as its sprite will ask it. */
        private static boolean cover(SurfaceFamily family, SurfaceFamily appearance, Block block, int meta) {
            return FaceRules
                .drawsCover(family, appearance, FaceSource.coverLook(TrmtConfig.family(appearance), block, meta));
        }
    }

    /**
     * True when a covered block hands out a side texture that expects a pass behind it.
     *
     * <p>
     * A render type of its own is the signal. It is not proof - plenty of blocks draw themselves
     * for reasons that have nothing to do with cut-away textures - but mending one that did not
     * need it costs a sprite and changes nothing, where missing one leaves a band you can see
     * through.
     */
    private static boolean wantsMendedSide(Block block, SurfaceFamily family) {
        if (block == null || family != SurfaceFamily.GRASS) return false;
        try {
            return block.getRenderType() != 0;
        } catch (RuntimeException awkwardBlock) {
            return false;
        }
    }

    /**
     * The covered blocks that draw themselves and cut their own side away, collected rather than
     * registered so they are priced before anything is planned, in the order the registration loop
     * used to walk them. A state is filed once, as registerMendedSide's own check does.
     */
    private static StateFiling.Builder<Block, Boolean> mendedSidesWanted(List<SurfaceRegistry.SurfaceState> surfaces) {
        StateFiling.Builder<Block, Boolean> out = StateFiling.<Block, Boolean>builder();
        for (SurfaceRegistry.SurfaceState state : surfaces) {
            for (int meta : metasOf(state.block)) {
                SurfaceFamily family = SurfaceRegistry.familyOf(state.block, meta);
                if (!wantsMendedSide(state.block, family)) continue;
                out.file(state.block, meta, Boolean.TRUE);
            }
        }
        return out;
    }

    /**
     * Declared metadata only, deliberately, where the planner above walks all sixteen. These two
     * allocate a whole atlas sprite apiece and share nothing on the face: the planner can afford to
     * widen because two values drawing one face cost it a single set, and this cannot. Sixteen
     * claimed values of every grass block in a pack would be several hundred sprites for states the
     * world never holds, each of them priced in the plan at the size of that block's own side.
     */
    private static StateFiling.Builder<Block, Boolean> grassWallsWanted(List<SurfaceRegistry.SurfaceState> surfaces) {
        StateFiling.Builder<Block, Boolean> out = StateFiling.<Block, Boolean>builder();
        for (SurfaceRegistry.SurfaceState state : surfaces) {
            for (int meta : metasOf(state.block)) {
                if (SurfaceRegistry.familyOf(state.block, meta) != SurfaceFamily.GRASS) continue;
                out.file(state.block, meta, Boolean.TRUE);
            }
        }
        return out;
    }

    private static void registerMendedSide(TextureMap map, StateFiling.Builder<Block, IIcon> out, Block block, int meta,
        SurfaceFamily family, int edge) {
        if (out.has(block, meta)) return;

        String name = Trmt.MODID + ":wear/mend_" + (sideSpritesNamed++);
        WearSprite sprite = new WearSprite(name, block, meta, family, family, 0, 1, 0, edge, true);
        if (map.setTextureEntry(name, sprite)) {
            REGISTERED.add(sprite);
            out.file(block, meta, sprite);
        }
    }

    /** Registers one gradation-and-rotation of the receding grass-side fringe. */
    private static void registerFringe(TextureMap map, int stage, int rotation) {
        int index = stage * rotations + rotation;
        if (index < 0 || index >= overlaySprites.length) return;
        String name = fringeSpriteName(stage, rotation);
        FringeSprite sprite = new FringeSprite(name, stage, spriteLayers, rotation);
        if (map.setTextureEntry(name, sprite)) {
            overlaySprites[index] = sprite;
            fringesRegistered++;
        } else if (overlaySprites[index] == null) {
            overlaySprites[index] = map.getTextureExtry(name);
        }
    }

    /** Registers one grass block's de-greened side wall, at the edge of that block's own side. */
    private static void registerGrassWall(TextureMap map, StateFiling.Builder<Block, IIcon> out, Block block, int meta,
        int edge) {
        if (block == null || out.has(block, meta)) return;

        String name = Trmt.MODID + ":wear/wall_" + (sideSpritesNamed++);
        WearSprite sprite = new WearSprite(
            name,
            block,
            meta,
            SurfaceFamily.GRASS,
            SurfaceFamily.GRASS,
            0,
            1,
            0,
            edge,
            false,
            true);
        if (map.setTextureEntry(name, sprite)) {
            REGISTERED.add(sprite);
            out.file(block, meta, sprite);
        } else {
            IIcon existing = map.getTextureExtry(name);
            if (existing != null) out.file(block, meta, existing);
        }
    }

    /**
     * Registers the wall a grass origin with none of its own falls back on, read from vanilla dirt at the edge of that
     * file, and hands it back, or whatever already holds its name when the atlas refuses it.
     */
    private static IIcon registerGrassWallFallback(TextureMap map, int edge) {
        String name = Trmt.MODID + ":wear/wall_fallback";
        WearSprite sprite = new WearSprite(
            name,
            null,
            0,
            SurfaceFamily.GRASS,
            SurfaceFamily.GRASS,
            0,
            1,
            0,
            edge,
            false,
            true);
        if (map.setTextureEntry(name, sprite)) {
            REGISTERED.add(sprite);
            return sprite;
        }
        return map.getTextureExtry(name);
    }

    /**
     * The receding grass-side fringe for a position's side wear, or null to draw the full one.
     *
     * @param layer    the side's wear layer, {@code 0} to the family's last, from
     *                 {@code GhostRendering.sideLayer}
     * @param rotation the position's pattern rotation
     */
    public static IIcon grassSideOverlay(int layer, int rotation) {
        Lookup current = lookup;
        IIcon[] table = current.fringes;
        if (table.length == 0) return null;
        // The counted gradation maps onto a drawn one, exactly as icon() does for the faces.
        int stage = current.stages[Math.max(0, Math.min(WearScale.COUNTED_STEPS - 1, layer))];
        if (stage < 0) stage = 0;
        if (stage >= current.layers) stage = current.layers - 1;
        int index = stage * current.rotations + (rotation % current.rotations);
        if (index < 0 || index >= table.length) return null;
        IIcon icon = table[index];
        if (icon == null) return null;
        if (icon instanceof FringeSprite && !((FringeSprite) icon).isUsable()) return null;
        return icon;
    }

    /**
     * The de-greened earth wall for a grass origin, falling back to the generic one, or null.
     *
     * @param origin the block painted over, or null when nothing is remembered there, which gets the
     *               generic wall
     */
    public static IIcon grassEarthWall(Block origin, int originMeta) {
        Lookup current = lookup;
        IIcon icon = origin == null ? null : current.walls.get(origin, originMeta);
        if (!usable(icon)) icon = current.wallFallback;
        return usable(icon) ? icon : null;
    }

    /**
     * The covered block's own side with the pass underneath baked in, or null when it needs none.
     */
    public static IIcon mendedSide(Block origin, int originMeta) {
        if (origin == null) return null;
        IIcon icon = lookup.mended.get(origin, originMeta);
        return usable(icon) ? icon : null;
    }

    /**
     * A wear texture's sprite name: set, appearance, rotation, then the stage padded to three digits, so that names
     * sort a face's stages one after another within each rotation. Vanilla's stitcher sorts holders of one size by
     * name, and lwjgl3ify's STB packer keeps that order, so a face's stages are stitched side by side and a distant
     * one that blends past its small Angelica gutter blends into its own next stage (0.9.229, public issue #3;
     * StagesStitchSideBySideTest). A name is only ever registered, never looked up.
     */
    static String wearSpriteName(int set, String appearance, int stage, int rotation) {
        return Trmt.MODID + ":wear/" + set + "_" + appearance + "_r" + rotation + "_" + padded(stage);
    }

    /** A fringe's sprite name, ordered as {@link #wearSpriteName} orders a face's. */
    static String fringeSpriteName(int stage, int rotation) {
        return Trmt.MODID + ":wear/fringe_r" + rotation + "_" + padded(stage);
    }

    private static String padded(int stage) {
        String digits = Integer.toString(stage);
        return digits.length() >= 3 ? digits : "000".substring(digits.length()) + digits;
    }

    private static void register(TextureMap map, SpritePlan plan) {
        String name = wearSpriteName(plan.set, plan.appearance.key(), plan.stage, plan.rotation);

        WearSprite sprite = new WearSprite(
            name,
            plan.origin,
            plan.meta,
            plan.originFamily,
            plan.appearance,
            plan.stage,
            plan.stageCount,
            plan.rotation,
            plan.edge);
        sprite.markTurns(plan.turns);

        // The return value matters. A name already in the atlas is refused, and the sprite we
        // just built is then one nothing will ever stitch - it has no coordinates, so anything
        // drawing it gets a black face. Storing it regardless is what turned worn ground into
        // black cubes: the wear chain visits the same appearance and layer once per depth, so
        // every layer that recurs at depth was registered nine times and kept the ninth.
        int index = slot(plan.set, plan.appearance, plan.stage, plan.rotation);
        if (index < 0 || index >= icons.length) return;
        if (map.setTextureEntry(name, sprite)) {
            icons[index] = sprite;
            REGISTERED.add(sprite);
            // The glow overlay of the end's and the nether's wear (real glow; Xep, 2026-11-15), composed beside it from
            // the same source. Named like the picture it goes with, ordered the same way, so a face's overlays stitch
            // side by side too; its own entry, and never shared with a picture (glowKey).
            if (glows(plan.appearance) && glowLights(plan.appearance, plan.stage, plan.stageCount)
                || ownLookGlows(plan)) {
                String glowName = wearSpriteName(plan.set, plan.appearance.key() + "_glow", plan.stage, plan.rotation);
                WearSprite glow = WearSprite.glowOf(sprite, glowName);
                if (map.setTextureEntry(glowName, glow)) {
                    glowIcons[index] = glow;
                    REGISTERED.add(glow);
                    glowsRegistered++;
                }
            }
        } else if (icons[index] == null) {
            // Refused because something already holds the name; use whatever that is.
            icons[index] = map.getTextureExtry(name);
        }
    }

    /**
     * Whether the look this appearance's family draws with has a glow: the end's, the nether's and sculk's shipped
     * looks
     * (real glow), and any family a player has given one of them. A family in a look with no glow, its classic look
     * included, has no overlay registered, priced or counted.
     */
    public static boolean glows(SurfaceFamily appearance) {
        com.trmtgtnh.config.FamilySettings look = appearance == null ? null : TrmtConfig.family(appearance);
        // Before the settings are read, the family's shipped look.
        if (look == null && appearance != null) look = com.trmtgtnh.config.FamilySettings.shipped(appearance);
        return glows(appearance, look == null ? null : look.wearPattern);
    }

    /**
     * The same, for the look given. Only where that look has a glow: under client.classicWear the end and the nether
     * draw classic looks with none, and GT New Horizons 2.9.0 registered 26,544 overlays that all came out empty and
     * still counted against client.maxWearSprites, which cost it a gradation (2026-11-17).
     */
    static boolean glows(SurfaceFamily appearance, String look) {
        // Glowing wear off (client.glowingWear): no overlay is registered, priced or drawn.
        if (!TrmtConfig.glowingWear || appearance == null) return false;
        return WearSprite.glowLevel(look) > 0;
    }

    /**
     * Whether the glow overlay of this appearance at this stage can light at all (GlowStagesTest). One that cannot is
     * never registered, and the plan does not count it against client.maxWearSprites.
     */
    static boolean glowLights(SurfaceFamily appearance, int stage, int stageCount) {
        return WearSprite.glowCanLight(lookOf(appearance), stage, stageCount);
    }

    /** How many of these appearances have a glow overlay. */
    static int glowCount(java.util.Collection<SurfaceFamily> appearances) {
        int count = 0;
        for (SurfaceFamily appearance : appearances) if (glows(appearance)) count++;
        return count;
    }

    /**
     * These appearances' glow sprites at one rotation, indexed by the gradations drawn: at each count, the stages whose
     * overlay can light, summed over the appearances that glow. Null where none glows.
     */
    static int[] glowLit(java.util.Collection<SurfaceFamily> appearances) {
        int[] out = null;
        for (SurfaceFamily appearance : appearances) {
            if (!glows(appearance)) continue;
            int[] one = litTable(lookOf(appearance));
            if (out == null) out = new int[one.length];
            for (int g = 0; g < one.length; g++) out[g] += one[g];
        }
        return out;
    }

    /** The family fallbacks' glow overlays: one for each staged family's appearance that glows. */
    static int fallbackGlows() {
        int total = 0;
        for (SurfaceFamily family : SurfaceFamily.values()) {
            if (family.staged) total += glowCount(appearancesFor(null, family));
        }
        return total;
    }

    /** The family fallbacks' glow sprites at one rotation, indexed by the gradations drawn. */
    static int[] fallbackGlowLit() {
        int[] out = new int[WearScale.COUNTED_STEPS + 1];
        for (SurfaceFamily family : SurfaceFamily.values()) {
            if (!family.staged) continue;
            int[] lit = glowLit(appearancesFor(null, family));
            if (lit != null) for (int g = 0; g < out.length && g < lit.length; g++) out[g] += lit[g];
        }
        return out;
    }

    /** At each count of gradations up to the most there can be, how many stages of this look's overlay can light. */
    static int[] litTable(String look) {
        int[] table = new int[WearScale.COUNTED_STEPS + 1];
        for (int g = 1; g < table.length; g++) {
            for (int stage = 0; stage < g; stage++) if (WearSprite.glowCanLight(look, stage, g)) table[g]++;
        }
        return table;
    }

    /** The look a family draws with: its shipped look before the settings are read. */
    private static String lookOf(SurfaceFamily appearance) {
        com.trmtgtnh.config.FamilySettings look = appearance == null ? null : TrmtConfig.family(appearance);
        if (look == null && appearance != null) look = com.trmtgtnh.config.FamilySettings.shipped(appearance);
        return look == null ? null : look.wearPattern;
    }

    /** Whether a looks file gives this planned sprite's own block a look that glows (W3 T5), on its own surface. */
    private static boolean ownLookGlows(SpritePlan plan) {
        if (!TrmtConfig.glowingWear || plan.origin == null || plan.appearance != plan.originFamily) return false;
        return WearSprite.glowLevel(
            FaceSource.lookOf(plan.origin, plan.meta)
                .patternOr(null))
            > 0;
    }

    /**
     * The slots one gradation of a set takes at one rotation, given the edge of each of its appearances by ordinal: the
     * pictures, and, for each appearance that glows, the overlay beside its picture, priced as large as the picture is.
     * The price is of the most that can be stitched: an overlay with nothing to light is never offered, so the room is
     * only ever more than enough (real glow).
     */
    static int pricedCells(int[] edges) {
        int cells = 0;
        for (int edge : pricedEdges(edges)) {
            if (edge > 0) cells += AtlasPlan.cellsFor(edge);
        }
        return cells;
    }

    /**
     * The edges a set is priced at: each appearance's, and once more for each appearance that glows, its overlay's.
     * Only
     * a set's edges by ordinal are widened; a single holder's are its own.
     */
    static int[] pricedEdges(int[] edges) {
        if (edges.length != FAMILIES) return edges;
        SurfaceFamily[] families = SurfaceFamily.values();
        int[] out = java.util.Arrays.copyOf(edges, edges.length * 2);
        for (int at = 0; at < edges.length; at++) {
            out[edges.length + at] = glows(families[at]) ? edges[at] : 0;
        }
        return out;
    }

    /** How many sprites a set of appearances is priced at, one gradation at one rotation: each, and each overlay. */
    static int pricedAppearances(java.util.Collection<SurfaceFamily> appearances) {
        int count = appearances.size();
        for (SurfaceFamily appearance : appearances) {
            if (glows(appearance)) count++;
        }
        return count;
    }

    /**
     * An overlay's picture as it is compared for sharing: one more value than the picture, so no overlay is ever the
     * same picture as a wear picture, whatever the pixels, and two overlays of the same pixels are the same.
     */
    static int[] glowKey(int[] pixels) {
        int[] own = java.util.Arrays.copyOf(pixels, pixels.length + 1);
        own[pixels.length] = 1;
        return own;
    }

    /**
     * The overlay drawn over a picture where it is drawn, or null: 1.7.10's face methods are handed the picture and
     * nothing else (GhostGlow). See {@link WearSprite#glowIcon()}.
     */
    public static IIcon glowIcon(IIcon worn) {
        return worn instanceof WearSprite ? ((WearSprite) worn).glowIcon() : null;
    }

    /**
     * The glow overlay that goes over the wear texture {@link #icon} answers for the same arguments, or null where that
     * one has none: the look has no glow, or nothing in this gradation glows (real glow). Drawn over the picture a
     * second time lit at {@link #glowLevelAt}, so by day it is the picture's own pixels again and in the dark it
     * shines.
     */
    public static IIcon glowIcon(Block origin, int originMeta, SurfaceFamily originFamily, SurfaceFamily appearance,
        int stage, int rotation) {
        Lookup current = lookup;
        int at = glowSlot(current, origin, originMeta, originFamily, appearance, stage, rotation);
        if (at < 0) return null;
        IIcon glow = current.glowIcons[at];
        return usable(glow) ? glow : null;
    }

    /** The light level, out of fifteen, the overlay {@link #glowIcon} answers for the same arguments is baked with. */
    public static int glowLevelAt(Block origin, int originMeta, SurfaceFamily originFamily, SurfaceFamily appearance,
        int stage, int rotation) {
        IIcon glow = glowIcon(origin, originMeta, originFamily, appearance, stage, rotation);
        return glow instanceof WearSprite ? ((WearSprite) glow).glowLevelOf() : 0;
    }

    /**
     * The slot of the overlay that goes with the picture {@link #icon} would draw, found as it finds that picture: the
     * block's own set, then the family's. Negative where the arguments name no picture at all.
     */
    private static int glowSlot(Lookup current, Block origin, int originMeta, SurfaceFamily originFamily,
        SurfaceFamily appearance, int stage, int rotation) {
        if (appearance == null || stage < 0 || stage >= WearScale.COUNTED_STEPS) return -1;
        IIcon[] table = current.icons;
        int layers = current.layers;
        int turn = rotation % current.rotations;
        stage = current.stages[stage];
        if (stage >= layers) stage = layers - 1;
        int set = fallbackSet(originFamily, null);
        if (origin != null) {
            Integer own = current.sets.get(origin, originMeta);
            if (own != null) set = own.intValue();
        }
        int index = slot(set, appearance, stage, turn, layers, current.rotations);
        if (index >= 0 && index < table.length && usable(table[index])) return index;
        int fallback = slot(fallbackSet(originFamily, appearance), appearance, stage, turn, layers, current.rotations);
        if (fallback < 0 || fallback >= table.length || !usable(table[fallback])) return -1;
        return fallback;
    }

    /**
     * The metadata values a block actually declares.
     *
     * <p>
     * Not what the planner walks any more - it asks the registry, because a block that hides a
     * variant from creative is exactly the case the planner was getting wrong. This is still what
     * the two side-texture passes walk, though, since those allocate a sprite apiece and share
     * nothing, and a block's own list is the honest bound on how many of those are worth having.
     */
    private static int[] metasOf(Block block) {
        return metasOf(block, true);
    }

    /**
     * {@link #metasOf(Block)}, saying nothing of a block that refuses when {@code report} is false.
     * The census walk asks every block this just before the planner's own walk asks it again, and
     * one refusal is worth one line in the log rather than two.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static int[] metasOf(Block block, boolean report) {
        Item item = Item.getItemFromBlock(block);
        if (item == null) return new int[] { 0 };
        try {
            List stacks = new ArrayList();
            block.getSubBlocks(item, (CreativeTabs) null, stacks);
            if (stacks.isEmpty()) return new int[] { 0 };

            int[] metas = new int[stacks.size()];
            int count = 0;
            for (Object entry : stacks) {
                if (!(entry instanceof ItemStack)) continue;
                int damage = ((ItemStack) entry).getItemDamage();
                if (damage < 0 || damage > 15) continue;
                metas[count++] = damage;
            }
            if (count == 0) return new int[] { 0 };
            int[] trimmed = new int[count];
            System.arraycopy(metas, 0, trimmed, 0, count);
            return trimmed;
        } catch (RuntimeException awkwardBlock) {
            // Plenty of 1.7.10 blocks assume a live creative tab in getSubBlocks. One of them
            // must not be able to stop the atlas from stitching.
            if (report) Trmt.LOG.debug("Block {} refused getSubBlocks; assuming metadata 0 only", block, awkwardBlock);
            return new int[] { 0 };
        }
    }

    // ------------------------------------------------------------------
    // Lookup
    // ------------------------------------------------------------------

    /**
     * The wear texture for a position.
     *
     * @param origin       the block being painted over, turned back from the number the painter
     *                     remembered under the ids in force, or null when the client no longer
     *                     remembers what was there
     * @param originMeta   that block's metadata
     * @param originFamily the family that block belongs to, deciding which fallback applies
     */
    public static IIcon icon(Block origin, int originMeta, SurfaceFamily originFamily, SurfaceFamily appearance,
        int stage, int rotation) {
        if (appearance == null || stage < 0 || stage >= WearScale.COUNTED_STEPS) return null;
        // One read, then work off that. Reading the holder twice can straddle a re-stitch and index
        // one stitch's table by a length, a ramp or a set that belonged to the other.
        Lookup current = lookup;
        IIcon[] table = current.icons;
        int layers = current.layers;
        // The counted position maps onto a drawn one. The identity at eighty pictures, which is the
        // whole point of the default; a fifth of the way at sixteen, which is what it always did.
        stage = current.stages[stage];
        if (stage >= layers) stage = layers - 1;
        int set = fallbackSet(originFamily, null);
        if (origin != null) {
            Integer own = current.sets.get(origin, originMeta);
            if (own != null) set = own.intValue();
        }
        // Which seeded pattern: the rotation folded onto the patterns drawn, or the one a set that turns has under
        // client.wearTurns' turn only (WearTurns.seed); the quarter turn itself is the renderer's (quarterTurns).
        int turn = WearTurns.seed(current.mode, current.turned.get(set), rotation, current.rotations);
        int index = slot(set, appearance, stage, turn, layers, current.rotations);
        if (index < 0 || index >= table.length) return null;
        IIcon icon = table[index];
        if (usable(icon)) return icon;

        // A surface set can be missing an appearance the chain has since gained, e.g. after a
        // config change without a resource reload. The family fallback always has it.
        int fallbackSet = fallbackSet(originFamily, appearance);
        int fallbackTurn = WearTurns.seed(current.mode, current.turned.get(fallbackSet), rotation, current.rotations);
        int fallback = slot(fallbackSet, appearance, stage, fallbackTurn, layers, current.rotations);
        if (fallback < 0 || fallback >= table.length) return null;
        IIcon generic = table[fallback];
        return usable(generic) ? generic : null;
    }

    static final String LINE_TURNS = "Worn tops turn with their position on {} of this stitch's {} wear sets (client.wearTurns is {}): {} faces read with no direction turn, {} read with lines or a grain keep still, {} block states keep still by a name hint or a looks file, and {} that could not be read follow their family. Kept still, for example: {}.";

    /**
     * Says which sets turn, and why the rest keep still (0.9.229). Every face judged is also written at debug, with its
     * two measures, which is what a face misjudged is checked against.
     */
    private static void reportTurns(int sets, FacePricing pricing) {
        int turning = TURNED.cardinality();
        int read = 0;
        int lined = 0;
        int unread = 0;
        List<String> still = new ArrayList<String>();
        for (Map.Entry<String, FaceDirection.Reading> each : pricing.readings.entrySet()) {
            FaceDirection.Reading reading = each.getValue();
            if (!reading.read) {
                unread++;
            } else if (reading.directional()) {
                lined++;
                if (still.size() < 12) still.add(each.getKey());
            } else {
                read++;
            }
            Trmt.LOG.debug("Face {} for the worn turn: {}", each.getKey(), reading);
        }
        int vetoed = pricing.vetoed.size();
        Trmt.LOG.info(
            LINE_TURNS,
            new Object[] { Integer.valueOf(turning), Integer.valueOf(sets), turnsMode, Integer.valueOf(read),
                Integer.valueOf(lined), Integer.valueOf(vetoed), Integer.valueOf(unread),
                still.isEmpty() ? "none" : String.join(", ", still) });
    }

    /**
     * How many quarter turns clockwise, seen from above with north up, a worn top drawing this icon is turned by at a
     * position of this rotation (0.9.229, docs/goals/0.9.229-rotation-spec.md): the position's own rotation where the
     * icon is one of this mod's worn pictures whose set turns and client.wearTurns turns anything, and none otherwise -
     * a face with a direction, a block's own unworn icon, a mended side, or reseed. Asked by the renderer with the icon
     * {@link #icon} gave, so the turn is the drawn set's whichever set the lookup fell through to.
     */
    public static int quarterTurns(IIcon drawn, int rotation, int depth) {
        if (!(drawn instanceof WearSprite) || !((WearSprite) drawn).turns()) return 0;
        // Told when the turn starts (client.wearTurnStart) and how far the square has sunk. This edition's game turns
        // no block's top, so the game's own turn of the unworn block is none, and matching it leaves the stages before
        // the first sink unturned, as the sink start does.
        return WearTurns.quarterTurns(lookup.mode, true, rotation, TrmtConfig.wearTurnStart, depth, 0);
    }

    /**
     * Whether a slot holds a sprite worth drawing.
     *
     * <p>
     * A sprite whose source could not be read, or that was never composed, carries placeholder
     * pixels so the atlas has something to stitch. Drawing those would put a flat color on the
     * ground; skipping them lets a block's own wear fall through to its family's, a grass wall to
     * the fallback wall and a mended side to the block's own plain side, while a family fallback
     * that cannot draw leaves nothing, which the end of the stitch names. That is the honest answer
     * for a block whose texture cannot be found.
     */
    private static boolean usable(IIcon icon) {
        if (icon == null) return false;
        return !(icon instanceof WearSprite) || ((WearSprite) icon).isUsable();
    }

    private static int fallbackSet(SurfaceFamily originFamily, SurfaceFamily appearance) {
        if (originFamily != null && originFamily.staged) return originFamily.ordinal();
        if (appearance != null && appearance.staged) return appearance.ordinal();
        return SurfaceFamily.DIRT.ordinal();
    }

    /** One planned sprite, kept as data so registration order stays independent of planning. */
    private static final class SpritePlan {

        final int set;
        final Block origin;
        final int meta;
        final SurfaceFamily originFamily;
        final SurfaceFamily appearance;
        final int stage;
        final int stageCount;
        final int rotation;
        final int edge;
        /** Whether the set turns with its position where it is drawn (FaceDirection). */
        final boolean turns;

        SpritePlan(int set, Block origin, int meta, SurfaceFamily originFamily, SurfaceFamily appearance, int stage,
            int stageCount, int rotation, int edge, boolean turns) {
            this.turns = turns;
            this.set = set;
            this.origin = origin;
            this.meta = meta;
            this.originFamily = originFamily;
            this.appearance = appearance;
            this.stage = stage;
            this.stageCount = stageCount;
            this.rotation = rotation;
            this.edge = edge;
        }
    }
}
