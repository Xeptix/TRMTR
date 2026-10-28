package com.trmtgtnh.client.journeymap;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.ToIntFunction;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

import com.trmtgtnh.ModsPresent;
import com.trmtgtnh.Trmt;
import com.trmtgtnh.block.BlockGhost;
import com.trmtgtnh.block.GhostMapColor;
import com.trmtgtnh.block.ModBlocks;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.erosion.ErosionChain;
import com.trmtgtnh.erosion.ErosionState;
import com.trmtgtnh.surface.SurfaceFamily;
import com.trmtgtnh.surface.SurfaceRegistry;

/**
 * Makes JourneyMap draw each worn position in a color taken from the block underneath it.
 *
 * <p>
 * The problem this solves: JourneyMap decides a block's color once per {@code BlockMD} - one per
 * block state - and caches it, so one ghost covering a hundred different modded dirts drew all
 * hundred the same brown. An unworn modded block shows its own distinct color on the map, that
 * color being JourneyMap's own average of the block's texture, and the moment it wore that
 * distinctness was lost. This hands JourneyMap a color proxy for the ghost that answers per
 * <em>position</em> instead: it looks up which real block the client is painting over at that spot,
 * asks JourneyMap what color it draws that block unworn, and shades it for the wear.
 *
 * <p>
 * <strong>This is also the one map that can show how worn a road is rather than merely that it is
 * worn.</strong> Vanilla's own map answer is a choice of sixty-four fixed palette entries, which is
 * why {@code GhostMapColor} settles for one darker entry per square and why, on those maps,
 * mapWearThroughTint does nothing. JourneyMap takes an ordinary RGB integer, so against it
 * {@code mapTracksWear}, {@code mapWearDarkening}, {@code desirePathHighlight} and
 * {@code desirePathRgb} all mean what they say.
 *
 * <p>
 * All of it is reflection, because JourneyMap is a soft dependency and not on the compile path. If it is absent, or
 * its internals have moved, every method here fails quietly and the ordinary family-generic fallback carries other
 * map mods. Nothing the game needs depends on any of this working.
 *
 * <p>
 * <strong>The names are this version's and were read out of the jar rather than carried.</strong>
 * The 1.7.10 edition reflects on {@code journeymap.client.model.BlockMD} and an inner interface
 * called {@code ModBlockDelegate$IModBlockColorHandler}; by JourneyMap 6 the model classes have
 * moved down a package each and the handler is a top-level {@code journeymap.client.mod.IBlockColorProxy} with two
 * methods rather than one. Carrying the old names across would have
 * compiled, run, found nothing and said nothing.
 */
public final class JourneyMapColors {

    private JourneyMapColors() {}

    /** Whether the proxy is in place, so the install is attempted once rather than every tick. */
    private static volatile boolean installed;

    /**
     * JourneyMap's lookup and setter, and this mod's proxy, kept from the install so the proxy can be handed over
     * again (0.9.224, spec CO18): JourneyMap 6 puts its own handler back on the stand-in's record after this mod's -
     * played on the 1.12.2 instance, it held its own before any change of dimension - as the version 1.7.10 was
     * written against did on every color reload and change of dimension. So the 1.7.10 edition's answer is carried:
     * handed over again every hundred ticks, which is a lookup and a set a state.
     */
    private static volatile Method recordFor;

    private static volatile Method handTo;

    private static volatile Object ours;

    /** Whether the attempt has been made and failed, so a broken look is not retried for ever. */
    private static volatile boolean refused;

    /** {@code BlockMD.getBlockColor(ChunkMD, BlockPos)}, for asking what a real block is drawn as. */
    private static volatile Method askColor;

    /** {@code BlockMD.getBlockColorProxy()}, for whether a hand-over put this handler where it was not (0.9.224). */
    private static volatile Method askedNow;

    /** JourneyMap's {@code DataCache.INSTANCE}, which keeps its record of every chunk mapped (0.9.224). */
    private static Object dataCache;

