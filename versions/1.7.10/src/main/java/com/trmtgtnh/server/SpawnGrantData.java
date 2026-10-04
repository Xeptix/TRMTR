package com.trmtgtnh.server;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;

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
public class SpawnGrantData extends WorldSavedData {

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
    public static SpawnGrantData get(World world) {
        if (world == null || world.mapStorage == null) return null;
        SpawnGrantData data = (SpawnGrantData) world.mapStorage.loadData(SpawnGrantData.class, NAME);
        if (data == null) {
            data = new SpawnGrantData(NAME);
            world.mapStorage.setData(NAME, data);
        }
        return data;
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
        if (who.add(player)) markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        granted.clear();
        NBTTagList items = tag.getTagList("items", 10);
        for (int i = 0; i < items.tagCount(); i++) {
            NBTTagCompound entry = items.getCompoundTagAt(i);
            String key = entry.getString("key");
            if (key.isEmpty()) continue;
            Set<String> who = new HashSet<String>();
            NBTTagList players = entry.getTagList("players", 8);
            for (int p = 0; p < players.tagCount(); p++) {
                String id = players.getStringTagAt(p);
                if (!id.isEmpty()) who.add(id);
            }
            granted.put(key, who);
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        NBTTagList items = new NBTTagList();
        for (Map.Entry<String, Set<String>> held : granted.entrySet()) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setString("key", held.getKey());
            NBTTagList players = new NBTTagList();
            for (String id : held.getValue()) {
                players.appendTag(new net.minecraft.nbt.NBTTagString(id));
            }
            entry.setTag("players", players);
            items.appendTag(entry);
        }
        tag.setTag("items", items);
    }
}
