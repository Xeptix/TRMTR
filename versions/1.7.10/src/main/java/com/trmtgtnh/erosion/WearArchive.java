package com.trmtgtnh.erosion;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * One wear backup on disk (0.9.229; docs/goals/0.9.229-wear-backup-spec.md, "Backups"): a zip holding the mirror as it
 * stood ({@code wear/...}, its region files as they are), all of this mod's world data beside it ({@code data/...},
 * the heal clock, the spawn grants and the rest, so a restore lines up with healing time), and a manifest
 * ({@code backup.properties}, {@link WearBackupSet.Backup#manifest}). Written beside itself and moved into place
 * whole, so the folder never holds half a backup. Portable, the same file in every edition.
 */
public final class WearArchive {

    public static final String MANIFEST = "backup.properties";

    /** Where the mirror's files go inside a backup. */
    public static final String WEAR = "wear/";

    /** Where the world data goes inside a backup. */
    public static final String DATA = "data/";

    private WearArchive() {}

    /**
     * Writes a backup: every mirror file under {@code mirrorRoot} as it is on disk now, the world data given, and the
     * manifest, which counts the chunks with wear as the files go in.
     *
     * @param zip        the backup's file, in the backups folder, named by {@link WearBackupSet#nameFor}
     * @param mirrorRoot the mirror's folder; a world with no mirror yet makes a backup of its world data alone
     * @param data       the world data's files by their names in {@code data/}, as bytes taken on the world's thread
     * @param made       when, in milliseconds since 1970
     * @return the backup as it now stands on disk
     */
    public static WearBackupSet.Backup write(File zip, File mirrorRoot, Map<String, byte[]> data, long made,
        String reason, String version, long ticks) throws IOException {
        return write(zip, mirrorRoot, data, made, reason, version, ticks, 0L);
    }

    /** As above, with the world's play when it was taken ({@link WearBackupSet.Backup#played}). */
    public static WearBackupSet.Backup write(File zip, File mirrorRoot, Map<String, byte[]> data, long made,
        String reason, String version, long ticks, long played) throws IOException {
        File folder = zip.getParentFile();
        if (folder != null && !folder.isDirectory() && !folder.mkdirs() && !folder.isDirectory()) {
            throw new IOException("could not make " + folder);
        }
        File beside = new File(folder, zip.getName() + ".tmp");
        int chunks = 0;
        ZipOutputStream out = new ZipOutputStream(new FileOutputStream(beside));
        try {
            for (String path : WearMirror.files(mirrorRoot)) {
                byte[] bytes = Files.readAllBytes(new File(mirrorRoot, path).toPath());
                chunks += WearMirror.chunksWithWear(WearMirror.read(bytes));
                put(out, WEAR + path, bytes);
            }
            for (Map.Entry<String, byte[]> each : new TreeMap<String, byte[]>(data).entrySet()) {
                put(out, DATA + each.getKey(), each.getValue());
            }
            WearBackupSet.Backup manifest = new WearBackupSet.Backup(
                zip.getName(),
                made,
                reason,
                version,
                ticks,
                chunks,
                0L,
                played);
            put(
                out,
                MANIFEST,
                manifest.manifest()
                    .getBytes("UTF-8"));
        } finally {
            out.close();
        }
        Files.move(beside.toPath(), zip.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return new WearBackupSet.Backup(zip.getName(), made, reason, version, ticks, chunks, zip.length(), played);
    }

    private static void put(ZipOutputStream out, String name, byte[] bytes) throws IOException {
        out.putNextEntry(new ZipEntry(name));
        out.write(bytes);
        out.closeEntry();
    }

    /** A backup's manifest, read from its file, or null when the file is not a whole backup. */
    public static WearBackupSet.Backup describe(File zip) {
        Opened opened = null;
        try {
            opened = open(zip);
            return opened.backup;
        } catch (IOException unreadable) {
            return null;
        } finally {
            if (opened != null) opened.close();
        }
    }

    /** Opens a backup for reading. The caller closes it. */
    public static Opened open(File zip) throws IOException {
        ZipFile file = new ZipFile(zip);
        try {
            ZipEntry manifest = file.getEntry(MANIFEST);
            if (manifest == null) throw new IOException(zip.getName() + " holds no " + MANIFEST);
            String text = new String(bytes(file, manifest), "UTF-8");
            WearBackupSet.Backup backup = WearBackupSet.Backup.fromManifest(zip.getName(), text, zip.length());
            if (backup == null) throw new IOException(zip.getName() + " has a manifest this build cannot read");
            return new Opened(file, backup);
        } catch (IOException failed) {
            file.close();
            throw failed;
        }
    }

    /** A backup opened for reading: its manifest, its world data, and each chunk's entry as its mirror held it. */
    public static final class Opened implements Closeable {

        public final WearBackupSet.Backup backup;

        private final ZipFile file;

        private final Map<String, Map<Integer, WearMirror.Entry>> regions = new HashMap<String, Map<Integer, WearMirror.Entry>>();

        Opened(ZipFile file, WearBackupSet.Backup backup) {
            this.file = file;
            this.backup = backup;
        }

        /** What the backup's mirror held for this chunk, or null. */
        public synchronized WearMirror.Entry entry(WearMirror.Spot spot) throws IOException {
            Map<Integer, WearMirror.Entry> region = regions.get(spot.file());
            if (region == null) {
                ZipEntry found = file.getEntry(WEAR + spot.file());
                region = found == null ? Collections.<Integer, WearMirror.Entry>emptyMap()
                    : WearMirror.read(bytes(file, found));
                regions.put(spot.file(), region);
            }
            return region.get(Integer.valueOf(spot.index()));
        }

        /** Every chunk the backup holds wear for. */
        public synchronized List<WearMirror.Spot> chunks() throws IOException {
            List<WearMirror.Spot> out = new ArrayList<WearMirror.Spot>();
            Enumeration<? extends ZipEntry> all = file.entries();
            while (all.hasMoreElements()) {
                ZipEntry each = all.nextElement();
                if (!each.getName()
                    .startsWith(WEAR)) continue;
                String path = each.getName()
                    .substring(WEAR.length());
                for (Map.Entry<Integer, WearMirror.Entry> entry : WearMirror.read(bytes(file, each))
                    .entrySet()) {
                    if (!entry.getValue()
                        .hasWear()) continue;
                    WearMirror.Spot spot = WearMirror.spotOf(
                        path,
                        entry.getKey()
                            .intValue());
                    if (spot != null) out.add(spot);
                }
            }
            return out;
        }

        /** The world data the backup holds, by name in {@code data/}. */
        public synchronized Map<String, byte[]> data() throws IOException {
            Map<String, byte[]> out = new TreeMap<String, byte[]>();
            Enumeration<? extends ZipEntry> all = file.entries();
            while (all.hasMoreElements()) {
                ZipEntry each = all.nextElement();
                if (each.getName()
                    .startsWith(DATA) && !each.isDirectory()) {
                    out.put(
                        each.getName()
                            .substring(DATA.length()),
                        bytes(file, each));
                }
            }
            return out;
        }

        @Override
        public synchronized void close() {
            try {
                file.close();
            } catch (IOException ignored) {
                // Closing a file opened for reading loses nothing.
            }
        }
    }

    private static byte[] bytes(ZipFile file, ZipEntry entry) throws IOException {
        InputStream in = file.getInputStream(entry);
        try {
            return WearMirror.readAll(in);
        } finally {
            in.close();
        }
    }
}
