package com.trmtgtnh.client.dh;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.enums.config.EDhApiHorizontalQuality;
import com.seibel.distanthorizons.api.enums.config.EDhApiMaxHorizontalResolution;
import com.seibel.distanthorizons.api.interfaces.block.IDhApiBlockStateWrapper;
import com.seibel.distanthorizons.api.interfaces.config.IDhApiConfigValue;
import com.seibel.distanthorizons.api.interfaces.config.client.IDhApiGraphicsConfig;
import com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiAfterRenderEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBlockColorOverrideEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBlockStateWrapperCreatedEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiChunkProcessingEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiLevelLoadEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam;
import com.seibel.distanthorizons.api.objects.DhApiResult;
import com.seibel.distanthorizons.api.objects.data.DhApiTerrainDataPoint;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.block.ModBlocks;
import com.trmtgtnh.client.DistantColors;

/**
 * Distant Horizons' far terrain told where the wear is and what it looks like (0.9.229 W6 T16, the spike, and T17, the
 * carry, here in its second phase as 1.20.1's plugin is; docs/goals/0.9.229-w6-spec.md 3.13 in the 1.7.10 tree).
 * <strong>The only class in this mod that names Distant Horizons</strong>, in a package that is the only place one may:
 * it is loaded only where that mod is (the client proxy asks Forge's loader first), so a game without it never loads a
 * class of it and starts exactly as before. Compiled against that mod's API from Modrinth's Maven and never shipped with
 * it (LGPL-3.0); DistantHorizonsIsTouchedOnlyThroughThePluginTest holds all three.
 *
 * <p>
 * Every signature here was read from the API classes of {@code DistantHorizons-3.3.3-1.12.2-forge.jar} (its API 7.2.0,
 * the version compiled against, whose 150 API classes javap line for line as {@code distanthorizonsapi-7.2.0.jar}'s), and
 * what it hands back from that jar's own {@code WrapperFactory}: a block state is wrapped from {IBlockState}, a chunk
 * from {Chunk, World}.
 * <ul>
 * <li>{@code DhApiBlockStateWrapperCreatedEvent}, fired once per block state as that mod first wraps it
 * ({@code BlockStateWrapper}): this mod's ghost is opted in with {@code setAllowApiColorOverride(true)} and drawn fully
 * opaque ({@code setOpacity(16)}, the figure its {@code LodDataBuilder} reads as opaque), and nothing else is touched,
 * so the color question below is asked of worn squares alone.</li>
 * <li>{@code DhApiChunkProcessingEvent}, fired as that mod builds its data from a chunk, once at each change of block
 * down a column ({@code LodDataBuilder}): on a square {@link DistantColors#wornAt} says is worn, the block is set to the
 * ghost, so a server chunk, which never holds one, still carries the path.</li>
 * <li>{@code DhApiBlockColorOverrideEvent}, fired as that mod works out a far square's color, for an opted-in state
 * only ({@code ClientBlockStateColorCache} reads the wrapper's flag first): {@link DistantColors#colorAt} answers; its
 * alpha is kept. That mod asks at the foot of the ghost's run, which reaches down through the ground under the worn
 * square ({@code FullDataToRenderDataTransformer}: the run's bottom plus the level's floor), so the answer is read up
 * the run from there (G17: the band drawn grey).</li>
 * <li>{@code DhApiAfterRenderEvent}, each frame that mod draws: counted, so the harness can wait for a far picture, and
 * the chunks owed to that mod are looked at ({@link DistantColors#drawn}).</li>
 * <li>{@code DhApiLevelLoadEvent}, as each of that mod's levels loads (its {@code levelWrapper}, fired by its
 * {@code DhClientServerWorld} and {@code DhClientWorld}): every state of the ghost is wrapped there by the plugin itself,
 * through {@code wrapperFactory.getBlockStateWrapper}, whose {@code WrapperFactory} calls
 * {@code BlockStateWrapper.fromBlockState} and so fires the wrapped event above while the plugin is bound. That mod
 * keeps every wrapper it makes ({@code WRAPPER_BY_BLOCK_STATE}, never cleared) and its API offers no way to make one
 * again, so the ghost is opted in by this call rather than whenever that mod first meets a ghost in a chunk, which on
 * 26.2 NeoForge was never (G17, carried from 1.20.1).</li>
 * </ul>
 * And a chunk the painter has just painted is handed back to it through {@code terrainRepo.overwriteChunkDataAsync},
 * because it read that chunk once, as it arrived, before the ghost was there. That call answers success and queues the
 * chunk, or drops it without a word where the chunk is already queued or set aside ({@code SharedApi.applyChunkUpdate}),
 * so each chunk handed over is owed until the processing event names it ({@link DistantColors#processing}) and is
 * handed over again until then. The events run on that mod's own worker threads; what they ask of this mod is the
 * client's record at a square, which the ghost's model already reads off the main thread. And where the overlay is
 * switched either way, {@code renderProxy.clearRenderDataCache} has it build its far terrain again from the data it
 * holds, so every opted-in square is asked its color again ({@link DistantColors#followTheSwitch}; Xep, 2026-11-09:
 * "Follow the switch").
 */
