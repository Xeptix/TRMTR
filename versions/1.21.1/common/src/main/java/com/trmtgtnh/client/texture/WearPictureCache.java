package com.trmtgtnh.client.texture;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.CRC32;

/**
 * The wear pictures already made, kept in memory and in {@code <game folder>/trmtr-cache/} so an unchanged picture is
 * never composed twice (1.0.1, W3: the freeze; D25, D26, D37).
 *
 * <p>
 * <strong>Keyed on every input.</strong> A key is a SHA-256 an edition builds with {@link Key} from everything that
 * shapes one picture: the source pixels and every other field of what it is composed from, which is where every
 * setting that shapes a picture arrives, its gradation, rotation, turn and edge, the mod's version and
 * {@link #FORMAT}. A picture whose key is found is the one a fresh compose makes, byte for byte; anything else is not
 * found. Nothing here knows what a picture is: the payload is the edition's own bytes, with four numbers beside it.
 *
 * <p>
 * <strong>On disk</strong>, segments of records, one written at the end of each stitch that made something new, each
 * with a header naming the format and the mod's version, every record with its own CRC-32, and an index at the end of
 * a finished segment. A segment of another format or version is deleted unread; a record whose head does not parse
 * ends its segment's reading there, so a file cut short by a crash loses only its tail; a payload whose CRC does not
 * match is dropped as it is read and composed afresh. At most {@link #DISK_CAP} bytes: the oldest segments go first,
 * and a picture read from one this session is written again into the newest before it goes. Only one game at a time
 * writes the folder (a lock file); another runs from memory alone. Deleting the folder, while the game runs or not,
 * costs only the composing it saved. In memory, the most recently used pictures up to {@link #MEMORY_CAP} bytes.
 *
 * <p>
 * Portable code, the same in every edition: it names nothing from the game.
 */
public final class WearPictureCache {

    /** The layout of a record and of what a key is made from; raised whenever either changes. */
    public static final int FORMAT = 1;

    /** The most the folder holds (D37). */
    public static final long DISK_CAP = 512L * 1024L * 1024L;

    /** The most kept in memory between stitches. */
    public static final long MEMORY_CAP = 192L * 1024L * 1024L;

    /** The folder's name, in the game folder. */
    public static final String FOLDER = "trmtr-cache";

    /**
     * Where a build says which build it is, beside this class: written by every edition's build
     * ({@code trmtr-build-stamp.gradle}), empty for a build stamped with its name (a snapshot or a release), and
     * {@code dev.} with a digest of the tree's main sources for a development build.
     */
    static final String STAMP = "build-stamp.txt";

    /**
     * The version every picture is kept under: the mod's version, and for a development build that version with its
     * build's stamp (t14, Xep 2026-11-22: "Fix that so it's based off version + snapshot when indev").
     *
     * <p>
     * A snapshot's jars and a release's are stamped with their own name (1.0.1-snapshot.17, 1.0.1), so the version
     * alone tells them apart. A development build is not: every one of the ports' is called by the version it is
     * heading for, and a 1.7.10 or 1.12.2 one by its commit, which two builds of uncommitted work share. A wall
     * composed by the rig's build before t13 was then served to the build after it from the cache, and the photograph
     * showed the fault the fix had removed. With the stamp, any change to what the jar is built from is another
     * version.
     */
    public static String keyedVersion(String version) {
        return keyedVersion(version, stamp());
    }

    static String keyedVersion(String version, String stamp) {
        String named = version == null ? "" : version;
        return stamp == null || stamp.isEmpty() ? named : named + "+" + stamp;
    }

    /** This build's stamp, or empty where the build wrote none. */
    static String stamp() {
        java.io.InputStream in = WearPictureCache.class.getResourceAsStream(STAMP);
        if (in == null) return "";
        try {
            ByteArrayOutputStream read = new ByteArrayOutputStream();
            byte[] chunk = new byte[256];
            for (int n; (n = in.read(chunk)) > 0;) read.write(chunk, 0, n);
            return new String(read.toByteArray(), StandardCharsets.UTF_8).trim();
        } catch (IOException unreadable) {
            return "";
        } finally {
            try {
                in.close();
            } catch (IOException ignored) {
                // Read already, or never: either way there is nothing left to do with it.
            }
        }
    }

