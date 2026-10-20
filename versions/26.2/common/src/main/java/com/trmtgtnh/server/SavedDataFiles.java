package com.trmtgtnh.server;

import java.io.DataInputStream;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.SavedDataStorage;

import com.trmtgtnh.Trmt;

/**
 * Where this mod's two world-saved records live, and how the ones a 1.21.1 world left behind are found.
 *
 * <p>
 * Until 1.21.4 a record was asked for by a bare file name, and 1.21.1 kept both of this mod's in the overworld's
 * {@code data} folder as {@code trmtgtnh_heal_clock.dat} and {@code trmtgtnh_spawn_grants.dat}. From 1.21.5 a record is
 * a {@code SavedDataType} named by an {@code Identifier}, and the file is {@code <namespace>/<path>.dat} under its
 * storage's folder; from 26.1 the server keeps a storage of its own, over the world's {@code data} folder - the very
 * folder 1.21.1 wrote into, which the game's own upgrade of a world leaves holding this mod's files where they were. A
 * name with a namespace cannot point at a file without a folder, so the game forces a new place:
 * {@code data/trmtgtnh/heal_clock.dat} and {@code data/trmtgtnh/spawn_grants.dat}. What is written in it is what 1.21.1
 * wrote - the record's own compound under {@code data}, beside the data version, compressed.
 *
 * <p>
 * So a world opened from 1.21.1 is read from the old file once, the first time the record is asked for and nothing is
 * yet in the new place, and is then saved in the new place like any other change. The old file is left as it was: it
 * is never read again once the new one exists, and deleting a player's file is not this mod's to do.
 */
final class SavedDataFiles {

    private SavedDataFiles() {}

    /** The server's own storage, which every level's server shares; null for a client's copy of a level. */
    static SavedDataStorage storage(Level world) {
        if (!(world instanceof ServerLevel)) return null;
        MinecraftServer server = ((ServerLevel) world).getServer();
        return server == null ? null : server.getDataStorage();
    }

    /** This mod's name for a record, as 26.1 files it. */
    static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Trmt.MODID, path);
    }

    /**
     * The record, read from its own place, else from the file 1.21.1 wrote, else made empty.
     *
     * @param legacyName the bare name 1.21.1 asked for, without {@code .dat}
     * @param fromLegacy how the compound 1.21.1 saved under {@code data} is read back
     */
    static <T extends SavedData> T computeIfAbsent(Level world, SavedDataType<T> type, String legacyName,
        java.util.function.Function<CompoundTag, T> fromLegacy) {
        SavedDataStorage storage = storage(world);
        if (storage == null) return null;
        T held = storage.get(type);
        if (held != null) return held;
        MinecraftServer server = ((ServerLevel) world).getServer();
        Path old = server.getWorldPath(LevelResource.DATA)
            .resolve(legacyName + ".dat");
        if (Files.isRegularFile(old)) {
            try {
                CompoundTag whole = readTag(old);
                T carried = fromLegacy.apply(whole.getCompoundOrEmpty("data"));
                // Set, which marks it changed, so the next save writes it in its new place.
                storage.set(type, carried);
                Trmt.LOG.info("Read {} from {}, as an older edition saved it; it is saved as {} from now on.",
                    legacyName, old.getFileName(), type.id());
                return carried;
            } catch (Exception e) {
                Trmt.error("Could not read {} as an older edition saved it; starting it afresh", old, e);
            }
        }
        return storage.computeIfAbsent(type);
    }

    /** A saved record's whole file, compressed or not - the two the game itself reads. Package-private for its test. */
    static CompoundTag readTag(Path file) throws java.io.IOException {
        try (InputStream in = Files.newInputStream(file);
            PushbackInputStream stream = new PushbackInputStream(new java.io.BufferedInputStream(in), 2)) {
            byte[] header = new byte[2];
            int read = stream.read(header, 0, 2);
            boolean gzip = read == 2 && ((header[1] & 255) << 8 | header[0] & 255) == 35615;
            if (read > 0) stream.unread(header, 0, read);
            if (gzip) return NbtIo.readCompressed(stream, NbtAccounter.unlimitedHeap());
            try (DataInputStream data = new DataInputStream(stream)) {
                return NbtIo.read(data);
            }
        }
    }
}