    /** Its {@code invalidateChunkMDCache()}, and {@code MapPlayerTask.forceNearbyRemap()}, found once, by name. */
    private static Method forgetAll;

    private static Method remapNearby;

    private static boolean forgetLooked;

    private static boolean forgetGaveUp;

    /** How often the watchdog looks, in client ticks - the 1.7.10 edition's install pass, every five seconds. */
    private static final int WATCH_INTERVAL_TICKS = 100;

    private static int tickCounter;

    /** Set the first time JourneyMap actually asked for a highlighted color. */
    private static volatile boolean answered;

    /** Set once the watchdog has complained, so it complains once rather than every five seconds. */
    private static boolean warned;

    /** Watchdog passes since worn ground existed on this client with nothing asked for. */
    private static int quiet;

    /**
     * Puts the proxy in place, once, from the client tick, and keeps the watchdog.
     *
     * <p>
     * From a tick rather than from start-up because JourneyMap builds its own tables as a world
     * loads, and a {@code BlockMD} asked for before then is one it will replace. The watchdog looks
     * every hundred ticks while JourneyMap is here, as the 1.7.10 edition's does after each of its
     * install passes (0.9.222, spec CO22).
     */
    public static void tick() {
        if (refused) return;
        if (!installed) {
            if (!ModsPresent.has("journeymap")) {
                refused = true;
                return;
            }
            install();
            if (refused) return;
        }
        if (tickCounter++ % WATCH_INTERVAL_TICKS != 0) return;
        handOver();
        watchdog();
    }

    /**
     * Forgets the install, so the next world puts it back - and the proof, so a new world proves the highlight afresh.
     */
    public static void reset() {
        installed = false;
        refused = false;
        askColor = null;
        tickCounter = 0;
        answered = false;
        warned = false;
        quiet = 0;
    }

    /**
     * Every chunk's record dropped and the ground around the player mapped again, for a hand-over that put this handler
     * where JourneyMap had its own back (0.9.224, CO18 and CO19). JourneyMap puts its own colors back on the stand-in
     * now and then - on the 1.12.2 Lawns instance three seconds after the harness laid its lawns - and every square it
     * mapped before the next hand-over was drawn by its own handler, from the stand-in's untinted texture: flat grey,
     * and kept, because JourneyMap asks a square again only when the block there changes, and a worn square's block
     * never does. Seen by the harness's minimap part: worn dry grass and wart block grey at every gradation while this
     * handler answered their colors; with this, the same lawns fade to earth.
     */
    private static void forgetEverything() {
        if (!lookForForgetting()) return;
        try {
            forgetAll.invoke(dataCache);
            remapNearby.invoke(null);
            Trmt.LOG.debug(
                "JourneyMap had put its own colors back on worn ground; this mod's are handed back and the ground around you mapped again");
        } catch (Throwable awkward) {
            giveUpForgetting(awkward);
        }
    }

    /**
     * Whether JourneyMap's chunk records can be dropped: looked for once, quietly where JourneyMap is not installed.
     */
    private static boolean lookForForgetting() {
        if (forgetGaveUp) return false;
        if (!forgetLooked) {
            forgetLooked = true;
            if (!ModsPresent.has("journeymap")) return false;
            try {
                Class<?> cache = Class.forName("journeymap.client.data.DataCache");
                dataCache = cache.getField("INSTANCE")
                    .get(null);
                forgetAll = cache.getMethod("invalidateChunkMDCache");
                remapNearby = Class.forName("journeymap.client.task.multi.MapPlayerTask")
                    .getMethod("forceNearbyRemap");
            } catch (Throwable movedOrGone) {
                dataCache = null;
                Trmt.LOG.info(
                    "JourneyMap's chunk records are not where this build looks for them ({}); worn ground it mapped while its own colors were back stays as it drew it",
                    String.valueOf(movedOrGone));
            }
        }
        return dataCache != null && forgetAll != null && remapNearby != null;
    }