    private static final byte[] SEGMENT_MAGIC = "TRMTRWPC".getBytes(StandardCharsets.US_ASCII);

    private static final byte[] INDEX_MAGIC = "TRMTRIDX".getBytes(StandardCharsets.US_ASCII);

    private static final int RECORD_MAGIC = 0x57504331;

    private static final int KEY_BYTES = 32;

    /** Key, four numbers and a length, after the record's magic. */
    private static final int HEAD_BYTES = KEY_BYTES + 16 + 4;

    /** No payload is larger: a 512-pixel picture's PNG with every frame of a moving layer is far below it. */
    private static final int MOST_PAYLOAD = 64 * 1024 * 1024;

    /** One picture: the edition's bytes and its four numbers. */
    public static final class Entry {

        public final byte[] payload;

        public final int a;

        public final int b;

        public final int c;

        public final int d;

        public Entry(byte[] payload, int a, int b, int c, int d) {
            this.payload = payload;
            this.a = a;
            this.b = b;
            this.c = c;
            this.d = d;
        }
    }

    /** Where one picture is on disk. */
    private static final class Place {

        final Segment segment;

        final long offset;

        final int length;

        final int a, b, c, d;

        Place(Segment segment, long offset, int length, int a, int b, int c, int d) {
            this.segment = segment;
            this.offset = offset;
            this.length = length;
            this.a = a;
            this.b = b;
            this.c = c;
            this.d = d;
        }
    }

    private static final class Segment {

        final File file;

        final int number;

        final long size;

        Segment(File file, int number, long size) {
            this.file = file;
            this.number = number;
            this.size = size;
        }
    }

    private final File folder;

    private final String version;

    private final long diskCap;

    private final long memoryCap;

    private final Map<String, Entry> memory = new LinkedHashMap<String, Entry>(1024, 0.75f, true);

    private long memoryBytes;

    private final Map<String, Place> disk = new HashMap<String, Place>();

    private final TreeMap<Integer, Segment> segments = new TreeMap<Integer, Segment>();

    /** Written since the last flush, to go into the next segment, in the order they came. */
    private final LinkedHashMap<String, Entry> unwritten = new LinkedHashMap<String, Entry>();

    /** Each segment's file, open for reading, once it has been read from. */
    private final Map<Segment, FileChannel> channels = new HashMap<Segment, FileChannel>();

    /** Read from disk this session, so they outlive the segment that held them. */
    private final Set<String> readFromDisk = new HashSet<String>();

    private FileChannel lockChannel;

    private FileLock lock;

    private boolean diskOpen;

    private int hits;

    private int misses;

    private int discarded;

    /**
     * A cache over {@code folder}, or in memory alone where {@code folder} is null or another game holds it. Nothing is
     * read until the first {@link #get}.
     */
    public WearPictureCache(File folder, String version) {
        this(folder, version, DISK_CAP, MEMORY_CAP);
    }

    WearPictureCache(File folder, String version, long diskCap, long memoryCap) {
        this.folder = folder;
        this.version = version == null ? "" : version;
        this.diskCap = diskCap;
        this.memoryCap = memoryCap;
    }

    /** The picture under this key, or null; from memory, then from disk. */
    public synchronized Entry get(byte[] key) {
        String name = name(key);
        Entry held = memory.get(name);
        if (held == null) held = unwritten.get(name);
        if (held != null) {
            hits++;
            return held;
        }
        openDisk();
        Place place = disk.get(name);
        if (place != null) {
            Entry read = read(place, key);
            if (read != null) {
                hits++;
                readFromDisk.add(name);
                remember(name, read);
                return read;
            }
            disk.remove(name);
            discarded++;
        }
        misses++;
        return null;
    }

    /** Keeps a picture under its key, in memory now and on disk at the next {@link #flush}. */
    public synchronized void put(byte[] key, Entry entry) {
        if (key == null || key.length != KEY_BYTES || entry == null || entry.payload == null) return;
        if (entry.payload.length > MOST_PAYLOAD) return;
        String name = name(key);
        remember(name, entry);
        if (!disk.containsKey(name)) unwritten.put(name, entry);
    }

