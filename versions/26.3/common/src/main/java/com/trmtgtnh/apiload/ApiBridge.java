package com.trmtgtnh.apiload;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.api.Actions;
import com.trmtgtnh.api.Queries;
import com.trmtgtnh.api.Trmtr;
import com.trmtgtnh.api.TrmtrApi;
import com.trmtgtnh.api.Wear;
import com.trmtgtnh.api.WearContext;
import com.trmtgtnh.apihost.PluginHost;
import com.trmtgtnh.apihost.WearRecord;
import com.trmtgtnh.block.ModBlocks;
import com.trmtgtnh.client.ClientErosionCache;
import com.trmtgtnh.config.FamilySettings;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.data.DataKeys;
import com.trmtgtnh.data.Selector;
import com.trmtgtnh.erosion.Dimensions;
import com.trmtgtnh.erosion.ErosionChain;
import com.trmtgtnh.erosion.ErosionEngine;
import com.trmtgtnh.erosion.ErosionEntry;
import com.trmtgtnh.erosion.ErosionState;
import com.trmtgtnh.erosion.ErosionStore;
import com.trmtgtnh.erosion.RecordReadout;
import com.trmtgtnh.erosion.SinkProfile;
import com.trmtgtnh.surface.SurfaceFamily;
import com.trmtgtnh.surface.SurfaceRegistry;
import com.trmtgtnh.surface.SurfaceShape;

/**
 * TRMTR as the 26.3 edition hands it to a mod: {@link Trmtr}, its {@link Queries} and its {@link Actions} (W3 spec
 * 2.5; api/API.md; carried from the 1.16.5 tree's class of the same name in T18). Installed into {@code TrmtrApi}
 * once, by {@link ApiLoad#start}, before any plugin is called.
 *
 * <p>
 * <strong>Queries</strong> by key ({@code familyOf}, {@code chainLength}) answer on any thread from what is published:
 * the surface table (published whole by {@code SurfaceRegistry.resolve}) and the chains. A question about a position
 * ({@code familyAt}, {@code chainLengthAt}, {@code wearAt}) reads the live world and the server's store or the
 * client's cache, so it is answered only on that world's own thread and answers as unworn elsewhere, said once (Codex
 * review R16); a position that is not loaded answers as unworn too, and a chunk still being finished counts as not
 * loaded, because a read of it from its own load waits for ever (0.9.229, W3). {@code familyOf(Object)} takes a
 * {@code BlockState} here (API.md's table). A selector by name ({@code ns:name}, or with properties
 * {@code ns:name[type=bottom]}) answers where every state of the block it takes is in one family, and where they differ
 * is null, the forms to use said once in the log; a tag names no one block, and answers null.
 *
 * <p>
 * <strong>Actions</strong> run on the server thread only, through the engine's own paths, so a pin, the per-chunk cap,
 * the wear ceiling and the plugins' own rules and listeners hold for them as for traffic; each wear carries the cause
 * {@code API}. Called anywhere else, or with a client's world, each returns false and says so once. On a chunk that is
 * not loaded and whole each returns false, for the same reason as a query.
 */
public final class ApiBridge implements Trmtr, Queries, Actions {

    static final ApiBridge INSTANCE = new ApiBridge();

    /** At most so many steps one {@link #restore} gives back, which is more than any run has. */
    static final int MOST_RESTORED = 512;

    private final List<String> families;

    private ApiBridge() {
        List<String> keys = new ArrayList<String>();
        for (SurfaceFamily family : SurfaceFamily.values()) keys.add(family.key());
        families = Collections.unmodifiableList(keys);
    }

    // ------------------------------------------------------------------
    // Trmtr
    // ------------------------------------------------------------------

    @Override
    public String version() {
        return TrmtrApi.VERSION;
    }

    @Override
    public boolean atLeast(int major, int minor) {
        return PluginHost.atLeast(TrmtrApi.VERSION, major, minor);
    }

    @Override
    public int format() {
        return DataKeys.FORMAT;
    }

    @Override
    public List<String> families() {
        return families;
    }

    @Override
    public Queries queries() {
        return this;
    }

