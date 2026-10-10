package com.trmtgtnh.erosion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.world.ChunkDataEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Server-side owner of all erosion data, persisted per chunk.
 *
 * <p>
 * Erosion rides inside each chunk's own NBT via {@link ChunkDataEvent} rather than living in
 * one world-level {@code .dat}. Three reasons:
 *
 * <ul>
 * <li>It scales. A GTNH world played for hundreds of hours would grow a single global file
 * without bound and pay that cost on every save; per-chunk data loads and unloads with the
 * chunk that owns it.</li>
 * <li>It is removal-safe. Uninstall the mod and the extra tag is an unrecognised key inside
 * the chunk NBT. Forge ignores it, the world loads, and because no eroded block was ever
 * written to the block array, every position is already the vanilla block it always
 * was.</li>
 * <li>It is locality-friendly. A chunk's erosion is in memory exactly when that chunk is,
 * which is also exactly when clients need to be told about it.</li>
 * </ul>
 *
 * <p>
 * Client-side there is a separate cache built from packets; this store is never touched from
 * the client.
 */
public final class ErosionStore {

    /** Key inside the chunk's NBT root compound. Namespaced to avoid collision. */
    private static final String NBT_KEY = "TrmtGtnhErosion";

    private static final ErosionStore INSTANCE = new ErosionStore();

    /** Loaded chunks only, keyed by dimension and chunk coordinate. */
    private final Map<Long, ChunkErosionData> loaded = new HashMap<Long, ChunkErosionData>();

    /**
     * Chunks whose NBT has been read but whose {@code ChunkEvent.Load} has not yet fired.
     *
     * <p>
     * Concurrent because 1.7.10 reads chunk NBT off the main thread in some Forge builds and
     * with several of the performance coremods in this pack. Nothing else touches the data
     * until it is promoted on the main thread, so a concurrent map is sufficient - no other
     * synchronisation is needed or wanted on a path this hot.
     */
    private final Map<Long, ChunkErosionData> pending = new ConcurrentHashMap<Long, ChunkErosionData>();

    /**
     * Chunks that have unloaded but have not yet been written.
     *
     * <p>
     * Minecraft unloads a chunk and then saves it, in that order: {@code unloadQueuedChunks}
     * posts the unload event and only afterwards calls {@code safeSaveChunk}. Dropping the
     * record on the unload therefore threw it away a moment before the one chance to persist
     * it, and because a chunk's root tag is built fresh on every save, contributing nothing is
     * not "leave what was there" - it is "erase it". That is the whole of the bug where worn
     * ground survived quitting the game but not flying away and coming back.
     *
     * <p>
     * Holds at most the handful of chunks between one unload and its own save.
     */
    private final Map<Long, ChunkErosionData> unloading = new HashMap<Long, ChunkErosionData>();

    /** Round-robin cursor for the healing sweep, so its cost stays flat. */
    private int sweepCursor;

    private ErosionStore() {}

    public static ErosionStore get() {
        return INSTANCE;
    }

    // ------------------------------------------------------------------
    // Keying
    // ------------------------------------------------------------------

    /** Packs dimension id and chunk coordinates into one long map key. */
    public static long chunkKey(int dimension, int chunkX, int chunkZ) {
        return ((long) (dimension & 0xFFFF) << 48) | ((long) (chunkX & 0xFFFFFF) << 24) | ((long) (chunkZ & 0xFFFFFF));
    }

