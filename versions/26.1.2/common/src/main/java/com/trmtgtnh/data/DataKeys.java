package com.trmtgtnh.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.trmtgtnh.surface.SurfaceFamily;
import com.trmtgtnh.util.MobWear;

/**
 * Every key of data format 1, in one table (W3 spec 2.1; trmtr-api 1.0.0).
 *
 * <p>
 * This table is what the reader accepts, and it is held to its record: {@code tools/check_tools.py}'s
 * {@code the_format_keys_match_their_record} reads the {@code key(...)} lines below and refuses any difference from
 * {@code api/format-1.json} - a key's name, its type, whether it is required, its range or item count and its values.
 * So each key stays on one line in this exact shape, and a key is added, renamed or bounded here and in the record
 * together. The format's rules on change are {@code api/DEPRECATION.md}: a minor may add optional keys; a key is
 * never removed before a major.
 *
 * <p>
 * The families a file may name are {@link SurfaceFamily}'s own keys and the mob groups {@link MobWear.Group}'s, so a
 * family added there is accepted here without a second list; the guard compares both with the record. The look ids
 * are written out, because they live in the config's settings, which name the game; they are the frozen ids of
 * {@code FamilySettings} (never renamed once shipped), and the guard compares them with the record, which it compares
 * with {@code FamilySettings}.
 *
 * <p>
 * Portable: the same file in all eight trees ({@code CoreStaysPortableTest}).
 */
public final class DataKeys {

    private DataKeys() {}

    /** The data format this TRMTR reads. Moves only with a major trmtr-api version. */
    public static final int FORMAT = 1;

    /** The trmtr-api version whose keys this table is: a file's {@code requires} newer than this is skipped. */
    public static final int API_MAJOR = 1;
    public static final int API_MINOR = 0;

    /** At most this many entries in one file. */
    public static final int MAX_ENTRIES = 4096;

    /** A list's item count with no upper bound. */
    public static final int ANY = -1;

    /** The types a key may have, each with the words FORMAT.md's tables and format-1.json's titles use for it. */
    public enum Type {

        INTEGER("integer"),
        NUMBER("number"),
        STRING("string"),
        BOOLEAN("boolean"),
        ANYTHING("anything"),
        OBJECT("object"),
        ENTRIES("list of entries"),
        BLOCKS("list of block selectors"),
        ENTITIES("list of entity selectors"),
        STRINGS("list of strings"),
        TEXTURE("texture name"),
        TEXTURES("list of texture names"),
        BOXES("list of boxes");

        /** The record's word for the type. */
        public final String words;

        Type(String words) {
            this.words = words;
        }

        public boolean isList() {
            return this == ENTRIES || this == BLOCKS
                || this == ENTITIES
                || this == STRINGS
                || this == TEXTURES
                || this == BOXES;
        }
    }

    /** Whether a key is required. */
    public static final boolean REQUIRED = true;
    public static final boolean OPTIONAL = false;

    /** One key of one kind of file. Built only by the table below. */
    public static final class Key {

        /** {@code file} for the keys every file has; otherwise the kind of entry, or {@code plugin}. */
        public final String kind;
        public final String name;
        public final Type type;
        public final boolean required;
        /** For a number: its range. For a list: its least and most items ({@link #ANY} for no most). */
        public double min = Double.NaN;
        public double max = Double.NaN;
        /** The values a string (or each string of a list) may take, or null for any. */
        public List<String> values;

        Key(String kind, String name, Type type, boolean required) {
            this.kind = kind;
            this.name = name;
            this.type = type;
            this.required = required;
        }

        Key range(double min, double max) {
            this.min = min;
            this.max = max;
            return this;
        }

        Key items(int min, int max) {
            this.min = min;
            this.max = max;
            return this;
        }

        Key values(String... values) {
            this.values = Collections.unmodifiableList(Arrays.asList(values));
            return this;
        }

        Key values(List<String> values) {
            this.values = Collections.unmodifiableList(new ArrayList<String>(values));
            return this;
        }

        public boolean hasRange() {
            return !Double.isNaN(min);
        }
    }

    /** {@code family}'s values: every built-in family's key, and {@code none}. */
    public static final List<String> FAMILIES_AND_NONE = familyKeys(false, true);

    /**
     * {@code wearsThroughTo}'s values: the families with stages, the only ones ground can wear through to, and
     * {@code none} (alone: the block sinks as itself, whatever its family wears through to; W3 T9).
     */
    public static final List<String> STAGED_FAMILIES_AND_NONE = familyKeys(true, true);

    /** {@code group}'s values: {@link MobWear.Group}'s. */
    public static final List<String> MOB_GROUPS = mobGroups();

    /** {@code look}'s values: the frozen wear-look ids. */
    public static final List<String> LOOK_IDS = Collections.unmodifiableList(
        Arrays.asList(
            "grass",
            "dirt",
            "sand",
            "polish",
            "crack",
            "smooth",
            "crack_lite",
            "smooth_heavy",
            "dirt_lite",
            "smooth_crack",
            "smooth_dirt",
            "crack_dirt",
            "crack_dirt_lite",
            "flesh",
            "taint",
            "sculk",
            "cloud",
            "sand_trodden",
            "gravel_parted",
            "cobble_polished",
            "snow_packed",
            "ice_scuffed",
            "nether_charred",
            "end_bleached"));

