package com.trmtgtnh.client.journeymap;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.EnumMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.block.material.MapColor;
import net.minecraft.client.Minecraft;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.block.GhostBlock;
import com.trmtgtnh.block.GhostRendering;
import com.trmtgtnh.block.ModBlocks;
import com.trmtgtnh.client.ClientErosionCache;
import com.trmtgtnh.erosion.ErosionChain;
import com.trmtgtnh.erosion.ErosionState;
import com.trmtgtnh.surface.SurfaceFamily;
import com.trmtgtnh.surface.SurfaceRegistry;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Makes JourneyMap draw each worn position in a colour taken from the block underneath it.
 *
 * <p>
 * The problem this solves: JourneyMap decides a block's colour once per block-and-metadata and
 * caches it, so one ghost block covering a hundred different modded dirts drew all hundred the
 * same brown. An unworn modded block shows its own distinct colour on the map - that colour is
 * JourneyMap's own average of the block's texture - and the moment it wore, that distinctness
 * was lost. This hands JourneyMap a colour handler for the ghost blocks that answers per
 * <em>position</em> instead: it looks up which real block the client is painting over at that
 * spot, asks JourneyMap what colour it draws that block unworn, and darkens it for the wear.
 *
 * <p>
 * All of it is reflection, because JourneyMap is a soft dependency and not on the compile path.
 * If it is absent, or its internals have moved, every method here fails quietly behind the
 * {@link Loader#isModLoaded} guard and the ordinary family-generic fallback carries other map
 * mods. Nothing the game needs depends on any of this working.
 *
 * <p>
 * The handler has to be reinstalled from time to time: JourneyMap resets its block descriptors
 * when it reloads colours or changes dimension, which drops our handler back to its own. A cheap
 * watchdog on the client tick puts it back. Reinstalling is idempotent, so a spare pass costs
 * nothing but a few reflective calls over a few dozen blocks.
 */
@SideOnly(Side.CLIENT)
public final class JourneyMapColors {

    private static final int REINSTALL_INTERVAL_TICKS = 100;

    private static boolean resolved;
    private static boolean usable;

    /** {@code static BlockMD BlockMD.get(Block, int)}. */
    private static Method mGet;
    /** {@code void BlockMD.setBlockColorHandler(IModBlockColorHandler)}. */
    private static Method mSetHandler;
    /** {@code int BlockMD.getColor(ChunkMD, int, int, int)} - the origin's own unworn colour. */
    private static Method mGetColor;
    private static Class<?> handlerInterface;

    private static final Map<SurfaceFamily, Object> PROXIES = new EnumMap<SurfaceFamily, Object>(SurfaceFamily.class);

    private static int tickCounter;

    private JourneyMapColors() {}

    /** Called from the client tick. Installs the handler, and puts it back if JourneyMap dropped it. */
    public static void tick() {
        if (!Loader.isModLoaded("journeymap")) return;
        if (!resolve()) return;
        if (tickCounter++ % REINSTALL_INTERVAL_TICKS != 0) return;
        install();
        watchdog();
    }

    /** Forgets everything, so a fresh world resolves and installs again. */
    public static void reset() {
        tickCounter = 0;
        // And the proof, so a new world proves the highlight again rather than
        // inheriting the last one's answer.
        answered = false;
        warned = false;
        quiet = 0;
    }

    /** Set the first time JourneyMap actually asked us for a highlighted colour. */
    private static volatile boolean answered;

    /** Set once the watchdog has complained, so it complains once rather than every five seconds. */
    private static boolean warned;

    /** Install passes since worn ground existed on this client with nothing asked for. */
    private static int quiet;

    /**
     * Says once, in the log, that the desire-path highlight is genuinely being applied.
     *
     * <p>
     * Six things have to be true before one pixel of this exists - JourneyMap installed, its
     * internals where the resolver expects them, our handler still on the block descriptor,
     * JourneyMap actually calling it, the setting above nought, and the position worn at all - and
     * five of the six fail silently. This is the line that says all of them held. Both colours are
     * printed, so it also proves the blend ran rather than passing its input through.
     */
    private static void announceOnce(int x, int y, int z, int base, int result) {
        if (answered || com.trmtgtnh.config.TrmtConfig.desirePathHighlight <= 0f) return;
        answered = true;
        Trmt.LOG.info(
            "Desire-path highlight is live: JourneyMap asked for {},{},{} and was given #{} where the ground's own colour is #{}",
            new Object[] { Integer.valueOf(x), Integer.valueOf(y), Integer.valueOf(z),
                String.format("%06X", Integer.valueOf(result & 0xFFFFFF)),
                String.format("%06X", Integer.valueOf(base & 0xFFFFFF)) });
    }

    /**
     * Says once, in the log, when the highlight is on and nothing has come of it.
     *
     * <p>
     * The piece two past silent failures argue for: a feature that never ran has to say so
     * unprompted rather than waiting to be asked. Held until this client actually holds worn
     * ground, because complaining on a fresh world - where there is simply nothing to highlight
     * yet - would train everybody to ignore the line, which is worse than having no line.
     */
    private static void watchdog() {
        if (com.trmtgtnh.config.TrmtConfig.desirePathHighlight <= 0f || answered || warned) {
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
            "Desire-path highlight is on and this client has worn ground, but JourneyMap has not asked this mod for a colour in twenty seconds. Either JourneyMap is not mapping right now - automap off, or nothing worn within range - or it kept a colour handler of its own instead of ours.");
    }

    // ------------------------------------------------------------------
    // Reflection setup
    // ------------------------------------------------------------------

    private static boolean resolve() {
        if (resolved) return usable;
        resolved = true;
        try {
            Class<?> blockMd = Class.forName("journeymap.client.model.BlockMD");
            Class<?> chunkMd = Class.forName("journeymap.client.model.ChunkMD");
            handlerInterface = Class.forName("journeymap.client.model.mod.ModBlockDelegate$IModBlockColorHandler");
            mGet = blockMd.getMethod("get", Block.class, int.class);
            mSetHandler = blockMd.getMethod("setBlockColorHandler", handlerInterface);
            mGetColor = blockMd.getMethod("getColor", chunkMd, int.class, int.class, int.class);
            usable = true;
        } catch (Throwable notThere) {
            // A different JourneyMap, or none. Leave the fallbacks to carry the map.
            Trmt.LOG.info("JourneyMap per-block colours unavailable ({}); using the family fallback", notThere);
            usable = false;
        }
        return usable;
    }

    // ------------------------------------------------------------------
    // Installing the handler
    // ------------------------------------------------------------------

    private static void install() {
        for (Block block : ModBlocks.all()) {
            if (!(block instanceof GhostBlock)) continue;
            Object proxy = proxyFor(((GhostBlock) block).appearance());
            if (proxy == null) continue;
            for (int meta = 0; meta < 16; meta++) {
                try {
                    Object blockMd = mGet.invoke(null, block, Integer.valueOf(meta));
                    if (blockMd != null) mSetHandler.invoke(blockMd, proxy);
                } catch (Throwable awkward) {
                    // One metadata JourneyMap will not describe is not worth stopping the rest.
                }
            }
        }
    }

    private static Object proxyFor(SurfaceFamily appearance) {
        Object proxy = PROXIES.get(appearance);
        if (proxy != null) return proxy;
        try {
            proxy = Proxy.newProxyInstance(
                handlerInterface.getClassLoader(),
                new Class<?>[] { handlerInterface },
                new Handler(appearance));
            PROXIES.put(appearance, proxy);
        } catch (Throwable awkward) {
            return null;
        }
        return proxy;
    }

    // ------------------------------------------------------------------
    // The handler itself
    // ------------------------------------------------------------------

    private static final class Handler implements InvocationHandler {

        private final SurfaceFamily appearance;

        Handler(SurfaceFamily appearance) {
            this.appearance = appearance;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if (method.getDeclaringClass() == Object.class) {
                if ("toString".equals(name)) return "TrmtGhostColour(" + appearance + ")";
                if ("hashCode".equals(name)) return Integer.valueOf(System.identityHashCode(proxy));
                if ("equals".equals(name)) return Boolean.valueOf(proxy == (args == null ? null : args[0]));
                return null;
            }
            try {
                // getBlockColor(ChunkMD, BlockMD, worldX, y, worldZ): the per-position question.
                if ("getBlockColor".equals(name) && args != null && args.length >= 5) {
                    int x = ((Integer) args[2]).intValue();
                    int y = ((Integer) args[3]).intValue();
                    int z = ((Integer) args[4]).intValue();
                    return Integer.valueOf(colourAt(args[0], x, y, z));
                }
                // Anything else JourneyMap asks that wants a colour - a palette export, say - gets
                // the family-generic answer. Non-colour methods (void, boolean) fall through to null.
                Class<?> returns = method.getReturnType();
                if (returns == Integer.class || returns == int.class) {
                    return Integer.valueOf(GhostRendering.mapFallbackColour(appearance));
                }
            } catch (Throwable awkward) {
                Class<?> returns = method.getReturnType();
                if (returns == Integer.class || returns == int.class) {
                    return Integer.valueOf(GhostRendering.mapFallbackColour(appearance));
                }
            }
            return null;
        }

        /** The colour to draw one position: the origin block's own, darkened for the wear. */
        private int colourAt(Object chunkMd, int x, int y, int z) {
            int wearMeta = 0;
            try {
                Minecraft mc = Minecraft.getMinecraft();
                if (mc.theWorld != null) wearMeta = mc.theWorld.getBlockMetadata(x, y, z);
            } catch (Throwable ignore) {
                // A read racing a chunk unload. Zero is the least-worn stage, which is harmless.
            }

            int packed = -1;
            try {
                packed = ClientErosionCache.get()
                    .originAt(x, y, z);
            } catch (Throwable ignore) {
                // Same race. Fall through to the family colour.
            }

            if (packed >= 0) {
                Block origin = Block.getBlockById(packed >> 4);
                int originMeta = packed & 0xF;
                if (origin != null && !(origin instanceof GhostBlock)) {
                    // The chain is the origin block's, not the ghost's - a worn lawn walks into
                    // dirt partway down, and it is grass's eighty steps it is walking.
                    SurfaceFamily base = SurfaceRegistry.familyOf(origin, originMeta);
                    SurfaceFamily walking = base == null ? appearance : base;
                    return GhostRendering.mapColourFor(
                        wornFraction(walking, wearMeta, x, y, z),
                        originColour(chunkMd, origin, originMeta, x, y, z),
                        GhostRendering.mapFallbackColour(endOf(walking)));
                }
            }
            return GhostRendering.mapColourFor(
                wornFraction(appearance, wearMeta, x, y, z),
                GhostRendering.mapFallbackColour(appearance),
                GhostRendering.mapFallbackColour(endOf(appearance)));
        }

        /**
         * What this ground turns into by the end of its run, or itself where it turns into nothing.
         *
         * <p>
         * Asked of the chain rather than of a table, so a pack that has pointed turf at its own
         * loam gets that loam and nothing here has to know it exists. A family whose chain never
         * leaves home answers with itself, which makes the fade toward it a no-op rather than a
         * case anybody has to write.
         */
        private SurfaceFamily endOf(SurfaceFamily base) {
            if (base == null) return null;
            int length = com.trmtgtnh.erosion.ErosionChain.length(base);
            if (length <= 0) return base;
            SurfaceFamily last = com.trmtgtnh.erosion.ErosionChain.familyAt(base, length - 1);
            return last == null ? base : last;
        }

        /**
         * How far along its whole run this position is, from nothing to one.
         *
         * <p>
         * The layer alone will not do it, and that is the whole of what was wrong here. A layer is
         * sixteen gradations and the ground sinks a pixel at the end of each one, so a square four
         * layers down reports the same layer number as one that has barely begun - and the map
         * drew them the same shade. The sink is the other half of the answer, cached beside the
         * state the client already draws from, and with both the chain can say exactly which of
         * its eighty steps this is.
         */
        private float wornFraction(SurfaceFamily base, int layer, int x, int y, int z) {
            if (base == null) return 0f;
            int length = ErosionChain.length(base);
            if (length <= 1) return 0f;
            int sink = 0;
            try {
                short state = ClientErosionCache.get()
                    .stateAt(x, y, z);
                if (state != ErosionState.NONE) sink = ErosionState.sinkOf(state);
            } catch (Throwable ignore) {
                // A read racing a chunk unload. No sink is the least-worn answer, and harmless.
            }
            int index = ErosionChain.indexOf(base, appearance, layer, sink);
            if (index < 0) index = 0;
            if (index > length - 1) index = length - 1;
            return index / (float) (length - 1);
        }

        /**
         * The colour JourneyMap draws the origin block unworn - its own texture average, which is
         * the distinct dot each modded block shows. Falls back to the block's vanilla map colour,
         * then to the family colour, if JourneyMap will not answer.
         */
        private int originColour(Object chunkMd, Block origin, int originMeta, int x, int y, int z) {
            try {
                Object originMd = mGet.invoke(null, origin, Integer.valueOf(originMeta));
                if (originMd != null) {
                    Object colour = mGetColor
                        .invoke(originMd, chunkMd, Integer.valueOf(x), Integer.valueOf(y), Integer.valueOf(z));
                    if (colour instanceof Integer) return ((Integer) colour).intValue() & 0xFFFFFF;
                }
            } catch (Throwable awkward) {
                // JourneyMap could not colour the origin. Its own map colour is the next best.
            }
            try {
                MapColor own = origin.getMapColor(originMeta);
                if (own != null) return own.colorValue;
            } catch (Throwable awkward) {
                // Fall through.
            }
            return GhostRendering.mapFallbackColour(appearance);
        }
    }
}