    /** One warning, then no more asking for the session, rather than a failure every square. */
    private static void giveUpForgetting(Throwable awkward) {
        forgetGaveUp = true;
        Trmt.LOG.warn(
            "JourneyMap would not map worn ground again ({}); what it drew while its own colors were back stays as it is",
            String.valueOf(awkward));
    }

    /**
     * Says once, in the log, that the desire-path highlight is genuinely being applied - the 1.7.10 edition's line,
     * word for word (0.9.222, spec CO22).
     *
     * <p>
     * Six things have to be true before one pixel of it exists - JourneyMap installed, its internals where this
     * looks for them, the proxy still on the ghost's {@code BlockMD}, JourneyMap actually calling it, the setting
     * above nought, and the square worn at all - and five of the six fail silently. This is the line that says all
     * of them held. Both colors are printed, so it also proves the blend ran rather than passing its input through.
     */
    private static void announceOnce(BlockPos at, int base, int result) {
        if (answered || TrmtConfig.desirePathHighlight <= 0f) return;
        answered = true;
        Trmt.LOG.info(
            "Desire-path highlight is live: JourneyMap asked for {},{},{} and was given #{} where the ground's own color is #{}",
            new Object[] { Integer.valueOf(at.getX()), Integer.valueOf(at.getY()), Integer.valueOf(at.getZ()),
                String.format("%06X", Integer.valueOf(result & 0xFFFFFF)),
                String.format("%06X", Integer.valueOf(base & 0xFFFFFF)) });
    }

    /**
     * Says once, in the log, when the highlight is on and nothing has come of it - the 1.7.10 edition's warning,
     * word for word (0.9.222, spec CO22).
     *
     * <p>
     * A feature that never ran has to say so unprompted rather than waiting to be asked. Held until this client
     * actually holds worn ground, because complaining on a fresh world - where there is simply nothing to highlight
     * yet - would train everybody to ignore the line, which is worse than having no line.
     */
    private static void watchdog() {
        if (TrmtConfig.desirePathHighlight <= 0f || answered || warned) {
            quiet = 0;
            return;
        }
        if (com.trmtgtnh.client.ClientErosionCache.get()
            .chunkCount() == 0) {
            quiet = 0;
            return;
        }
        if (++quiet < 4) return;
        warned = true;
        Trmt.LOG.warn(
            "Desire-path highlight is on and this client has worn ground, but JourneyMap has not asked this mod for a color in twenty seconds. Either JourneyMap is not mapping right now - automap off, or nothing worn within range - or it kept a color handler of its own instead of ours.");
    }

    /**
     * Hands this mod's proxy to JourneyMap's record for every state of the stand-in, as the 1.7.10 edition hands one to
     * every stand-in at every metadata (spec CO18): JourneyMap keeps a record per state. Says how many records took it.
     */
    private static int handOver() {
        Method get = recordFor;
        Method set = handTo;
        Object proxy = ours;
        BlockGhost ghost = ModBlocks.ghostGrass();
        if (get == null || set == null || proxy == null || ghost == null) return 0;
        int handed = 0;
        int replaced = 0;
        Method now = askedNow;
        for (IBlockState state : ghost.getBlockState()
            .getValidStates()) {
            try {
                Object mine = get.invoke(null, state);
                if (mine == null) continue;
                if (now != null && now.invoke(mine) != proxy) replaced++;
                set.invoke(mine, proxy);
                handed++;
            } catch (ReflectiveOperationException | RuntimeException awkward) {
                // One state JourneyMap will not describe is not worth stopping the rest.
            }
        }
        // Where this handler went in place of JourneyMap's own - the first hand-over, and after JourneyMap reloads its
        // colors - every square it already drew was drawn without it, and is kept (see forgetEverything).
        if (replaced > 0) forgetEverything();
        return handed;
    }

