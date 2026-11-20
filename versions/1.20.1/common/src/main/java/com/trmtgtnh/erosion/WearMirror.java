package com.trmtgtnh.erosion;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.CRC32;

/**
 * The wear mirror (0.9.229; docs/goals/0.9.229-wear-backup-spec.md, "The mirror"): every chunk's wear blob, written a
 * second time, as the chunk saves, into a small file per region under {@code <world>/trmtgtnh/wear/}, laid out as the
 * region files are ({@code region/r.<x>.<z>.wear}, {@code DIM-1/region/...}). The blob is the chunk's own, already in
 * hand at the save, so the cost is one more small write, and a chunk that is not loaded is still covered. The mirror is
 * always the newest copy: backups freeze it, and a chunk that loads without wear the mirror holds for that same save is
 * put back from it.
 *
 * <p>
 * Each entry carries the chunk's own save stamp beside the blob - the world clock its save wrote into the chunk - so a
 * chunk that loads without its wear can be told apart: the same stamp is the same save, and nothing is lost by putting
 * it back; a later stamp is a save made without this mod (a world opened without it), which is a real loss. A chunk
 * whose wear has all healed, or been cleared, keeps a stamp and no blob, so a later load without wear is known to be
 * right.
 *
 * <p>
 * Portable, byte for byte the same in every edition, as {@link ChunkErosionData} is: each edition hands it a folder
 * name for the dimension, the chunk's coordinates, its save stamp and the blob, and nothing of the game.
 */
public final class WearMirror {

    /** The mod's own folder in a world, beside {@code region/} and {@code data/}. */
    public static final String HOME = "trmtgtnh";

    /** The mirror's folder inside {@link #HOME}. */
    public static final String FOLDER = "wear";

    /** A region's side, in chunks, as the game's own region files have it. */
    public static final int SIDE = 32;

    /** A mirror file's first bytes: "TWM" and the format. */
    private static final byte[] MAGIC = { 'T', 'W', 'M' };

    private static final byte FORMAT = 1;

    /** The suffix of a mirror file, beside the game's {@code .mca}. */
    public static final String SUFFIX = ".wear";

    private WearMirror() {}

    // ------------------------------------------------------------------
    // Where a chunk's entry lives
    // ------------------------------------------------------------------

    /**
     * A chunk, by the dimension's folder and its coordinates: the one way the mirror, a backup and a restore name a
     * chunk, in every edition (whose dimensions are numbers in some and names in others).
     */
    public static final class Spot {

        /**
         * The dimension's own save folder, as the game names it: empty for the overworld, {@code DIM-1}, ..., and from
         * 1.16.5 {@code dimensions/<namespace>/<path>} for a dimension a mod adds.
         */
        public final String dimension;

        public final int chunkX;

        public final int chunkZ;

        public Spot(String dimension, int chunkX, int chunkZ) {
            this.dimension = dimension == null ? "" : dimension;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }

        /** The mirror file this chunk's entry is kept in, under the mirror's folder. */
        public String file() {
            return regionPath(dimension, chunkX >> 5, chunkZ >> 5);
        }

