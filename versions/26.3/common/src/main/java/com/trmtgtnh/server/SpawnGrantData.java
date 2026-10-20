package com.trmtgtnh.server;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Who has already been given which starting item, for this save.
 *
 * <p>
 * Kept as world-saved data rather than on the player, and that is the whole point of it: the
 * question being asked is "has this player ever had one <em>here</em>", which has to be answerable
 * for somebody who is not logged in and must not travel with them to another world. Map storage is
 * per-save and shared across every dimension, so one copy answers for all of them.
 *
 * <p>
 * It also sidesteps the trap the obvious approach falls into. Player data is copied on death and
 * on a dimension change, so a flag kept there can be lost or duplicated by an ordinary respawn -
 * and a starting item that comes back every time you die is not a starting item.
 */
public class SpawnGrantData extends SavedData {

    public static final String NAME = "trmtgtnh_spawn_grants";

    /** Item key to the set of players who have had one, as UUID strings. */
    private final Map<String, Set<String>> granted = new HashMap<String, Set<String>>();

    /** Made empty; from 1.18 a record is not told its name, which the storage keeps instead. */
    public SpawnGrantData() {}

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
    public static SpawnGrantData get(Level world) {
        return SavedDataFiles.computeIfAbsent(world, TYPE, NAME, SpawnGrantData::read);
    }

    /**
     * The record as 26.1 asks for it: a {@code SavedDataType} (1.21.5) named by an {@code Identifier}, in the server's
     * own storage (26.1), which is the world's {@code data} folder 1.21.1 kept it in. The codec carries the same
     * compound 1.21.1 wrote and read, key for key; {@link SavedDataFiles} says where the file moved and why. No data
     * fixer, as in 1.21.1: each loader lets a record without one through unchanged.
     */
    static final SavedDataType<SpawnGrantData> TYPE = new SavedDataType<SpawnGrantData>(
        SavedDataFiles.id("spawn_grants"), SpawnGrantData::new,
        CompoundTag.CODEC.xmap(SpawnGrantData::read, held -> held.save(new CompoundTag())), null);

    public boolean hasHad(String itemKey, String player) {
        Set<String> who = granted.get(itemKey);
        return who != null && who.contains(player);
    }

    public void record(String itemKey, String player) {
        Set<String> who = granted.get(itemKey);
        if (who == null) {
            who = new HashSet<String>();
            granted.put(itemKey, who);
        }
        if (who.add(player)) setDirty();
    }

    /** A saved record read back: the loader the storage is handed from 1.18, where it called load on one it made. */
    static SpawnGrantData read(CompoundTag tag) {
        SpawnGrantData held = new SpawnGrantData();
        held.load(tag);
        return held;
    }

    private void load(CompoundTag tag) {
        granted.clear();
        // From 1.21.5 a list is asked for without its element type and each element answers for itself; an element
        // of the wrong kind reads as the empty compound or string 1.21.1's typed list gave, and is skipped alike.
        ListTag items = tag.getListOrEmpty("items");
        for (int i = 0; i < items.size(); i++) {
            CompoundTag entry = items.getCompoundOrEmpty(i);
            String key = entry.getStringOr("key", "");
            if (key.isEmpty()) continue;
            Set<String> who = new HashSet<String>();
            ListTag players = entry.getListOrEmpty("players");
            for (int p = 0; p < players.size(); p++) {
                String id = players.getStringOr(p, "");
                if (!id.isEmpty()) who.add(id);
            }
            granted.put(key, who);
        }
    }

    /** What is saved: 1.21.1's list of items. Called by the codec from 1.21.5, where it was the record's own save. */
    public CompoundTag save(CompoundTag tag) {
        ListTag items = new ListTag();
        for (Map.Entry<String, Set<String>> held : granted.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("key", held.getKey());
            ListTag players = new ListTag();
            for (String id : held.getValue()) {
                players.add(net.minecraft.nbt.StringTag.valueOf(id));
            }
            entry.put("players", players);
            items.add(entry);
        }
        tag.put("items", items);
        return tag;
    }
}
