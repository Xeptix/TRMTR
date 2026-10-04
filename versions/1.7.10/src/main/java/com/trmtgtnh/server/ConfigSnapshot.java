package com.trmtgtnh.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

import net.minecraftforge.common.config.Configuration;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.config.ConfigReload;
import com.trmtgtnh.config.TrmtConfig;

/**
 * A whole config, taken and put back.
 *
 * <p>
 * The snapshot is the config file's own text rather than a list of settings, and that is the
 * point: it captures every property the mod declares including the list ones, needs no schema of
 * its own, and can never drift out of step with what the config actually holds. Putting one back
 * is writing that text and reloading, which is the same path a hand edit and a reload take.
 *
 * <p>
 * Saving before reading matters - the live config can hold changes the file has not been given
 * yet, and a snapshot of a stale file would quietly capture the wrong thing.
 */
public final class ConfigSnapshot {

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private ConfigSnapshot() {}

    /** The config exactly as it stands, or null when there is none to read. */
    public static String capture() {
        Configuration config = TrmtConfig.raw();
        if (config == null) return null;
        File file = config.getConfigFile();
        if (file == null || !file.isFile()) return null;
        // A file that failed to read is refused outright rather than flushed. The live settings are
        // then the last good read merged with whatever parsed before the bad line, and saving them -
        // which a snapshot used to do, straight past the guard every other save honours - wrote that
        // over a file somebody was part way through putting right by hand.
        if (TrmtConfig.isPoisoned()) return null;
        // Flush anything the live config is holding, so what is read is what is set.
        try {
            TrmtConfig.save();
        } catch (RuntimeException awkward) {
            Trmt.LOG.debug("Could not flush the config before snapshotting", awkward);
        }
        return read(file);
    }

    /**
     * Writes a captured config back and reloads it. Returns false when nothing could be written, in
     * which case the config on disk is untouched, and also when the reload refused what was written,
     * in which case the file holds the snapshot and the running settings do not.
     */
    public static boolean apply(String snapshot) {
        if (snapshot == null || snapshot.isEmpty()) return false;
        Configuration config = TrmtConfig.raw();
        if (config == null) return false;
        File file = config.getConfigFile();
        if (file == null) return false;

        if (!write(file, snapshot)) return false;
        try {
            // Null is a file the reader refused, and then nothing was applied whatever was written.
            // Reporting it is what lets the tool say it failed rather than announce a snapshot that
            // never took.
            if (ConfigReload.fromDisk() == null) return false;
        } catch (RuntimeException awkward) {
            Trmt.LOG.warn("Config was written but could not be reloaded", awkward);
            return false;
        }
        return true;
    }

    private static String read(File file) {
        InputStream in = null;
        try {
            in = new FileInputStream(file);
            byte[] buffer = new byte[Math.max(16, (int) Math.min(file.length(), 1 << 22))];
            StringBuilder out = new StringBuilder();
            int got;
            while ((got = in.read(buffer)) > 0) {
                out.append(new String(buffer, 0, got, UTF8));
            }
            return out.toString();
        } catch (IOException unreadable) {
            Trmt.LOG.warn("Could not read the config to snapshot it", unreadable);
            return null;
        } finally {
            close(in);
        }
    }

    private static boolean write(File file, String text) {
        OutputStream out = null;
        try {
            out = new FileOutputStream(file);
            out.write(text.getBytes(UTF8));
            return true;
        } catch (IOException unwritable) {
            Trmt.LOG.warn("Could not write a config snapshot back", unwritable);
            return false;
        } finally {
            close(out);
        }
    }

    private static void close(java.io.Closeable stream) {
        if (stream == null) return;
        try {
            stream.close();
        } catch (IOException ignored) {
            // Read or written either way; nothing useful to do here.
        }
    }
}
