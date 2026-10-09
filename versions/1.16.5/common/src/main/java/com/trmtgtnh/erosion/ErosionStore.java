package com.trmtgtnh.erosion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import com.trmtgtnh.Trmt;

/**
 * Server-side owner of all erosion data, persisted per chunk.
 *
 * <p>
 * Erosion rides inside each chunk's own NBT rather than living in one world-level {@code .dat}.
 * Three reasons, and all three still hold here:
 *
 * <ul>
 * <li>It scales. A world played for hundreds of hours would grow a single global file without bound
 * and pay that cost on every save; per-chunk data loads and unloads with the chunk that owns it.</li>
 * <li>It is removal-safe. Uninstall the mod and the extra tag is an unrecognised key inside the chunk
 * NBT. The world loads, and because no eroded block was ever written to the block array, every
 * position is already the vanilla block it always was.</li>
 * <li>It is locality-friendly. A chunk's erosion is in memory exactly when that chunk is, which is
 * also exactly when clients need to be told about it.</li>
 * </ul>
 *
 * <p>
 * Client-side there is a separate cache built from packets; this store is never touched from the
 * client.
 *
 * <p>
 * <strong>Two things differ from the older editions, and both are forced.</strong> A third thing
 * deliberately does not: the world is still 0 to 255 here. Official Mojang mappings make this version
 * read like 1.17 - {@code Level}, {@code BlockGetter}, {@code net.minecraft.core.BlockPos} - but the
 * names moved before the world did. {@code getMinBuildHeight} does not exist yet, and assuming it did
 * was the one thing the compiler caught in this class.
 *
 * <p>
 * <em>The dimension is no longer a number.</em> Both older editions pack a dimension id into the top
 * sixteen bits of the map key, because a dimension <em>was</em> an int. Here it is a
 * {@code ResourceKey<Level>}, and there is no number to pack. Rather than restructure every map and
 * every caller around a composite key, the store keeps its own list of the levels it has seen and
 * packs that list's index. The index is session-local and that is safe, because <strong>it is never
 * persisted</strong> - the record goes into the chunk's own NBT, which already knows which chunk and
 * therefore which level it belongs to. A key that escaped to disk would be a bug; nothing here writes
 * one.
 *
 * <p>
 * <em>The four persistence hooks are plain methods.</em> In the 1.12.2 edition they are Forge event
 * handlers and the class names Forge because of it. Here they are named for what happened rather than
 * for which event said so, and each loader calls them from its own: Forge from {@code ChunkDataEvent}
 * and {@code ChunkEvent}, Fabric from {@code ServerChunkEvents} and a mixin into
 * {@code ChunkSerializer}, because Fabric has no save hook at all.
 */
public final class ErosionStore {

    /** Key inside the chunk's NBT root compound. Namespaced to avoid collision. */
    public static final String NBT_KEY = "TrmtGtnhErosion";

    private static final ErosionStore INSTANCE = new ErosionStore();

    /**
     * What the engine does with a chunk that has just arrived holding wear.
     *
     * <p>
     * A seam rather than a call, because the engine is a larger thing than this and has not been
     * ported. Carrying half of it to satisfy one call would be carrying it out of dependency order,
     * which is how a feature ends up looking present and doing nothing.
     */
    public interface CatchUp {

        void catchUpChunk(Level level, int chunkX, int chunkZ, ChunkErosionData data);
    }

    private static volatile CatchUp engine;

    /** Loaded chunks only, keyed by level index and chunk coordinate. */
    private final Map<Long, ChunkErosionData> loaded = new HashMap<Long, ChunkErosionData>();

    /**
     * Chunks whose NBT has been read but whose load has not yet been announced.
     *
     * <p>
     * Concurrent because chunk NBT is read off the main thread. Nothing else touches the data until
     * it is promoted on the main thread, so a concurrent map is sufficient - no other synchronisation
     * is needed or wanted on a path this hot.
     */
    private final Map<Long, ChunkErosionData> pending = new ConcurrentHashMap<Long, ChunkErosionData>();

    /**
     * Chunks that have unloaded but have not yet been written.
     *
     * <p>
     * Minecraft unloads a chunk and then saves it, in that order. Dropping the record on the unload
     * therefore threw it away a moment before the one chance to persist it, and because a chunk's
     * root tag is built fresh on every save, contributing nothing is not "leave what was there" - it
     * is "erase it". That is the whole of the bug where worn ground survived quitting the game but
     * not flying away and coming back.
     *
     * <p>
     * Holds at most the handful of chunks between one unload and its own save.
     */
    private final Map<Long, ChunkErosionData> unloading = new HashMap<Long, ChunkErosionData>();

