package com.trmtgtnh.dataload;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;

import com.trmtgtnh.data.DataFile;
import com.trmtgtnh.data.DataProblems;
import com.trmtgtnh.data.DataSet;
import com.trmtgtnh.data.DataSource;
import com.trmtgtnh.data.DataTable;
import com.trmtgtnh.data.JsonReader;
import com.trmtgtnh.data.Selector;

/**
 * Where 26.3 finds data files, and the read that merges them (W3 spec 2.2, task T4; carried from the 1.16.5 tree's
 * class of the same name in T18).
 *
 * <p>
 * Four sources. A mod's file - its jar, or in a development run its class and resources folders - read directly, on
 * both sides, under {@code assets/<ns>/trmtr/}: each loader hands over its own list of mods with the roots it loaded
 * them from ({@link ModFile}, Forge's mod file list and Fabric Loader's mod containers). A data pack, through the
 * server's resource manager, under {@code data/<ns>/trmtr/}; a resource pack, through the client's, under
 * {@code assets/<ns>/trmtr/} - families, shapes and wearers come from data packs and looks from resource packs, and
 * the data package refuses each kind from the other with one line. And
 * {@code config/trmtr/<ns>/<kind>/}. Selectors take block-state properties and block and entity tags here
 * ({@link #FORMS}), and no metadata.
 *
 * <p>
 * A loader's own packs of its mods' resources are passed over by the packs read ({@link #useModPacks}), since each mod's
 * file is read as its jar already, where its own blocks are its own (W3 spec 2.2, "the owner wins its own blocks in
 * layer 1"); read again as a pack, the same file would be heard a second time in layer 2 and win over every other mod.
 *
 * <p>
 * <strong>Nothing here throws to its caller</strong> (W3 spec 2.3: a bad file never crashes the game). A jar that will
 * not open, a folder that cannot be listed or a file that cannot be read is said once, at WARN, through the session's
 * {@link DataProblems}, and the read goes on without it. Not portable: each tree finds its files through its own
 * loaders.
 */
public final class DataFiles {

    private DataFiles() {}

    /**
     * The selector forms 26.3 reads (FORMAT.md, "Block selectors"): {@code ns:name[half=bottom]}, a block tag
     * {@code #ns:tag} and an entity type tag; no metadata, which 1.13 took away.
     */
    public static final Selector.Forms FORMS = new Selector.Forms(false, true, true, true);

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

    /** A mod's file, as its loader found it, with the roots it reads it from and the mods it holds. */
    public static final class ModFile {

        /** The file's name as {@code /trmt data} shows it: the jar's, or a development run's folder's. */
        public final String name;
        /**
         * Where its resources are read from: a jar's root inside the jar, or each folder of a development run (its
         * classes and its resources).
         */
        public final List<Path> roots;
        /** The mod ids the loader loaded from this file, in its order; one file can hold several. */
        public final List<String> modIds;
        /** Where a loader hands over no root to walk, what it hands over instead ({@link Contents}); else null. */
        public final Contents contents;

        public ModFile(String name, List<Path> roots, List<String> modIds) {
            this(name, roots, null, modIds);
        }

        /** A mod file read through its loader's own contents, where no root inside a jar is to be had. */
        public ModFile(String name, Contents contents, List<String> modIds) {
            this(name, null, contents, modIds);
        }

        private ModFile(String name, List<Path> roots, Contents contents, List<String> modIds) {
            this.name = name == null || name.isEmpty() ? "unknown" : name;
            this.roots = roots == null ? Collections.<Path>emptyList()
                : Collections.unmodifiableList(new ArrayList<Path>(roots));
            this.contents = contents;
            this.modIds = modIds == null || modIds.isEmpty() ? Collections.singletonList("unknown")
                : Collections.unmodifiableList(new ArrayList<String>(modIds));
        }
    }

    /**
     * A mod's file as NeoForge hands it over from FML 11 (26.1): its roots are paths on disk, a jar's own file rather
     * than its inside, so the file is asked rather than walked. {@code JarContents.visitContent}, its
     * {@code containsFile}.
     */
    public interface Contents {

        /** Every file under a folder, in no order, by its whole path from the mod file's root. */
        void visit(String folder, Visitor visitor);

        /** Whether the file holds this path. */
        boolean contains(String path);
    }

    /** One file of a {@link Contents}; {@code open} is good only while this is being called. */
    public interface Visitor {

        void file(String path, Opener open);
    }

    /** A way to read one file of a {@link Contents}. */
    public interface Opener {

