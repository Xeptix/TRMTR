package com.trmtgtnh.erosion;

import java.nio.ByteBuffer;

import com.trmtgtnh.surface.SurfaceFamily;
import com.trmtgtnh.util.IntKeyMap;

/**
 * Sparse erosion map for one chunk. Only positions that have been stepped on appear.
 *
 * <p>
 * Serialises to a single {@code byte[]} rather than an NBT list of compounds. A
 * well-travelled GTNH world accumulates a great many of these, and NBT compounds cost
 * roughly an order of magnitude more per entry than the fifteen-byte packed record used
 * here. The blob carries a version byte so the layout can change without invalidating
 * saves.
 *
 * <p>
 * Keys are primitive shorts in a Trove map. Trove ships with Forge 1.7.10, and boxing every
 * key would allocate a {@link Short} per tracked position on a server that may hold tens of
 * thousands of them at once.
 */
public final class ChunkErosionData {

    /** Bumped whenever the packed record layout changes. */
    private static final byte FORMAT_VERSION = 6;

    /** key(4) + state(2) + wear(4) + threshold(4) + touched(4) + ward(1) + light(1) = 20 bytes. */
    private static final int BYTES_PER_ENTRY = 4 + 2 + 4 + 4 + 4 + 1 + 1;

    /**
     * The version 5 record, whose key was a short.
     *
     * <p>
     * The first format change to touch the key rather than append to the record, so it is also the
     * first whose reader has to move every field of every key rather than skip a trailing byte. See
     * {@link ErosionKey#upgradeFromShort}.
     */
    private static final byte FORMAT_VERSION_V5 = 5;

    private static final int BYTES_PER_ENTRY_V5 = 2 + 2 + 4 + 4 + 4 + 1 + 1;

    /** The version 4 record, before the light byte was appended. */
    private static final byte FORMAT_VERSION_V4 = 4;

    private static final int BYTES_PER_ENTRY_V4 = 2 + 2 + 4 + 4 + 4 + 1;

    /** The version 3 record, before the spawn-ward byte was appended. */
    private static final byte FORMAT_VERSION_V3 = 3;

    private static final int BYTES_PER_ENTRY_V3 = 2 + 2 + 4 + 4 + 4;

    private final IntKeyMap<ErosionEntry> entries = new IntKeyMap<ErosionEntry>(16);

    /** Set when anything changes, so the store knows the chunk needs re-saving. */
    private boolean dirty;

    public ErosionEntry get(int key) {
        return entries.get(key);
    }

    public void put(int key, ErosionEntry entry) {
        entries.put(key, entry);
        dirty = true;
    }

    public void remove(int key) {
        if (entries.remove(key) != null) {
            dirty = true;
        }
    }

    public int[] keys() {
        return entries.keys();
    }

