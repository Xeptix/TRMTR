package com.trmtgtnh.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

/**
 * Who has quieted the update notice on this server, and how (0.9.221; Xep, 2026-10-09: a link "for silencing that
 * message, allowing you to choose that version (silence just that update) or to disable the update notify feature
 * entirely"). Each player's own choice, kept by the server in its config folder by the player's id: one build
 * silenced - and a newer build is told as usual - or notices off for them altogether, until they turn them on again.
 * Nobody else's notice changes.
 *
 * <p>
 * A Properties file, {@value #FILE}: {@code <player id>=off} or {@code <player id>=<build>}. Portable: no Minecraft,
 * no logging framework. A file that cannot be read is taken as nobody having quieted anything - the notice says too
 * much rather than too little.
 */
public final class NoticeSilences {

    /** The file, in the server's config folder. */
    public static final String FILE = "trmtgtnh-notices.properties";

    /** The value meaning notices are off for that player. */
    static final String OFF = "off";

    private final Properties held = new Properties();

    private final File file;

    private NoticeSilences(File file) {
        this.file = file;
    }

    /** What the file in this folder says, or nothing quieted when there is none or it cannot be read. */
    public static NoticeSilences in(File folder) {
        NoticeSilences out = new NoticeSilences(folder == null ? null : new File(folder, FILE));
        if (out.file != null && out.file.isFile()) {
            InputStream in = null;
            try {
                in = new FileInputStream(out.file);
                out.held.load(in);
            } catch (IOException e) {
                out.held.clear();
            } catch (IllegalArgumentException e) {
                out.held.clear();
            } finally {
                if (in != null) try {
                    in.close();
                } catch (IOException ignored) {
                    // Read already; nothing to do.
                }
            }
        }
        return out;
    }

    /** Whether this player turned update notices off. */
    public synchronized boolean off(String player) {
        return OFF.equals(held.getProperty(player));
    }

    /** Whether this player silenced exactly this build. */
    public synchronized boolean silenced(String player, String build) {
        return build != null && build.equals(held.getProperty(player));
    }

    /** Silences one build for this player, and writes it down. */
    public synchronized boolean silence(String player, String build) {
        held.setProperty(player, build);
        return write();
    }

    /** Turns update notices off for this player, and writes it down. */
    public synchronized boolean turnOff(String player) {
        held.setProperty(player, OFF);
        return write();
    }

    /** Turns update notices back on for this player, forgetting any build silenced, and writes it down. */
    public synchronized boolean turnOn(String player) {
        held.remove(player);
        return write();
    }

    private boolean write() {
        if (file == null) return false;
        OutputStream out = null;
        try {
            File folder = file.getParentFile();
            if (folder != null && !folder.isDirectory()) folder.mkdirs();
            out = new FileOutputStream(file);
            held.store(
                out,
                "Who has quieted TRMT: Reimagined's update notice here, by player id: off, or the one build silenced.");
            return true;
        } catch (IOException e) {
            return false;
        } finally {
            if (out != null) try {
                out.close();
            } catch (IOException ignored) {
                // Written already, or not at all; said by the answer.
            }
        }
    }
}
