package com.trmtgtnh.data;

/**
 * Where a data file came from: a mod's jar, a plugin's code, a resource pack, a data pack or the config folder (W3
 * spec 2.2 and 2.3).
 *
 * <p>
 * A source decides three things. Its layer, which decides what overrides what. The kinds it may give: a resource pack
 * gives looks alone and a data pack never looks, because a pack on one side deciding where a player stands would put a
 * client at odds with its server; a {@code plugin.json} comes from a mod jar alone, because only a jar carries code.
 * And whom a problem is reported to - a bad file from another mod is that mod's to mend, so the line says so rather
 * than sending the player to TRMTR's issue tracker.
 *
 * <p>
 * Each tree's adapter makes these (from FML's mod list, the pack list, the config folder); this package only reads
 * them. Portable: the same file in all eight trees.
 */
public final class DataSource {

    /** The sorts of source, each with its layer (FORMAT.md, "Sources and load order"). */
    public enum Type {

        /** {@code assets/<ns>/trmtr/} in a mod's file. Layer 1. */
        MOD_JAR(1),
        /** A plugin's {@code Registrar.data} call, which counts as its own mod's jar. Layer 1. */
        PLUGIN(1),
        /** A resource pack's {@code assets/<ns>/trmtr/looks/}. Layer 2. */
        RESOURCE_PACK(2),
        /** A data pack's {@code data/<ns>/trmtr/{families,shapes,wearers}/} (1.16.5 on). Layer 2. */
        DATA_PACK(2),
        /** {@code config/trmtr/<ns>/<kind>/}. Layer 3. */
        CONFIG(3);

        public final int layer;

        Type(int layer) {
            this.layer = layer;
        }

        /**
         * Whether this sort of source may give a kind of file. A refusal is said once by the reader: a misplaced
         * {@code plugin.json} is refused as a problem, and a pack's folder of the other side's kind is ignored with a
         * line.
         */
        public boolean accepts(DataKind kind) {
            switch (this) {
                case MOD_JAR:
                    return true;
                case PLUGIN:
                    return kind != DataKind.PLUGIN;
                case RESOURCE_PACK:
                    return kind == DataKind.LOOKS;
                case DATA_PACK:
                    return kind == DataKind.FAMILIES || kind == DataKind.SHAPES || kind == DataKind.WEARERS;
                default:
                    return kind != DataKind.PLUGIN;
            }
        }
    }

    public final Type type;

    /** The file's own name: a jar's file name, a pack's name, or the config folder's path. */
    public final String name;

    /** The mod id behind a jar or a plugin; null for a pack or the config folder. */
    public final String modId;

    /** For a pack, its place in the game's pack order, the later winning; otherwise 0. */
    public final int order;

    private DataSource(Type type, String name, String modId, int order) {
        this.type = type;
        this.name = name == null ? "" : name;
        this.modId = modId;
        this.order = order;
    }

    /** A mod's jar, by its file name and mod id: {@code example-1.0.jar}, {@code example}. */
    public static DataSource modJar(String fileName, String modId) {
        return new DataSource(Type.MOD_JAR, fileName, modId, 0);
    }

    /** A plugin's {@code Registrar.data} call, for the mod that declared the plugin. */
    public static DataSource plugin(String modId) {
        return new DataSource(Type.PLUGIN, modId, modId, 0);
    }

    /** A resource pack, by its name and its place in the pack order (later wins). */
    public static DataSource resourcePack(String name, int order) {
        return new DataSource(Type.RESOURCE_PACK, name, null, order);
    }

    /** A data pack, by its name and its place in the pack order (later wins). */
    public static DataSource dataPack(String name, int order) {
        return new DataSource(Type.DATA_PACK, name, null, order);
    }

    /** The config folder, by the path it is read from ({@code config/trmtr}). */
    public static DataSource config(String path) {
        return new DataSource(Type.CONFIG, path, null, 0);
    }

    public int layer() {
        return type.layer;
    }

    /** Where, as a refusal says it: "in example-1.0.jar (mod example)", "in resource pack Faithful". */
    public String where() {
        switch (type) {
            case MOD_JAR:
                return "in " + name + " (mod " + modId + ")";
            case PLUGIN:
                return "from the plugin of mod " + modId;
            case RESOURCE_PACK:
                return "in resource pack " + name;
            case DATA_PACK:
                return "in data pack " + name;
            default:
                return "in the config folder";
        }
    }

    /** Who, as a conflict says it: "mod example", "pack Faithful", "the config folder". */
    public String who() {
        switch (type) {
            case MOD_JAR:
            case PLUGIN:
                return "mod " + modId;
            case RESOURCE_PACK:
            case DATA_PACK:
                return "pack " + name;
            default:
                return "the config folder";
        }
    }

    /** Whom to tell, as the end of a refusal: never TRMTR for another mod's or pack's file. */
    public String reportTo() {
        switch (type) {
            case MOD_JAR:
            case PLUGIN:
                return "report this to that mod";
            case RESOURCE_PACK:
            case DATA_PACK:
                return "report this to that pack's author";
            default:
                return "correct that file in the config folder";
        }
    }

    /** A key telling one source from another, for the said-once log and for conflicts. */
    public String id() {
        return type.name() + "|" + name + "|" + (modId == null ? "" : modId);
    }

    @Override
    public String toString() {
        return who();
    }
}
