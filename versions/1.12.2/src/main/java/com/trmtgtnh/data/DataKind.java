package com.trmtgtnh.data;

/**
 * The five kinds of data file, each by the folder (or name) it is found under (W3 spec 2.1).
 *
 * <p>
 * Families, shapes and wearers are the server's facts and looks the client's; a plugin is code, so it comes from a mod
 * jar alone. Which source may give which kind is {@link DataSource.Type#accepts}.
 *
 * <p>
 * Portable: the same file in all eight trees.
 */
public enum DataKind {

    FAMILIES("families", true),
    LOOKS("looks", false),
    SHAPES("shapes", true),
    WEARERS("wearers", true),
    PLUGIN("plugin", false);

    /** The folder under {@code trmtr/} ({@code plugin} is the file {@code trmtr/plugin.json}). */
    public final String folder;

    /** Whether the server decides it, so that a visiting client follows the server's. */
    public final boolean server;

    DataKind(String folder, boolean server) {
        this.folder = folder;
        this.server = server;
    }

    /** The kind whose folder this is, or null for a folder format 1 does not have. */
    public static DataKind byFolder(String folder) {
        for (DataKind kind : values()) {
            if (kind != PLUGIN && kind.folder.equals(folder)) return kind;
        }
        return null;
    }

    /** The key table's name for this kind's entries ({@link DataKeys#of}). */
    public String table() {
        return folder;
    }
}