        InputStream open() throws IOException;
    }

    /** One data file found in a source, with its bytes, not yet read. */
    public static final class Found {

        public final DataSource source;
        /** From the source's root, forward slashes: {@code assets/<ns>/trmtr/...}, {@code data/...} or config. */
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
     * Every data file in a mod's file, under {@code assets/<ns>/trmtr/}, read from each of its roots, the first root to
     * hold a path winning. Each namespace's files are reported to the mod whose id is that namespace, or the file's
     * first mod, so a refusal names the mod most likely to mend it.
     */
    public static List<Found> fromModFile(ModFile mod, DataProblems problems) {
        List<Found> out = new ArrayList<Found>();
        if (mod == null) return out;
        if (mod.contents != null) return fromContents(mod, problems);
        Set<String> seen = new LinkedHashSet<String>();
        for (Path root : mod.roots) {
            try {
                if (root == null) continue;
                Path assets = root.resolve("assets");
                if (!Files.isDirectory(assets)) continue;
                List<Path> namespaces = sorted(assets, mod, problems);
                for (Path namespace : namespaces) {
                    Path trmtr = namespace.resolve("trmtr");
                    if (!Files.isDirectory(trmtr)) continue;
                    String ns = nameOf(namespace);
                    DataSource source = modSource(mod, ns);
                    List<Found> here = new ArrayList<Found>();
                    walk(trmtr, "assets/" + ns + "/trmtr", 0, source, problems, here);
                    for (Found found : here) if (seen.add(found.path)) out.add(found);
                }
            } catch (RuntimeException unexpected) {
                cannot(problems, DataSource.modJar(mod.name, mod.modIds.get(0)), "look in", unexpected);
            }
        }
        return out;
    }

    /** The same, for a file its loader hands over as {@link Contents}: the same paths, the same order and the same limits. */
    private static List<Found> fromContents(ModFile mod, DataProblems problems) {
        Map<String, Found> found = new TreeMap<String, Found>();
        try {
            mod.contents.visit("assets", (path, open) -> {
                String[] parts = path.split("/");
                // assets/<ns>/trmtr/ and a file under it, no deeper than the walk over a root goes.
                if (parts.length < 4 || parts.length - 4 > MOST_DEPTH || !"trmtr".equals(parts[2])) return;
                if (DataFile.place(path) == null) return;
                DataSource source = modSource(mod, parts[1]);
                InputStream in = null;
                try {
                    in = open.open();
                    byte[] bytes = bounded(in, -1, source, path, problems);
                    if (bytes != null) found.put(path, new Found(source, path, bytes));
                } catch (IOException unread) {
                    cannot(problems, source, "read " + path + " in", unread);
                } finally {
                    close(in);
                }
            });
        } catch (RuntimeException unexpected) {
            cannot(problems, DataSource.modJar(mod.name, mod.modIds.get(0)), "look in", unexpected);
        }
        return new ArrayList<Found>(found.values());
    }

    /**
     * Which of a manager's packs are a loader's own packs of its mods' resources, passed over by {@link #fromPacks};
     * set by each loader as it starts. None until then.
     */
    private static volatile Predicate<Object> modPacks = pack -> false;

    /** Tells the packs read which packs are the loader's own packs of its mods' files. */
    public static void useModPacks(Predicate<Object> isModPack) {
        if (isModPack != null) modPacks = isModPack;
    }