    private static void install() {
        BlockGhost ghost = ModBlocks.ghostGrass();
        if (ghost == null) return;
        try {
            Class<?> blockMd = Class.forName("journeymap.client.model.block.BlockMD");
            Class<?> chunkMd = Class.forName("journeymap.client.model.chunk.ChunkMD");
            Class<?> proxyType = Class.forName("journeymap.client.mod.IBlockColorProxy");

            Method get = blockMd.getMethod("get", IBlockState.class);
            Method setProxy = blockMd.getMethod("setBlockColorProxy", proxyType);
            askColor = blockMd.getMethod("getBlockColor", chunkMd, BlockPos.class);
            try {
                askedNow = blockMd.getMethod("getBlockColorProxy");
            } catch (NoSuchMethodException older) {
                askedNow = null;
            }

            recordFor = get;
            handTo = setProxy;
            ours = Proxy.newProxyInstance(
                JourneyMapColors.class.getClassLoader(),
                new Class<?>[] { proxyType },
                new Answer(get));
            if (handOver() == 0) return;

            installed = true;
            Trmt.LOG.info("JourneyMap found; worn ground will be drawn on it in the color of what it covers");
        } catch (ClassNotFoundException movedOrGone) {
            refused = true;
            // At info, as the 1.7.10 edition says it, one line and no trace: debug reached only debug.log, where nobody
            // asking why the map shows no worn ground would look (0.9.222, spec CO17).
            Trmt.LOG.info(
                "JourneyMap's color classes are not where this build looks for them ({}); it draws worn ground in its own colors",
                String.valueOf(movedOrGone));
        } catch (Throwable awkward) {
            refused = true;
            Trmt.LOG.info(
                "JourneyMap's color proxy could not be installed ({}); it draws worn ground in its own colors",
                String.valueOf(awkward));
        }
    }

    /** What JourneyMap asks, and what this answers. Both of its methods want the same number. */
    private static final class Answer implements InvocationHandler {

        private final Method get;

        Answer(Method get) {
            this.get = get;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if ("toString".equals(name)) return "TrmtGhostColor";
            if ("hashCode".equals(name)) return Integer.valueOf(System.identityHashCode(proxy));
            if ("equals".equals(name)) return Boolean.valueOf(proxy == (args == null ? null : args[0]));

            try {
                // getBlockColor(ChunkMD, BlockMD, BlockPos) and
                // deriveBlockColor(BlockMD, ChunkMD, BlockPos) - the same question, the arguments
                // the other way round, so the chunk and the position are picked out by type.
                if (args != null && args.length >= 3) {
                    Object chunk = null;
                    BlockPos at = null;
                    for (Object each : args) {
                        if (each instanceof BlockPos) at = (BlockPos) each;
                        else if (each != null && each.getClass()
                            .getName()
                            .endsWith("ChunkMD")) chunk = each;
                    }
                    if (at != null) return Integer.valueOf(colorAt(chunk, at));
                }
            } catch (Throwable awkward) {
                // Fall through to the family's answer rather than letting a map tile fail.
            }

            // Anything else JourneyMap asks that wants a color - a palette export, say - gets the family's map
            // color, as the 1.7.10 edition's handler answers it (0.9.222, spec CO21). Non-color methods fall
            // through to null.
            Class<?> returns = method.getReturnType();
            if (returns == Integer.class || returns == int.class) {
                return Integer.valueOf(otherwise(args, clientWorld()));
            }
            return null;
        }

        /** The color to draw one position: the covered block's own, shaded for the wear. */
        private int colorAt(final Object chunk, final BlockPos at) {
            return colorFor(clientWorld(), at, origin -> originColor(chunk, origin, at));
        }