    public int size() {
        return entries.size();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public boolean isDirty() {
        return dirty;
    }

    public void markDirty() {
        dirty = true;
    }

    public void clearDirty() {
        dirty = false;
    }

    public void clear() {
        if (!entries.isEmpty()) {
            entries.clear();
            dirty = true;
        }
    }

    /** Drops entries that carry neither a visible stage nor pending progress. */
    public void prune() {
        int[] keys = entries.keys();
        for (int key : keys) {
            ErosionEntry entry = entries.get(key);
            if (entry == null || entry.isPrunable()) {
                entries.remove(key);
                dirty = true;
            }
        }
    }

    // ------------------------------------------------------------------
    // Serialization
    // ------------------------------------------------------------------

    /**
     * How many chunks this session has read from a format older than the current one, and the oldest
     * format it has seen.
     *
     * <p>
     * Counted here and reported elsewhere. This class is one of those that make up the
     * portable core and may not reach the mod's logger, so what it does is arithmetic; see
     * {@code ErosionStore}, which says the sentence. Not synchronised, because chunk reads happen on
     * the thread that owns the world and a count that is occasionally one out would change nothing
     * about what it is for.
     */
    private static int upgraded;

    private static int oldestSeen = FORMAT_VERSION;

    /** Notes that a chunk arrived in an older format. */
    private static void noteUpgrade(int from) {
        upgraded++;
        if (from < oldestSeen) oldestSeen = from;
    }

    /** The format this build writes, for whoever reports an upgrade. */
    public static int currentFormat() {
        return FORMAT_VERSION;
    }

    /** How many chunks have been upgraded this session, for whoever reports it. */
    public static int upgradedCount() {
        return upgraded;
    }

    /** The oldest format seen this session, or the current one when nothing has been upgraded. */
    public static int oldestFormatSeen() {
        return oldestSeen;
    }

    /** The format that stored a family and a stage in one byte, with no depth of its own. */
    private static final byte LEGACY_FORMAT_VERSION = 2;

    private static final int LEGACY_BYTES_PER_ENTRY = 2 + 1 + 4 + 4 + 4;

    /**
     * Reads the format that had no depth field, and works out what depth each record meant.
     *
     * <p>
     * Back then sinking was a consequence of how far along a family's stages a position had got,
     * so the depth is recoverable: ask the profile that drew it what that stage rendered as, and
     * store that as the depth now that it is a number in its own right. The visual layer is
     * rescaled at the same time, because families that used to have five or six stages now have
     * a longer run of finer ones and a stage two of five is not a layer two of sixteen.
     *
     * <p>
     * Worth stating plainly: this runs once per chunk, the first time it is loaded after the
     * update, and the chunk is written back in the new format. There is no ongoing cost and no
     * second migration.
     */
    private static ChunkErosionData readLegacy(byte[] data) {
        ChunkErosionData out = new ChunkErosionData();
        ByteBuffer buf = ByteBuffer.wrap(data);
        buf.get(); // consume version

        while (buf.remaining() >= LEGACY_BYTES_PER_ENTRY) {
            // Relayouted, not merely widened. A short read into an int keeps the old bit positions,
            // which puts every record at the wrong place in the chunk - silently, because a wrong key
            // is still a valid key. The oldest reader needs the upgrade as much as the newer ones.
            int key = ErosionKey.upgradeFromShort(buf.getShort());
            byte flags = buf.get();
            float wear = buf.getFloat();
            float threshold = buf.getFloat();
            int touched = buf.getInt();

            SurfaceFamily family = ErosionEntry.familyOf(ErosionState.fromLegacy(flags));
            int stage = ErosionState.legacyStageOf(flags);
            if (family == null || stage < 0) continue;

            ErosionEntry entry = LegacyErosionFormat.upgrade(family, stage, wear, threshold, touched);
            if (entry != null) out.entries.put(key, entry);
        }
        out.dirty = true; // written back in the current format at the next save
        return out;
    }

    /**
     * Packs every entry into a versioned byte blob. Returns null when the chunk holds
     * nothing, so the caller can drop the NBT tag entirely rather than writing an empty
     * array into every chunk in the world.
     */
    public byte[] write() {
        if (entries.isEmpty()) return null;

        ByteBuffer buf = ByteBuffer.allocate(1 + entries.size() * BYTES_PER_ENTRY);
        buf.put(FORMAT_VERSION);

        int[] keys = entries.keys();
        for (int key : keys) {
            ErosionEntry entry = entries.get(key);
            if (entry == null) continue;
            buf.putInt(key);
            buf.putShort(entry.packState());
            buf.putFloat(entry.getWear());
            buf.putFloat(entry.getThreshold());
            buf.putInt(entry.getLastTouchedSeconds());
            buf.put((byte) entry.getWard());
            buf.put((byte) entry.getLight());
        }
        // Entries may have been removed between sizing and filling in a pathological case;
        // trim rather than shipping trailing zeroes that would decode as bogus records.
        if (buf.position() == buf.capacity()) return buf.array();
        byte[] trimmed = new byte[buf.position()];
        System.arraycopy(buf.array(), 0, trimmed, 0, trimmed.length);
        return trimmed;
    }

    /**
     * Reads a blob produced by {@link #write()}.
     *
     * <p>
     * Deliberately lenient: a truncated, empty or unrecognised-version blob yields an empty
     * map rather than an exception. Erosion is cosmetic, so losing some of it is always
     * preferable to preventing a chunk - and therefore a world - from loading. Nothing in
     * this mod should ever be able to stop a save from opening.
     */
    public static ChunkErosionData read(byte[] data) {
        ChunkErosionData out = new ChunkErosionData();
        if (data == null || data.length < 1) return out;
        // A version this build predates is left alone rather than guessed at; anything older is
        // upgraded, because silently discarding every path in a world is not an acceptable way to
        // handle a format change we chose to make.
        if (data[0] == LEGACY_FORMAT_VERSION) {
            noteUpgrade(LEGACY_FORMAT_VERSION);
            return readLegacy(data);
        }
        if (data[0] == FORMAT_VERSION_V3) {
            noteUpgrade(FORMAT_VERSION_V3);
            return readV3(data);
        }
        if (data[0] == FORMAT_VERSION_V4) {
            noteUpgrade(FORMAT_VERSION_V4);
            return readOlder(data, BYTES_PER_ENTRY_V4, true, false);
        }
        if (data[0] == FORMAT_VERSION_V5) {
            noteUpgrade(FORMAT_VERSION_V5);
            return readOlder(data, BYTES_PER_ENTRY_V5, true, true);
        }
        if (data[0] != FORMAT_VERSION) return out;

        ByteBuffer buf = ByteBuffer.wrap(data);
        buf.get(); // consume version

        while (buf.remaining() >= BYTES_PER_ENTRY) {
            int key = buf.getInt();
            short state = buf.getShort();
            float wear = buf.getFloat();
            float threshold = buf.getFloat();
            int touched = buf.getInt();
            byte ward = buf.get();
            byte light = buf.get();

            ErosionEntry entry = ErosionEntry.fromPacked(state, wear, threshold, touched);
            if (entry != null) {
                entry.setWard(ward);
                entry.setLight(light & 0xFF);
                // A world loading with lights already in it has to take the fast path off too.
                if (entry.isLit()) com.trmtgtnh.block.GhostLight.noteLit();
                out.entries.put(key, entry);
            }
        }
        out.dirty = false;
        return out;
    }

    /**
     * Reads a format one or two revisions back, which differ only by trailing bytes.
     *
     * <p>
     * Every version from 3 to 5 appended a byte and changed nothing before it, so one reader
     * parameterised by record size and which trailing bytes are present covers all of them. Fields
     * the older record has no room for keep the default the field already holds, so there is nothing
     * to recover, only a byte not to read.
     *
     * <p>
     * Version 6 is the first that changed something other than the tail: its key is an int where
     * every format before it wrote a short. So this reads a short and widens it, which is the one
     * piece of real recovery any of these readers does.
     *
     * <p>
     * The chunk is marked dirty so it is written back in the current format at the next save. There
     * is no ongoing cost and no second migration.
     */
    private static ChunkErosionData readOlder(byte[] data, int bytesPerEntry, boolean hasWard, boolean hasLight) {
        ChunkErosionData out = new ChunkErosionData();
        ByteBuffer buf = ByteBuffer.wrap(data);
        buf.get(); // consume version

        while (buf.remaining() >= bytesPerEntry) {
            int key = ErosionKey.upgradeFromShort(buf.getShort());
            short state = buf.getShort();
            float wear = buf.getFloat();
            float threshold = buf.getFloat();
            int touched = buf.getInt();
            byte ward = hasWard ? buf.get() : 0;
            byte light = hasLight ? buf.get() : 0;

            ErosionEntry entry = ErosionEntry.fromPacked(state, wear, threshold, touched);
            if (entry != null) {
                if (hasWard) entry.setWard(ward);
                if (hasLight) {
                    entry.setLight(light & 0xFF);
                    // A world loading with lights already in it has to take the fast path off too.
                    if (entry.isLit()) com.trmtgtnh.block.GhostLight.noteLit();
                }
                out.entries.put(key, entry);
            }
        }
        out.dirty = true; // rewritten in the current format at the next save
        return out;
    }

    /** Version 3: no ward and no light. */
    private static ChunkErosionData readV3(byte[] data) {
        return readOlder(data, BYTES_PER_ENTRY_V3, false, false);
    }
}
