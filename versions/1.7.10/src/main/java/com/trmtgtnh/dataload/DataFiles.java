package com.trmtgtnh.dataload;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.trmtgtnh.data.DataFile;
import com.trmtgtnh.data.DataProblems;
import com.trmtgtnh.data.DataSet;
import com.trmtgtnh.data.DataSource;
import com.trmtgtnh.data.DataTable;
import com.trmtgtnh.data.JsonReader;
import com.trmtgtnh.data.Selector;

/**
 * Where 1.7.10 finds data files, and the read that merges them (W3 spec 2.2, task T4).
 *
 * <p>
 * Three sources. A mod's file - the jar FML loaded it from, or in a development run the class folder and Gradle's
 * resources folder beside it - read directly, on both sides, under {@code assets/<ns>/trmtr/}. A resource pack, zipped
 * or a folder, under the same path; the data package lets it give looks alone and says one line for any other folder.
 * And {@code config/trmtr/<ns>/<kind>/}. 1.7.10 has no data packs, so a jar's {@code data/} is not looked in.
 *
 * <p>
 * Free of the game, so the tests read real jars and folders through it; {@link DataLoad} hands it FML's mod list and
 * the client's pack list. A file is read only when its path is a data file's ({@link DataFile#place}), so a pack's
 * thousand textures cost a listing and nothing more.
 *
 * <p>
 * <strong>Nothing here throws to its caller</strong> (W3 spec 2.3: a bad file never crashes the game). A jar that will
 * not open, a folder that cannot be listed or a file that cannot be read is said once, at WARN, through the session's
 * {@link DataProblems}, and the read goes on without it. Not portable: modern editions find their files through their
 * loaders' own paths, and each tree writes its own.
 */
public final class DataFiles {

    private DataFiles() {}

    /**
     * Past this a file is not read at all, only said to be too big. Between the reader's own limit
     * ({@link JsonReader#MAX_BYTES}, 1 MB) and this, it is read whole so the reader's refusal can give its true size;
     * past it, reading it whole would cost memory for nothing.
     */
    static final long READ_WHOLE_UP_TO = 8L * 1024 * 1024;

    /**
     * How deep a folder under {@code trmtr/} is walked: format 1's deepest path is one folder, and a loop of links
     * ends.
     */
    static final int MOST_DEPTH = 8;

    /** A mod's file, as FML found it, with the mods it holds. */
    public static final class ModFile {

        public final File file;
        /** The mod ids FML loaded from this file, in its order; one file can hold several (a core mod and its mod). */
        public final List<String> modIds;

        public ModFile(File file, List<String> modIds) {
            this.file = file;
            this.modIds = modIds == null || modIds.isEmpty() ? Collections.singletonList("unknown")
                : Collections.unmodifiableList(new ArrayList<String>(modIds));
        }
    }

    /** One data file found in a source, with its bytes, not yet read. */
    public static final class Found {

        public final DataSource source;
        /** From the source's root, forward slashes: {@code assets/<ns>/trmtr/...} or {@code config/trmtr/...}. */
        public final String path;
        public final byte[] bytes;

        Found(DataSource source, String path, byte[] bytes) {
            this.source = source;
            this.path = path;
            this.bytes = bytes;
        }

        /** Whether two finds are the same file with the same bytes, which is how a resource reload is told apart. */
        boolean same(Found other) {
            return other != null && source.id()
                .equals(other.source.id())
                && source.order == other.source.order
                && path.equals(other.path)
                && Arrays.equals(bytes, other.bytes);
        }
    }

    /** What one read came to: the merged table, and each source with how many data files it gave. */
    public static final class Result {

        public final DataTable table;
        /**
         * Each source as {@code /trmt data} names it, in the load order, with the data files read from it (one refused
         * whole is not counted).
         */
        public final Map<String, Integer> sources;

        Result(DataTable table, Map<String, Integer> sources) {
            this.table = table;
            this.sources = Collections.unmodifiableMap(sources);
        }

        /** Nothing read yet. */
        static final Result NONE = new Result(DataTable.empty(), new LinkedHashMap<String, Integer>());
    }

