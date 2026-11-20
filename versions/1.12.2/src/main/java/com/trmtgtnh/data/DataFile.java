package com.trmtgtnh.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads one data file against format 1: where it sits, whether its source may give it, its JSON, its top-level keys,
 * and each entry against the key table (W3 spec 2.1 and 2.3; FORMAT.md).
 *
 * <p>
 * What is refused, and how much: a file that does not parse, that is over 1 MB or 4,096 entries, or has a missing or
 * higher {@code format}, is refused whole; an entry with a wrong type, a number out of range, an unknown family or look
 * or a malformed selector is refused alone and the rest of the file kept; a key format 1 does not have is ignored with
 * a warning (a later minor may add it), and a file whose {@code requires} names a newer trmtr-api is skipped whole with
 * one line rather than half-read. A selector this edition cannot read is not an error: it is counted and skipped.
 *
 * <p>
 * Every problem goes to {@link DataProblems} with its line and column. Nothing here throws to its caller.
 *
 * <p>
 * Portable: the same file in all eight trees.
 */
public final class DataFile {

    private static final Pattern IN_PACK = Pattern.compile("^(?:assets|data)/([^/]+)/trmtr/(.+)$");
    private static final Pattern IN_CONFIG = Pattern.compile("^config/trmtr/([^/]+)/(.+)$");
    private static final Pattern REQUIRES = Pattern.compile("^([0-9]{1,4})\\.([0-9]{1,4})$");
    private static final Pattern CLASS_NAME = Pattern
        .compile("^[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)+$");
    private static final Pattern TEXTURE = Pattern.compile("^([-A-Za-z0-9_.]+:)?[-A-Za-z0-9_./]+$");

    /** Where a file sits: its namespace and kind, from its path. */
    public static final class Place {

        public final String namespace;
        /** The kind, or null for a folder format 1 does not have (a later minor's kind). */
        public final DataKind kind;
        /** The folder as written, for a line about a kind format 1 does not have. */
        public final String folder;

        Place(String namespace, DataKind kind, String folder) {
            this.namespace = namespace;
            this.kind = kind;
            this.folder = folder;
        }
    }

    /**
     * Where a path sits, or null when it is no data file: {@code assets/<ns>/trmtr/<kind>/<name>.json},
     * {@code data/<ns>/trmtr/<kind>/<name>.json}, {@code config/trmtr/<ns>/<kind>/<name>.json}, or
     * {@code .../trmtr/plugin.json}. Paths use forward slashes.
     */
    public static Place place(String path) {
        if (path == null) return null;
        Matcher m = IN_PACK.matcher(path);
        if (!m.matches()) m = IN_CONFIG.matcher(path);
        if (!m.matches()) return null;
        String namespace = m.group(1);
        String rest = m.group(2);
        if (rest.equals("plugin.json")) return new Place(namespace, DataKind.PLUGIN, "plugin.json");
        int slash = rest.indexOf('/');
        if (slash <= 0 || !rest.endsWith(".json") || rest.length() - slash <= ".json".length() + 1) return null;
        String folder = rest.substring(0, slash);
        return new Place(namespace, DataKind.byFolder(folder), folder);
    }

    /** What reading one file came to. */
    public static final class Read {

        public final List<DataEntry> entries = new ArrayList<DataEntry>();
        /** The plugin class a {@code plugin.json} names, or null. */
        public String pluginClass;
        public boolean refused;
        public boolean skipped;
        public int entriesRefused;
        /** Selectors this edition cannot read (a tag on 1.7.10, a meta on 1.16.5), counted and skipped. */
        public int unreadable;
        public final List<DataProblems.Problem> problems = new ArrayList<DataProblems.Problem>();

        /** Whether the file was read (perhaps with entries refused): neither refused nor skipped whole. */
        public boolean read() {
            return !refused && !skipped;
        }
    }

    /** An entry's refusal, thrown inside this class only. */
    private static final class EntryRefused extends Exception {