    @Override
    public Actions actions() {
        return this;
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    @Override
    public String familyOf(String blockSelector) {
        try {
            Selector selector = Selector.block(blockSelector);
            if (selector == null || selector.tag || selector.meta != Selector.ANY_META) return null;
            Identifier at = Identifier.tryParse(selector.id);
            if (at == null) return null;
            Block block = BuiltInRegistries.BLOCK.getOptional(at)
                .orElse(null);
            if (block == null) return null;
            return everyState(block, selector, selector.id);
        } catch (RuntimeException unread) {
            return null;
        }
    }

    @Override
    public String familyOf(Object blockState) {
        if (blockState instanceof String) return familyOf((String) blockState);
        if (!(blockState instanceof BlockState)) {
            ApiLoad.host()
                .sayOnce(
                    "familyOf|" + (blockState == null ? "null"
                        : blockState.getClass()
                            .getName()),
                    PluginHost.PREFIX
                        + "familyOf(Object) takes a net.minecraft.world.level.block.state.BlockState on 26.3, and was"
                        + " given "
                        + (blockState == null ? "null"
                            : "a " + blockState.getClass()
                                .getName())
                        + ", so it answered null");
            return null;
        }
        try {
            return key(SurfaceRegistry.familyOf((BlockState) blockState));
        } catch (RuntimeException unread) {
            return null;
        }
    }

    /**
     * The family every state the selector takes shares (each state of the block, or each with its properties), or
     * null, said once where they differ.
     */
    private static String everyState(Block block, Selector selector, String name) {
        List<String> taken = new ArrayList<String>();
        for (BlockState state : block.getStateDefinition()
            .getPossibleStates()) {
            if (!selector.matches(selector.id, Selector.ANY_META, propertiesOf(state), null)) continue;
            taken.add(key(SurfaceRegistry.familyOf(state)));
        }
        String[] byState = taken.toArray(new String[taken.size()]);
        String shared = PluginHost.sharedFamily(byState);
        if (shared == null && PluginHost.mixedFamilies(byState)) {
            ApiLoad.host()
                .sayOnce("forms|" + name, PluginHost.formsLine(name));
        }
        return shared;
    }

    @Override
    public String familyAt(Object level, int x, int y, int z) {
        if (!ownThread(level, "familyAt")) return null;
        try {
            return key(chainThere(level, x, y, z).base);
        } catch (RuntimeException unread) {
            return null;
        }
    }

    @Override
    public int chainLength(String family) {
        SurfaceFamily known = SurfaceFamily.byKey(family);
        return known == null ? 0 : ErosionChain.length(known);
    }

    @Override
    public int chainLengthAt(Object level, int x, int y, int z) {
        if (!ownThread(level, "chainLengthAt")) return 0;
        try {
            // The block's own chain where a data file gives it one (W3 T9), as Queries promises.
            return chainThere(level, x, y, z).length();
        } catch (RuntimeException unread) {
            return 0;
        }
    }

    @Override
    public Wear wearAt(Object level, int x, int y, int z) {
        if (!ownThread(level, "wearAt")) return null;
        Level world = (Level) level;
        try {
            if (!world.isClientSide()) return whole(world, x, z) ? ApiHooks.wearAt(world, x, y, z) : null;
            short state = ClientErosionCache.get()
                .stateAt(x, y, z);
            if (state == ErosionState.NONE) return null;
            BlockState there = groundThere(world, x, y, z);
            return there == null ? clientWear(state, ErosionChain.of(null)) : clientWearOn(state, there);
        } catch (RuntimeException unread) {
            return null;
        }
    }

    /**
     * Whether a question about a position may be answered here: it reads the live world and store, so only on that
     * world's own thread, the server's for a server's world and the client's for a client's (Codex review R16).
     * Anywhere else, or given no world, it is said once for that query and the caller answers its no-answer.
     */
    private static boolean ownThread(Object level, String query) {
        if (level instanceof Level
            && (((Level) level).isClientSide() ? com.trmtgtnh.Client.onClientThread() : Trmt.onServerThread())) {
            return true;
        }
        ApiLoad.host()
            .sayOnce(
                "queries|" + query,
                PluginHost.PREFIX + "Queries."
                    + query
                    + " was asked "
                    + (level instanceof Level ? "off that world's own thread" : "without a world")
                    + " and answered as if nothing were there; a question about a position is answered only on its"
                    + " world's thread, the server's or the client's");
        return false;
    }

    /**
     * The chain the block at a position follows as it stands - its own where a data file gives it one (W3 T9); on a
     * client, the block a worn ghost stands in for. One with no family and no steps where it does not wear, or the
     * position is not loaded.
     */
    private static ErosionChain.Chain chainThere(Object level, int x, int y, int z) {
        BlockState ground = groundThere(level, x, y, z);
        return ground == null ? ErosionChain.of(null) : SurfaceRegistry.chainOf(ground);
    }

    /** The block state a position stands for: on a client, what a worn ghost stands in for. Null where unknown. */
    private static BlockState groundThere(Object level, int x, int y, int z) {
        if (!(level instanceof Level)) return null;
        Level world = (Level) level;
        if (!whole(world, x, z) || !com.trmtgtnh.util.Worlds.loaded(world, x, y, z)) return null;
        BlockState state = com.trmtgtnh.util.Worlds.stateAt(world, x, y, z);
        if (world.isClientSide() && ModBlocks.isGhost(state.getBlock())) {
            int origin = ClientErosionCache.get()
                .originAt(x, y, z);
            if (origin < 0) return null;
            // The cache keeps the covered block as the game's own state id (Block.getId), as every reader of it on
            // this edition decodes it.
            state = Block.stateById(origin);
        }
        return state;
    }

    /**
     * A server record as the API hands it, its step and steps counted on the chain its block follows (W3 T9); on the
     * family's chain of what it shows where the block does not wear.
     */
    /**
     * A server record where its block is known: {@code sinkPixels} is the depth drawn on that block, by the same call the
     * drawing makes, so a slab's halved cap and a data shape's own rule are in it. One source says both.
     */
    public static Wear serverWearOn(ErosionEntry entry, BlockState block) {
        return serverWear(entry, SurfaceRegistry.chainOf(block),
            SurfaceShape.sunk(entry.getFamily(), entry.getSink(), block, false));
    }

    /**
     * A server record with only its chain to go on: the depth switch and the family's cap are in {@code sinkPixels}, and
     * what the block's own shape takes off is not (API.md's one limit). Use {@link #serverWearOn} wherever a block is known.
     */
    public static Wear serverWear(ErosionEntry entry, ErosionChain.Chain chain) {
        return serverWear(entry, chain, SinkProfile.shown(entry.getFamily(), entry.getSink()));
    }

    private static Wear serverWear(ErosionEntry entry, ErosionChain.Chain chain, int drawn) {
        SurfaceFamily appearance = entry.getFamily();
        ErosionChain.Chain on = chain != null && chain.base != null && chain.base.staged ? chain
            : ErosionChain.of(appearance);
        SurfaceFamily ground = on.base;
        return new WearRecord(
            key(ground),
            key(appearance),
            entry.getStage(),
            stagesOf(appearance, entry.getStage(), entry.getSink()),
            drawn,
            on.indexOf(appearance, entry.getStage(), entry.getSink()),
            on.length(),
            entry.isFrozen(),
            entry.getReinforce(),
            RecordReadout.progress(entry.getWear(), entry.effectiveThreshold()));
    }

    /**
     * How many stages a record's stretch of its run has: the face's while it stands flat, one pixel's once it has sunk,
     * which is what {@code Wear.stage()} counts up to. The figure this mod's own tooltips have always said after the
     * stage; never less than the stage it is told with, so a family's settings lowered under a record cannot make it
     * read past the end.
     */
    private static int stagesOf(SurfaceFamily appearance, int stage, int sink) {
        FamilySettings settings = TrmtConfig.family(appearance);
        if (settings == null) return stage + 1;
        return Math.max(sink > 0 ? settings.layersPerDepth : settings.stages, stage + 1);
    }

    /** A client record as the API hands it: what the server sent, with no progress, which a client is not told. */
    /** A client record where its block is known, its depth as {@link #serverWearOn} gives one. */
    public static Wear clientWearOn(short state, BlockState block) {
        return clientWear(state, SurfaceRegistry.chainOf(block),
            SurfaceShape.sunk(ErosionState.familyOf(state), ErosionState.sinkOf(state), block, false));
    }

    /** A client record with only its chain to go on, as {@link #serverWear} is. */
    public static Wear clientWear(short state, ErosionChain.Chain chain) {
        return clientWear(state, chain, SinkProfile.shown(ErosionState.familyOf(state), ErosionState.sinkOf(state)));
    }

    private static Wear clientWear(short state, ErosionChain.Chain chain, int drawn) {
        SurfaceFamily appearance = ErosionState.familyOf(state);
        ErosionChain.Chain on = chain != null && chain.base != null && chain.base.staged ? chain
            : ErosionChain.of(appearance);
        SurfaceFamily ground = on.base;
        int stage = ErosionState.layerOf(state);
        int sink = ErosionState.sinkOf(state);
        return new WearRecord(
            key(ground),
            key(appearance),
            stage,
            stagesOf(appearance, stage, sink),
            drawn,
            on.indexOf(appearance, stage, sink),
            on.length(),
            ErosionState.frozenOf(state),
            ErosionState.reinforceOf(state),
            WearRecord.WITHHELD);
    }

    /**
     * A block state as the API spells it on 26.3: {@code ns:name}, with {@code [prop=value,...]} in name order where
     * the state has properties - a selector this edition reads (FORMAT.md, "Block selectors").
     */
    public static String blockText(BlockState state) {
        if (state == null) return "unknown";
        String name = SurfaceRegistry.registryName(state.getBlock());
        StringBuilder out = new StringBuilder(name == null ? "unknown" : name);
        Map<String, String> properties = propertiesOf(state);
        if (!properties.isEmpty()) {
            out.append('[');
            boolean first = true;
            for (Map.Entry<String, String> each : properties.entrySet()) {
                if (!first) out.append(',');
                out.append(each.getKey())
                    .append('=')
                    .append(each.getValue());
                first = false;
            }
            out.append(']');
        }
        return out.toString();
    }

    /** A state's properties as names and values in the game's own spelling, sorted by name; empty where none. */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static Map<String, String> propertiesOf(BlockState state) {
        Map<String, String> out = new TreeMap<String, String>();
        if (state == null) return out;
        for (Property property : state.getProperties()) {
            out.put(property.getName(), property.getName(state.getValue(property)));
        }
        return out;
    }

    private static String key(SurfaceFamily family) {
        return family == null ? null : family.key();
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    @Override
    public boolean wear(Object level, int x, int y, int z, float amount) {
        Level world = serverWorld(level, "wear");
        if (world == null || !whole(world, x, z)) return false;
        if (!(amount > 0f) || Float.isInfinite(amount)) return false;
        // Held as a listener's answer is, so the wear banked stays finite (Codex review R16).
        amount = Math.min(amount, PluginHost.MOST_AMOUNT);
        if (!TrmtConfig.enabled || !Dimensions.allowed(world)) return false;
        long before = mark(world, x, y, z);
        ApiHooks.enter(WearContext.Cause.API, null);
        try {
            ErosionEngine.get()
                .step(world, x, y, z, amount);
        } finally {
            ApiHooks.leave();
        }
        return mark(world, x, y, z) != before;
    }

    @Override
    public boolean restore(Object level, int x, int y, int z, int steps) {
        Level world = serverWorld(level, "restore");
        if (world == null || steps <= 0 || !whole(world, x, z)) return false;
        boolean did = false;
        ApiHooks.enter(WearContext.Cause.API, null);
        try {
            for (int i = 0; i < steps && i < MOST_RESTORED; i++) {
                if (!ErosionEngine.get()
                    .restoreOneStage(world, x, y, z)) break;
                did = true;
            }
        } finally {
            ApiHooks.leave();
        }
        return did;
    }

    @Override
    public boolean freeze(Object level, int x, int y, int z, boolean frozen) {
        Level world = serverWorld(level, "freeze");
        if (world == null || !whole(world, x, z)) return false;
        ApiHooks.enter(WearContext.Cause.API, null);
        try {
            return ErosionEngine.get()
                .setFrozen(world, x, y, z, frozen);
        } finally {
            ApiHooks.leave();
        }
    }

    @Override
    public boolean forget(Object level, int x, int y, int z) {
        Level world = serverWorld(level, "forget");
        if (world == null || !whole(world, x, z)) return false;
        if (ErosionStore.get()
            .getEntry(world, x, y, z) == null) return false;
        ApiHooks.enter(WearContext.Cause.API, null);
        try {
            ErosionEngine.get()
                .forget(world, x, y, z);
        } finally {
            ApiHooks.leave();
        }
        return ErosionStore.get()
            .getEntry(world, x, y, z) == null;
    }

    /**
     * Whether the chunk of a position is loaded and whole, asked without loading it or waiting for it. A chunk's load
     * event comes while the chunk is being finished: the level already calls it loaded, the record is already in the
     * store, and any read of its blocks through the level waits for a step that is waiting for the caller. A mod asking
     * from its own load handler is answered as if the ground were not there yet, never left waiting for ever (0.9.229,
     * W3; API.md, "Chunk loading").
     */
    private static boolean whole(Level world, int x, int z) {
        return com.trmtgtnh.util.Worlds.chunkLoaded(world, x >> 4, z >> 4);
    }

    /** The server's world, on the server thread; otherwise null, said once for that action. */
    private static Level serverWorld(Object level, String action) {
        if (level instanceof Level && !((Level) level).isClientSide() && Trmt.onServerThread()) return (Level) level;
        ApiLoad.host()
            .sayOnce(
                "actions|" + action,
                PluginHost.PREFIX + "Actions."
                    + action
                    + " was called "
                    + (level instanceof Level && !((Level) level).isClientSide() ? "off the server thread"
                        : "without a server's world")
                    + " and did nothing; actions run on the server thread only, with the server's world");
        return null;
    }

    /** What a record is now, to tell whether an action changed it. */
    private static long mark(Level world, int x, int y, int z) {
        ErosionEntry entry = ErosionStore.get()
            .getEntry(world, x, y, z);
        if (entry == null) return Long.MIN_VALUE;
        return ((long) entry.packState() << 32) | (Float.floatToIntBits(entry.getWear()) & 0xFFFFFFFFL);
    }
}