    public static int dimensionOf(long key) {
        return (short) ((key >>> 48) & 0xFFFF);
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
    public ChunkErosionData getChunk(int dimension, int chunkX, int chunkZ) {
        return loaded.get(Long.valueOf(chunkKey(dimension, chunkX, chunkZ)));
    }

    public ChunkErosionData getChunk(long key) {
        return loaded.get(Long.valueOf(key));
    }

    /** The chunk's erosion data, creating an empty map if absent. */
    public ChunkErosionData getOrCreateChunk(int dimension, int chunkX, int chunkZ) {
        Long key = Long.valueOf(chunkKey(dimension, chunkX, chunkZ));
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

    /** Looks up a single position by world coordinates. Null when untracked. */
    public ErosionEntry getEntry(World world, int x, int y, int z) {
        if (y < 0 || y > 255) return null;
        ChunkErosionData data = getChunk(world.provider.getDimension(), x >> 4, z >> 4);
        if (data == null) return null;
        return data.get(ErosionKey.packWorld(x, y, z));
    }

    /** Removes a single position, e.g. after the block there is broken or replaced. */
    public void removeEntry(World world, int x, int y, int z) {
        if (y < 0 || y > 255) return;
        ChunkErosionData data = getChunk(world.provider.getDimension(), x >> 4, z >> 4);
        if (data != null) {
            data.remove(ErosionKey.packWorld(x, y, z));
        }
    }

    /**
     * Drops every entry in every loaded chunk. Used by {@code /trmt purge}. Chunks are
     * marked dirty so the cleared state is written on the next save.
     */
    public void clearAll() {
        for (java.util.Map.Entry<Long, ChunkErosionData> each : loaded.entrySet()) {
            each.getValue()
                .clear();
            // And the chunk told it has changed, which is the part that makes a purge last. Every
            // save but the unload one asks the chunk's own modified flag, and a chunk whose only
            // change was losing its wear had nothing else to set it - so autosave, and the save
            // made on quitting, skipped it and the purged wear loaded again next session.
            long key = each.getKey()
                .longValue();
            World world = net.minecraftforge.common.DimensionManager.getWorld(dimensionOf(key));
            if (world != null) markModified(world, chunkXOf(key), chunkZOf(key));
        }
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
    // Persistence hooks
    // ------------------------------------------------------------------

    /**
     * Reads the blob. Deliberately does no more than parse and park it: this can run off the
     * main thread, and the healing catch-up it needs to do next wants a live world.
     */
    @SubscribeEvent
    public void onChunkDataLoad(ChunkDataEvent.Load event) {
        NBTTagCompound tag = event.getData();
        if (tag == null || !tag.hasKey(NBT_KEY)) return;

        Chunk chunk = event.getChunk();
        if (chunk == null) return;

        ChunkErosionData data = ChunkErosionData.read(tag.getByteArray(NBT_KEY));
        sayIfUpgrading();
        if (data.isEmpty()) return;

        World world = chunk.getWorld();
        int dimension = world == null ? 0 : world.provider.getDimension();
        pending.put(Long.valueOf(chunkKey(dimension, chunk.x, chunk.z)), data);
    }

    /** Set once the log has mentioned that chunks are arriving in an older format. */
    private boolean saidUpgrading;

    /**
     * Says, once, that wear is being read from a format older than this build writes.
     *
     * <p>
     * {@link ChunkErosionData} counts the upgrades but may not say anything about them: it is one of
     * the classes the portable core is made of and does not get to name the mod's logger.
     * So the count is read from here, which is the class that owns every other sentence this layer
     * writes.
     */
    private void sayIfUpgrading() {
        if (saidUpgrading || ChunkErosionData.upgradedCount() == 0) return;
        saidUpgrading = true;
        com.trmtgtnh.Trmt.LOG.info(
            "Reading wear from save format {} and writing it back as {}. Chunks upgrade as they load, once each, and nothing is lost. Expected the first time a world is opened after an update.",
            Integer.valueOf(ChunkErosionData.oldestFormatSeen()),
            Integer.valueOf(ChunkErosionData.currentFormat()));
    }

    /**
     * Promotes parsed data onto the main thread and lets the engine apply everything the
     * chunk missed while it was away.
     */
    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        Chunk chunk = event.getChunk();
        if (chunk == null || chunk.getWorld() == null || chunk.getWorld().isRemote) return;

        long key = chunkKey(chunk.getWorld().provider.getDimension(), chunk.x, chunk.z);
        ChunkErosionData data = pending.remove(Long.valueOf(key));

        // A record this chunk left behind on its way out and which was never written. The save that
        // was meant to follow the unload did not come, so the record sat in `unloading` while the
        // disk kept an older copy of the same chunk - and when the chunk came back that older copy
        // was read into `pending` and promoted straight over the top of it. Wear going backwards,
        // with every byte of the newer version still in memory at the time. What is held here is
        // always at least as new as what is on disk, so it wins outright.
        ChunkErosionData stranded = unloading.remove(Long.valueOf(key));
        if (stranded != null && !stranded.isEmpty()) {
            data = stranded;
            chunk.markDirty();
        }

        if (data != null && !data.isEmpty()) {
            loaded.put(Long.valueOf(key), data);
        } else {
            data = loaded.get(Long.valueOf(key));
        }
        if (data == null || data.isEmpty()) return;

        ErosionEngine.get()
            .catchUpChunk(chunk.getWorld(), chunk.x, chunk.z, data);
    }

    @SubscribeEvent
    public void onChunkDataSave(ChunkDataEvent.Save event) {
        Chunk chunk = event.getChunk();
        if (chunk == null || chunk.getWorld() == null) return;

        Long key = Long.valueOf(chunkKey(chunk.getWorld().provider.getDimension(), chunk.x, chunk.z));
        ChunkErosionData data = loaded.get(key);
        if (data == null) data = pending.get(key);
        // Last, and the reason this map exists: a chunk that has already unloaded is saved
        // immediately afterwards, and its record has to still be here to be written.
        if (data == null) data = unloading.get(key);
        if (data == null) {
            unloading.remove(key);
            return;
        }

        data.prune();
        byte[] blob = data.write();
        if (blob == null) {
            // Nothing left worth storing. Remove the tag so an abandoned chunk sheds the key
            // entirely rather than carrying an empty array forever.
            event.getData()
                .removeTag(NBT_KEY);
        } else {
            event.getData()
                .setByteArray(NBT_KEY, blob);
        }
        data.clearDirty();
        // Written, so it has had its chance either way.
        unloading.remove(key);
    }

    /**
     * Sets the in-memory copy aside when a chunk unloads, rather than dropping it.
     *
     * <p>
     * The save comes after this, not before - see {@link #unloading}. What was here before was
     * a comment asserting the opposite and a pair of removes that made it true by destroying
     * the evidence.
     */
    @SubscribeEvent
    public void onChunkUnload(ChunkEvent.Unload event) {
        Chunk chunk = event.getChunk();
        if (chunk == null || chunk.getWorld() == null || chunk.getWorld().isRemote) return;
        Long key = Long.valueOf(chunkKey(chunk.getWorld().provider.getDimension(), chunk.x, chunk.z));

        // The wet-healing meter's reading for this chunk goes with it. Held only while a chunk is
        // loaded, so the map is bounded by what is in memory rather than by what has ever been
        // walked on - and a chunk that comes back is met as new, which pays it nothing on arrival.
        Weather.forget(key.longValue());
        // And whatever its snow had taken, for the same reason: the weather will have
        // relaid or melted it long before anybody comes back.
        SnowCover.forget(key.longValue());

        ChunkErosionData data = loaded.remove(key);
        if (data == null) data = pending.remove(key);
        else pending.remove(key);

        // A save that never comes would leave this behind, so the set is bounded rather than
        // trusted. Anything still here when it fills up has missed its chance already.
        if (unloading.size() > 512) unloading.clear();
        if (data != null && !data.isEmpty()) unloading.put(key, data);
    }

    /**
     * Tells Minecraft the chunk has something worth writing.
     *
     * <p>
     * Every save pass but the unload one is gated on {@code Chunk.needsSaving}, which asks the
     * chunk's own modified flag - and a chunk whose only change is a wear record has nothing
     * else to set it. Without this, ordinary autosaves and even a full world save skip the very
     * chunks this mod cares about, and persistence rests entirely on the unload path.
     */
    public void markModified(World world, int chunkX, int chunkZ) {
        if (world == null || world.isRemote) return;
        if (!world.isBlockLoaded(new net.minecraft.util.math.BlockPos(chunkX << 4, 64, chunkZ << 4))) return;
        Chunk chunk = world.getChunk(chunkX, chunkZ);
        if (chunk != null) chunk.markDirty();
    }

    /** Logs a one-line summary. Used by {@code /trmt status}. */
    public String describe() {
        return "chunks=" + loaded.size() + " pending=" + pending.size() + " positions=" + trackedPositionCount();
    }

}