        /** The chunk's place inside its region file. */
        public int index() {
            return indexOf(chunkX, chunkZ);
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Spot)) return false;
            Spot that = (Spot) other;
            return chunkX == that.chunkX && chunkZ == that.chunkZ && dimension.equals(that.dimension);
        }

        @Override
        public int hashCode() {
            return (dimension.hashCode() * 31 + chunkX) * 31 + chunkZ;
        }

        /** {@code dimension:x,z}, which {@link #parse} reads back; the overworld's dimension is empty. */
        @Override
        public String toString() {
            return dimension + ":" + chunkX + "," + chunkZ;
        }

        /** A spot from {@link #toString}, or null for anything else. */
        public static Spot parse(String text) {
            if (text == null) return null;
            int colon = text.lastIndexOf(':');
            int comma = text.lastIndexOf(',');
            if (colon < 0 || comma < colon) return null;
            try {
                return new Spot(
                    text.substring(0, colon),
                    Integer.parseInt(text.substring(colon + 1, comma)),
                    Integer.parseInt(text.substring(comma + 1)));
            } catch (NumberFormatException notOne) {
                return null;
            }
        }
    }

    /** A region file's path under the mirror's folder: {@code region/r.0.-1.wear}, {@code DIM-1/region/...}. */
    public static String regionPath(String dimension, int regionX, int regionZ) {
        String folder = dimension == null || dimension.isEmpty() ? "" : dimension + "/";
        return folder + "region/r." + regionX + "." + regionZ + SUFFIX;
    }

    /** A chunk's place in its region file, 0 to 1023, as the game's region files number them. */
    public static int indexOf(int chunkX, int chunkZ) {
        return (chunkX & (SIDE - 1)) | ((chunkZ & (SIDE - 1)) << 5);
    }

    /**
     * The spot a region file's entry stands for, from the file's path under the mirror's folder and the entry's index,
     * or null when the path is not one {@link #regionPath} writes.
     */
    public static Spot spotOf(String path, int index) {
        if (path == null) return null;
        String clean = path.replace('\\', '/');
        int cut = clean.lastIndexOf("region/r.");
        if (cut < 0 || !clean.endsWith(SUFFIX)) return null;
        String dimension = cut == 0 ? "" : clean.substring(0, cut - 1);
        String[] parts = clean.substring(cut + "region/r.".length(), clean.length() - SUFFIX.length())
            .split("\\.");
        if (parts.length != 2) return null;
        try {
            int regionX = Integer.parseInt(parts[0]);
            int regionZ = Integer.parseInt(parts[1]);
            return new Spot(dimension, regionX * SIDE + (index & (SIDE - 1)), regionZ * SIDE + (index >> 5));
        } catch (NumberFormatException notOne) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // One chunk's entry
    // ------------------------------------------------------------------

    /** What the mirror holds for one chunk: the save stamp it was written with, and the blob, or none. */
    public static final class Entry {

        /** The chunk's own save stamp at the save that wrote this: the world clock, in ticks. */
        public final long savedAt;

        private final byte[] blob;

        public Entry(long savedAt, byte[] blob) {
            this.savedAt = savedAt;
            this.blob = blob == null || blob.length == 0 ? null : blob;
        }

        /** Whether the chunk held wear at that save. False for a chunk whose wear healed or was cleared. */
        public boolean hasWear() {
            return blob != null;
        }

        /** The blob as the chunk wrote it, or null. The caller may not change it. */
        public byte[] blob() {
            return blob;
        }
    }

    // ------------------------------------------------------------------
    // A region file
    // ------------------------------------------------------------------

    /**
     * A region file's bytes: "TWM", the format, the count, then each entry - its index (two bytes), its save stamp
     * (eight), the blob's length (four) and the blob - in index order, and last a CRC-32 of everything before it. A
     * file whose check does not hold is not read at all, so a torn write can never be taken for a chunk's wear.
     */
    public static byte[] write(Map<Integer, Entry> entries) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.write(MAGIC);
            out.writeByte(FORMAT);
            TreeMap<Integer, Entry> sorted = new TreeMap<Integer, Entry>(entries);
            out.writeInt(sorted.size());
            for (Map.Entry<Integer, Entry> each : sorted.entrySet()) {
                Entry entry = each.getValue();
                out.writeShort(
                    each.getKey()
                        .intValue());
                out.writeLong(entry.savedAt);
                byte[] blob = entry.blob();
                out.writeInt(blob == null ? 0 : blob.length);
                if (blob != null) out.write(blob);
            }
            out.flush();
            CRC32 crc = new CRC32();
            byte[] body = bytes.toByteArray();
            crc.update(body, 0, body.length);
            ByteBuffer tail = ByteBuffer.allocate(body.length + 8);
            tail.put(body);
            tail.putLong(crc.getValue());
            return tail.array();
        } catch (IOException never) {
            // A stream over memory does not fail.
            throw new IllegalStateException(never);
        }
    }

    /**
     * A region file's entries, by index, from {@link #write}'s bytes; empty for a file that is not one, is of a format
     * this build does not know, or fails its check. Never throws: a mirror that cannot be read is a mirror with
     * nothing in it, and nothing in this mod may stop a world from opening.
     */
    public static Map<Integer, Entry> read(byte[] file) {
        Map<Integer, Entry> out = new TreeMap<Integer, Entry>();
        if (!intact(file)) return out;
        ByteBuffer buf = ByteBuffer.wrap(file, 0, file.length - 8);
        buf.position(MAGIC.length + 1);
        int count = buf.getInt();
        for (int i = 0; i < count && buf.remaining() >= 2 + 8 + 4; i++) {
            int index = buf.getShort() & 0xFFFF;
            long savedAt = buf.getLong();
            int length = buf.getInt();
            if (length < 0 || length > buf.remaining()) break;
            byte[] blob = null;
            if (length > 0) {
                blob = new byte[length];
                buf.get(blob);
            }
            if (index < SIDE * SIDE) out.put(Integer.valueOf(index), new Entry(savedAt, blob));
        }
        return out;
    }

    /** Whether these bytes are a whole mirror file of this format: its marks, and a check that holds. */
    public static boolean intact(byte[] file) {
        if (file == null || file.length < MAGIC.length + 1 + 4 + 8) return false;
        for (int i = 0; i < MAGIC.length; i++) {
            if (file[i] != MAGIC[i]) return false;
        }
        if (file[MAGIC.length] != FORMAT) return false;
        CRC32 crc = new CRC32();
        crc.update(file, 0, file.length - 8);
        return ByteBuffer.wrap(file, file.length - 8, 8)
            .getLong() == crc.getValue();
    }

    /** How many chunks a region file holds wear for. */
    public static int chunksWithWear(Map<Integer, Entry> entries) {
        int count = 0;
        for (Entry entry : entries.values()) {
            if (entry.hasWear()) count++;
        }
        return count;
    }

    // ------------------------------------------------------------------
    // The mirror of one world, in memory and on disk
    // ------------------------------------------------------------------

    /**
     * One world's mirror: the region files under {@code <world>/trmtgtnh/wear/}, read as they are first asked about and
     * written back by whoever calls {@link #flush} - the edition's own background thread, so no save waits on a disk.
     *
     * <p>
     * Every method is synchronised on the store, because a chunk's wear is read wherever the game reads chunks (off
     * the main thread in some editions) and written where it saves them, and the flush runs on a thread of its own.
     * What a save hands over is kept as an update and never read from disk on the saving thread unless the region is
     * not yet known; a flush folds the updates into the file.
     */
    public static final class Store {

        /** Regions kept in memory once clean, newest asked first; past this the least recently asked go. */
        static final int KEPT_REGIONS = 64;

        private final File root;

        private final LinkedHashMap<String, Region> regions = new LinkedHashMap<String, Region>(16, 0.75f, true);

        /** Chunks whose entry may not be written over: wear awaiting a decision, or a restore still to be done. */
        private final Set<Spot> held = new HashSet<Spot>();

        /** Backups waiting on the mirror as it stood, and the saves that came in meanwhile, in order. */
        private int frozen;

        private final LinkedHashMap<Spot, Entry> later = new LinkedHashMap<Spot, Entry>();

        private static final class Region {

            /** What the file held, by index; null until it has been read. */
            Map<Integer, Entry> entries;

            /** What saves have handed over since the last flush, by index. */
            final Map<Integer, Entry> updates = new TreeMap<Integer, Entry>();

            /** Bumped by every update, so a flush knows whether anything came in while it wrote. */
            int version;
        }

        /** @param root the mirror's own folder, {@code <world>/trmtgtnh/wear} */
        public Store(File root) {
            this.root = root;
        }

        public File root() {
            return root;
        }

        /** What the mirror holds for this chunk, or null when it holds nothing. Reads the region's file once. */
        public synchronized Entry get(Spot spot) {
            Entry waiting = later.get(spot);
            if (waiting != null) return waiting;
            Region region = region(spot.file());
            Entry updated = region.updates.get(Integer.valueOf(spot.index()));
            if (updated != null) return updated;
            loaded(spot.file(), region);
            return region.entries.get(Integer.valueOf(spot.index()));
        }

        /**
         * A chunk has saved: what it wrote is now the mirror's entry, unless the chunk is {@link #hold held}. A chunk
         * saved with no wear is written down only where the mirror held some for it, so a world's untouched chunks
         * cost nothing.
         */
        public synchronized void put(Spot spot, long savedAt, byte[] blob) {
            if (held.contains(spot)) return;
            if (frozen > 0) {
                // A backup is being taken of the mirror as it stood when it was asked for: this waits for it.
                later.remove(spot);
                later.put(spot, new Entry(savedAt, blob));
                return;
            }
            Entry next = new Entry(savedAt, blob);
            if (!next.hasWear()) {
                Entry was = get(spot);
                if (was == null || !was.hasWear()) return;
            }
            Region region = region(spot.file());
            region.updates.put(Integer.valueOf(spot.index()), next);
            region.version++;
        }

        /**
         * A chunk's wear as a world from before the mirror holds it on disk, read from its region file (see
         * {@link WearSeed}): written down only where the mirror holds nothing for the chunk, so a save made since,
         * which
         * is newer, is never written over, and a held chunk is left alone.
         *
         * @return whether it went in
         */
        public synchronized boolean seed(Spot spot, long savedAt, byte[] blob) {
            if (blob == null || blob.length == 0 || held.contains(spot) || get(spot) != null) return false;
            Region region = region(spot.file());
            region.updates.put(Integer.valueOf(spot.index()), new Entry(savedAt, blob));
            region.version++;
            return true;
        }

        /**
         * Freezes the mirror for a backup, on the thread that asks for it, so the backup holds the mirror as it stood
         * at that moment however long the background thread takes to reach it: saves meanwhile wait aside, in order,
         * until {@link #thaw}. Frozen twice, it thaws when both are done.
         */
        public synchronized void freeze() {
            frozen++;
        }

        /** Lets the saves that waited for a backup into the mirror. */
        public synchronized void thaw() {
            if (frozen <= 0) return;
            if (--frozen > 0) return;
            Map<Spot, Entry> waited = new LinkedHashMap<Spot, Entry>(later);
            later.clear();
            for (Map.Entry<Spot, Entry> each : waited.entrySet()) {
                put(
                    each.getKey(),
                    each.getValue().savedAt,
                    each.getValue()
                        .blob());
            }
        }

        /** Keeps this chunk's entry as it is until {@link #release}: its wear is lost and may yet be put back. */
        public synchronized void hold(Spot spot) {
            held.add(spot);
        }

        public synchronized void release(Spot spot) {
            held.remove(spot);
        }

        public synchronized boolean holds(Spot spot) {
            return held.contains(spot);
        }

        /** Whether anything handed over has not reached the disk. */
        public synchronized boolean dirty() {
            for (Region region : regions.values()) {
                if (!region.updates.isEmpty()) return true;
            }
            return false;
        }

        /**
         * Writes every region with updates to its file - whole, beside itself first and then moved over the old one,
         * so a file is never half written - and lets clean regions past {@link #KEPT_REGIONS} go. Called on the
         * edition's background thread; a save arriving meanwhile waits only for the copy, never for the disk.
         *
         * @return how many files were written
         */
        public int flush() throws IOException {
            List<String> paths;
            synchronized (this) {
                paths = new ArrayList<String>();
                for (Map.Entry<String, Region> each : regions.entrySet()) {
                    if (!each.getValue().updates.isEmpty()) paths.add(each.getKey());
                }
            }
            int written = 0;
            for (String path : paths) {
                byte[] bytes;
                int version;
                synchronized (this) {
                    Region region = regions.get(path);
                    if (region == null || region.updates.isEmpty()) continue;
                    loaded(path, region);
                    Map<Integer, Entry> merged = new TreeMap<Integer, Entry>(region.entries);
                    merged.putAll(region.updates);
                    bytes = write(merged);
                    version = region.version;
                }
                File file = new File(root, path);
                writeWhole(file, bytes);
                written++;
                synchronized (this) {
                    Region region = regions.get(path);
                    if (region != null) {
                        region.entries.putAll(region.updates);
                        if (region.version == version) region.updates.clear();
                    }
                }
            }
            synchronized (this) {
                trim();
            }
            return written;
        }

        /** Forgets every region in memory, written or not: the world's own files are the truth from here. */
        public synchronized void forget() {
            regions.clear();
            held.clear();
            later.clear();
            frozen = 0;
        }

        private Region region(String path) {
            Region region = regions.get(path);
            if (region == null) {
                region = new Region();
                regions.put(path, region);
            }
            return region;
        }

        private void loaded(String path, Region region) {
            if (region.entries != null) return;
            File file = new File(root, path);
            Map<Integer, Entry> read = Collections.emptyMap();
            if (file.isFile()) {
                try {
                    read = WearMirror.read(Files.readAllBytes(file.toPath()));
                } catch (IOException unreadable) {
                    read = Collections.emptyMap();
                }
            }
            region.entries = new TreeMap<Integer, Entry>(read);
        }

        private void trim() {
            int over = regions.size() - KEPT_REGIONS;
            for (Iterator<Map.Entry<String, Region>> it = regions.entrySet()
                .iterator(); over > 0 && it.hasNext();) {
                Region region = it.next()
                    .getValue();
                if (region.updates.isEmpty()) {
                    it.remove();
                    over--;
                }
            }
        }
    }

    /** Writes a file whole: beside itself, then moved over the old one, so it is never seen half written. */
    public static void writeWhole(File file, byte[] bytes) throws IOException {
        File folder = file.getParentFile();
        if (folder != null && !folder.isDirectory() && !folder.mkdirs() && !folder.isDirectory()) {
            throw new IOException("could not make " + folder);
        }
        File beside = new File(folder, file.getName() + ".tmp");
        OutputStream out = new FileOutputStream(beside);
        try {
            out.write(bytes);
        } finally {
            out.close();
        }
        Files.move(beside.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    /** Every mirror file under a mirror folder, as paths relative to it, in a stable order. */
    public static List<String> files(File root) {
        List<String> out = new ArrayList<String>();
        collect(root, "", out, 0);
        Collections.sort(out);
        return out;
    }

    private static void collect(File folder, String prefix, List<String> out, int depth) {
        // The mirror's own layout is a dimension's folder and its region folder: dimensions/<namespace>/<path>/region
        // in the newer editions, a path perhaps several folders deep itself. Nothing deeper than this is ours.
        if (depth > 8) return;
        File[] children = folder.listFiles();
        if (children == null) return;
        for (File child : children) {
            String name = prefix + child.getName();
            if (child.isDirectory()) collect(child, name + "/", out, depth + 1);
            else if (child.getName()
                .endsWith(SUFFIX)) out.add(name);
        }
    }

    /** Reads a stream to its end. */
    static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        for (int got = in.read(chunk); got >= 0; got = in.read(chunk)) out.write(chunk, 0, got);
        return out.toByteArray();
    }
}
