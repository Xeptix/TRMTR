package com.trmtgtnh.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * One read of every data file, from every source, into one merged {@link DataTable} (W3 spec 2.2).
 *
 * <p>
 * The adapter hands over each file it finds - a mod jar's, a pack's, the config folder's, a plugin's - in any order,
 * and {@link #read} sorts them into the load order, reads each inside its own catch, and merges them. Each read builds
 * a new table and the adapter publishes it whole, as {@code SurfaceRegistry.resolve} publishes its own.
 *
 * <p>
 * The order (FORMAT.md, "Sources and load order"): layer by layer, mod jars (and plugins' {@code Registrar.data},
 * which count as their mod's jar) before packs before the config folder; inside the mod-jar layer by namespace and
 * then path; packs in the game's pack order, the later winning; config files by path. The merge's own rules - key by
 * key, the owner winning its blocks among the jars, a conflict inside a layer said once - are {@link DataTable}'s.
 *
 * <p>
 * Portable: the same file in all eight trees. Nothing here throws to its caller.
 */
public final class DataSet {

    private static final class Pending {

        final DataSource source;
        final String path;
        final DataFile.Place place;
        final byte[] bytes;
        final String text;
        final int added;

        Pending(DataSource source, String path, DataFile.Place place, byte[] bytes, String text, int added) {
            this.source = source;
            this.path = path;
            this.place = place;
            this.bytes = bytes;
            this.text = text;
            this.added = added;
        }

        String namespaceOrder() {
            return place.namespace;
        }
    }

    private final Selector.Forms forms;
    private final DataProblems problems;
    private final List<Pending> pending = new ArrayList<Pending>();
    private final List<DataProblems.Problem> refusedHere = new ArrayList<DataProblems.Problem>();
    private int ignoredFolders;

    /**
     * @param forms    the selector forms this edition reads
     * @param problems the session's said-once log, kept by the adapter across reads
     */
    public DataSet(Selector.Forms forms, DataProblems problems) {
        this.forms = forms;
        this.problems = problems;
    }

    /**
     * Offers a file found in a source, by its path from the source's root (forward slashes). Returns whether it is a
     * data file this read takes: a path that is no data file is not one, and nothing is said; a kind its source may
     * not give is refused (a {@code plugin.json} outside a mod jar) or ignored with one line (a pack's folder of the
     * other side's kind). Never throws.
     */
    public boolean add(DataSource source, String path, byte[] bytes) {
        return offer(source, path, bytes, null);
    }

    /** As {@link #add(DataSource, String, byte[])}, for a file given as text (a plugin's {@code Registrar.data}). */
    public boolean addText(DataSource source, String path, String text) {
        return offer(source, path, null, text == null ? "" : text);
    }

    private boolean offer(DataSource source, String path, byte[] bytes, String text) {
        try {
            if (source == null || path == null) return false;
            String clean = path.replace('\\', '/');
            DataFile.Place place = DataFile.place(clean);
            if (place == null) return false;
            if (place.kind == null) {
                ignoredFolders++;
                problems.warnOnce(
                    source.id() + "|folder|" + place.namespace + "|" + place.folder,
                    DataProblems.PREFIX + "ignored the folder trmtr/"
                        + place.folder
                        + " of namespace "
                        + place.namespace
                        + " "
                        + source.where()
                        + " - format 1 has no such kind; a newer TRMTR may read it");
                return false;
            }
            if (!source.type.accepts(place.kind)) {
                if (place.kind == DataKind.PLUGIN) {
                    DataProblems.Problem problem = new DataProblems.Problem(
                        DataProblems.Problem.Outcome.FILE_REFUSED,
                        source,
                        clean,
                        0,
                        0,
                        0,
                        "a plugin.json is read from mod jars only, since only a mod's jar carries code",
                        source.reportTo());
                    refusedHere.add(problem);
                    problems.say(problem);
                } else {
                    ignoredFolders++;
                    String side = place.kind.server ? "the server's (a data pack or a mod gives them)"
                        : "the client's (a resource pack or a mod gives them)";
                    problems.warnOnce(
                        source.id() + "|side|" + place.namespace + "|" + place.kind.folder,
                        DataProblems.PREFIX + "ignored the folder trmtr/"
                            + place.kind.folder
                            + " of namespace "
                            + place.namespace
                            + " "
                            + source.where()
                            + " - "
                            + place.kind.folder
                            + " are "
                            + side
                            + " - "
                            + source.reportTo());
                }
                return false;
            }
            pending.add(new Pending(source, clean, place, bytes, text, pending.size()));
            return true;
        } catch (RuntimeException unexpected) {
            problems.sink()
                .warn(
                    DataProblems.PREFIX + "could not take "
                        + path
                        + " "
                        + (source == null ? "" : source.where())
                        + " ("
                        + unexpected.getClass()
                            .getName()
                        + ") - report this to TRMTR");
            return false;
        }
    }

    /** The load order: layer, then namespace and path among jars, pack order and path among packs, path in config. */
    static final Comparator<Pending> LOAD_ORDER = new Comparator<Pending>() {

        @Override
        public int compare(Pending a, Pending b) {
            int by = Integer.compare(a.source.layer(), b.source.layer());
            if (by != 0) return by;
            switch (a.source.type) {
                case MOD_JAR:
                case PLUGIN:
                    by = a.namespaceOrder()
                        .compareTo(b.namespaceOrder());
                    if (by != 0) return by;
                    // A plugin's data counts as its mod's jar, after the jar's own files.
                    by = Integer.compare(a.source.type.ordinal(), b.source.type.ordinal());
                    if (by != 0) return by;
                    break;
                case RESOURCE_PACK:
                case DATA_PACK:
                    by = Integer.compare(a.source.order, b.source.order);
                    if (by != 0) return by;
                    break;
                default:
                    break;
            }
            by = a.path.compareTo(b.path);
            if (by != 0) return by;
            return Integer.compare(a.added, b.added);
        }
    };

    /** Reads every file offered, in the load order, and merges them. Never throws. */
    public DataTable read() {
        List<Pending> ordered = new ArrayList<Pending>(pending);
        Collections.sort(ordered, LOAD_ORDER);
        DataTable.Builder table = new DataTable.Builder(problems);
        for (DataProblems.Problem refused : refusedHere) table.refusedOutright(refused);
        table.ignoredFolders(ignoredFolders);
        for (Pending file : ordered) {
            DataFile.Read read;
            try {
                JsonReader.Result json = file.bytes != null ? JsonReader.read(file.bytes) : JsonReader.read(file.text);
                read = DataFile.read(file.source, file.path, file.place, json, forms, problems);
            } catch (RuntimeException unexpected) {
                problems.sink()
                    .warn(
                        DataProblems.PREFIX + "could not read "
                            + file.path
                            + " "
                            + file.source.where()
                            + " ("
                            + unexpected.getClass()
                                .getName()
                            + ") - report this to TRMTR");
                continue;
            }
            table.add(file.source, file.path, file.place, read);
        }
        return table.build();
    }
}