    /**
     * Writes what was kept since the last flush as one new segment, then removes the oldest segments past the cap,
     * writing again first any picture read from one of them this session. Said nothing and wrote nothing where the
     * folder cannot be written.
     */
    public synchronized void flush() {
        openDisk();
        if (!diskOpen) {
            unwritten.clear();
            return;
        }
        try {
            if (!folder.isDirectory() && !folder.mkdirs()) {
                unwritten.clear();
                return;
            }
            evictPast(diskCap - estimate(unwritten.values()));
            if (unwritten.isEmpty()) return;
            int number = segments.isEmpty() ? 1
                : segments.lastKey()
                    .intValue() + 1;
            File file = new File(folder, String.format("wear-%06d.bin", Integer.valueOf(number)));
            File part = new File(folder, file.getName() + ".part");
            List<Object[]> heads = new ArrayList<Object[]>();
            try (RandomAccessFile out = new RandomAccessFile(part, "rw")) {
                out.setLength(0L);
                out.write(header());
                for (Map.Entry<String, Entry> each : unwritten.entrySet()) {
                    long offset = out.getFilePointer();
                    byte[] key = unname(each.getKey());
                    out.write(record(key, each.getValue()));
                    heads.add(new Object[] { key, Long.valueOf(offset), each.getValue() });
                }
                long indexAt = out.getFilePointer();
                ByteArrayOutputStream index = new ByteArrayOutputStream();
                DataOutputStream data = new DataOutputStream(index);
                for (Object[] head : heads) {
                    Entry entry = (Entry) head[2];
                    data.write((byte[]) head[0]);
                    data.writeLong(((Long) head[1]).longValue());
                    data.writeInt(entry.payload.length);
                    data.writeInt(entry.a);
                    data.writeInt(entry.b);
                    data.writeInt(entry.c);
                    data.writeInt(entry.d);
                }
                byte[] indexBytes = index.toByteArray();
                out.write(indexBytes);
                CRC32 crc = new CRC32();
                crc.update(indexBytes);
                out.writeInt((int) crc.getValue());
                out.writeInt(heads.size());
                out.writeLong(indexAt);
                out.write(INDEX_MAGIC);
            }
            if (!part.renameTo(file)) {
                part.delete();
                unwritten.clear();
                return;
            }
            Segment segment = new Segment(file, number, file.length());
            segments.put(Integer.valueOf(number), segment);
            for (Object[] head : heads) {
                Entry entry = (Entry) head[2];
                disk.put(
                    name((byte[]) head[0]),
                    new Place(
                        segment,
                        ((Long) head[1]).longValue(),
                        entry.payload.length,
                        entry.a,
                        entry.b,
                        entry.c,
                        entry.d));
            }
            unwritten.clear();
        } catch (IOException | RuntimeException unwritable) {
            // The folder is a convenience: a picture that could not be kept is composed again next time.
            unwritten.clear();
        }
    }

    /** Lets go of everything in memory, and of the folder's lock. The folder itself stays. */
    public synchronized void close() {
        for (Segment segment : new ArrayList<Segment>(channels.keySet())) closeChannel(segment);
        memory.clear();
        memoryBytes = 0L;
        unwritten.clear();
        disk.clear();
        segments.clear();
        readFromDisk.clear();
        diskOpen = false;
        try {
            if (lock != null) lock.release();
            if (lockChannel != null) lockChannel.close();
        } catch (IOException ignored) {
            // Released with the process either way.
        }
        lock = null;
        lockChannel = null;
    }

    /** Pictures found, not found and found but unreadable since the last {@link #counted}, then reset. */
    public synchronized int[] counted() {
        int[] out = { hits, misses, discarded };
        hits = 0;
        misses = 0;
        discarded = 0;
        return out;
    }

    /** What the folder holds, in bytes, as this cache knows it. */
    public synchronized long diskBytes() {
        long total = 0L;
        for (Segment segment : segments.values()) total += segment.size;
        return total;
    }

    /**
     * ARGB pixels as a payload, four bytes each, high byte first: what an edition that keeps composed pixels rather
     * than an encoded picture stores (1.7.10 and 1.12.2, whose stitch takes the pixels themselves).
     */
    public static byte[] pixelsToBytes(int[] pixels) {
        ByteBuffer out = ByteBuffer.allocate(4 * pixels.length);
        out.asIntBuffer()
            .put(pixels);
        return out.array();
    }