    /**
     * Every data file the packs a resource manager holds give, in the game's own pack order (the later pack wins, as
     * the game's own resources do): with {@code dataPacks}, the server's data packs under {@code data/<ns>/trmtr/};
     * otherwise the client's resource packs under {@code assets/<ns>/trmtr/}. Never throws.
     *
     * <p>
     * The two are read two ways, as on 1.16.5, though {@code listPacks} is no longer marked client-only at this version:
     * the data packs through {@code listResourceStacks}, each file's copies in the game's pack order and each copy's
     * pack known by its id; the resource packs, a client's alone, pack by pack, so a loader's own pack of its mods' files
     * can be passed over by what it is.
     */
    public static List<Found> fromPacks(net.minecraft.server.packs.resources.ResourceManager manager, boolean dataPacks,
        DataProblems problems) {
        if (dataPacks) return fromDataPacks(manager, problems);
        List<Found> out = new ArrayList<Found>();
        if (manager == null) return out;
        net.minecraft.server.packs.PackType type = net.minecraft.server.packs.PackType.CLIENT_RESOURCES;
        String top = "assets/";
        List<net.minecraft.server.packs.PackResources> packs = new ArrayList<net.minecraft.server.packs.PackResources>();
        try {
            manager.listPacks()
                .forEach(packs::add);
        } catch (RuntimeException | LinkageError unlisted) {
            problems.warnOnce(
                "packs|list|" + top,
                DataProblems.PREFIX + "could not list the resource packs to look for data files ("
                    + unlisted.getClass()
                        .getSimpleName()
                    + "); none of theirs was read");
            return out;
        }
        int order = 0;
        for (net.minecraft.server.packs.PackResources pack : packs) {
            order++;
            if (pack == null || modPacks.test(pack)) continue;
            String name;
            try {
                name = pack.packId();
            } catch (RuntimeException | LinkageError unnamed) {
                name = pack.getClass()
                    .getSimpleName();
            }
            DataSource source = DataSource.resourcePack(name, order);
            try {
                Set<String> namespaces = new TreeSet<String>(pack.getNamespaces(type));
                for (String namespace : namespaces) {
                    // The pack hands over each file with the way to open it, and no depth is asked for any more, so
                    // 26.3's zipped packs' depth rule does not apply. DataFile.place still refuses a path too deep.
                    TreeMap<String, net.minecraft.server.packs.resources.IoSupplier<InputStream>> byPath = new TreeMap<String, net.minecraft.server.packs.resources.IoSupplier<InputStream>>();
                    pack.listResources(type, namespace, "trmtr", (each, opener) -> {
                        String path = top + each.getNamespace() + "/" + each.getPath();
                        if (path.endsWith(".json") && DataFile.place(path) != null) byPath.put(path, opener);
                    });
                    for (Map.Entry<String, net.minecraft.server.packs.resources.IoSupplier<InputStream>> each : byPath
                        .entrySet()) {
                        InputStream in = null;
                        try {
                            in = each.getValue()
                                .get();
                            byte[] bytes = bounded(in, -1, source, each.getKey(), problems);
                            if (bytes != null) out.add(new Found(source, each.getKey(), bytes));
                        } catch (IOException unread) {
                            cannot(problems, source, "read " + each.getKey() + " in", unread);
                        } finally {
                            close(in);
                        }
                    }
                }
            } catch (RuntimeException | LinkageError unexpected) {
                cannot(problems, source, "look in", unexpected);
            }
        }
        return out;
    }

    /**
     * The data packs' files under {@code data/<ns>/trmtr/}, through the server's resource manager: every location the
     * packs hold with each pack's copy of it in the game's pack order ({@code listResourceStacks}), each from the pack
     * its id gives. The packs' order is merged from those lists, which each keep it. A mod's own {@code data/} is read
     * here as the data pack its loader makes of it, never as its jar (whose {@code assets/} alone is layer 1), so nothing
     * is heard twice. Never throws.
     */
    static List<Found> fromDataPacks(net.minecraft.server.packs.resources.ResourceManager manager,
        DataProblems problems) {
        List<Found> out = new ArrayList<Found>();
        if (manager == null) return out;
        TreeMap<String, List<net.minecraft.server.packs.resources.Resource>> paths = new TreeMap<String, List<net.minecraft.server.packs.resources.Resource>>();
        try {
            for (Map.Entry<net.minecraft.resources.Identifier, List<net.minecraft.server.packs.resources.Resource>> each : manager
                .listResourceStacks(
                    "trmtr",
                    file -> file.getPath()
                        .endsWith(".json"))
                .entrySet()) {
                String path = "data/" + each.getKey()
                    .getNamespace() + "/"
                    + each.getKey()
                        .getPath();
                if (DataFile.place(path) != null) paths.put(path, each.getValue());
            }
        } catch (RuntimeException | LinkageError unlisted) {
            problems.warnOnce(
                "packs|list|data",
                DataProblems.PREFIX + "could not list the data packs to look for data files ("
                    + unlisted.getClass()
                        .getSimpleName()
                    + "); none of theirs was read");
            return out;
        }
        List<String> order = new ArrayList<String>();
        List<Object[]> read = new ArrayList<Object[]>();
        for (Map.Entry<String, List<net.minecraft.server.packs.resources.Resource>> stack : paths.entrySet()) {
            String path = stack.getKey();
            List<net.minecraft.server.packs.resources.Resource> copies = stack.getValue();
            int last = -1;
            for (net.minecraft.server.packs.resources.Resource copy : copies) {
                String name;
                try {
                    name = copy.sourcePackId();
                } catch (RuntimeException | LinkageError unnamed) {
                    name = "unknown";
                }
                int place = order.indexOf(name);
                if (place < 0) {
                    order.add(last + 1, name);
                    place = last + 1;
                }
                last = place;
                InputStream in = null;
                DataSource source = DataSource.dataPack(name, 0);
                try {
                    in = copy.open();
                    byte[] bytes = bounded(in, -1, source, path, problems);
                    if (bytes != null) read.add(new Object[] { name, path, bytes });
                } catch (IOException | RuntimeException unread) {
                    cannot(problems, source, "read " + path + " in", unread);
                } finally {
                    close(in);
                }
            }
        }
        for (Object[] each : read) {
            String name = (String) each[0];
            out.add(new Found(DataSource.dataPack(name, order.indexOf(name) + 1), (String) each[1], (byte[]) each[2]));
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
            if (root.isDirectory()) walk(root.toPath(), "config/trmtr", 0, source, problems, out);
        } catch (RuntimeException unexpected) {
            cannot(problems, source, "look in", unexpected);
        }
        return out;
    }