    /* The table. One key per line, in the shape the format guard reads; see the class comment. */
    // @formatter:off
    private static final Key[] TABLE = {
        key("file", "format", Type.INTEGER, REQUIRED).range(1, 1),
        key("file", "requires", Type.STRING, OPTIONAL),
        key("file", "_", Type.ANYTHING, OPTIONAL),
        key("file", "entries", Type.ENTRIES, REQUIRED).items(0, 4096),
        key("families", "blocks", Type.BLOCKS, REQUIRED).items(1, ANY),
        key("families", "family", Type.STRING, OPTIONAL).values(FAMILIES_AND_NONE),
        key("families", "resistance", Type.NUMBER, OPTIONAL).range(1.0, 64.0),
        key("families", "repairWith", Type.BLOCKS, OPTIONAL).items(1, ANY),
        key("families", "cover", Type.STRING, OPTIONAL).values("breaks", "holds"),
        key("families", "wearsThroughTo", Type.STRINGS, OPTIONAL).items(1, ANY).values(STAGED_FAMILIES_AND_NONE),
        key("looks", "blocks", Type.BLOCKS, REQUIRED).items(1, ANY),
        key("looks", "look", Type.STRING, OPTIONAL).values(LOOK_IDS),
        key("looks", "strength", Type.NUMBER, OPTIONAL).range(0.0, 1.0),
        key("looks", "face", Type.TEXTURE, OPTIONAL),
        key("looks", "innerLayer", Type.TEXTURE, OPTIONAL),
        key("looks", "stages", Type.TEXTURES, OPTIONAL).items(2, 16),
        key("looks", "turns", Type.BOOLEAN, OPTIONAL),
        key("shapes", "blocks", Type.BLOCKS, REQUIRED).items(1, ANY),
        key("shapes", "shape", Type.STRING, REQUIRED).values("full", "slab", "stair", "boxes"),
        key("shapes", "state", Type.OBJECT, OPTIONAL),
        key("shapes", "meta", Type.INTEGER, OPTIONAL).range(0, 15),
        key("shapes", "boxes", Type.BOXES, OPTIONAL).items(1, 8),
        key("shapes", "maxSinkPixels", Type.INTEGER, OPTIONAL).range(0, 15),
        key("shapes", "sinks", Type.BOOLEAN, OPTIONAL),
        key("wearers", "entities", Type.ENTITIES, REQUIRED).items(1, ANY),
        key("wearers", "multiplier", Type.NUMBER, OPTIONAL).range(0.0, 4.0),
        key("wearers", "group", Type.STRING, OPTIONAL).values(MOB_GROUPS),
        key("plugin", "format", Type.INTEGER, REQUIRED).range(1, 1),
        key("plugin", "requires", Type.STRING, OPTIONAL),
        key("plugin", "_", Type.ANYTHING, OPTIONAL),
        key("plugin", "class", Type.STRING, REQUIRED),
    };
    // @formatter:on

    /** A box is six whole pixels, each 0 to 16. */
    public static final int BOX_NUMBERS = 6;
    public static final int BOX_PIXELS = 16;

    private static final Map<String, Map<String, Key>> BY_KIND = index();

    private static Key key(String kind, String name, Type type, boolean required) {
        return new Key(kind, name, type, required);
    }

    private static Map<String, Map<String, Key>> index() {
        Map<String, Map<String, Key>> out = new LinkedHashMap<String, Map<String, Key>>();
        for (Key key : TABLE) {
            Map<String, Key> kind = out.get(key.kind);
            if (kind == null) {
                kind = new LinkedHashMap<String, Key>();
                out.put(key.kind, kind);
            }
            kind.put(key.name, key);
        }
        for (Map.Entry<String, Map<String, Key>> each : out.entrySet()) {
            each.setValue(Collections.unmodifiableMap(each.getValue()));
        }
        return Collections.unmodifiableMap(out);
    }

    /**
     * The keys of one kind: {@code file} (every file's top level), {@code families}, {@code looks}, {@code shapes},
     * {@code wearers} (their entries) or {@code plugin}. Empty for a kind this format does not have.
     */
    public static Map<String, Key> of(String kind) {
        Map<String, Key> keys = BY_KIND.get(kind);
        return keys == null ? Collections.<String, Key>emptyMap() : keys;
    }

    /** Every key, in the table's order. */
    public static List<Key> all() {
        return Collections.unmodifiableList(Arrays.asList(TABLE));
    }

    private static List<String> familyKeys(boolean stagedOnly, boolean withNone) {
        List<String> out = new ArrayList<String>();
        for (SurfaceFamily family : SurfaceFamily.values()) {
            if (!stagedOnly || family.staged) out.add(family.key());
        }
        if (withNone) out.add("none");
        return Collections.unmodifiableList(out);
    }

    private static List<String> mobGroups() {
        List<String> out = new ArrayList<String>();
        for (MobWear.Group group : MobWear.Group.values()) {
            out.add(
                group.name()
                    .toLowerCase(Locale.ROOT));
        }
        return Collections.unmodifiableList(out);
    }
}