    // ------------------------------------------------------------------
    // Finding the files
    // ------------------------------------------------------------------

    /**
     * A data file a plugin gave from code ({@code Registrar.data}, W3 T7), placed at {@code assets/<ns>/trmtr/<name>}
     * as its own mod's jar would hold it, and read as that mod's.
     */
    public static Found fromPlugin(String modId, String path, String text) {
        return new Found(
            DataSource.plugin(modId),
            path,
            (text == null ? "" : text).getBytes(java.nio.charset.Charset.forName("UTF-8")));
    }

    /**
     * Every data file in a mod's file, under {@code assets/<ns>/trmtr/}. Each namespace's files are reported to the mod
     * of the file whose id is that namespace (FML ids are matched without case, {@code Second} for {@code second}),
     * or the file's first mod, so a refusal names the mod most likely to mend it.
     */
    public static List<Found> fromModFile(ModFile mod, DataProblems problems) {
        List<Found> out = new ArrayList<Found>();
        if (mod == null || mod.file == null) return out;
        try {
            if (mod.file.isDirectory()) {
                Set<String> seen = new LinkedHashSet<String>();
                fromFolder(mod.file, mod, null, 0, problems, out, seen);
                // A development run loads a mod from Gradle's class folder (build/classes/java/main), and its
                // resources sit in build/resources/main beside it, not inside: without this a dev run's own data
                // files would never be read.
                File resources = gradleResources(mod.file);
                if (resources != null) fromFolder(resources, mod, null, 0, problems, out, seen);
            } else if (mod.file.isFile()) {
                fromZip(mod.file, mod, null, 0, problems, out);
            }
        } catch (RuntimeException unexpected) {
            cannot(problems, DataSource.modJar(mod.file.getName(), mod.modIds.get(0)), "look in", unexpected);
        }
        return out;
    }

    /** Every data file in a resource pack, zipped or a folder, by its name and place in the game's pack order. */
    public static List<Found> fromPack(File pack, String name, int order, DataProblems problems) {
        List<Found> out = new ArrayList<Found>();
        if (pack == null) return out;
        String shown = name == null ? pack.getName() : name;
        try {
            if (pack.isDirectory()) fromFolder(pack, null, shown, order, problems, out, new LinkedHashSet<String>());
            else if (pack.isFile()) fromZip(pack, null, shown, order, problems, out);
        } catch (RuntimeException unexpected) {
            cannot(problems, DataSource.resourcePack(shown, order), "look in", unexpected);
        }
        return out;
    }

    /**
     * Every file under {@code <config>/trmtr/}, as {@code config/trmtr/<ns>/<kind>/<name>.json}. Nothing there, nothing
     * said.
     */
    public static List<Found> fromConfig(File configFolder, DataProblems problems) {
        List<Found> out = new ArrayList<Found>();
        DataSource source = DataSource.config("config/trmtr");
        try {
            File root = new File(configFolder, "trmtr");
            if (root.isDirectory()) walk(root, "config/trmtr", 0, source, problems, out);
        } catch (RuntimeException unexpected) {
            cannot(problems, source, "look in", unexpected);
        }
        return out;
    }

    private static File gradleResources(File folder) {
        File java = folder.getParentFile();
        File classes = java == null ? null : java.getParentFile();
        if (!"main".equals(folder.getName()) || java == null
            || !"java".equals(java.getName())
            || classes == null
            || !"classes".equals(classes.getName())) return null;
        File resources = new File(new File(classes.getParentFile(), "resources"), "main");
        return resources.isDirectory() ? resources : null;
    }

    /** A source for a mod's namespace: the mod of the file whose id it is, or the file's first. */
    private static DataSource modSource(File file, ModFile mod, String namespace) {
        String modId = mod.modIds.get(0);
        for (String id : mod.modIds) {
            if (id.toLowerCase(Locale.ROOT)
                .equals(namespace.toLowerCase(Locale.ROOT))) {
                modId = id;
                break;
            }
        }
        return DataSource.modJar(file.getName(), modId);
    }

    private static DataSource sourceFor(File file, ModFile mod, String packName, int order, String namespace) {
        return mod != null ? modSource(file, mod, namespace) : DataSource.resourcePack(packName, order);
    }