        /**
         * What JourneyMap would draw the covered block as, unworn, asked of its own tables, or -1 where it will not
         * say.
         *
         * <p>
         * At this square's own position, which is the whole point and was wrong first time: asking at
         * the origin of the world gave every worn square the color of whatever happens to stand
         * there, so a whole map of roads came out one flat color. The position is also what makes a
         * biome-tinted block answer for the biome it is actually in.
         */
        private int originColor(Object chunk, IBlockState origin, BlockPos at) {
            Method ask = askColor;
            if (ask == null || chunk == null) return -1;
            try {
                Object theirs = get.invoke(null, origin);
                if (theirs == null) return -1;
                Object answer = ask.invoke(theirs, chunk, at);
                return answer instanceof Integer ? ((Integer) answer).intValue() & 0xFFFFFF : -1;
            } catch (Throwable awkward) {
                return -1;
            }
        }
    }

    /** The client's world, or null where there is none to ask - a square's record is read through it. */
    @Nullable
    private static IBlockAccess clientWorld() {
        try {
            net.minecraft.client.Minecraft game = net.minecraft.client.Minecraft.getMinecraft();
            return game == null ? null : game.world;
        } catch (Throwable notThere) {
            return null;
        }
    }

    /**
     * The family a square is drawn as, from the client's record there, or null where nothing is recorded.
     */
    @Nullable
    private static SurfaceFamily familyAt(short record) {
        return BlockGhost.shows(record) ? ErosionState.familyOf(record) : null;
    }

    /**
     * The color for one square, as the 1.7.10 edition's {@code colorAt} works it out (0.9.222, spec CO19, CO20).
     *
     * <p>
     * It starts from the covered block's own: what JourneyMap draws that block unworn at this position, then its
     * vanilla map color, then the family's map color - and the family's map color where nothing was recorded under the
     * square. It fades by how far along its chain the square has come toward the family map color of what that chain
     * ends in, which for a chain that never leaves home is the family's own map color: 1.7.10 passes the family's color
     * there, not the square's, so such a square does fade. Package-private for its test, which hands in JourneyMap's
     * answer as {@code journeyMaps}, -1 for none.
     */
    static int colorFor(@Nullable IBlockAccess world, BlockPos at, ToIntFunction<IBlockState> journeyMaps) {
        short record = Trmt.proxy.ghostRecordAt(world, at.getX(), at.getY(), at.getZ());
        int packed = Trmt.proxy.ghostOriginAt(at.getX(), at.getY(), at.getZ());
        SurfaceFamily appearance = familyAt(record);

        if (packed >= 0) {
            IBlockState origin = Block.getStateById(packed);
            if (origin != null && !(origin.getBlock() instanceof BlockGhost)) {
                // The chain is the covered block's, not the ghost's - a worn lawn walks into
                // dirt partway down, and it is grass's eighty steps it is walking.
                SurfaceFamily base = SurfaceRegistry.familyOf(
                    origin.getBlock(),
                    origin.getBlock()
                        .getMetaFromState(origin));
                SurfaceFamily walking = base == null ? appearance : base;
                int own = journeyMaps.applyAsInt(origin);
                if (own < 0) own = GhostMapColor.coveredRgb(origin, world, at, appearance);
                return shaded(at, wornFraction(walking, appearance, record), own, GhostMapColor.rgbOf(endOf(walking)));
            }
        }
        return shaded(
            at,
            wornFraction(appearance, appearance, record),
            GhostMapColor.rgbOf(appearance),
            GhostMapColor.rgbOf(endOf(appearance)));
    }

    /** The shade, and the proof the first time a highlighted square was really asked for (spec CO22). */
    private static int shaded(BlockPos at, float worn, int base, int toward) {
        int result = shade(worn, base, toward);
        if (worn > 0f) announceOnce(at, base, result);
        return result;
    }