public final class DhPlugin implements DistantColors.Plugin {

    /** The name this mod gives that mod as the one setting its options, so a player can see who did. */
    static final String CALLER = "TRMT: Reimagined";

    /** The opacity that mod reads as a whole block. */
    static final int OPAQUE = 16;

    /** The ghost as that mod wraps it, made once it is first needed. */
    private static volatile IDhApiBlockStateWrapper ghostWrapped;

    private DhPlugin() {}

    /** Binds the events and opens the door; said once in the log either way. */
    public static void register() {
        try {
            DhApi.events.bind(DhApiBlockStateWrapperCreatedEvent.class, new OptInTheGhost());
            DhApi.events.bind(DhApiChunkProcessingEvent.class, new PutTheWearOn());
            DhApi.events.bind(DhApiBlockColorOverrideEvent.class, new ColorTheWear());
            DhApi.events.bind(DhApiAfterRenderEvent.class, new CountTheFrames());
            DistantColors.registered(new DhPlugin());
            DhApi.events.bind(DhApiLevelLoadEvent.class, new OptInEveryGhost());
            Trmt.LOG.info(
                "Distant Horizons {} (API {}.{}.{}): worn squares are drawn in far terrain in their map color",
                DhApi.getModVersion(),
                Integer.valueOf(DhApi.getApiMajorVersion()),
                Integer.valueOf(DhApi.getApiMinorVersion()),
                Integer.valueOf(DhApi.getApiPatchVersion()));
        } catch (RuntimeException | LinkageError refused) {
            // An API this was not compiled against: far terrain keeps that mod's own colors, and the game goes on.
            Trmt.LOG.warn("Distant Horizons is loaded and its API refused this mod's plugin; far terrain keeps its own colors: {}", refused.toString());
        }
    }

    /** This mod's ghost, and nothing else, asked for its color and drawn as a whole block. */
    static final class OptInTheGhost extends DhApiBlockStateWrapperCreatedEvent {

        @Override
        public void blockStateWrapperCreated(DhApiEventParam<DhApiBlockStateWrapperCreatedEvent.EventParam> event) {
            if (event == null || event.value == null) return;
            IDhApiBlockStateWrapper wrapper = event.value.getBlockStateWrapper();
            if (wrapper != null && DistantColors.optsIn(wrapper.getWrappedMcObject())) {
                event.value.setAllowApiColorOverride(true);
                event.value.setOpacity(OPAQUE);
            }
        }
    }

    /** Every state of this mod's ghost wrapped as each of that mod's levels loads, so each is opted in now (G17). */
    static final class OptInEveryGhost extends DhApiLevelLoadEvent {