    /** The pixels {@link #pixelsToBytes} made, or null where the bytes are not a whole number of them. */
    public static int[] bytesToPixels(byte[] bytes) {
        if (bytes == null || bytes.length % 4 != 0) return null;
        int[] out = new int[bytes.length / 4];
        ByteBuffer.wrap(bytes)
            .asIntBuffer()
            .get(out);
        return out;
    }

    // ------------------------------------------------------------------
    // Keys
    // ------------------------------------------------------------------

    /** What a key is made from, in the order an edition adds it. Every value is written with its kind and length. */
    public static final class Key {

        private final MessageDigest digest;

        public Key() {
            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException impossible) {
                throw new IllegalStateException("no SHA-256 in this JDK", impossible);
            }
            add(FORMAT);
        }

        public Key add(int value) {
            digest.update((byte) 'i');
            putInt(value);
            return this;
        }

        public Key add(long value) {
            digest.update((byte) 'l');
            putInt((int) (value >>> 32));
            putInt((int) value);
            return this;
        }

        public Key add(boolean value) {
            digest.update((byte) (value ? 'T' : 'F'));
            return this;
        }

        public Key add(float value) {
            digest.update((byte) 'f');
            putInt(Float.floatToIntBits(value));
            return this;
        }

        public Key add(double value) {
            return add(Double.doubleToLongBits(value)).mark('d');
        }

        public Key add(String value) {
            if (value == null) return mark('n');
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            digest.update((byte) 's');
            putInt(bytes.length);
            digest.update(bytes);
            return this;
        }

        public Key add(byte[] value) {
            if (value == null) return mark('n');
            digest.update((byte) 'B');
            putInt(value.length);
            digest.update(value);
            return this;
        }

        public Key add(int[] value) {
            if (value == null) return mark('n');
            digest.update((byte) 'I');
            putInt(value.length);
            ByteBuffer buffer = ByteBuffer.allocate(4 * Math.min(value.length, 4096));
            for (int at = 0; at < value.length;) {
                buffer.clear();
                int end = Math.min(value.length, at + 4096);
                for (; at < end; at++) buffer.putInt(value[at]);
                digest.update(buffer.array(), 0, buffer.position());
            }
            return this;
        }

        public Key add(float[] value) {
            if (value == null) return mark('n');
            digest.update((byte) 'G');
            putInt(value.length);
            for (float each : value) putInt(Float.floatToIntBits(each));
            return this;
        }

        /**
         * Any value, deep: a number, a string, an enum by its name, an array, a list in its order, a set or a map in
         * the
         * order of its members' text, and any other object by every field it and its classes declare, by name, statics
         * and transients aside. An object met again inside itself is written as a reference, so a cycle ends.
         */
        public Key addDeep(Object value) {
            deep(value, new java.util.IdentityHashMap<Object, Boolean>(), 0);
            return this;
        }

        /** The finished key. The builder is spent. */
        public byte[] finish() {
            return digest.digest();
        }

        private Key mark(char kind) {
            digest.update((byte) kind);
            return this;
        }

        private void putInt(int value) {
            digest.update((byte) (value >>> 24));
            digest.update((byte) (value >>> 16));
            digest.update((byte) (value >>> 8));
            digest.update((byte) value);
        }

