package com.trmtgtnh.server;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

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

    public SpawnGrantData() {
        super(NAME);
    }

    public SpawnGrantData(String name) {
        super(name);
    }

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
        if (!(world instanceof ServerLevel)) return null;
        return ((ServerLevel) world).getDataStorage()
            .computeIfAbsent(() -> new SpawnGrantData(NAME), NAME);
    }

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

    @Override
    public void load(CompoundTag tag) {
        granted.clear();
        ListTag items = tag.getList("items", 10);
        for (int i = 0; i < items.size(); i++) {
            CompoundTag entry = items.getCompound(i);
            String key = entry.getString("key");
            if (key.isEmpty()) continue;
            Set<String> who = new HashSet<String>();
            ListTag players = entry.getList("players", 8);
            for (int p = 0; p < players.size(); p++) {
                String id = players.getString(p);
                if (!id.isEmpty()) who.add(id);
            }
            granted.put(key, who);
        }
    }

    @Override
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