        private static final long serialVersionUID = 1L;
        final JsonValue at;

        EntryRefused(JsonValue at, String message) {
            super(message, null, false, false);
            this.at = at;
        }
    }

    private final DataSource source;
    private final String path;
    private final Place place;
    private final Selector.Forms forms;
    private final DataProblems problems;
    private final Read read = new Read();

    private DataFile(DataSource source, String path, Place place, Selector.Forms forms, DataProblems problems) {
        this.source = source;
        this.path = path;
        this.place = place;
        this.forms = forms;
        this.problems = problems;
    }

    /**
     * Reads one file of a known place. The caller has already checked that its source may give its kind
     * ({@link DataSet} does). Never throws.
     */
    static Read read(DataSource source, String path, Place place, JsonReader.Result json, Selector.Forms forms,
        DataProblems problems) {
        DataFile file = new DataFile(source, path, place, forms, problems);
        try {
            file.body(json);
        } catch (RuntimeException unexpected) {
            // A fault of TRMTR's own reader, not of the file: say so, and keep the game up.
            file.read.entries.clear();
            file.read.pluginClass = null;
            file.refuseFile(
                0,
                0,
                "TRMTR could not read it (" + unexpected.getClass()
                    .getName() + ")",
                "report this to TRMTR");
        }
        return file.read;
    }

    private void problem(DataProblems.Problem.Outcome outcome, int entry, int line, int column, String message,
        String action) {
        DataProblems.Problem problem = new DataProblems.Problem(
            outcome,
            source,
            path,
            entry,
            line,
            column,
            message,
            action);
        read.problems.add(problem);
        problems.say(problem);
    }

    private void refuseFile(int line, int column, String message, String action) {
        read.refused = true;
        problem(DataProblems.Problem.Outcome.FILE_REFUSED, 0, line, column, message, action);
    }