    /** A source for a mod's namespace: the mod of the file whose id it is, or the file's first. */
    private static DataSource modSource(ModFile mod, String namespace) {
        String modId = mod.modIds.get(0);
        for (String id : mod.modIds) {
            if (id.toLowerCase(Locale.ROOT)
                .equals(namespace.toLowerCase(Locale.ROOT))) {
                modId = id;
                break;
            }
        }
        return DataSource.modJar(mod.name, modId);
    }

    /** A path's last name, without the slash a path inside a jar keeps on a folder. */
    private static String nameOf(Path path) {
        Path last = path.getFileName();
        String name = last == null ? path.toString() : last.toString();
        while (name.endsWith("/")) name = name.substring(0, name.length() - 1);
        return name;
    }

    /** A folder's children in name order, or none (said once) where it cannot be listed. */
    private static List<Path> sorted(Path folder, ModFile mod, DataProblems problems) {
        List<Path> out = new ArrayList<Path>();
        try (DirectoryStream<Path> children = Files.newDirectoryStream(folder)) {
            for (Path child : children) out.add(child);
        } catch (IOException | RuntimeException unlisted) {
            if (mod != null) cannot(problems, DataSource.modJar(mod.name, mod.modIds.get(0)), "list", unlisted);
            return out;
        }
        out.sort((a, b) -> nameOf(a).compareTo(nameOf(b)));
        return out;
    }

    private static void walk(Path folder, String path, int depth, DataSource source, DataProblems problems,
        List<Found> out) {
        if (depth > MOST_DEPTH) return;
        List<Path> children = new ArrayList<Path>();
        try (DirectoryStream<Path> listed = Files.newDirectoryStream(folder)) {
            for (Path child : listed) children.add(child);
        } catch (IOException | RuntimeException unlisted) {
            problems.warnOnce(
                source.id() + "|list|" + path,
                DataProblems.PREFIX + "could not list "
                    + path
                    + " "
                    + source.where()
                    + " - its data files were not read");
            return;
        }
        children.sort((a, b) -> nameOf(a).compareTo(nameOf(b)));
        for (Path child : children) {
            String at = path + "/" + nameOf(child);
            if (Files.isDirectory(child)) {
                walk(child, at, depth + 1, source, problems, out);
            } else if (DataFile.place(at) != null) {
                InputStream in = null;
                try {
                    long size;
                    try {
                        size = Files.size(child);
                    } catch (IOException unsized) {
                        size = -1;
                    }
                    in = Files.newInputStream(child);
                    byte[] bytes = bounded(in, size, source, at, problems);
                    if (bytes != null) out.add(new Found(source, at, bytes));
                } catch (IOException unread) {
                    cannot(problems, source, "read " + at + " in", unread);
                } finally {
                    close(in);
                }
            }
        }
    }

    /**
     * A file's bytes, or null when it is past {@link #READ_WHOLE_UP_TO}: that is said here, once, and the file is not
     * read. A size not known (-1) is found by reading, still bounded.
     */
    private static byte[] bounded(InputStream in, long size, DataSource source, String path, DataProblems problems)
        throws IOException {
        if (in == null) throw new IOException("no stream");
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

    private static void cannot(DataProblems problems, DataSource source, String what, Throwable why) {
        problems.warnOnce(
            source.id() + "|cannot|" + what,
            DataProblems.PREFIX + "could not "
                + what
                + " "
                + source.where()
                + " to look for data files ("
                + why.getClass()
                    .getSimpleName()
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
            DataSet set = new DataSet(FORMS, problems);
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