        private void deep(Object value, java.util.IdentityHashMap<Object, Boolean> seen, int depth) {
            if (value == null) {
                mark('n');
                return;
            }
            if (value instanceof Boolean) {
                add(((Boolean) value).booleanValue());
                return;
            }
            if (value instanceof Integer || value instanceof Short || value instanceof Byte) {
                add(((Number) value).intValue());
                return;
            }
            if (value instanceof Long) {
                add(((Long) value).longValue());
                return;
            }
            if (value instanceof Float) {
                add(((Float) value).floatValue());
                return;
            }
            if (value instanceof Double) {
                add(((Double) value).doubleValue());
                return;
            }
            if (value instanceof Character) {
                add((int) ((Character) value).charValue()).mark('c');
                return;
            }
            if (value instanceof CharSequence) {
                add(value.toString());
                return;
            }
            if (value instanceof Enum) {
                mark('e').add(
                    ((Enum<?>) value).getDeclaringClass()
                        .getName())
                    .add(((Enum<?>) value).name());
                return;
            }
            if (value instanceof int[]) {
                add((int[]) value);
                return;
            }
            if (value instanceof float[]) {
                add((float[]) value);
                return;
            }
            if (value instanceof byte[]) {
                add((byte[]) value);
                return;
            }
            if (depth > 32 || seen.containsKey(value)) {
                mark('r').add(
                    value.getClass()
                        .getName());
                return;
            }
            seen.put(value, Boolean.TRUE);
            if (value.getClass()
                .isArray()) {
                int length = java.lang.reflect.Array.getLength(value);
                mark('[').add(length);
                for (int i = 0; i < length; i++) deep(java.lang.reflect.Array.get(value, i), seen, depth + 1);
                return;
            }
            if (value instanceof List) {
                List<?> list = (List<?>) value;
                mark('L').add(list.size());
                for (Object each : list) deep(each, seen, depth + 1);
                return;
            }
            if (value instanceof Map) {
                TreeMap<String, Object> sorted = new TreeMap<String, Object>();
                for (Map.Entry<?, ?> each : ((Map<?, ?>) value).entrySet()) {
                    sorted.put(String.valueOf(each.getKey()), each.getValue());
                }
                mark('M').add(sorted.size());
                for (Map.Entry<String, Object> each : sorted.entrySet()) {
                    add(each.getKey());
                    deep(each.getValue(), seen, depth + 1);
                }
                return;
            }
            if (value instanceof java.util.Collection) {
                List<String> texts = new ArrayList<String>();
                for (Object each : (java.util.Collection<?>) value) texts.add(String.valueOf(each));
                java.util.Collections.sort(texts);
                mark('S').add(texts.size());
                for (String each : texts) add(each);
                return;
            }
            mark('o').add(
                value.getClass()
                    .getName());
            TreeMap<String, java.lang.reflect.Field> fields = new TreeMap<String, java.lang.reflect.Field>();
            for (Class<?> type = value.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
                for (java.lang.reflect.Field field : type.getDeclaredFields()) {
                    int modifiers = field.getModifiers();
                    if (java.lang.reflect.Modifier.isStatic(modifiers)
                        || java.lang.reflect.Modifier.isTransient(modifiers)
                        || field.isSynthetic()) continue;
                    fields.put(type.getName() + "." + field.getName(), field);
                }
            }
            for (Map.Entry<String, java.lang.reflect.Field> each : fields.entrySet()) {
                add(each.getKey());
                try {
                    each.getValue()
                        .setAccessible(true);
                    deep(
                        each.getValue()
                            .get(value),
                        seen,
                        depth + 1);
                } catch (ReflectiveOperationException | RuntimeException unreadable) {
                    // A field that cannot be read is written as such, and the key it is in is its own.
                    mark('x').add(String.valueOf(unreadable));
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Inside
    // ------------------------------------------------------------------

    private void remember(String name, Entry entry) {
        Entry before = memory.put(name, entry);
        if (before != null) memoryBytes -= before.payload.length;
        memoryBytes += entry.payload.length;
        for (Iterator<Map.Entry<String, Entry>> oldest = memory.entrySet()
            .iterator(); memoryBytes > memoryCap && oldest.hasNext();) {
            Map.Entry<String, Entry> gone = oldest.next();
            memoryBytes -= gone.getValue().payload.length;
            oldest.remove();
        }
    }

    /** The folder read, once: its lock taken and every segment's index. Nothing at all where it cannot be. */
    private void openDisk() {
        if (diskOpen || folder == null || lockChannel != null) return;
        try {
            if (!folder.isDirectory() && !folder.mkdirs()) return;
            lockChannel = new RandomAccessFile(new File(folder, "lock"), "rw").getChannel();
            lock = lockChannel.tryLock();
            if (lock == null) return;
            diskOpen = true;
            File[] files = folder.listFiles();
            if (files == null) return;
            Arrays.sort(files);
            for (File file : files) {
                String fileName = file.getName();
                if (fileName.endsWith(".part")) {
                    // A segment a game stopped writing.
                    file.delete();
                    continue;
                }
                if (!fileName.startsWith("wear-") || !fileName.endsWith(".bin")) continue;
                int number;
                try {
                    number = Integer.parseInt(fileName.substring(5, fileName.length() - 4));
                } catch (NumberFormatException foreign) {
                    file.delete();
                    continue;
                }
                if (!readSegment(file, number)) {
                    file.delete();
                    discarded++;
                }
            }
        } catch (IOException | RuntimeException unreadable) {
            diskOpen = false;
        }
    }

    /** One segment's index into {@link #disk}; false for one to delete: another format or version, or no head. */
    private boolean readSegment(File file, int number) {
        try (RandomAccessFile in = new RandomAccessFile(file, "r")) {
            byte[] expected = header();
            if (in.length() < expected.length) return false;
            byte[] head = new byte[expected.length];
            in.readFully(head);
            if (!Arrays.equals(head, expected)) return false;
            Segment segment = new Segment(file, number, in.length());
            List<Place> places = new ArrayList<Place>();
            List<String> names = new ArrayList<String>();
            if (!readIndex(in, segment, places, names)) {
                places.clear();
                names.clear();
                scan(in, segment, expected.length, places, names);
            }
            if (places.isEmpty()) return false;
            segments.put(Integer.valueOf(number), segment);
            for (int i = 0; i < places.size(); i++) disk.put(names.get(i), places.get(i));
            return true;
        } catch (IOException | RuntimeException unreadable) {
            return false;
        }
    }

    /** A finished segment's own index, checked by its CRC; false where it has none that holds. */
    private static boolean readIndex(RandomAccessFile in, Segment segment, List<Place> places, List<String> names)
        throws IOException {
        long length = in.length();
        if (length < 24) return false;
        in.seek(length - 8);
        byte[] magic = new byte[8];
        in.readFully(magic);
        if (!Arrays.equals(magic, INDEX_MAGIC)) return false;
        in.seek(length - 24);
        int crc = in.readInt();
        int count = in.readInt();
        long indexAt = in.readLong();
        long entryBytes = KEY_BYTES + 8 + 20;
        if (count < 0 || indexAt < 0 || indexAt + count * entryBytes != length - 24) return false;
        byte[] index = new byte[(int) (count * entryBytes)];
        in.seek(indexAt);
        in.readFully(index);
        CRC32 check = new CRC32();
        check.update(index);
        if ((int) check.getValue() != crc) return false;
        ByteBuffer read = ByteBuffer.wrap(index);
        for (int i = 0; i < count; i++) {
            byte[] key = new byte[KEY_BYTES];
            read.get(key);
            long offset = read.getLong();
            int payload = read.getInt();
            int a = read.getInt(), b = read.getInt(), c = read.getInt(), d = read.getInt();
            if (offset < 0 || payload < 0
                || payload > MOST_PAYLOAD
                || offset + 4 + HEAD_BYTES + payload + 4 > indexAt) {
                return false;
            }
            places.add(new Place(segment, offset, payload, a, b, c, d));
            names.add(name(key));
        }
        return true;
    }

    /** A segment with no index that holds, read record by record to the first that does not parse. */
    private static void scan(RandomAccessFile in, Segment segment, long from, List<Place> places, List<String> names)
        throws IOException {
        long length = in.length();
        long at = from;
        byte[] head = new byte[4 + HEAD_BYTES];
        while (at + head.length + 4 <= length) {
            in.seek(at);
            in.readFully(head);
            ByteBuffer read = ByteBuffer.wrap(head);
            if (read.getInt() != RECORD_MAGIC) return;
            byte[] key = new byte[KEY_BYTES];
            read.get(key);
            int a = read.getInt(), b = read.getInt(), c = read.getInt(), d = read.getInt();
            int payload = read.getInt();
            if (payload < 0 || payload > MOST_PAYLOAD || at + head.length + payload + 4 > length) return;
            places.add(new Place(segment, at, payload, a, b, c, d));
            names.add(name(key));
            at += head.length + payload + 4;
        }
    }

    /** One picture's record read back, its key and CRC checked; null where either does not hold. */
    private Entry read(Place place, byte[] key) {
        try {
            FileChannel in = channelOf(place.segment);
            byte[] whole = new byte[4 + HEAD_BYTES + place.length + 4];
            ByteBuffer into = ByteBuffer.wrap(whole);
            long at = place.offset;
            while (into.hasRemaining()) {
                int got = in.read(into, at);
                if (got < 0) return null;
                at += got;
            }
            ByteBuffer read = ByteBuffer.wrap(whole);
            if (read.getInt() != RECORD_MAGIC) return null;
            byte[] stored = new byte[KEY_BYTES];
            read.get(stored);
            if (!Arrays.equals(stored, key)) return null;
            int a = read.getInt(), b = read.getInt(), c = read.getInt(), d = read.getInt();
            int payload = read.getInt();
            if (payload != place.length) return null;
            byte[] bytes = new byte[payload];
            read.get(bytes);
            int crc = read.getInt();
            CRC32 check = new CRC32();
            check.update(whole, 4, HEAD_BYTES + payload);
            if ((int) check.getValue() != crc) return null;
            return new Entry(bytes, a, b, c, d);
        } catch (IOException | RuntimeException unreadable) {
            // A folder deleted under the game, or a file cut short: a miss, and the picture is composed again.
            return null;
        }
    }

    /**
     * The segment's file, opened once and kept open for the session's reads: a stitch that finds ninety thousand
     * pictures on disk would otherwise open and close the file ninety thousand times, which took 23 s in a dev run
     * (2026-11-21). Opened through NIO, which on Windows lets the file be deleted while it is open, so deleting the
     * folder under a running game still works.
     */
    private FileChannel channelOf(Segment segment) throws IOException {
        FileChannel open = channels.get(segment);
        if (open == null || !open.isOpen()) {
            open = FileChannel.open(segment.file.toPath(), java.nio.file.StandardOpenOption.READ);
            channels.put(segment, open);
        }
        return open;
    }

    private void closeChannel(Segment segment) {
        FileChannel open = channels.remove(segment);
        try {
            if (open != null) open.close();
        } catch (IOException ignored) {
            // Closed with the process either way.
        }
    }

    /** Removes the oldest segments until what stays is under {@code room}, carrying on what this session read. */
    private void evictPast(long room) {
        while (!segments.isEmpty() && diskBytes() > Math.max(0L, room)) {
            Segment oldest = segments.firstEntry()
                .getValue();
            for (Iterator<Map.Entry<String, Place>> each = disk.entrySet()
                .iterator(); each.hasNext();) {
                Map.Entry<String, Place> one = each.next();
                if (one.getValue().segment != oldest) continue;
                if (readFromDisk.contains(one.getKey()) && !unwritten.containsKey(one.getKey())) {
                    Entry carried = memory.get(one.getKey());
                    if (carried == null) carried = read(one.getValue(), unname(one.getKey()));
                    if (carried != null) unwritten.put(one.getKey(), carried);
                }
                each.remove();
            }
            segments.remove(Integer.valueOf(oldest.number));
            closeChannel(oldest);
            oldest.file.delete();
            if (segments.isEmpty()) break;
        }
    }

    private static long estimate(java.util.Collection<Entry> entries) {
        long total = 0L;
        for (Entry entry : entries) total += 4 + HEAD_BYTES + entry.payload.length + 4 + KEY_BYTES + 28;
        return total;
    }

    private byte[] header() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(out);
        try {
            data.write(SEGMENT_MAGIC);
            data.writeInt(FORMAT);
            data.writeUTF(version);
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
        return out.toByteArray();
    }

    private static byte[] record(byte[] key, Entry entry) {
        ByteBuffer out = ByteBuffer.allocate(4 + HEAD_BYTES + entry.payload.length + 4);
        out.putInt(RECORD_MAGIC);
        out.put(key);
        out.putInt(entry.a);
        out.putInt(entry.b);
        out.putInt(entry.c);
        out.putInt(entry.d);
        out.putInt(entry.payload.length);
        out.put(entry.payload);
        CRC32 crc = new CRC32();
        crc.update(out.array(), 4, HEAD_BYTES + entry.payload.length);
        out.putInt((int) crc.getValue());
        return out.array();
    }

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private static String name(byte[] key) {
        char[] out = new char[key.length * 2];
        for (int i = 0; i < key.length; i++) {
            out[2 * i] = HEX[(key[i] >>> 4) & 15];
            out[2 * i + 1] = HEX[key[i] & 15];
        }
        return new String(out);
    }

    private static byte[] unname(String name) {
        byte[] out = new byte[name.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(name.substring(2 * i, 2 * i + 2), 16);
        }
        return out;
    }
}