    private void body(JsonReader.Result json) {
        if (!json.ok()) {
            refuseFile(json.line, json.column, json.problem, source.reportTo());
            return;
        }
        JsonValue top = json.value;
        if (top.kind != JsonValue.Kind.OBJECT) {
            refuseFile(top.line, top.column, "the file is " + top.described() + ", not an object", source.reportTo());
            return;
        }
        boolean plugin = place.kind == DataKind.PLUGIN;
        Map<String, DataKeys.Key> keys = DataKeys.of(plugin ? "plugin" : "file");

        JsonValue format = top.get("format");
        if (format == null) {
            refuseFile(
                top.line,
                top.column,
                "it has no \"format\" - a format 1 file begins { \"format\": 1",
                source.reportTo());
            return;
        }
        if (!format.isWhole()) {
            refuseFile(
                format.line,
                format.column,
                "\"format\" must be a whole number, found " + format.described(),
                source.reportTo());
            return;
        }
        int formatNumber = (int) format.number();
        if (formatNumber > DataKeys.FORMAT) {
            refuseFile(
                format.line,
                format.column,
                "format " + formatNumber + " is newer than the format this TRMTR reads (" + DataKeys.FORMAT + ")",
                source.reportTo());
            return;
        }
        if (formatNumber < DataKeys.FORMAT) {
            refuseFile(
                format.line,
                format.column,
                "format " + formatNumber + " is no format (the first is 1)",
                source.reportTo());
            return;
        }

        JsonValue requires = top.get("requires");
        if (requires != null) {
            Matcher m = requires.string() == null ? null : REQUIRES.matcher(requires.string());
            if (m == null || !m.matches()) {
                refuseFile(
                    requires.line,
                    requires.column,
                    "\"requires\" must be a version as major.minor, such as \"1.0\"",
                    source.reportTo());
                return;
            }
            int major = Integer.parseInt(m.group(1));
            int minor = Integer.parseInt(m.group(2));
            if (major > DataKeys.API_MAJOR || (major == DataKeys.API_MAJOR && minor > DataKeys.API_MINOR)) {
                read.skipped = true;
                problem(
                    DataProblems.Problem.Outcome.SKIPPED,
                    0,
                    requires.line,
                    requires.column,
                    "it needs trmtr-api " + major
                        + "."
                        + minor
                        + " and this TRMTR carries "
                        + DataKeys.API_MAJOR
                        + "."
                        + DataKeys.API_MINOR,
                    "a newer TRMTR reads it");
                return;
            }
        }

        for (Map.Entry<String, JsonValue> member : top.members()
            .entrySet()) {
            if (!keys.containsKey(member.getKey())) {
                int[] at = top.placeOf(member.getKey());
                problem(
                    DataProblems.Problem.Outcome.IGNORED,
                    0,
                    at[0],
                    at[1],
                    "the key \"" + member.getKey()
                        + "\" is not a key of "
                        + (plugin ? "plugin.json" : "a data file")
                        + " in format 1",
                    source.reportTo());
            }
        }

        if (plugin) {
            JsonValue name = top.get("class");
            if (name == null) {
                refuseFile(top.line, top.column, "it has no \"class\" naming the plugin", source.reportTo());
                return;
            }
            if (name.string() == null || !CLASS_NAME.matcher(name.string())
                .matches()) {
                refuseFile(
                    name.line,
                    name.column,
                    "\"class\" must be a class name, such as com.example.ExamplePlugin",
                    source.reportTo());
                return;
            }
            read.pluginClass = name.string();
            return;
        }

        JsonValue entries = top.get("entries");
        if (entries == null) {
            refuseFile(top.line, top.column, "it has no \"entries\"", source.reportTo());
            return;
        }
        if (entries.kind != JsonValue.Kind.ARRAY) {
            refuseFile(
                entries.line,
                entries.column,
                "\"entries\" must be a list, found " + entries.described(),
                source.reportTo());
            return;
        }
        if (entries.items()
            .size() > DataKeys.MAX_ENTRIES) {
            refuseFile(
                entries.line,
                entries.column,
                "it has " + entries.items()
                    .size() + " entries, over the " + DataKeys.MAX_ENTRIES + " a file may have",
                source.reportTo());
            return;
        }
        int index = 0;
        for (JsonValue entry : entries.items()) {
            index++;
            try {
                DataEntry made = entry(entry, index);
                if (made != null) read.entries.add(made);
            } catch (EntryRefused refused) {
                read.entriesRefused++;
                problem(
                    DataProblems.Problem.Outcome.ENTRY_REFUSED,
                    index,
                    refused.at.line,
                    refused.at.column,
                    refused.getMessage(),
                    source.reportTo());
            }
        }
    }