    /** {@code assets/<ns>/trmtr/} in a folder: each namespace's {@code trmtr} folder, walked. */
    private static void fromFolder(File root, ModFile mod, String packName, int order, DataProblems problems,
        List<Found> out, Set<String> seen) {
        File[] namespaces = new File(root, "assets").listFiles();
        if (namespaces == null) return;
        Arrays.sort(namespaces);
        for (File namespace : namespaces) {
            File trmtr = new File(namespace, "trmtr");
            if (!trmtr.isDirectory()) continue;
            DataSource source = sourceFor(root, mod, packName, order, namespace.getName());
            List<Found> here = new ArrayList<Found>();
            walk(trmtr, "assets/" + namespace.getName() + "/trmtr", 0, source, problems, here);
            for (Found found : here) if (seen.add(found.path)) out.add(found);
        }
    }

    private static void walk(File folder, String path, int depth, DataSource source, DataProblems problems,
        List<Found> out) {
        if (depth > MOST_DEPTH) return;
        File[] children = folder.listFiles();
        if (children == null) {
            problems.warnOnce(
                source.id() + "|list|" + path,
                DataProblems.PREFIX + "could not list "
                    + path
                    + " "
                    + source.where()
                    + " - its data files were not read");
            return;
        }
        Arrays.sort(children);
        for (File child : children) {
            String at = path + "/" + child.getName();
            if (child.isDirectory()) {
                walk(child, at, depth + 1, source, problems, out);
            } else if (DataFile.place(at) != null) {
                FileInputStream in = null;
                try {
                    in = new FileInputStream(child);
                    byte[] bytes = bounded(in, child.length(), source, at, problems);
                    if (bytes != null) out.add(new Found(source, at, bytes));
                } catch (IOException unread) {
                    cannot(problems, source, "read " + at + " in", unread);
                } finally {
                    close(in);
                }
            }
        }
    }

