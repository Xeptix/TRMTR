package com.trmtgtnh.config;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * The original mod's settings file, {@code trmt.json}, looked for beside this mod's own (0.9.229 W5 T9;
 * docs/goals/0.9.229-w5-spec.md, 2.8): "checked when the settings screen opens and on {@code /trmt reload}". The screen
 * offers "Use your TRMT settings" only where it is found; {@code /trmt reload} looks again and says so where it is.
 */
public final class OriginalFile {

    private OriginalFile() {}

    /** The original's file, in the config folder beside this mod's; its {@code trmt-client.json} is not read. */
    public static final String NAME = "trmt.json";

    /** What the last look found, or null where it found nothing. */
    private static volatile String found;

    /**
     * Looks for the file in this folder and keeps what it found: its text, or null where there is none. A file that is
     * there and cannot be read answers empty text, which the reader refuses, so the row says so rather than hiding.
     */
    public static String look(File folder) {
        File file = new File(folder, NAME);
        String text;
        if (!file.isFile()) {
            text = null;
        } else {
            try {
                text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            } catch (IOException unreadable) {
                text = "";
            }
        }
        found = text;
        return text;
    }

    /** What the last look found: the file's text, empty where it could not be read, null where there was none. */
    public static String found() {
        return found;
    }
}