    /**
     * What anything else JourneyMap asks for a color is given: the family's map color at the square it names, grey
     * where it names none or nothing is recorded there - this one proxy stands for every square, where the 1.7.10
     * edition has a handler per family that knows its own (0.9.222, spec CO21).
     */
    static int otherwise(@Nullable Object[] args, @Nullable IBlockAccess world) {
        try {
            if (args != null) {
                for (Object each : args) {
                    if (!(each instanceof BlockPos)) continue;
                    BlockPos at = (BlockPos) each;
                    return GhostMapColor
                        .rgbOf(familyAt(Trmt.proxy.ghostRecordAt(world, at.getX(), at.getY(), at.getZ())));
                }
            }
        } catch (Throwable awkward) {
            // Grey rather than a map tile that fails.
        }
        return GhostMapColor.NO_FAMILY;
    }

    /**
     * What this ground turns into by the end of its run, or itself where it turns into nothing.
     *
     * <p>
     * Asked of the chain rather than of a table, so a pack that has pointed turf at its own loam gets that loam and
     * nothing here has to know it exists. The fade goes toward this family's map color, which for a chain that never
     * leaves home is the family's own - the 1.7.10 edition's code, whose comment calls that fade a no-op, which it is
     * not (0.9.222, spec CO20).
     */
    @Nullable
    static SurfaceFamily endOf(@Nullable SurfaceFamily base) {
        if (base == null) return null;
        int length = ErosionChain.length(base);
        if (length <= 0) return base;
        SurfaceFamily last = ErosionChain.familyAt(base, length - 1);
        return last == null ? base : last;
    }

    /** How far along its run this square is, nought to one. */
    private static float wornFraction(SurfaceFamily base, SurfaceFamily appearance, short record) {
        if (base == null) return 0f;
        int length = ErosionChain.length(base);
        if (length <= 1) return 0f;
        int index = ErosionChain.indexOf(base, appearance, ErosionState.layerOf(record), ErosionState.sinkOf(record));
        if (index < 0) index = 0;
        if (index > length - 1) index = length - 1;
        return index / (float) (length - 1);
    }

    // ------------------------------------------------------------------
    // The shading, which is the 1.7.10 edition's arithmetic
    // ------------------------------------------------------------------

    /**
     * Material first, then how used it is, then - only if somebody has asked for it - pulled toward
     * a color that is not a material at all. Each reading is laid over the last rather than
     * replacing it, so turning the third off leaves the first two exactly as they were.
     */
    private static int shade(float worn, int baseRgb, int towardRgb) {
        if (!TrmtConfig.mapTracksWear) return baseRgb;
        return highlightBy(darkenBy(blendToward(baseRgb, towardRgb, worn), worn), worn);
    }

    /**
     * A worn square's map color darkened for how used it is, as the map draws it before the path color pulls it: the
     * public seam the settings screen's color preview draws through (0.9.228, spec M10.6), as the 1.7.10 edition's
     * {@code GhostRendering.mapColorFor(worn, baseRgb)} is - this edition draws the path color on JourneyMap alone.
     */
    public static int mapColorFor(float worn, int baseRgb) {
        return darkenBy(baseRgb, worn);
    }

    private static int darkenBy(int rgb, float worn) {
        float depth = TrmtConfig.mapWearDarkening;
        float keep = 1f - Math.min(Math.max(worn, 0f), 1f) * Math.min(Math.max(depth, 0f), 0.9f);
        int result = 0;
        for (int channel = 0; channel < 3; channel++) {
            int shift = 16 - channel * 8;
            int value = Math.round(((rgb >>> shift) & 0xFF) * keep);
            if (value < 0) value = 0;
            if (value > 255) value = 255;
            result |= value << shift;
        }
        return result;
    }

    private static int blendToward(int from, int to, float amount) {
        // The one arithmetic for the path color, shared with the settings screen's preview (0.9.228, spec M10.6).
        return com.trmtgtnh.client.settings.ColorMath.mix(from, to, amount);
    }

    private static int highlightBy(int rgb, float worn) {
        // The color picker's preview draws through the same call (0.9.228, spec M10.6), so the two cannot drift.
        return com.trmtgtnh.client.settings.ColorMath
            .highlight(rgb, TrmtConfig.desirePathRgb, TrmtConfig.desirePathHighlight, worn);
    }
}