    /** Every level this store has seen, in the order it saw them. The index is the key's top bits. */
    private final List<ResourceLocation> levels = new ArrayList<ResourceLocation>();

    /** Round-robin cursor for the healing sweep, so its cost stays flat. */
    private int sweepCursor;

    /** Set once the log has mentioned that chunks are arriving in an older format. */
    private boolean saidUpgrading;

    private ErosionStore() {}

    public static ErosionStore get() {
        return INSTANCE;
    }

    /** Tells the store what to hand a newly-arrived chunk to. Called once as the mod starts. */
    public static void useEngine(CatchUp catchUp) {
        engine = catchUp;
    }

    /** Forgets it again. For tests, which must not leak an engine into the next one. */
    public static void forgetEngine() {
        engine = null;
    }

    // ------------------------------------------------------------------
    // Keying
    // ------------------------------------------------------------------

    /**
     * The index this store uses for a level, assigning one if it has not seen it before.
     *
     * <p>
     * Synchronised, and it is the only synchronised thing here: levels arrive on the server thread
     * but chunk NBT is read off it, so two threads can meet a level for the first time at once. The
     * cost is paid once per level per session rather than once per chunk.
     */
    public synchronized int indexOf(Level level) {
        return indexOf(
            level.dimension()
                .location());
    }

    public synchronized int indexOf(ResourceLocation levelName) {
        int at = levels.indexOf(levelName);
        if (at >= 0) return at;
        levels.add(levelName);
        if (levels.size() > 0xFFFF) {
            // Sixteen bits of index, which is sixty-five thousand levels in one session. A pack that
            // reached this would have other problems, but a silently wrapped index would mix two
            // levels' wear together, which is worse than saying so.
            Trmt.error("More than 65535 levels seen in one session; erosion keys will collide");
        }
        return levels.size() - 1;
    }

    /** The level an index belongs to, or null if this store has never seen it. */
    public synchronized ResourceLocation levelNameOf(int index) {
        return index >= 0 && index < levels.size() ? levels.get(index) : null;
    }

    /** Packs a level index and chunk coordinates into one long map key. */
    public static long chunkKey(int levelIndex, int chunkX, int chunkZ) {
        return ((long) (levelIndex & 0xFFFF) << 48) | ((long) (chunkX & 0xFFFFFF) << 24) | ((long) (chunkZ & 0xFFFFFF));
    }

    public static int levelIndexOf(long key) {
        return (int) ((key >>> 48) & 0xFFFF);
    }

    public static int chunkXOf(long key) {
        return signExtend24((int) ((key >>> 24) & 0xFFFFFF));
    }

    public static int chunkZOf(long key) {
        return signExtend24((int) (key & 0xFFFFFF));
    }

    private static int signExtend24(int value) {
        return (value << 8) >> 8;
    }

    // ------------------------------------------------------------------
    // Access
    // ------------------------------------------------------------------

    /** The chunk's erosion data, or null when that chunk has none loaded. */
    public ChunkErosionData getChunk(int levelIndex, int chunkX, int chunkZ) {
        return loaded.get(Long.valueOf(chunkKey(levelIndex, chunkX, chunkZ)));
    }

    public ChunkErosionData getChunk(long key) {
        return loaded.get(Long.valueOf(key));
    }

    /** The chunk's erosion data, creating an empty map if absent. */
    public ChunkErosionData getOrCreateChunk(int levelIndex, int chunkX, int chunkZ) {
        Long key = Long.valueOf(chunkKey(levelIndex, chunkX, chunkZ));
        ChunkErosionData data = loaded.get(key);
        if (data == null) {
            data = new ChunkErosionData();
            loaded.put(key, data);
        }
        return data;
    }

    /** Every loaded chunk that currently holds erosion. */
    public List<Long> loadedChunkKeys() {
        return new ArrayList<Long>(loaded.keySet());
    }

    public int loadedChunkCount() {
        return loaded.size();
    }

    public int trackedPositionCount() {
        int total = 0;
        for (ChunkErosionData data : loaded.values()) {
            total += data.size();
        }
        return total;
    }

    /**
     * What this store is holding, in one line, for {@code /trmt status}.
     *
     * <p>
     * The other editions' wording and the other editions' three numbers. Pending is the one worth
     * having beside the other two: a chunk read off disk but not yet handed to a level is neither
     * loaded nor absent, and a status line counting only the loaded ones would read as data lost for
     * the few ticks a world takes to start.
     */
    public String describe() {
        return "chunks=" + loaded.size() + " pending=" + pending.size() + " positions=" + trackedPositionCount();
    }

