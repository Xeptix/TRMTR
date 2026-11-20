package com.trmtgtnh.erosion;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/**
 * The mirror built from a world's own region files (0.9.229; Xep, after phase 1): on the first load of a world from
 * before wear backups, so the first upgrade from 0.9.228 is truly backed up. A region file is read as the game wrote
 * it (the Anvil format, 1.7.10 to 26.x: a table of where each chunk sits, then for each a length, a compression byte
 * and its NBT), and of each chunk only this mod's tag and the save's stamp are looked at - the rest of the chunk is
 * skipped over, never built - so nothing of the world is read that the mirror does not hold. Portable, the same file in
 * every edition; the edition finds the region files and hands each one's bytes over, on its own background thread.
 */
public final class WearSeed {

    private static final int SECTOR = 4096;

    private WearSeed() {}

    /** One chunk's wear as its region file holds it. */
    public static final class Found {

        /** The chunk's place in its region file, as {@link WearMirror#indexOf} numbers it. */
        public final int index;

        /** The save's own stamp, {@code LastUpdate}, at the chunk's root or under its {@code Level}. */
        public final long savedAt;

        /** This mod's tag, or null where the chunk has none ({@link #readAll} alone answers such a chunk). */
        public final byte[] blob;

        Found(int index, long savedAt, byte[] blob) {
            this.index = index;
            this.savedAt = savedAt;
            this.blob = blob;
        }
    }

    /**
     * Every chunk of a region file that holds this mod's tag. A chunk that cannot be read - torn by a write going on
     * as it is read, or compressed in a way this does not know - is passed over: the chunk's own next save writes its
     * entry.
     *
     * @param region the region file's bytes
     * @param key    the chunk tag's name, {@code TrmtGtnhErosion} in every edition
     */
    public static List<Found> read(byte[] region, String key) {
        return chunks(region, key, false);
    }

    /**
     * Every chunk of a region file that can be read and has its stamp, with this mod's tag or without it (a null blob;
     * an empty one is a tag with nothing in it): the look for lost wear as a world opens, in the editions that read a
     * chunk's save from its region file here rather than through the game (1.16.5 and later). Passed over as
     * {@link #read} passes them.
     */
    public static List<Found> readAll(byte[] region, String key) {
        return chunks(region, key, true);
    }

    private static List<Found> chunks(byte[] region, String key, boolean all) {
        List<Found> out = new ArrayList<Found>();
        if (region == null || region.length < 2 * SECTOR) return out;
        for (int index = 0; index < WearMirror.SIDE * WearMirror.SIDE; index++) {
            int entry = intAt(region, index * 4);
            if (entry == 0) continue;
            int start = (entry >>> 8) * SECTOR;
            if (start + 5 > region.length) continue;
            int length = intAt(region, start);
            int kind = region[start + 4];
            if (length <= 1 || start + 4 + length > region.length) continue;
            try {
                byte[] nbt = inflate(region, start + 5, length - 1, kind);
                if (nbt == null) continue;
                Found found = skim(nbt, key, index, all);
                if (found != null) out.add(found);
            } catch (IOException | RuntimeException unreadable) {
                // Passed over: see above.
            }
        }
        return out;
    }

    private static int intAt(byte[] bytes, int at) {
        return ((bytes[at] & 0xFF) << 24) | ((bytes[at + 1] & 0xFF) << 16)
            | ((bytes[at + 2] & 0xFF) << 8)
            | (bytes[at + 3] & 0xFF);
    }

    private static byte[] inflate(byte[] bytes, int from, int length, int kind) throws IOException {
        InputStream in;
        if (kind == 1) in = new GZIPInputStream(new ByteArrayInputStream(bytes, from, length));
        else if (kind == 2) in = new InflaterInputStream(new ByteArrayInputStream(bytes, from, length));
        else if (kind == 3) in = new ByteArrayInputStream(bytes, from, length);
        else return null;
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            for (int got = in.read(chunk); got >= 0; got = in.read(chunk)) out.write(chunk, 0, got);
            return out.toByteArray();
        } finally {
            in.close();
        }
    }

    /** The chunk's tag and stamp, skimming its NBT: the root compound's entries, and its Level's for the stamp. */
    static Found skim(byte[] nbt, String key, int index, boolean all) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(nbt));
        if (in.readByte() != 10) return null;
        in.readUTF();
        byte[] blob = null;
        long savedAt = Long.MIN_VALUE;
        while (true) {
            byte type = in.readByte();
            if (type == 0) break;
            String name = in.readUTF();
            if (type == 7 && key.equals(name)) {
                blob = new byte[in.readInt()];
                in.readFully(blob);
            } else if (type == 4 && "LastUpdate".equals(name)) {
                savedAt = in.readLong();
            } else if (type == 10 && "Level".equals(name)) {
                long inLevel = levelStamp(in);
                if (inLevel != Long.MIN_VALUE) savedAt = inLevel;
            } else {
                skip(in, type);
            }
        }
        if (savedAt == Long.MIN_VALUE || !all && (blob == null || blob.length == 0)) return null;
        return new Found(index, savedAt, blob);
    }

    /** Reads a Level compound through to its end, answering its LastUpdate. */
    private static long levelStamp(DataInputStream in) throws IOException {
        long stamp = Long.MIN_VALUE;
        while (true) {
            byte type = in.readByte();
            if (type == 0) return stamp;
            String name = in.readUTF();
            if (type == 4 && "LastUpdate".equals(name)) stamp = in.readLong();
            else skip(in, type);
        }
    }

    private static void skip(DataInputStream in, int type) throws IOException {
        switch (type) {
            case 1:
                skipBytes(in, 1);
                return;
            case 2:
                skipBytes(in, 2);
                return;
            case 3:
            case 5:
                skipBytes(in, 4);
                return;
            case 4:
            case 6:
                skipBytes(in, 8);
                return;
            case 7:
                skipBytes(in, in.readInt());
                return;
            case 8:
                skipBytes(in, in.readUnsignedShort());
                return;
            case 9: {
                byte inner = in.readByte();
                int count = in.readInt();
                for (int i = 0; i < count; i++) skip(in, inner);
                return;
            }
            case 10:
                while (true) {
                    byte inner = in.readByte();
                    if (inner == 0) return;
                    in.readUTF();
                    skip(in, inner);
                }
            case 11:
                skipBytes(in, 4L * in.readInt());
                return;
            case 12:
                skipBytes(in, 8L * in.readInt());
                return;
            default:
                throw new IOException("no NBT tag of kind " + type);
        }
    }

    private static void skipBytes(DataInputStream in, long count) throws IOException {
        if (count < 0) throw new IOException("a negative length");
        long left = count;
        while (left > 0) {
            int skipped = in.skipBytes((int) Math.min(left, Integer.MAX_VALUE));
            if (skipped <= 0) throw new EOFException();
            left -= skipped;
        }
    }
}