    private DataEntry entry(JsonValue entry, int index) throws EntryRefused {
        DataKind kind = place.kind;
        String table = kind.table();
        if (entry.kind != JsonValue.Kind.OBJECT) {
            throw new EntryRefused(entry, "an entry must be an object, found " + entry.described());
        }
        Map<String, DataKeys.Key> keys = DataKeys.of(table);
        String selectorKey = kind == DataKind.WEARERS ? "entities" : "blocks";
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        List<Selector> selectors = null;
        int meta = Selector.ANY_META;
        Map<String, String> state = Collections.emptyMap();
        boolean hasMeta = false;
        boolean hasState = false;
        int unknown = 0;
        for (Map.Entry<String, JsonValue> member : entry.members()
            .entrySet()) {
            String name = member.getKey();
            DataKeys.Key key = keys.get(name);
            if (key == null) {
                unknown++;
                int[] at = entry.placeOf(name);
                problem(
                    DataProblems.Problem.Outcome.IGNORED,
                    index,
                    at[0],
                    at[1],
                    "the key \"" + name + "\" is not a key of " + table + " entries in format 1",
                    source.reportTo());
                continue;
            }
            Object value = value(key, member.getValue());
            if (name.equals(selectorKey)) {
                @SuppressWarnings("unchecked")
                List<Selector> listed = (List<Selector>) value;
                selectors = listed;
            } else if (kind == DataKind.SHAPES && name.equals("meta")) {
                hasMeta = true;
                meta = ((Integer) value).intValue();
            } else if (kind == DataKind.SHAPES && name.equals("state")) {
                hasState = true;
                @SuppressWarnings("unchecked")
                Map<String, String> props = (Map<String, String>) value;
                state = props;
            } else {
                values.put(name, value);
            }
        }
        for (DataKeys.Key key : keys.values()) {
            if (key.required && !entry.members()
                .containsKey(key.name)) {
                throw new EntryRefused(entry, "it has no \"" + key.name + "\"");
            }
        }
        if (kind == DataKind.FAMILIES) {
            // "none" is a whole chain in itself - the block sinks as itself - so a list that names it beside a family
            // says two things at once, and which was meant cannot be told (W3 T9).
            Object through = values.get("wearsThroughTo");
            if (through instanceof List && ((List<?>) through).size() > 1 && ((List<?>) through).contains("none")) {
                throw new EntryRefused(
                    entry.members()
                        .get("wearsThroughTo"),
                    "\"wearsThroughTo\" names none beside a family; none stands alone");
            }
        }
        if (kind == DataKind.SHAPES) {
            Object shape = values.get("shape");
            if ("boxes".equals(shape) && !values.containsKey("boxes")) {
                throw new EntryRefused(entry, "shape \"boxes\" needs \"boxes\", the boxes it is made of");
            }
            if (!"boxes".equals(shape) && values.containsKey("boxes")) {
                values.remove("boxes");
                int[] at = entry.placeOf("boxes");
                problem(
                    DataProblems.Problem.Outcome.IGNORED,
                    index,
                    at[0],
                    at[1],
                    "\"boxes\" is read only with shape \"boxes\"",
                    source.reportTo());
            }
            // The wear is held below the walked box's height (FORMAT.md, W3 T6): a cap that would wear it through is
            // held a pixel short of it, and said, so the author learns the depth they asked for is not the one drawn.
            Object cap = values.get("maxSinkPixels");
            int height = walkedHeight(shape, values.get("boxes"));
            if (cap instanceof Integer && height > 0 && ((Integer) cap).intValue() >= height) {
                values.put("maxSinkPixels", Integer.valueOf(height - 1));
                int[] at = entry.placeOf("maxSinkPixels");
                problem(
                    DataProblems.Problem.Outcome.IGNORED,
                    index,
                    at[0],
                    at[1],
                    "\"maxSinkPixels\" " + cap
                        + " is not below the walked box's height of "
                        + height
                        + " pixels; held to "
                        + (height - 1),
                    source.reportTo());
            }
        } else if (values.isEmpty()) {
            // Only its selectors: either it says nothing (refused, as the record's two keys at least ask), or all it
            // said was in keys a later minor added, already warned about.
            if (unknown > 0) return null;
            throw new EntryRefused(entry, "it says nothing about its " + selectorKey);
        }

        // The states a shapes entry is about, as this edition reads them: an entry for block states alone is not for
        // an edition without them, and one for metadata alone not for an edition without metadata.
        if (kind == DataKind.SHAPES) {
            if ((hasState && !hasMeta && !forms.states) || (hasMeta && !hasState && !forms.meta)) {
                read.unreadable += selectors.size();
                problems.sink()
                    .debug(
                        DataProblems.PREFIX + "entry "
                            + index
                            + " of "
                            + path
                            + " is for "
                            + (hasState ? "block states" : "metadata")
                            + ", which this edition does not have; skipped");
                return null;
            }
            if (!forms.meta) meta = Selector.ANY_META;
            if (!forms.states) state = Collections.emptyMap();
        }

        List<Selector> readable = new ArrayList<Selector>();
        for (Selector selector : selectors) {
            if (selector.readableIn(forms)) readable.add(selector);
            else {
                read.unreadable++;
                problems.sink()
                    .debug(
                        DataProblems.PREFIX + selector.text
                            + " in "
                            + path
                            + " is a form this edition does not read; skipped");
            }
        }
        if (readable.isEmpty()) return null;
        return new DataEntry(kind, source, path, place.namespace, index, entry.line, readable, values, meta, state);
    }