    /** Looks up a single position by world coordinates. Null when untracked. */
    public ErosionEntry getEntry(Level level, int x, int y, int z) {
        if (y < 0 || y >= level.getMaxBuildHeight()) return null;
        ChunkErosionData data = getChunk(indexOf(level), x >> 4, z >> 4);
        if (data == null) return null;
        return data.get(ErosionKey.packWorld(x, y, z));
    }

    /** Removes a single position, e.g. after the block there is broken or replaced. */
    public void removeEntry(Level level, int x, int y, int z) {
        if (y < 0 || y >= level.getMaxBuildHeight()) return;
        ChunkErosionData data = getChunk(indexOf(level), x >> 4, z >> 4);
        if (data != null) {
            data.remove(ErosionKey.packWorld(x, y, z));
        }
    }

    /**
     * Drops every entry in every loaded chunk, and tells each chunk it has changed.
     *
     * <p>
     * The telling is the part that makes a purge last. Every save but the unload one asks the chunk's
     * own modified flag, and a chunk whose only change was losing its wear had nothing else to set it
     * - so autosave, and the save made on quitting, skipped it and the purged wear loaded again next
     * session.
     *
     * @param levels how to find a live level by name, which only the caller knows
     */
    public void clearAll(LevelLookup levels) {
        for (Map.Entry<Long, ChunkErosionData> each : loaded.entrySet()) {
            each.getValue()
                .clear();
            long key = each.getKey()
                .longValue();
            ResourceLocation name = levelNameOf(levelIndexOf(key));
            Level level = name == null || levels == null ? null : levels.find(name);
            if (level != null) markModified(level, chunkXOf(key), chunkZOf(key));
        }
    }

    /** How a caller turns a level's name back into the level. */
    public interface LevelLookup {

        Level find(ResourceLocation name);
    }

    /** Tells a chunk it has changed, so the next ordinary save writes it. */
    public static void markModified(Level level, int chunkX, int chunkZ) {
        if (level == null) return;
        net.minecraft.world.level.chunk.ChunkAccess chunk = level
            .getChunk(chunkX, chunkZ, net.minecraft.world.level.chunk.ChunkStatus.FULL, false);
        if (chunk != null) chunk.setUnsaved(true);
    }

    /** Releases in-memory state without touching anything on disk. */
    public void clearMemory() {
        loaded.clear();
        pending.clear();
        unloading.clear();
        sweepCursor = 0;
    }

    /** The sweep's rotating window over loaded chunks, so its cost is independent of scale. */
    public List<Long> nextSweepSlice(int count) {
        if (loaded.isEmpty()) return Collections.emptyList();
        List<Long> keys = new ArrayList<Long>(loaded.keySet());
        Collections.sort(keys);
        int size = keys.size();
        int take = Math.min(count, size);
        List<Long> slice = new ArrayList<Long>(take);
        for (int i = 0; i < take; i++) {
            slice.add(keys.get((sweepCursor + i) % size));
        }
        sweepCursor = (sweepCursor + take) % size;
        return slice;
    }

    // ------------------------------------------------------------------
    // The four things a loader tells this store
    // ------------------------------------------------------------------

    /**
     * A chunk's NBT has been read. Deliberately does no more than parse and park it: this can run off
     * the main thread, and the healing catch-up it needs to do next wants a live level.
     */
    public void chunkDataLoaded(ResourceLocation levelName, int chunkX, int chunkZ, CompoundTag tag) {
        if (tag == null || levelName == null || !tag.contains(NBT_KEY)) return;

        ChunkErosionData data = ChunkErosionData.read(tag.getByteArray(NBT_KEY));
        sayIfUpgrading();
        if (data.isEmpty()) return;

        pending.put(Long.valueOf(chunkKey(indexOf(levelName), chunkX, chunkZ)), data);
    }

