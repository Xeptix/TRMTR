package com.trmtgtnh.server;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import com.trmtgtnh.config.ConfigFile;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.config.TrmtConfig;

/**
 * Each player's two config snapshots, kept by the server and outliving a restart.
 *
 * <p>
 * Server-side and per player on purpose. A snapshot is a whole config, so it belongs where the
 * config does; and scoping it to the player means two people tuning the same server do not
 * overwrite each other's comparison. It is written beside the config itself rather than into a
 * world save, because the thing it holds is not a property of any world.
 *
 * <p>
 * Alongside the two slots each player has a <em>baseline</em>: the config as it was before they
 * first loaded a snapshot. That is what the undo goes back to, and it is what stops a comparison
 * from being a one-way door.
 */
public final class SnapshotStore {

    public static final int LEFT = 0;
    public static final int RIGHT = 1;

    private static final String FILE_NAME = "trmtgtnh-snapshots.dat";

    private static final SnapshotStore INSTANCE = new SnapshotStore();

    /** Player to {left, right}. A null or empty string is an empty slot. */
    private final Map<UUID, String[]> slots = new HashMap<UUID, String[]>();

    /** Player to the config they had before they started loading snapshots. */
    private final Map<UUID, String> baselines = new HashMap<UUID, String>();

    private boolean loaded;

    private SnapshotStore() {}

    public static SnapshotStore get() {
        return INSTANCE;
    }

    // ------------------------------------------------------------------
    // Slots
    // ------------------------------------------------------------------

    public String slot(UUID player, int which) {
        ensureLoaded();
        String[] pair = slots.get(player);
        if (pair == null) return null;
        String held = pair[which == RIGHT ? RIGHT : LEFT];
        return held == null || held.isEmpty() ? null : held;
    }

    public boolean has(UUID player, int which) {
        return slot(player, which) != null;
    }

    public void store(UUID player, int which, String snapshot) {
        ensureLoaded();
        String[] pair = slots.get(player);
        if (pair == null) {
            pair = new String[2];
            slots.put(player, pair);
        }
        pair[which == RIGHT ? RIGHT : LEFT] = snapshot;
        save();
    }

    public void clear(UUID player, int which) {
        ensureLoaded();
        String[] pair = slots.get(player);
        if (pair == null) return;
        pair[which == RIGHT ? RIGHT : LEFT] = null;
        save();
    }

    // ------------------------------------------------------------------
    // The way back
    // ------------------------------------------------------------------

    /** The config this player had before they first loaded a snapshot, or null when none is held. */
    public String baseline(UUID player) {
        ensureLoaded();
        String held = baselines.get(player);
        return held == null || held.isEmpty() ? null : held;
    }

    /**
     * Remembers the way back, replacing whatever was remembered before.
     *
     * <p>
     * Replacing rather than keeping the first is what makes undo mean "before the last change"
     * rather than "before the first one I ever made" - the config having been touched again is
     * exactly the thing that should move the mark.
     */
    public void rememberBaseline(UUID player, String snapshot) {
        ensureLoaded();
        if (snapshot == null || snapshot.isEmpty()) return;
        baselines.put(player, snapshot);
        save();
    }

    public void forgetBaseline(UUID player) {
        ensureLoaded();
        if (baselines.remove(player) != null) save();
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    private static File file() {
        ConfigFile config = TrmtConfig.raw();
        if (config == null) return null;
        File configFile = config.getConfigFile();
        if (configFile == null) return null;
        File dir = configFile.getParentFile();
        return dir == null ? null : new File(dir, FILE_NAME);
    }

    private void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        File file = file();
        if (file == null || !file.isFile()) return;
        try {
            NBTTagCompound root = CompressedStreamTools.read(file);
            if (root == null) return;
            NBTTagList list = root.getTagList("players", 10);
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound entry = list.getCompoundTagAt(i);
                String id = entry.getString("id");
                if (id.isEmpty()) continue;
                UUID player;
                try {
                    player = UUID.fromString(id);
                } catch (IllegalArgumentException notAnId) {
                    continue;
                }
                slots.put(player, new String[] { read(entry, "left"), read(entry, "right") });
                String base = read(entry, "base");
                if (base != null) baselines.put(player, base);
            }
        } catch (IOException unreadable) {
            Trmt.LOG.warn("Could not read the config snapshots; starting empty", unreadable);
        } catch (RuntimeException broken) {
            Trmt.LOG.warn("The config snapshot file is unreadable; starting empty", broken);
        }
    }

    private void save() {
        File file = file();
        if (file == null) return;
        NBTTagCompound root = new NBTTagCompound();
        NBTTagList list = new NBTTagList();
        for (Map.Entry<UUID, String[]> held : slots.entrySet()) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setString(
                "id",
                held.getKey()
                    .toString());
            write(entry, "left", held.getValue()[LEFT]);
            write(entry, "right", held.getValue()[RIGHT]);
            write(entry, "base", baselines.get(held.getKey()));
            list.appendTag(entry);
        }
        // A player with only a baseline and no slots still has something worth keeping.
        for (Map.Entry<UUID, String> base : baselines.entrySet()) {
            if (slots.containsKey(base.getKey())) continue;
            NBTTagCompound entry = new NBTTagCompound();
            entry.setString(
                "id",
                base.getKey()
                    .toString());
            write(entry, "left", null);
            write(entry, "right", null);
            write(entry, "base", base.getValue());
            list.appendTag(entry);
        }
        root.setTag("players", list);
        try {
            CompressedStreamTools.write(root, file);
        } catch (IOException unwritable) {
            Trmt.LOG.warn("Could not write the config snapshots", unwritable);
        }
    }

    /**
     * One snapshot, as bytes rather than as a string.
     *
     * <p>
     * A snapshot is a whole config file, and this mod's runs to a few hundred kilobytes. An NBT string
     * cannot hold that: it is written with {@code writeUTF}, whose length is two bytes, so anything
     * past 65535 of them throws and the whole file goes unwritten. Which is what happened - silently,
     * because the only sign of it was a warning in the log, and a snapshot taken and used in the same
     * session works perfectly either way. It is the restart afterwards that lost them.
     */
    private static void write(NBTTagCompound entry, String key, String value) {
        if (value == null || value.isEmpty()) return;
        entry.setByteArray(key, value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * The same, read back - and a string if that is what is there.
     *
     * <p>
     * Nothing has written one of those since this was fixed, but a build before it could have, for a
     * config small enough to fit. Reading both costs a line and means nobody loses a slot.
     */
    private static String read(NBTTagCompound entry, String key) {
        if (entry.hasKey(key, 7)) {
            byte[] bytes = entry.getByteArray(key);
            return bytes.length == 0 ? null : new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        }
        return emptyToNull(entry.getString(key));
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