    /**
     * The height wear is held below, in pixels, of an entry's own shape: the shortest of its highest boxes, half a
     * block for a slab, the whole block for a full one, or 0 where the entry alone does not say (a stair, which never
     * sinks).
     */
    private static int walkedHeight(Object shape, Object boxes) {
        if ("full".equals(shape)) return DataKeys.BOX_PIXELS;
        if ("slab".equals(shape)) return DataKeys.BOX_PIXELS / 2;
        if (!"boxes".equals(shape) || !(boxes instanceof List)) return 0;
        int top = -1;
        int least = 0;
        for (Object one : (List<?>) boxes) {
            int[] box = (int[]) one;
            if (box[4] > top) {
                top = box[4];
                least = box[4] - box[1];
            } else if (box[4] == top) least = Math.min(least, box[4] - box[1]);
        }
        return least;
    }

    /** One key's value, typed by the table, or the entry's refusal. */
    private Object value(DataKeys.Key key, JsonValue v) throws EntryRefused {
        switch (key.type) {
            case INTEGER: {
                if (!v.isWhole()) throw wrong(key, v, "a whole number");
                int n = (int) v.number();
                inRange(key, v, n);
                return Integer.valueOf(n);
            }
            case NUMBER: {
                if (v.kind != JsonValue.Kind.NUMBER) throw wrong(key, v, "a number");
                double n = v.number();
                inRange(key, v, n);
                return Double.valueOf(n);
            }
            case BOOLEAN:
                if (v.kind != JsonValue.Kind.BOOLEAN) throw wrong(key, v, "true or false");
                return Boolean.valueOf(v.bool());
            case STRING: {
                if (v.string() == null) throw wrong(key, v, "a string");
                oneOf(key, v, v.string());
                return v.string();
            }
            case ANYTHING:
                return null;
            case OBJECT: {
                if (v.kind != JsonValue.Kind.OBJECT) throw wrong(key, v, "an object");
                Map<String, String> out = new TreeMap<String, String>();
                for (Map.Entry<String, JsonValue> each : v.members()
                    .entrySet()) {
                    String text = each.getValue()
                        .string();
                    if (text == null) {
                        throw new EntryRefused(
                            each.getValue(),
                            "\"" + key.name
                                + "\"'s "
                                + each.getKey()
                                + " must be a string, found "
                                + each.getValue()
                                    .described());
                    }
                    out.put(each.getKey(), text);
                }
                return out;
            }
            case TEXTURE:
                return texture(key, v);
            default:
                break;
        }

        // The lists.
        if (v.kind != JsonValue.Kind.ARRAY) throw wrong(key, v, "a list");
        List<JsonValue> items = v.items();
        if (key.hasRange()) {
            int least = (int) key.min;
            int most = (int) key.max;
            if (items.size() < least || (most != DataKeys.ANY && items.size() > most)) {
                throw new EntryRefused(
                    v,
                    "\"" + key.name
                        + "\" holds "
                        + items.size()
                        + (items.size() == 1 ? " item" : " items")
                        + "; it takes "
                        + (most == DataKeys.ANY ? "at least " + least
                            : least == most ? String.valueOf(least) : least + " to " + most));
            }
        }
        switch (key.type) {
            case BLOCKS:
            case ENTITIES: {
                boolean entity = key.type == DataKeys.Type.ENTITIES;
                List<Selector> out = new ArrayList<Selector>();
                for (JsonValue item : items) {
                    Selector selector = item.string() == null ? null
                        : entity ? Selector.entity(item.string()) : Selector.block(item.string());
                    if (selector == null) {
                        throw new EntryRefused(
                            item,
                            (item.string() == null ? item.described() : "\"" + item.string() + "\"") + " is not "
                                + (entity ? "an entity selector (an entity id, or #ns:tag)"
                                    : "a block selector (ns:name, ns:name:3, ns:name[prop=value] or #ns:tag)"));
                    }
                    out.add(selector);
                }
                return out;
            }
            case STRINGS: {
                List<String> out = new ArrayList<String>();
                Set<String> seen = new HashSet<String>();
                for (JsonValue item : items) {
                    if (item.string() == null) throw wrong(key, item, "a list of strings");
                    oneOf(key, item, item.string());
                    if (!seen.add(item.string())) {
                        throw new EntryRefused(item, "\"" + key.name + "\" names " + item.string() + " twice");
                    }
                    out.add(item.string());
                }
                return Collections.unmodifiableList(out);
            }
            case TEXTURES: {
                List<String> out = new ArrayList<String>();
                for (JsonValue item : items) out.add(texture(key, item));
                return Collections.unmodifiableList(out);
            }
            case BOXES: {
                List<int[]> out = new ArrayList<int[]>();
                for (JsonValue item : items) out.add(box(key, item));
                return Collections.unmodifiableList(out);
            }
            default:
                throw new EntryRefused(v, "\"" + key.name + "\" is a key this TRMTR cannot read");
        }
    }