        @Override
        public void onLevelLoad(DhApiEventParam<DhApiLevelLoadEvent.EventParam> event) {
            if (event == null || event.value == null) return;
            DistantColors.levelLoaded(event.value.levelWrapper);
        }
    }

    /** The ghost put on every worn square of a chunk that mod builds its data from. */
    static final class PutTheWearOn extends DhApiChunkProcessingEvent {

        @Override
        public void blockOrBiomeChangedDuringChunkProcessing(DhApiEventParam<DhApiChunkProcessingEvent.EventParam> event) {
            if (event == null || event.value == null) return;
            DistantColors.processing(event.value.chunkX, event.value.chunkZ);
            DhApiChunkProcessingEvent.EventParam at = event.value;
            IDhApiBlockStateWrapper current = at.currentBlock;
            if (current == null || current.isAir() || current.isLiquid()) return;
            IBlockAccess world = clientWorldFor(at.levelWrapper);
            if (world == null) return;
            int x = (at.chunkX << 4) + at.relativeBlockPosX;
            int z = (at.chunkZ << 4) + at.relativeBlockPosZ;
            if (!DistantColors.wornAt(world, x, at.blockPosY, z)) return;
            Object state = current.getWrappedMcObject();
            if (state instanceof IBlockState && ModBlocks.isGhost(((IBlockState) state).getBlock())) return;
            IDhApiBlockStateWrapper ghost = ghostFor(at.levelWrapper);
            if (ghost != null) at.setBlockOverride(ghost);
        }
    }

    /** A worn square's color, from this client's record there or remembered from it. */
    static final class ColorTheWear extends DhApiBlockColorOverrideEvent {

