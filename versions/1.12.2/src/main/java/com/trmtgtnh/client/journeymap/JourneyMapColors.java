package com.trmtgtnh.client.journeymap;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;

import com.trmtgtnh.ModsPresent;
import com.trmtgtnh.Trmt;
import com.trmtgtnh.block.BlockGhost;
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
 * why {@code GhostMapColor} settles for one darker entry per square and why four settings in this
 * edition's file say they do nothing. JourneyMap takes an ordinary RGB integer, so against it
 * {@code mapTracksWear}, {@code mapWearDarkening}, {@code desirePathHighlight} and
 * {@code desirePathRgb} all mean what they say.
 *
 * <p>
 * All of it is reflection, because JourneyMap is a soft dependency and not on the compile path. If it
 * is absent, or its internals have moved, every method here fails quietly and the ordinary
 * family-generic fallback carries other map mods. Nothing the game needs depends on any of this
 * working.
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

    /** Whether the attempt has been made and failed, so a broken look is not retried for ever. */
    private static volatile boolean refused;

    /** {@code BlockMD.getBlockColor(ChunkMD, BlockPos)}, for asking what a real block is drawn as. */
    private static volatile Method askColor;

    /**
     * Puts the proxy in place, once, from the client tick.
     *
     * <p>
     * From a tick rather than from start-up because JourneyMap builds its own tables as a world
     * loads, and a {@code BlockMD} asked for before then is one it will replace.
     */
    public static void tick() {
        if (installed || refused) return;
        if (!ModsPresent.has("journeymap")) {
            refused = true;
            return;
        }
        install();
    }

    /** Forgets the install, so the next world puts it back. */
    public static void reset() {
        installed = false;
        refused = false;
        askColor = null;
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

            Object mine = get.invoke(null, ghost.getDefaultState());
            if (mine == null) return;

            Object proxy = Proxy.newProxyInstance(
                JourneyMapColors.class.getClassLoader(),
                new Class<?>[] { proxyType },
                new Answer(get));
            setProxy.invoke(mine, proxy);

            installed = true;
            Trmt.LOG.info("JourneyMap found; worn ground will be drawn on it in the color of what it covers");
        } catch (ClassNotFoundException movedOrGone) {
            refused = true;
            Trmt.LOG.debug("JourneyMap's color classes are not where this build looks for them", movedOrGone);
        } catch (Throwable awkward) {
            refused = true;
            Trmt.LOG.debug("JourneyMap's color proxy could not be installed", awkward);
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
                // Fall through to the generic answer rather than letting a map tile fail.
            }

            Class<?> returns = method.getReturnType();
            if (returns == Integer.class || returns == int.class) {
                return Integer.valueOf(0x7F7F7F);
            }
            return null;
        }

        /** The color to draw one position: the covered block's own, shaded for the wear. */
        private int colorAt(Object chunk, BlockPos at) {
            net.minecraft.client.Minecraft game = net.minecraft.client.Minecraft.getMinecraft();
            short record = com.trmtgtnh.Trmt.proxy
                .ghostRecordAt(game == null ? null : game.world, at.getX(), at.getY(), at.getZ());
            int packed = com.trmtgtnh.Trmt.proxy.ghostOriginAt(at.getX(), at.getY(), at.getZ());
            SurfaceFamily appearance = ErosionState.familyOf(record);

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
                    int own = originColor(chunk, origin, at);
                    if (own != 0) {
                        return shade(wornFraction(walking, appearance, record), own, endColor(walking, own));
                    }
                }
            }
            return 0x7F7F7F;
        }

        /**
         * What JourneyMap would draw the covered block as, unworn, asked of its own tables.
         *
         * <p>
         * At this square's own position, which is the whole point and was wrong first time: asking at
         * the origin of the world gave every worn square the color of whatever happens to stand
         * there, so a whole map of roads came out one flat color. The position is also what makes a
         * biome-tinted block answer for the biome it is actually in.
         */
        private int originColor(Object chunk, IBlockState origin, BlockPos at) {
            Method ask = askColor;
            if (ask == null || chunk == null) return 0;
            try {
                Object theirs = get.invoke(null, origin);
                if (theirs == null) return 0;
                Object answer = ask.invoke(theirs, chunk, at);
                return answer instanceof Integer ? ((Integer) answer).intValue() : 0;
            } catch (Throwable awkward) {
                return 0;
            }
        }

        /**
         * The color the run ends on, or the color it starts on where it never leaves home.
         *
         * <p>
         * Asked of the chain rather than of a table, so a pack that has pointed turf at its own loam
         * gets that loam and nothing here has to know it exists. A family whose chain never leaves
         * home answers with the square's own color, which makes the fade toward it a no-op rather
         * than a case anybody has to write.
         */
        private int endColor(SurfaceFamily base, int fallback) {
            if (base == null) return fallback;
            int length = ErosionChain.length(base);
            if (length <= 0) return fallback;
            SurfaceFamily last = ErosionChain.familyAt(base, length - 1);
            if (last == null || last == base) return fallback;
            try {
                Block stock = com.trmtgtnh.command.CommandTrmt.roadBlock(last);
                if (stock == null) return fallback;
                net.minecraft.block.material.MapColor color = stock.getMapColor(stock.getDefaultState(), null, null);
                return color == null ? fallback : color.colorValue;
            } catch (RuntimeException awkwardBlock) {
                return fallback;
            }
        }

        /** How far along its run this square is, nought to one. */
        private float wornFraction(SurfaceFamily base, SurfaceFamily appearance, short record) {
            if (base == null) return 0f;
            int length = ErosionChain.length(base);
            if (length <= 1) return 0f;
            int index = ErosionChain
                .indexOf(base, appearance, ErosionState.layerOf(record), ErosionState.sinkOf(record));
            if (index < 0) index = 0;
            if (index > length - 1) index = length - 1;
            return index / (float) (length - 1);
        }
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
        float mix = amount < 0f ? 0f : (amount > 1f ? 1f : amount);
        if (mix <= 0f) return from;
        int result = 0;
        for (int channel = 0; channel < 3; channel++) {
            int shift = 16 - channel * 8;
            int start = (from >>> shift) & 0xFF;
            int end = (to >>> shift) & 0xFF;
            int value = Math.round(start + (end - start) * mix);
            if (value < 0) value = 0;
            if (value > 255) value = 255;
            result |= value << shift;
        }
        return result;
    }

    private static int highlightBy(int rgb, float worn) {
        float strength = TrmtConfig.desirePathHighlight;
        if (strength <= 0f) return rgb;
        float clamped = worn < 0f ? 0f : (worn > 1f ? 1f : worn);
        if (clamped <= 0f) return rgb;
        return blendToward(rgb, TrmtConfig.desirePathRgb, (float) Math.sqrt(clamped) * Math.min(strength, 1f));
    }
}