    private int[] box(DataKeys.Key key, JsonValue item) throws EntryRefused {
        String shape = "a box is [x0, y0, z0, x1, y1, z1], six whole pixels from 0 to " + DataKeys.BOX_PIXELS;
        if (item.kind != JsonValue.Kind.ARRAY || item.items()
            .size() != DataKeys.BOX_NUMBERS) throw new EntryRefused(item, shape);
        int[] box = new int[DataKeys.BOX_NUMBERS];
        for (int i = 0; i < DataKeys.BOX_NUMBERS; i++) {
            JsonValue n = item.items()
                .get(i);
            if (!n.isWhole()) throw new EntryRefused(n, shape);
            box[i] = (int) n.number();
            if (box[i] < 0 || box[i] > DataKeys.BOX_PIXELS) throw new EntryRefused(n, shape);
        }
        if (box[0] >= box[3] || box[1] >= box[4] || box[2] >= box[5]) {
            throw new EntryRefused(item, "a box's far corner must be past its near one on every axis, so it has room");
        }
        return box;
    }

    private String texture(DataKeys.Key key, JsonValue v) throws EntryRefused {
        String text = v.string();
        if (text == null) throw wrong(key, v, "a texture name");
        if (!TEXTURE.matcher(text)
            .matches()) {
            throw new EntryRefused(v, "\"" + text + "\" is not a texture name, such as example:loam_top");
        }
        return text;
    }

    private EntryRefused wrong(DataKeys.Key key, JsonValue v, String wanted) {
        return new EntryRefused(v, "\"" + key.name + "\" must be " + wanted + ", found " + v.described());
    }

    private void inRange(DataKeys.Key key, JsonValue v, double n) throws EntryRefused {
        if (!key.hasRange()) return;
        if (n < key.min || n > key.max) {
            throw new EntryRefused(
                v,
                "\"" + key.name
                    + "\" is "
                    + v.numberText()
                    + ", outside "
                    + shown(key, key.min)
                    + " to "
                    + shown(key, key.max));
        }
    }

    private static String shown(DataKeys.Key key, double n) {
        if (key.type == DataKeys.Type.INTEGER) return String.valueOf((long) n);
        return String.format(Locale.ROOT, "%.1f", n);
    }

    private void oneOf(DataKeys.Key key, JsonValue v, String text) throws EntryRefused {
        if (key.values == null || key.values.contains(text)) return;
        StringBuilder known = new StringBuilder();
        for (String each : key.values) {
            if (known.length() > 0) known.append(", ");
            known.append(each);
        }
        throw new EntryRefused(v, "\"" + key.name + "\" is " + text + ", which is not one of " + known);
    }
}