        @Override
        public void onBlockColorOverridden(DhApiEventParam<DhApiBlockColorOverrideEvent.EventParam> event) {
            if (event == null || event.value == null) return;
            DhApiBlockColorOverrideEvent.EventParam square = event.value;
            IDhApiBlockStateWrapper wrapper = square.getBlockStateWrapper();
            Object state = wrapper == null ? null : wrapper.getWrappedMcObject();
            if (!(state instanceof IBlockState)) return;
            int rgb = DistantColors.colorAt(
                clientWorldFor(square.getLevelWrapper()),
                (IBlockState) state,
                square.getBlockPosX(),
                square.getBlockPosY(),
                square.getBlockPosZ());
            if (rgb == DistantColors.OWN) return;
            square.setColor(square.getAlpha(), (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
        }
    }

    /** Each frame that mod draws, counted for the harness. */
    static final class CountTheFrames extends DhApiAfterRenderEvent {

        @Override
        public void afterRender(DhApiEventParam<Void> event) {
            DistantColors.drawn();
        }
    }

    /**
     * This client's world, where the level that mod is building is the same dimension: the records are the client's,
     * whether that mod is reading the client's chunk or, in a world of the client's own, the server's. Null for another
     * dimension's level, whose squares this client knows nothing of.
     */
    private static IBlockAccess clientWorldFor(IDhApiLevelWrapper level) {
        Minecraft game = Minecraft.getMinecraft();
        World client = game == null ? null : game.world;
        if (client == null) return null;
        Object wrapped = level == null ? null : level.getWrappedMcObject();
        if (wrapped instanceof World && ((World) wrapped).provider.getDimension() != client.provider.getDimension()) return null;
        return client;
    }

    /** The ghost's default state as that mod wraps it, made once. */
    private static IDhApiBlockStateWrapper ghostFor(IDhApiLevelWrapper level) {
        IDhApiBlockStateWrapper held = ghostWrapped;
        if (held != null || ModBlocks.ghostGrass() == null || DhApi.Delayed.wrapperFactory == null) return held;
        held = DhApi.Delayed.wrapperFactory.getBlockStateWrapper(new Object[] { ModBlocks.ghostGrass()
            .getDefaultState() }, level);
        ghostWrapped = held;
        return held;
    }

    /** That mod's wrapper of this world, or of its single-player level where it holds none of this one. */
    private static IDhApiLevelWrapper levelOf(Object world) {
        if (DhApi.Delayed.worldProxy == null || !DhApi.Delayed.worldProxy.worldLoaded()) return null;
        for (IDhApiLevelWrapper each : DhApi.Delayed.worldProxy.getAllLoadedLevelWrappers()) {
            if (each != null && each.getWrappedMcObject() == world) return each;
        }
        return DhApi.Delayed.worldProxy.getSinglePlayerLevel();
    }

    @Override
    public String heldAt(int x, int y, int z) {
        IDhApiLevelWrapper level = DhApi.Delayed.worldProxy == null ? null : DhApi.Delayed.worldProxy.getSinglePlayerLevel();
        if (level == null || DhApi.Delayed.terrainRepo == null) return "no single-player level loaded in Distant Horizons";
        DhApiResult<DhApiTerrainDataPoint> found = DhApi.Delayed.terrainRepo
            .getSingleDataPointAtBlockPos(level, x, y, z, DhApi.Delayed.terrainRepo.createSoftCache());
        if (found == null || !found.success) return "no reading: " + (found == null ? "no answer" : found.message);
        if (found.payload == null) return "nothing";
        DhApiTerrainDataPoint point = found.payload;
        return (point.blockStateWrapper == null ? "no block" : point.blockStateWrapper.getSerialString()) + " (detail level "
            + point.detailLevel + ", y " + point.bottomYBlockPos + " to " + point.topYBlockPos + ")";
    }

    @Override
    public String reread(Object world, Object chunk) {
        if (DhApi.Delayed.terrainRepo == null) return "no terrain repository yet";
        IDhApiLevelWrapper level = levelOf(world);
        if (level == null) return "no level of this world loaded in Distant Horizons";
        DhApiResult<Void> done = DhApi.Delayed.terrainRepo.overwriteChunkDataAsync(level, new Object[] { chunk, world });
        return done != null && done.success ? null : done == null ? "no answer" : done.message;
    }

    @Override
    public String wrap(Object state, Object level) {
        if (DhApi.Delayed.wrapperFactory == null) return "no wrapper factory yet";
        if (!(level instanceof IDhApiLevelWrapper)) return "not a level of Distant Horizons': " + level;
        return DhApi.Delayed.wrapperFactory.getBlockStateWrapper(new Object[] { state }, (IDhApiLevelWrapper) level) == null
            ? "no wrapper made"
            : null;
    }

    @Override
    public String redraw() {
        if (DhApi.Delayed.renderProxy == null) return "no render proxy yet";
        DhApiResult<Boolean> done = DhApi.Delayed.renderProxy.clearRenderDataCache();
        return done != null && done.success ? null : done == null ? "no answer" : done.message;
    }

    @Override
    public void pinForPhotograph(int chunks) {
        if (DhApi.Delayed.configs == null) return;
        IDhApiGraphicsConfig graphics = DhApi.Delayed.configs.graphics();
        if (chunks > 0) {
            set(graphics.chunkRenderDistance(), Integer.valueOf(chunks));
            set(graphics.fog()
                .enableDhFog(), Boolean.FALSE);
            set(graphics.horizontalQuality(), EDhApiHorizontalQuality.EXTREME);
            set(graphics.maxHorizontalResolution(), EDhApiMaxHorizontalResolution.BLOCK);
        } else {
            graphics.chunkRenderDistance()
                .clearValue();
            graphics.fog()
                .enableDhFog()
                .clearValue();
            graphics.horizontalQuality()
                .clearValue();
            graphics.maxHorizontalResolution()
                .clearValue();
        }
    }

    /** A setting set with this mod named as the one setting it: the API's own form, not the deprecated one without. */
    private static <T> void set(IDhApiConfigValue<T> setting, T value) {
        setting.setValue(value, CALLER);
    }
}
