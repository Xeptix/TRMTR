package com.trmtgtnh.server;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.original.OriginalChunk;

/**
 * What this save knows of being converted from the original mod, The Roads More Travelled (0.9.229 W5, spec 2.9,
 * "Telling"; D39): when it first converted, how many chunks and squares have been converted since, and whether an
 * operator has been told.
 *
 * <p>
 * Built like {@link SpawnGrantData} and {@link HealClockData}: one record a save, in the overworld's data storage, which
 * is per save and shared by every dimension. Unlike them it is never made by being asked: a world that never held a
 * block of the original's has no record, so {@code /trmt status} says nothing of a conversion that never happened, and
 * only the first converted chunk makes one.
 *
 * <p>
 * <strong>Counted off the server thread, written on it.</strong> A chunk is converted where it is read, on the chunk
 * loading threads, where saved data must not be touched; {@link OriginalChunk} counts there, in memory, and each server
 * tick {@link #record} takes what it counted into this record. A count taken from one world and not yet recorded is
 * dropped when the server stops ({@link #serverStopped}), so it never reaches the next world opened.
 */
public class OriginalConversionData extends SavedData {

    public static final String NAME = "trmtgtnh_original_conversion";

    /**
     * The chat line an operator is told at join until one has been (spec 2.9, final words for Xep with the rest); the
     * count is the squares converted so far, said in the brackets.
     */
    static final String TOLD_BEFORE = "This world was made with The Roads More Travelled. Its worn paths are carried over "
        + "as each part of the world loads (";

    static final String TOLD_AFTER = " so far). Keep a copy of the world from before today if you may go back to that mod.";

    /** When this save first converted, in milliseconds since 1970 by the server's clock. */
    private long firstMillis;

    private long chunks;

    private long squares;

    /** Whether an operator, or the host of a world of one's own, has been told. */
    private boolean told;

    /** Made empty; from 1.18 a record is not told its name, which the storage keeps instead. */
    public OriginalConversionData() {}

    private static SavedData.Factory<OriginalConversionData> factory() {
        return new SavedData.Factory<OriginalConversionData>(OriginalConversionData::new, OriginalConversionData::read,
            null);
    }

    /** This save's record, or null when nothing in it has been converted. Asked on the server thread. */
    public static OriginalConversionData find(MinecraftServer server) {
        ServerLevel overworld = server == null ? null : server.overworld();
        if (overworld == null) return null;
        return overworld.getDataStorage()
            .get(factory(), NAME);
    }

    /**
     * Takes what the chunk loading threads converted since the last tick into this save's record, making the record at
     * the first converted chunk - which is the moment the log is told, once a world. Called each server tick, and
     * before an operator is told at join so the count said is the live one.
     */
    public static void record(MinecraftServer server) {
        long[] taken = OriginalChunk.takeUnrecorded();
        if (taken[0] == 0L && taken[1] == 0L) return;
        ServerLevel overworld = server == null ? null : server.overworld();
        if (overworld == null) return;
        OriginalConversionData data = overworld.getDataStorage()
            .computeIfAbsent(factory(), NAME);
        boolean first = data.firstMillis == 0L;
        data.add(taken[0], taken[1], System.currentTimeMillis());
        if (first) {
            Trmt.LOG.info(
                "This world was made with The Roads More Travelled: its worn blocks are being converted to this mod's wear as each chunk loads ({} so far). conversion.enabled in the settings turns it off; an operator is told at join.",
                plural(data.squares, "square"));
        }
    }

    /** Dropped with the server: a count not yet recorded belongs to the world that stopped. */
    public static void serverStopped() {
        OriginalChunk.takeUnrecorded();
    }

    /**
     * Tells a joining operator, or the host of a world of one's own, that this world is being converted - until one has
     * been told. The record notes it only when the line went out.
     */
    public static void tellAtJoin(Player player) {
        if (!(player instanceof ServerPlayer)) return;
        ServerPlayer joined = (ServerPlayer) player;
        MinecraftServer server = joined.getServer();
        if (server == null) return;
        if (!joined.hasPermissions(2) && !server.isSingleplayerOwner(joined.getGameProfile())) return;
        record(server);
        OriginalConversionData data = find(server);
        if (data == null || data.told) return;
        if (Notices.say(joined, Notices.line(toldLine(data.squares), ChatFormatting.GRAY, true))) data.markTold();
    }

    /** The chat line, with the squares converted so far. */
    public static String toldLine(long squares) {
        return TOLD_BEFORE + plural(squares, "square") + TOLD_AFTER;
    }

    /**
     * {@code /trmt status}'s line while the record exists, or null: the totals, the day it began and whether anyone
     * has been told.
     */
    public static String statusLine(MinecraftServer server) {
        record(server);
        OriginalConversionData data = find(server);
        if (data == null) return null;
        return "  converted from The Roads More Travelled: " + plural(data.chunks, "chunk") + ", "
            + plural(data.squares, "worn square") + " since "
            + DateTimeFormatter.ISO_LOCAL_DATE.format(Instant.ofEpochMilli(data.firstMillis)
                .atZone(ZoneId.systemDefault()))
            + (data.told ? ", an operator told" : ", no operator told yet");
    }

    static String plural(long count, String noun) {
        return count + " " + noun + (count == 1L ? "" : "s");
    }

    void add(long chunksMore, long squaresMore, long nowMillis) {
        if (firstMillis == 0L) firstMillis = nowMillis;
        chunks += chunksMore;
        squares += squaresMore;
        setDirty();
    }

    void markTold() {
        if (told) return;
        told = true;
        setDirty();
    }

    public long chunks() {
        return chunks;
    }

    public long squares() {
        return squares;
    }

    public long firstMillis() {
        return firstMillis;
    }

    public boolean told() {
        return told;
    }

    /** A saved record read back: the loader the storage is handed from 1.18, where it called load on one it made. */
    static OriginalConversionData read(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        OriginalConversionData held = new OriginalConversionData();
        held.load(tag);
        return held;
    }

    void load(CompoundTag tag) {
        firstMillis = tag.getLong("first");
        chunks = tag.getLong("chunks");
        squares = tag.getLong("squares");
        told = tag.getBoolean("told");
    }

    @Override
    public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        tag.putLong("first", firstMillis);
        tag.putLong("chunks", chunks);
        tag.putLong("squares", squares);
        tag.putBoolean("told", told);
        return tag;
    }
}
