package com.trmtgtnh.client.settings;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * What a list line names in this game, judged (spec M3.2, M18.3, C1, C2, C3).
 *
 * <p>
 * <b>Red is a warning, never a refusal</b> (Xep, 2026-10-21): the shipped lists name mods a pack may not have -
 * Chisel in {@code innerLayerTextures}, Extra Utilities in the golem lists, Iron Chest in {@code demoContainer} - and
 * their comments say an absent name is inert. So no status here holds Save ({@link Status#holdsSave()} is always
 * false); the only list value that holds Save is a number that does not read, which {@code NumberWords} refuses in the
 * number field beside the line.
 *
 * <p>
 * <b>A known block with no item is not unknown</b> (survey 2.3): the default vegetation list holds exactly such blocks
 * ({@code minecraft:carrots}), and the old picture code returned the same nothing for them as for a name nobody has.
 * Here they are {@link State#KNOWN_NO_ITEM}, drawn by their texture and never red.
 *
 * <p>
 * Everything the game knows comes through {@link Lookup}, which each edition writes ({@code SettingsPictures}); this
 * class names nothing from the game, so its test can hold every status with a stand-in.
 */
public final class NameStatus {

    private NameStatus() {}

    /** The edition's answers about what the game has registered, asked when the screen opens (M3.6). */
    public interface Lookup {

        /** Whether a block answers to this registry name. */
        boolean block(String id);

        /** Whether the block has an item form at this metadata (0 for a line that wrote none, or a wildcard). */
        boolean blockHasItem(String id, int meta);

        /** Whether this metadata is one of the block's own subtypes. */
        boolean metaExists(String id, int meta);

        /** How many items the ore group (on 1.7.10 and 1.12.2) or the tag (from 1.16.5) holds; 0 for none. */
        int oreMembers(String name);

        /** Whether a fluid answers to this registry name. */
        boolean fluid(String name);

        /**
         * Whether an item (or a block's item) answers to this name at this metadata ({@link ListLine#NO_META}: any).
         */
        boolean item(String id, int meta);

        /** Whether a mob answers to this name, as the mob list matches it. */
        boolean mob(String name);

        /** The display names of every enchantment this fragment matches, as the game matches it; empty for none. */
        List<String> enchantMatches(String fragment);

        /** Whether the block atlas holds a sprite of this name. */
        boolean sprite(String name);

        /** The name of the dimension with this id, or null where none is known. */
        String dimension(int id);

        /**
         * The id a name stands for, where the edition writes dimensions as ids (1.7.10 and 1.12.2): a provider's own
         * name. Null where it knows none; the three vanilla names are answered by {@link NameStatus} itself.
         */
        Integer dimensionId(String name);

        /**
         * The display name of the dimension a name stands for, where the edition writes dimensions by name (from
         * 1.16.5), or null where none is known.
         */
        String dimensionName(String name);

        /**
         * Whether the edition's {@code ore:} lines name tags (from 1.16.5) rather than ore dictionary groups, which
         * decides only the words an unknown one is given.
         */
        boolean oreIsTag();

        /**
         * Whether the edition's file takes dimension names (from 1.16.5) as well as ids; on 1.7.10 and 1.12.2 a typed
         * name is written as its id (M18.3).
         */
        boolean writesDimensionNames();

        /**
         * Whether the dimensions the game knows can be asked at all - true with a world open. Without one only ids and
         * the three vanilla names are judged, and nothing else is red (M18.3, C3).
         */
        boolean dimensionsKnown();
    }

    /** What a line is, in M3.2's table. */
    public enum State {

        /** A blank line: nothing to show and nothing to say. */
        EMPTY,
        /** A line of free words, which nothing judges. */
        PLAIN,
        /** The thing named, with an item (or a mob, or an item) to picture. */
        KNOWN,
        /** A block the game has, with no item form: drawn by its texture. */
        KNOWN_NO_ITEM,
        /** {@code :*}: every variant of a block the game has. */
        WILDCARD,
        /** An ore dictionary group with members. */
        ORE_GROUP,
        /** A tag with members (from 1.16.5). */
        TAG,
        /** A fluid the game has. */
        FLUID,
        /** An enchantment fragment matching at least one enchantment. */
        ENCHANTMENTS,
        /** A block and a texture, both known. */
        SPRITE_PAIR,
        /** A surface family's key. */
        FAMILY,
        /** A dimension: its name where one is known, and the id written. */
        DIMENSION,
        /** A known mob at 0: kept out. */
        KEPT_OUT,
        /** {@code *}: every mob no line names. */
        ANY_MOB,
        UNKNOWN_BLOCK,
        UNKNOWN_ITEM,
        UNKNOWN_FLUID,
        UNKNOWN_ORE,
        UNKNOWN_TAG,
        UNKNOWN_MOB,
        UNKNOWN_ENCHANTMENT,
        UNKNOWN_SPRITE,
        UNKNOWN_FAMILY,
        UNKNOWN_DIMENSION,
        /** A block list that takes names only, given a metadata. */
        META_REFUSED,
        /** A line that does not read. */
        BAD_LINE;

        /** Whether a line in this state is drawn red: the unknowns, a refused metadata, a line that does not read. */
        public boolean red() {
            return name().startsWith("UNKNOWN_") || this == META_REFUSED || this == BAD_LINE;
        }
    }

    /** The judgement of one line, with what its row shows. */
    public static final class Status {

        /** What the line is. */
        public final State state;

        /** An ore group's or tag's member count; 0 otherwise. */
        public final int count;

        /** The enchantments matched, or empty. */
        public final List<String> names;

        /**
         * A dimension's name where one is known, or null - the name line of a dimension row (C3).
         */
        public final String dimensionName;

        /**
         * The id a typed dimension name is written as, on an edition that writes ids (M18.3, {@code dimension.byName});
         * null where the line is a number or the edition writes names.
         */
        public final Integer writtenAs;

        /** The argument of the reason's words: the part that does not read, for {@code status.badLine}. */
        public final String reasonArg;

        /** Whether this is a grade's line, whose line that does not read takes {@code grade.bad} (C2). */
        private final boolean grade;

        private Status(State state, int count, List<String> names, String dimensionName, Integer writtenAs,
            String reasonArg, boolean grade) {
            this.state = state;
            this.count = count;
            this.names = names == null ? Collections.<String>emptyList() : names;
            this.dimensionName = dimensionName;
            this.writtenAs = writtenAs;
            this.reasonArg = reasonArg;
            this.grade = grade;
        }

        private Status(State state, int count, List<String> names, String dimensionName, Integer writtenAs,
            String reasonArg) {
            this(state, count, names, dimensionName, writtenAs, reasonArg, false);
        }

        /** Whether the row is drawn red. A warning only. */
        public boolean red() {
            return state.red();
        }

        /** Never: an unmatched name is a warning and Done is never held for it (Xep, 2026-10-21; M3.3). */
        public boolean holdsSave() {
            return false;
        }

        /** The key of the red row's hover, why it is red; null for a row that is not red. */
        public String reasonKey() {
            return NameStatus.reasonKey(state, grade);
        }

        /** The key of the status column's words where the row has some (1.4, M3.2); null where it has none. */
        public String statusKey() {
            switch (state) {
                case KNOWN_NO_ITEM:
                    return KEY + "status.noItem";
                case WILDCARD:
                    return KEY + "status.every";
                case ORE_GROUP:
                case TAG:
                    return KEY + (count == 1 ? "status.members.one" : "status.members.many");
                case FLUID:
                    return KEY + "status.fluid";
                case KEPT_OUT:
                    return KEY + "status.keptOut";
                case ANY_MOB:
                    return KEY + "status.anyMob";
                case ENCHANTMENTS:
                    return KEY + (names.size() == 1 ? "status.matches.one" : "status.matches.many");
                default:
                    return red() ? KEY + "status.notHere" : null;
            }
        }

        @Override
        public String toString() {
            return state + (count > 0 ? " " + count : "")
                + (names.isEmpty() ? "" : " " + names)
                + (dimensionName != null ? " " + dimensionName : "")
                + (writtenAs != null ? " as " + writtenAs : "")
                + (reasonArg != null ? " (" + reasonArg + ")" : "");
        }
    }

    /** Where every key here lives (1.5). */
    static final String KEY = "trmtgtnh.config.own.";

    /**
     * The ten surface families a family list may name, which are the families that wear in stages - leaves and
     * vegetation have no pictures to wear through to, so {@code TrmtConfig.wearsThroughTo} skips them, and
     * {@code status.unknownFamily} lists these ten. Matched without regard to case, as {@code SurfaceFamily.byKey}.
     */
    public static final List<String> FAMILIES = Collections.unmodifiableList(
        java.util.Arrays.asList("grass", "dirt", "sand", "gravel", "stone", "cobble", "nether", "end", "snow", "ice"));

    /**
     * The id one of the three vanilla dimensions is called by, or null (M18.3): {@code overworld} 0, {@code nether}
     * -1, {@code the_end} and {@code end} 1, any case, with a {@code minecraft:} prefix, {@code the nether} and
     * {@code the end} with a space, and the names 1.7.10's providers give ({@code Overworld}, {@code Nether},
     * {@code The End}). Without a world this is all a typed name can be resolved by.
     */
    public static Integer vanillaDimensionId(String name) {
        if (name == null) return null;
        String plain = name.trim()
            .toLowerCase(Locale.ROOT)
            .replace(' ', '_')
            .replace('-', '_');
        if (plain.startsWith("minecraft:")) plain = plain.substring("minecraft:".length());
        if ("overworld".equals(plain) || "the_overworld".equals(plain)) return Integer.valueOf(0);
        if ("nether".equals(plain) || "the_nether".equals(plain)) return Integer.valueOf(-1);
        if ("the_end".equals(plain) || "end".equals(plain)) return Integer.valueOf(1);
        return null;
    }

    /** The English name of a vanilla dimension id, for a row with no world to ask; null for any other id. */
    public static String vanillaDimensionName(int id) {
        if (id == 0) return "Overworld";
        if (id == -1) return "Nether";
        if (id == 1) return "The End";
        return null;
    }

    /**
     * Judges one line against what the game has registered.
     *
     * @param line   the line, as {@link ListLine#parse} read it
     * @param lookup the edition's answers
     */
    public static Status of(ListLine line, Lookup lookup) {
        if (line == null || line.blank) return status(State.EMPTY);
        // A names-only list given any suffix after a second colon - a number, '*', or anything else - is told the list
        // takes names only, which is what is wrong with the line, rather than that the suffix does not read.
        if (line.kind == ListLine.Kind.BLOCK_NO_META && (line.meta != ListLine.NO_META || line.error != null)) {
            return status(State.META_REFUSED);
        }
        if (line.error != null) {
            return new Status(State.BAD_LINE, 0, null, null, null, line.error, line.kind == ListLine.Kind.GRADE);
        }
        switch (line.kind) {
            case BLOCK:
            case BLOCK_OR_ORE:
                if (line.ore != null) return ore(line.ore, lookup);
                return block(line, lookup);
            case BLOCK_NO_META:
                return block(line, lookup);
            case ITEM_OR_FLUID:
                if (line.fluid != null) return status(lookup.fluid(line.fluid) ? State.FLUID : State.UNKNOWN_FLUID);
                return item(line, lookup);
            case ITEM:
                return item(line, lookup);
            case MOB:
                return mob(line, lookup);
            case ENCHANT: {
                List<String> matched = lookup.enchantMatches(line.id);
                if (matched == null || matched.isEmpty()) return status(State.UNKNOWN_ENCHANTMENT);
                return new Status(State.ENCHANTMENTS, 0, Collections.unmodifiableList(matched), null, null, null);
            }
            case SPRITE_PAIR: {
                Status block = block(line, lookup);
                if (block.red()) return block;
                return status(lookup.sprite(line.texture) ? State.SPRITE_PAIR : State.UNKNOWN_SPRITE);
            }
            case GRADE: {
                // A grade whose ore the pack lacks is skipped by the game, and said here: never held (C2).
                int members = lookup.oreMembers(line.ore);
                return members > 0 ? new Status(State.KNOWN, members, null, null, null, null)
                    : status(State.UNKNOWN_ORE);
            }
            case FAMILY:
                return status(FAMILIES.contains(line.lowerId()) ? State.FAMILY : State.UNKNOWN_FAMILY);
            case DIMENSION:
                return dimension(line, lookup);
            case TEXT:
            default:
                return status(State.PLAIN);
        }
    }

    private static Status status(State state) {
        return new Status(state, 0, null, null, null, null);
    }

    private static Status ore(String name, Lookup lookup) {
        int members = lookup.oreMembers(name);
        if (members > 0)
            return new Status(lookup.oreIsTag() ? State.TAG : State.ORE_GROUP, members, null, null, null, null);
        return status(lookup.oreIsTag() ? State.UNKNOWN_TAG : State.UNKNOWN_ORE);
    }

    /**
     * A block line. No metadata means every metadata to the game; the picture is metadata 0's. A metadata the block
     * does not have is unknown, as the game would find nothing at it.
     */
    private static Status block(ListLine line, Lookup lookup) {
        if (line.id == null || !lookup.block(line.id)) return status(State.UNKNOWN_BLOCK);
        if (line.meta >= 0 && !lookup.metaExists(line.id, line.meta)) return status(State.UNKNOWN_BLOCK);
        if (line.meta == ListLine.WILDCARD) return status(State.WILDCARD);
        int pictured = line.meta >= 0 ? line.meta : 0;
        return status(lookup.blockHasItem(line.id, pictured) ? State.KNOWN : State.KNOWN_NO_ITEM);
    }

    private static Status item(ListLine line, Lookup lookup) {
        return status(lookup.item(line.id, line.meta) ? State.KNOWN : State.UNKNOWN_ITEM);
    }

    /**
     * A mob line. {@code *} is never red (MobRows.unknown). A name the game has, at 0, is kept out; a name it does not
     * have is unknown whatever its number, since nothing is kept out by it.
     */
    private static Status mob(ListLine line, Lookup lookup) {
        if (ListLine.EVERY_MOB.equals(line.id)) return status(State.ANY_MOB);
        if (!lookup.mob(line.id)) return status(State.UNKNOWN_MOB);
        if (line.weight != null && line.weight.floatValue() == 0f) return status(State.KEPT_OUT);
        return status(State.KNOWN);
    }

    /**
     * A dimension line (M18.3, C3). An id is named where the game or vanilla knows it, and red only where the world can
     * be asked and has none. A name is resolved: on an edition that writes ids, to the id it is written as; on one that
     * writes names, to its display name. Without a world only the three vanilla names resolve, and anything else is not
     * red, because there is nothing to ask.
     */
    private static Status dimension(ListLine line, Lookup lookup) {
        if (line.dimension != null) {
            int id = line.dimension.intValue();
            String name = lookup.dimension(id);
            if (name == null) name = vanillaDimensionName(id);
            if (name == null && lookup.dimensionsKnown()) return status(State.UNKNOWN_DIMENSION);
            return new Status(State.DIMENSION, 0, null, name, null, null);
        }
        if (lookup.writesDimensionNames()) {
            String name = lookup.dimensionName(line.id);
            if (name == null) {
                Integer vanilla = vanillaDimensionId(line.id);
                if (vanilla != null) name = vanillaDimensionName(vanilla.intValue());
            }
            if (name == null && lookup.dimensionsKnown()) return status(State.UNKNOWN_DIMENSION);
            return new Status(State.DIMENSION, 0, null, name, null, null);
        }
        Integer id = lookup.dimensionId(line.id);
        if (id == null) id = vanillaDimensionId(line.id);
        if (id == null) {
            return lookup.dimensionsKnown() ? status(State.UNKNOWN_DIMENSION)
                : new Status(State.DIMENSION, 0, null, null, null, null);
        }
        String name = lookup.dimension(id.intValue());
        if (name == null) name = vanillaDimensionName(id.intValue());
        return new Status(State.DIMENSION, 0, null, name, id, null);
    }

    /** The hover key of a red state; null for one that is not red. */
    static String reasonKey(State state, boolean grade) {
        switch (state) {
            case UNKNOWN_BLOCK:
                return KEY + "status.unknownBlock";
            case UNKNOWN_ITEM:
                return KEY + "status.unknownItem";
            case UNKNOWN_FLUID:
                return KEY + "status.unknownFluid";
            case UNKNOWN_ORE:
                return KEY + "status.unknownOre";
            case UNKNOWN_TAG:
                return KEY + "status.unknownTag";
            case UNKNOWN_MOB:
                return KEY + "unknownMob";
            case UNKNOWN_ENCHANTMENT:
                return KEY + "status.unknownEnchantment";
            case UNKNOWN_SPRITE:
                return KEY + "status.unknownSprite";
            case UNKNOWN_FAMILY:
                return KEY + "status.unknownFamily";
            case UNKNOWN_DIMENSION:
                return KEY + "status.unknownDimension";
            case META_REFUSED:
                return KEY + "status.metaRefused";
            case BAD_LINE:
                return grade ? KEY + "grade.bad" : KEY + "status.badLine";
            default:
                return null;
        }
    }
}
