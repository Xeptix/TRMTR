package com.trmtgtnh.data;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The merged data of one read: every entry from every source, in the order that decides what wins, with where each
 * value came from (W3 spec 2.2; FORMAT.md, "Sources and load order").
 *
 * <p>
 * <strong>The merge.</strong> A later entry overrides an earlier one key by key, not file by file: a pack that sets
 * only a block's {@code look} keeps the {@code strength} the mod gave it. Later means, in order: a higher layer (mod
 * jars 1, packs 2, the config folder 3); inside the mod-jar layer, an entry from the block's own namespace after every
 * other jar's, whatever the read order, so that a mod describing itself beats a mod describing it (TRMTR's own shipped
 * files, under {@code trmtgtnh}, give way so); and otherwise the read order {@link DataSet} sorted.
 *
 * <p>
 * <strong>Kept per entry, merged per state.</strong> Which entries take a block state is a question of its id, its
 * metadata or properties and its tags, and only the adapter knows a state's; so the table keeps every entry in rank
 * order and merges the ones that take a state when it is asked ({@link #family}, {@link #look}, {@link #shape},
 * {@link #wearer}). {@link #targets} is the same merge per selector as written, for {@code /trmt data} and the
 * fingerprint.
 *
 * <p>
 * <strong>Conflicts.</strong> Two sources of one layer setting one key of one selector to different values is said
 * once - "example:loam: family dirt from mod a, grass from mod b; using grass (mod b)". A higher layer overriding a
 * lower one is the point of the layers and is not said.
 *
 * <p>
 * <strong>The player beats data</strong>, but not here: the player's lists are layer 4, applied by the adapter over
 * what this table answers ({@link PlayerLayer}), and nothing here writes into them.
 *
 * <p>
 * Portable: the same file in all eight trees.
 */
public final class DataTable {

    /** One value and the entry that set it. */
    public static final class Fact {

        public final Object value;
        public final DataEntry from;

        Fact(Object value, DataEntry from) {
            this.value = value;
            this.from = from;
        }

        @Override
        public String toString() {
            return render(value) + " (" + from.origin() + ")";
        }
    }

    /** The merged values for one state or one selector: each key's last value in rank order, with its origin. */
    public static final class Facts {

        private final Map<String, Fact> facts;

        Facts(Map<String, Fact> facts) {
            this.facts = Collections.unmodifiableMap(facts);
        }

        public boolean isEmpty() {
            return facts.isEmpty();
        }

        public Fact get(String key) {
            return facts.get(key);
        }

        /** A key's value, or null when no entry set it. */
        public Object value(String key) {
            Fact fact = facts.get(key);
            return fact == null ? null : fact.value;
        }

        public Map<String, Fact> all() {
            return facts;
        }

        @Override
        public String toString() {
            return facts.toString();
        }
    }

    /** A {@code plugin.json} read from a mod's jar. */
    public static final class Plugin {

        public final String className;
        public final DataSource source;
        public final String path;
        public final String namespace;

        Plugin(String className, DataSource source, String path, String namespace) {
            this.className = className;
            this.source = source;
            this.path = path;
            this.namespace = namespace;
        }
    }

    /** One selector of one entry, placed in the merge's order. */
    static final class Contribution {

        final DataEntry entry;
        final Selector selector;
        final boolean owner;
        final int seq;
        final String target;

        Contribution(DataEntry entry, Selector selector, boolean owner, int seq) {
            this.entry = entry;
            this.selector = selector;
            this.owner = owner;
            this.seq = seq;
            this.target = targetOf(entry, selector);
        }
    }

    /** Rank: layer, then (among jars) others' entries before the owner's, then read order. Later wins. */
    static final Comparator<Contribution> RANK = new Comparator<Contribution>() {

        @Override
        public int compare(Contribution a, Contribution b) {
            int by = Integer.compare(a.entry.source.layer(), b.entry.source.layer());
            if (by != 0) return by;
            by = Boolean.compare(a.owner, b.owner);
            if (by != 0) return by;
            return Integer.compare(a.seq, b.seq);
        }
    };

    private final Map<DataKind, List<Contribution>> ranked;
    private final Map<DataKind, Map<String, List<Contribution>>> byId;
    private final Map<DataKind, List<Contribution>> byTag;
    private final Map<DataKind, Map<String, Facts>> targets;
    private final List<Plugin> plugins;
    private final List<DataProblems.Problem> problems;
    private final List<String> conflicts;
    private final long fingerprint;

    private final int filesRead;
    private final int filesRefused;
    private final int filesSkipped;
    private final int entriesRefused;
    private final int unreadable;
    private final int ignoredFolders;
    private final int mods;
    private final int packs;
    private final boolean config;

    private DataTable(Builder b) {
        ranked = new EnumMap<DataKind, List<Contribution>>(DataKind.class);
        byId = new EnumMap<DataKind, Map<String, List<Contribution>>>(DataKind.class);
        byTag = new EnumMap<DataKind, List<Contribution>>(DataKind.class);
        targets = new EnumMap<DataKind, Map<String, Facts>>(DataKind.class);
        List<String> said = new ArrayList<String>();
        for (DataKind kind : DataKind.values()) {
            if (kind == DataKind.PLUGIN) continue;
            List<Contribution> list = new ArrayList<Contribution>();
            for (Contribution c : b.contributions) if (c.entry.kind == kind) list.add(c);
            Collections.sort(list, RANK);
            ranked.put(kind, Collections.unmodifiableList(list));
            Map<String, List<Contribution>> ids = new HashMap<String, List<Contribution>>();
            List<Contribution> tags = new ArrayList<Contribution>();
            for (Contribution c : list) {
                if (c.selector.tag) {
                    tags.add(c);
                    continue;
                }
                List<Contribution> same = ids.get(c.selector.id);
                if (same == null) {
                    same = new ArrayList<Contribution>();
                    ids.put(c.selector.id, same);
                }
                same.add(c);
            }
            byId.put(kind, ids);
            byTag.put(kind, tags);
            targets.put(kind, mergeTargets(list, b.problems, said));
        }
        plugins = Collections.unmodifiableList(b.plugins);
        problems = Collections.unmodifiableList(b.allProblems);
        conflicts = Collections.unmodifiableList(said);
        filesRead = b.filesRead;
        filesRefused = b.filesRefused;
        filesSkipped = b.filesSkipped;
        entriesRefused = b.entriesRefused;
        unreadable = b.unreadable;
        ignoredFolders = b.ignoredFolders;
        mods = b.mods.size();
        packs = b.packs.size();
        config = b.config;
        fingerprint = fingerprintOf(ranked);
    }

    /** Builds a table from the files of one read, in the load order. */
    static final class Builder {

        final DataProblems problems;
        final List<Contribution> contributions = new ArrayList<Contribution>();
        final List<Plugin> plugins = new ArrayList<Plugin>();
        final List<DataProblems.Problem> allProblems = new ArrayList<DataProblems.Problem>();
        final Set<String> mods = new LinkedHashSet<String>();
        final Set<String> packs = new LinkedHashSet<String>();
        boolean config;
        int seq;
        int filesRead;
        int filesRefused;
        int filesSkipped;
        int entriesRefused;
        int unreadable;
        int ignoredFolders;

        Builder(DataProblems problems) {
            this.problems = problems;
        }

        void refusedOutright(DataProblems.Problem problem) {
            allProblems.add(problem);
            filesRefused++;
        }

        void ignoredFolders(int count) {
            ignoredFolders += count;
        }

        void add(DataSource source, String path, DataFile.Place place, DataFile.Read read) {
            allProblems.addAll(read.problems);
            entriesRefused += read.entriesRefused;
            unreadable += read.unreadable;
            if (read.refused) {
                filesRefused++;
                return;
            }
            if (read.skipped) {
                filesSkipped++;
                return;
            }
            filesRead++;
            switch (source.type) {
                case MOD_JAR:
                case PLUGIN:
                    mods.add(source.modId == null ? source.name : source.modId);
                    break;
                case RESOURCE_PACK:
                case DATA_PACK:
                    packs.add(source.name);
                    break;
                default:
                    config = true;
                    break;
            }
            if (read.pluginClass != null) {
                plugins.add(new Plugin(read.pluginClass, source, path, place.namespace));
            }
            for (DataEntry entry : read.entries) {
                for (Selector selector : entry.selectors) {
                    boolean owner = source.layer() == 1 && selector.namespace.equals(entry.namespace);
                    contributions.add(new Contribution(entry, selector, owner, seq++));
                }
            }
        }

        DataTable build() {
            return new DataTable(this);
        }
    }

    /** An empty table: no data read. */
    public static DataTable empty() {
        return new Builder(new DataProblems(DataProblems.QUIET)).build();
    }

    // ---- what a block state, an entity or a selector gets ----

    /** The families facts for one block state ({@code family}, {@code resistance}, {@code repairWith}, ...). */
    public Facts family(String blockId, int meta, Map<String, String> state, Collection<String> tags) {
        return merge(DataKind.FAMILIES, blockId, meta, state, tags);
    }

    /** The looks facts for one block state. */
    public Facts look(String blockId, int meta, Map<String, String> state, Collection<String> tags) {
        return merge(DataKind.LOOKS, blockId, meta, state, tags);
    }

    /** The shapes facts for one block state, the entries' own {@code meta} and {@code state} included in the match. */
    public Facts shape(String blockId, int meta, Map<String, String> state, Collection<String> tags) {
        return merge(DataKind.SHAPES, blockId, meta, state, tags);
    }

    /** The wearers facts for one entity, by its id and (where the edition has them) its tags. */
    public Facts wearer(String entityId, Collection<String> tags) {
        Map<String, Fact> out = new LinkedHashMap<String, Fact>();
        for (Contribution c : candidates(DataKind.WEARERS, entityId)) {
            if (c.selector.matchesEntity(entityId, tags)) apply(out, c);
        }
        return new Facts(out);
    }

    private Facts merge(DataKind kind, String blockId, int meta, Map<String, String> state, Collection<String> tags) {
        Map<String, Fact> out = new LinkedHashMap<String, Fact>();
        for (Contribution c : candidates(kind, blockId)) {
            if (!c.selector.matches(blockId, meta, state, tags)) continue;
            if (kind == DataKind.SHAPES && !qualifiersTake(c.entry, meta, state)) continue;
            apply(out, c);
        }
        return new Facts(out);
    }

    private static boolean qualifiersTake(DataEntry entry, int meta, Map<String, String> state) {
        if (entry.meta != Selector.ANY_META && entry.meta != meta) return false;
        for (Map.Entry<String, String> wanted : entry.state.entrySet()) {
            if (state == null || !wanted.getValue()
                .equals(state.get(wanted.getKey()))) return false;
        }
        return true;
    }

    /** The contributions that might take an id, in rank order: those naming it, and every tag's. */
    private List<Contribution> candidates(DataKind kind, String id) {
        List<Contribution> named = byId.get(kind)
            .get(id);
        List<Contribution> tags = byTag.get(kind);
        if (tags.isEmpty()) return named == null ? Collections.<Contribution>emptyList() : named;
        List<Contribution> out = new ArrayList<Contribution>(tags);
        if (named != null) out.addAll(named);
        Collections.sort(out, RANK);
        return out;
    }

    private static void apply(Map<String, Fact> into, Contribution c) {
        for (Map.Entry<String, Object> value : c.entry.values.entrySet()) {
            into.put(value.getKey(), new Fact(value.getValue(), c.entry));
        }
    }

    /**
     * Every selector as written (with a shapes entry's {@code meta} and {@code state}), each with its merged facts:
     * what {@code /trmt data} shows for a named block, and where each of its values came from.
     */
    public Map<String, Facts> targets(DataKind kind) {
        Map<String, Facts> found = targets.get(kind);
        return found == null ? Collections.<String, Facts>emptyMap() : found;
    }

    /** Every block id (not tag) the files name for a kind, for the adapter to resolve and count those not installed. */
    public Set<String> namedIds(DataKind kind) {
        Map<String, List<Contribution>> ids = byId.get(kind);
        return ids == null ? Collections.<String>emptySet() : Collections.unmodifiableSet(ids.keySet());
    }

    /** Every tag the files name for a kind, without the mark. */
    public Set<String> namedTags(DataKind kind) {
        Set<String> out = new LinkedHashSet<String>();
        List<Contribution> tags = byTag.get(kind);
        if (tags != null) for (Contribution c : tags) out.add(c.selector.id);
        return out;
    }

    private static Map<String, Facts> mergeTargets(List<Contribution> ranked, DataProblems problems,
        List<String> said) {
        Map<String, Map<String, Fact>> building = new TreeMap<String, Map<String, Fact>>();
        for (Contribution c : ranked) {
            Map<String, Fact> facts = building.get(c.target);
            if (facts == null) {
                facts = new LinkedHashMap<String, Fact>();
                building.put(c.target, facts);
            }
            for (Map.Entry<String, Object> value : c.entry.values.entrySet()) {
                Fact before = facts.get(value.getKey());
                Fact now = new Fact(value.getValue(), c.entry);
                if (before != null && before.from.source.layer() == c.entry.source.layer()
                    && !before.from.source.id()
                        .equals(c.entry.source.id())
                    && !render(before.value).equals(render(now.value))) {
                    String line = DataProblems.PREFIX + c.target
                        + ": "
                        + value.getKey()
                        + " "
                        + render(before.value)
                        + " from "
                        + before.from.source.who()
                        + ", "
                        + render(now.value)
                        + " from "
                        + c.entry.source.who()
                        + "; using "
                        + render(now.value)
                        + " ("
                        + c.entry.source.who()
                        + (c.owner ? ", whose block it is" : "")
                        + ")";
                    said.add(line);
                    problems.warnOnce("conflict|" + line, line);
                }
                facts.put(value.getKey(), now);
            }
        }
        Map<String, Facts> out = new LinkedHashMap<String, Facts>();
        for (Map.Entry<String, Map<String, Fact>> each : building.entrySet()) {
            out.put(each.getKey(), new Facts(each.getValue()));
        }
        return Collections.unmodifiableMap(out);
    }

    static String targetOf(DataEntry entry, Selector selector) {
        StringBuilder out = new StringBuilder(selector.canonical());
        if (entry.kind == DataKind.SHAPES) {
            if (entry.meta != Selector.ANY_META) out.append(" meta ")
                .append(entry.meta);
            if (!entry.state.isEmpty()) out.append(" state ")
                .append(render(entry.state));
        }
        return out.toString();
    }

    /** A value as a line shows it, and as the fingerprint and the conflict check compare it. */
    static String render(Object value) {
        if (value == null) return "null";
        if (value instanceof Selector) return ((Selector) value).canonical();
        if (value instanceof int[]) {
            StringBuilder out = new StringBuilder("[");
            int[] box = (int[]) value;
            for (int i = 0; i < box.length; i++) {
                if (i > 0) out.append(", ");
                out.append(box[i]);
            }
            return out.append(']')
                .toString();
        }
        if (value instanceof List) {
            StringBuilder out = new StringBuilder("[");
            boolean first = true;
            for (Object each : (List<?>) value) {
                if (!first) out.append(", ");
                out.append(render(each));
                first = false;
            }
            return out.append(']')
                .toString();
        }
        if (value instanceof Map) {
            StringBuilder out = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> each : new TreeMap<Object, Object>((Map<?, ?>) value).entrySet()) {
                if (!first) out.append(", ");
                out.append(each.getKey())
                    .append('=')
                    .append(render(each.getValue()));
                first = false;
            }
            return out.append('}')
                .toString();
        }
        return String.valueOf(value);
    }

    // ---- the fingerprint ----

    /**
     * A fingerprint of the server's data - families, shapes and wearers, never looks, which are each client's own -
     * folded into {@code SurfaceRegistry}'s, so that a client whose mods or packs give it other data uses the server's
     * table for the visit (W3 spec 2.2). Values and their order only: which jar or pack a value came from is not in
     * it, so two sides with the same data agree whatever their files are called. Nought for no server data.
     */
    public long fingerprint() {
        return fingerprint;
    }

    private static long fingerprintOf(Map<DataKind, List<Contribution>> ranked) {
        StringBuilder text = new StringBuilder();
        boolean any = false;
        for (DataKind kind : DataKind.values()) {
            if (!kind.server || kind == DataKind.PLUGIN) continue;
            List<Contribution> list = ranked.get(kind);
            if (list == null) continue;
            token(text, kind.folder);
            token(text, String.valueOf(list.size()));
            for (Contribution c : list) {
                any = true;
                token(text, c.target);
                token(text, String.valueOf(c.entry.values.size()));
                for (Map.Entry<String, Object> value : c.entry.values.entrySet()) {
                    token(text, value.getKey());
                    token(text, render(value.getValue()));
                }
            }
        }
        if (!any) return 0L;
        long hash = 0xcbf29ce484222325L;
        for (byte b : text.toString()
            .getBytes(Charset.forName("UTF-8"))) {
            hash ^= b & 0xff;
            hash *= 0x100000001b3L;
        }
        return hash == 0L ? 1L : hash;
    }

    private static void token(StringBuilder into, String text) {
        into.append(text.length())
            .append(':')
            .append(text)
            .append(';');
    }

    // ---- what the read came to ----

    public List<Plugin> plugins() {
        return plugins;
    }

    /** Every problem of this read, said or already said this session: the refusals {@code /trmt data} shows. */
    public List<DataProblems.Problem> problems() {
        return problems;
    }

    /** Every conflict inside a layer, as said. */
    public List<String> conflicts() {
        return conflicts;
    }

    public int filesRead() {
        return filesRead;
    }

    public int filesRefused() {
        return filesRefused;
    }

    public int filesSkipped() {
        return filesSkipped;
    }

    public int entriesRefused() {
        return entriesRefused;
    }

    /** Folders a source may not give (a pack's of the other side) or format 1 does not have, ignored with a line. */
    public int ignoredFolders() {
        return ignoredFolders;
    }

    /** Selectors this edition cannot read, counted and skipped. */
    public int unreadable() {
        return unreadable;
    }

    /** The distinct blocks (selectors) the families and looks files name. */
    public int blocks() {
        Set<String> names = new LinkedHashSet<String>();
        for (DataKind kind : new DataKind[] { DataKind.FAMILIES, DataKind.LOOKS }) {
            for (Contribution c : ranked.get(kind)) names.add(c.selector.canonical());
        }
        return names.size();
    }

    /**
     * The one summary line of a read, at INFO (W3 spec 2.3): "TRMTR data: 14 files from 5 mods, 2 packs and the config
     * folder; 211 blocks, 3 shapes, 4 wearers, 1 plugin; 1 file refused, 6 names not installed".
     *
     * @param notInstalled the names the adapter found no block or entity for
     */
    public String summary(int notInstalled) {
        StringBuilder out = new StringBuilder(DataProblems.PREFIX);
        out.append(count(filesRead, "file", "files"));
        List<String> from = new ArrayList<String>();
        if (mods > 0) from.add(count(mods, "mod", "mods"));
        if (packs > 0) from.add(count(packs, "pack", "packs"));
        if (config) from.add("the config folder");
        if (!from.isEmpty()) {
            out.append(" from ");
            for (int i = 0; i < from.size(); i++) {
                if (i > 0) out.append(i == from.size() - 1 ? " and " : ", ");
                out.append(from.get(i));
            }
        }
        out.append("; ")
            .append(count(blocks(), "block", "blocks"))
            .append(", ")
            .append(count(targets(DataKind.SHAPES).size(), "shape", "shapes"))
            .append(", ")
            .append(count(targets(DataKind.WEARERS).size(), "wearer", "wearers"))
            .append(", ")
            .append(count(plugins.size(), "plugin", "plugins"));
        out.append("; ")
            .append(count(filesRefused, "file refused", "files refused"))
            .append(", ")
            .append(count(notInstalled, "name not installed", "names not installed"));
        if (filesSkipped > 0) out.append(", ")
            .append(count(filesSkipped, "file needing a newer TRMTR", "files needing a newer TRMTR"));
        if (entriesRefused > 0) out.append(", ")
            .append(count(entriesRefused, "entry refused", "entries refused"));
        if (unreadable > 0) out.append(", ")
            .append(count(unreadable, "name for other editions", "names for other editions"));
        return out.toString();
    }

    private static String count(int n, String one, String many) {
        return n + " " + (n == 1 ? one : many);
    }
}
