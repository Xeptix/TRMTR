package com.trmtgtnh.server;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * How much in-game time has passed that the ground is not allowed to recover over.
 *
 * <p>
 * Healing is measured against the world clock rather than ticked, which is what lets a chunk
 * nobody has visited for a month catch up in a single pass the moment it loads. The same property
 * is a problem on a server nobody is playing on: the clock runs, so a golem left tending a road in
 * a chunkloaded corner has its work quietly undone all night by time no player experienced.
 *
 * <p>
 * The fix is not to stop the sweep - a sweep that is skipped is a sweep that catches up later,
 * because the entries still carry their old stamps and nothing walks an unloaded chunk to advance
 * them. What is kept instead is the total of the seconds that went by with nobody connected, and
 * every reading of the clock has that total taken off it. Time spent empty then simply is not part
 * of the clock: entries stamped during it and entries stamped before it are all measured on the
 * same scale, and nothing has to be visited for that to be true.
 *
 * <p>
 * Saved with the world, because it has to survive a restart. It starts at zero on a world that has
 * never had one, which makes the new clock agree exactly with the old one for every stamp already
 * written; the two only separate once the server has actually sat empty.
 */
public class HealClockData extends SavedData {

    public static final String NAME = "trmtgtnh_heal_clock";

    /** In-game seconds that have passed with nobody connected. Only ever grows. */
    private long idleSeconds;

    /** The clock reading this was last advanced against, so a restart adds nothing. */
    private long lastSeen = -1L;

    /** Made empty; from 1.18 a record is not told its name, which the storage keeps instead. */
    public HealClockData() {}

    /** The record for this save, made if this is the first time anything asked. */
    /**
     * The record for this save, made if this is the first time anything asked.
     *
     * <p>
     * One call where the other editions need four and a cast: a level's data storage is handed how to
     * make one and what it is called, and it either loads what is already saved or makes it. The cast
     * goes with it, and so does the chance of a record coming back as the wrong type.
     *
     * <p>
     * A client's own copy of a level has no data storage, and this is only ever asked on a server, so
     * one is the answer to the other.
     */
    public static HealClockData get(Level world) {
        return SavedDataFiles.computeIfAbsent(world, TYPE, NAME, HealClockData::read);
    }

    /**
     * The record as 26.1 asks for it: a {@code SavedDataType} (1.21.5) named by an {@code Identifier}, in the server's
     * own storage (26.1), which is the world's {@code data} folder 1.21.1 kept it in. The codec carries the same
     * compound 1.21.1 wrote and read, key for key; {@link SavedDataFiles} says where the file moved and why. No data
     * fixer, as in 1.21.1: each loader lets a record without one through unchanged.
     */
    static final SavedDataType<HealClockData> TYPE = new SavedDataType<HealClockData>(SavedDataFiles.id("heal_clock"),
        HealClockData::new, CompoundTag.CODEC.xmap(HealClockData::read, held -> held.save(new CompoundTag())), null);

    public long idleSeconds() {
        return idleSeconds;
    }

    /**
     * Moves the clock on by however long it has been since the last look.
     *
     * <p>
     * Only the gap is counted, never the absolute reading, so a server that was switched off for a
     * week adds nothing when it comes back: no in-game time passed while it was off, so the gap is
     * zero. A reading that has gone backwards - which a restored backup or a rewritten level.dat
     * can produce - resets the mark rather than counting a negative gap.
     *
     * @param nowSeconds   the raw world clock, before anything is taken off it
     * @param anyoneOnline whether the seconds since the last look should count
     */
    public void advance(long nowSeconds, boolean anyoneOnline) {
        if (lastSeen < 0L || nowSeconds < lastSeen) {
            lastSeen = nowSeconds;
            setDirty();
            return;
        }
        long step = nowSeconds - lastSeen;
        if (step <= 0L) return;
        lastSeen = nowSeconds;
        if (!anyoneOnline) idleSeconds += step;
        setDirty();
    }

    /** A saved record read back: the loader the storage is handed from 1.18, where it called load on one it made. */
    static HealClockData read(CompoundTag tag) {
        HealClockData held = new HealClockData();
        held.load(tag);
        return held;
    }

    private void load(CompoundTag tag) {
        // From 1.21.5 a getter answers in an Optional; the Or form gives 1.21.1's nought for a missing or wrong key.
        idleSeconds = tag.getLongOr("idle", 0L);
        lastSeen = tag.contains("seen") ? tag.getLongOr("seen", 0L) : -1L;
    }

    /** What is saved: 1.21.1's two keys. Called by the codec from 1.21.5, where it was the record's own save. */
    public CompoundTag save(CompoundTag tag) {
        tag.putLong("idle", idleSeconds);
        tag.putLong("seen", lastSeen);
        return tag;
    }
}