    /** {@code assets/<ns>/trmtr/} in a zip: a jar or a zipped pack. */
    private static void fromZip(File file, ModFile mod, String packName, int order, DataProblems problems,
        List<Found> out) {
        ZipFile zip;
        try {
            zip = new ZipFile(file);
        } catch (IOException unopened) {
            DataSource source = mod != null ? DataSource.modJar(file.getName(), mod.modIds.get(0))
                : DataSource.resourcePack(packName, order);
            cannot(problems, source, "open", unopened);
            return;
        }
        try {
            TreeMap<String, ZipEntry> data = new TreeMap<String, ZipEntry>();
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName()
                    .replace('\\', '/');
                if (entry.isDirectory() || !name.startsWith("assets/") || !name.contains("/trmtr/")) continue;
                if (DataFile.place(name) == null) continue;
                data.put(name, entry);
            }
            for (Map.Entry<String, ZipEntry> each : data.entrySet()) {
                String name = each.getKey();
                String namespace = name.substring("assets/".length(), name.indexOf('/', "assets/".length()));
                DataSource source = sourceFor(file, mod, packName, order, namespace);
                InputStream in = null;
                try {
                    in = zip.getInputStream(each.getValue());
                    byte[] bytes = bounded(
                        in,
                        each.getValue()
                            .getSize(),
                        source,
                        name,
                        problems);
                    if (bytes != null) out.add(new Found(source, name, bytes));
                } catch (IOException unread) {
                    cannot(problems, source, "read " + name + " in", unread);
                } finally {
                    close(in);
                }
            }
        } catch (RuntimeException unexpected) {
            DataSource source = mod != null ? DataSource.modJar(file.getName(), mod.modIds.get(0))
                : DataSource.resourcePack(packName, order);
            cannot(problems, source, "read", unexpected);
        } finally {
            try {
                zip.close();
            } catch (IOException ignored) {
                // Nothing was written; a jar that will not close is still read.
            }
        }
    }

    /**
     * A file's bytes, or null when it is past {@link #READ_WHOLE_UP_TO}: that is said here, once, and the file is not
     * read. A size the zip does not know (-1) is found by reading, still bounded.
     */
    private static byte[] bounded(InputStream in, long size, DataSource source, String path, DataProblems problems)
        throws IOException {
        if (size > READ_WHOLE_UP_TO) {
            tooBig(problems, source, path, size);
            return null;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(size > 0 ? (int) size : 4096);
        byte[] buffer = new byte[8192];
        long total = 0;
        for (int n; (n = in.read(buffer)) > 0;) {
            total += n;
            if (total > READ_WHOLE_UP_TO) {
                tooBig(problems, source, path, -1);
                return null;
            }
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    private static void tooBig(DataProblems problems, DataSource source, String path, long size) {
        problems.warnOnce(
            source.id() + "|size|" + path,
            DataProblems.PREFIX + "refused "
                + path
                + " "
                + source.where()
                + " - the file is "
                + (size > 0 ? size + " bytes" : "over " + READ_WHOLE_UP_TO + " bytes")
                + ", over the 1 MB a data file may be - "
                + source.reportTo());
    }

    private static void cannot(DataProblems problems, DataSource source, String what, Exception why) {
        problems.warnOnce(
            source.id() + "|cannot|" + what,
            DataProblems.PREFIX + "could not "
                + what
                + " "
                + source.where()
                + " to look for data files ("
                + why.getClass()
                    .getSimpleName()
                + (why.getMessage() == null ? "" : ": " + why.getMessage())
                + "); the rest was read");
    }

    private static void close(InputStream in) {
        if (in == null) return;
        try {
            in.close();
        } catch (IOException ignored) {
            // Read already; nothing to keep.
        }
    }

    // ------------------------------------------------------------------
    // The read
    // ------------------------------------------------------------------

    /**
     * Reads every file found, in the load order the data package sorts them into, and merges them into one table.
     * Each read is a new table, published whole by the caller. Never throws.
     */
    public static Result read(List<Found> files, DataProblems problems) {
        try {
            DataSet set = new DataSet(Selector.Forms.LEGACY, problems);
            // Each source in the load order: layer, then a pack's place in the pack order, then its name.
            TreeMap<String, String> order = new TreeMap<String, String>();
            Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
            Set<String> taken = new LinkedHashSet<String>();
            for (Found found : files) {
                if (!set.add(found.source, found.path, found.bytes)) continue;
                String label = label(found.source);
                count(counts, label, 1);
                taken.add(found.source.id() + "|" + found.path);
                order.put(
                    found.source.layer() + "|" + String.format(Locale.ROOT, "%08d", found.source.order) + "|" + label,
                    label);
            }
            DataTable table = set.read();
            // A source's count is the files read from it: one refused or skipped whole is listed with the refusals
            // instead, so the two never add up to more files than the source has.
            for (DataProblems.Problem problem : table.problems()) {
                boolean whole = problem.outcome == DataProblems.Problem.Outcome.FILE_REFUSED
                    || problem.outcome == DataProblems.Problem.Outcome.SKIPPED;
                if (whole && taken.remove(problem.source.id() + "|" + problem.path)) {
                    count(counts, label(problem.source), -1);
                }
            }
            Map<String, Integer> sources = new LinkedHashMap<String, Integer>();
            for (String label : order.values()) sources.put(label, counts.get(label));
            return new Result(table, sources);
        } catch (RuntimeException unexpected) {
            problems.sink()
                .warn(
                    DataProblems.PREFIX + "could not merge the data files ("
                        + unexpected.getClass()
                            .getName()
                        + "); none is used until the next read - report this to TRMTR");
            return Result.NONE;
        }
    }

    private static void count(Map<String, Integer> counts, String label, int by) {
        Integer was = counts.get(label);
        counts.put(label, (was == null ? 0 : was) + by);
    }

    /** A source as {@code /trmt data} lists it: "example-1.0.jar (mod example)", "resource pack Faithful". */
    static String label(DataSource source) {
        switch (source.type) {
            case MOD_JAR:
                return source.name + " (mod " + source.modId + ")";
            case PLUGIN:
                return "the plugin of mod " + source.modId;
            case RESOURCE_PACK:
                return "resource pack " + source.name;
            case DATA_PACK:
                return "data pack " + source.name;
            default:
                return "the config folder (" + source.name + ")";
        }
    }
}