    /**
     * A chunk has arrived. Promotes parsed data onto the main thread and lets the engine apply
     * everything the chunk missed while it was away.
     */
    public void chunkLoaded(Level level, int chunkX, int chunkZ) {
        if (level == null || level.isClientSide()) return;

        long key = chunkKey(indexOf(level), chunkX, chunkZ);
        ChunkErosionData data = pending.remove(Long.valueOf(key));

        // A record this chunk left behind on its way out and which was never written.
        //
        // The save that was meant to follow the unload did not come - a chunk is only written when
        // something has told it that it changed, and a chunk can be told, written, and then change
        // again before it leaves. So the record sat here while the disk kept an older copy of the
        // same chunk, and when the chunk came back the older copy was read into `pending` and
        // promoted straight over the top of it. That is wear going backwards, or vanishing, with
        // every byte of it still in memory at the time.
        //
        // What is held here is always at least as new as what is on disk, so it wins outright.
        ChunkErosionData stranded = unloading.remove(Long.valueOf(key));
        if (stranded != null && !stranded.isEmpty()) {
            data = stranded;
            // It has not been written, so the next save must not skip this chunk.
            markModified(level, chunkX, chunkZ);
        }

        if (data != null && !data.isEmpty()) {
            loaded.put(Long.valueOf(key), data);
        } else {
            data = loaded.get(Long.valueOf(key));
        }
        if (data == null || data.isEmpty()) return;

        CatchUp asking = engine;
        if (asking != null) asking.catchUpChunk(level, chunkX, chunkZ, data);
    }

    /**
     * A chunk is being written. Returns the blob to store under {@link #NBT_KEY}, or null to store
     * nothing - which is how an abandoned chunk sheds the key entirely rather than carrying an empty
     * array for ever.
     */
    public byte[] chunkDataSaving(ResourceLocation levelName, int chunkX, int chunkZ) {
        if (levelName == null) return null;
        Long key = Long.valueOf(chunkKey(indexOf(levelName), chunkX, chunkZ));

        ChunkErosionData data = loaded.get(key);
        if (data == null) data = pending.get(key);
        // Last, and the reason that map exists: a chunk that has already unloaded is saved
        // immediately afterwards, and its record has to still be here to be written.
        if (data == null) data = unloading.get(key);
        if (data == null) {
            unloading.remove(key);
            return null;
        }

        data.prune();
        byte[] blob = data.write();
        data.clearDirty();
        unloading.remove(key);
        return blob;
    }

    /**
     * A chunk has gone. Its record is kept, not dropped: the save comes after the unload, and the
     * record has to still be here to be written.
     */
    public void chunkUnloaded(Level level, int chunkX, int chunkZ) {
        if (level == null || level.isClientSide()) return;
        Long key = Long.valueOf(chunkKey(indexOf(level), chunkX, chunkZ));

        // The wet-healing meter's reading for this chunk goes with it. Held only while a chunk is
        // loaded, so the map is bounded by what is in memory rather than by what has ever been
        // walked on - and a chunk that comes back is met as new, which pays it nothing on arrival.
        Weather.forget(key.longValue());
        // And whatever its snow had taken, for the same reason: the weather will have relaid or
        // melted it long before anybody comes back.
        SnowCover.forget(key.longValue());

        ChunkErosionData data = loaded.remove(key);
        if (data == null) data = pending.remove(key);
        else pending.remove(key);

        // A save that never comes would leave this behind, so the set is bounded rather than
        // trusted. Anything still here when it fills up has missed its chance already - and it is
        // said out loud, because what is dropped here is somebody's worn ground and the silence was
        // how a handful of chunks lost theirs without anything to read afterwards.
        if (unloading.size() > 512) {
            Trmt.LOG.warn(
                "{} chunks are waiting to be written and none of them has been; dropping them. Wear "
                    + "in those chunks is lost. This means chunks are unloading without being saved, "
                    + "which is not something this mod can cause on its own.",
                Integer.valueOf(unloading.size()));
            unloading.clear();
        }
        if (data != null && !data.isEmpty()) {
            unloading.put(key, data);
            // The chunk is on its way out and its record has not been written. Telling it that it
            // changed is what makes the save that follows the unload actually happen: a chunk whose
            // only change was its wear has nothing else to set that flag, so without this the save
            // is skipped and the record is stranded. Best effort - the chunk may already be out of
            // reach by now, and chunkLoaded recovers the stranded record if it is.
            markModified(level, chunkX, chunkZ);
        }
    }

    /**
     * Says, once, that wear is being read from a format older than this build writes.
     *
     * <p>
     * {@link ChunkErosionData} counts the upgrades but may not say anything about them: it is one of
     * the twenty-eight classes the portable core is made of and does not get to name the mod's
     * logger. So the count is read from here, which is the class that owns every other sentence this
     * layer writes.
     */
    private void sayIfUpgrading() {
        if (saidUpgrading || ChunkErosionData.upgradedCount() == 0) return;
        saidUpgrading = true;
        Trmt.LOG.info(
            "Reading wear from save format {} and writing it back as {}. Chunks upgrade as they load, "
                + "once each, and nothing is lost. Expected the first time a world is opened after an "
                + "update.",
            Integer.valueOf(ChunkErosionData.oldestFormatSeen()),
            Integer.valueOf(ChunkErosionData.currentFormat()));
    }
}
