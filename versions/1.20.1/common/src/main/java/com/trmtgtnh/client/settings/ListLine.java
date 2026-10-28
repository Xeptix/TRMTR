package com.trmtgtnh.client.settings;

import java.util.Locale;

/**
 * One line of a settings list, read the way the game reads it (spec Part 2; survey 2.2).
 *
 * <p>
 * Every list in the file names something - a block, an item, a fluid, a mob, an enchantment, a texture, a tamper
 * grade, a surface family, a dimension - in a syntax its comment gives, and each syntax already has a reader in the
 * game: {@code SurfaceRegistry.applyList} and {@code HealingCost} for blocks and ore groups, {@code ReinforceCost}
 * for items and {@code fluid:}, {@code MobEntries.parse} for mobs, {@code InnerLayers.readTable} for block and
 * texture pairs, {@code TamperGrade.resolve} for grades. This reads a line by exactly those rules, so what the screen
 * says a line is, is what the game will make of it. Where a rule here looks odd - a bare {@code modid:block} with no
 * metadata, a mob weight read by {@link Float#parseFloat}, a grade split with {@link String#split} so a trailing colon
 * vanishes - that is the game's rule, and changing it here would make the screen disagree with the game.
 *
 * <p>
 * <b>The line as written is kept byte for byte</b> ({@link #line()}), so opening a list and leaving it never changes
 * the file. {@link #canonical()} writes the line from its fields, and for every well-formed line in the file's own
 * syntaxes it gives back the same text once trimmed (Forge trims every list line it reads); the {@code with...}
 * methods are how a structured row - a pair's texture picker, a mob's number field, a grade's columns - changes one
 * part and leaves the others as they were.
 *
 * <p>
 * Nothing here asks the game whether a name exists: that is {@link NameStatus}'s, through its {@code Lookup}.
 */
public final class ListLine {

    /** What a list's lines name, which decides how a line is read. */
    public enum Kind {
        /** A block: {@code modid:block}, {@code modid:block:meta}, {@code modid:block:*}. */
        BLOCK,
        /**
         * A block or an ore group: as {@link #BLOCK}, or {@code ore:<name>} ({@code families.*.repairBlocks}). Only
         * that list reads the prefix; in every other block list {@code ore:stone} is the name of a block nobody has.
         */
        BLOCK_OR_ORE,
        /** A block by its registry name alone, no metadata ({@code surfaces.exclude}). */
        BLOCK_NO_META,
        /** An item, {@code modid:name[:meta]}, or {@code fluid:<name>} ({@code reinforce.materials}). */
        ITEM_OR_FLUID,
        /** An item, {@code modid:name[:meta]}. */
        ITEM,
        /** A mob: {@code Name}, {@code Name:n}, {@code *}, {@code *:n}. */
        MOB,
        /** A fragment of an enchantment's name. */
        ENCHANT,
        /** A block and the texture behind it: {@code modid:block=texture}, {@code modid:block:meta=texture}. */
        SPRITE_PAIR,
        /** A tamper grade: {@code name:oreName:uses} or {@code name:oreName:uses:reach}. */
        GRADE,
        /** A surface family's key. */
        FAMILY,
        /** A dimension: its id, or (typed) its name. */
        DIMENSION,
        /** Free words, judged by nothing. */
        TEXT
    }

    /** {@link #meta} when the line wrote none. For a block list that means every metadata, as the game reads it. */
    public static final int NO_META = -1;

    /** {@link #meta} for {@code :*}, every metadata. */
    public static final int WILDCARD = -2;

    /** The ore dictionary prefix {@code HealingCost} reads, any case. */
    public static final String ORE_PREFIX = "ore:";

    /** The fluid prefix {@code ReinforceCost} reads, any case. */
    public static final String FLUID_PREFIX = "fluid:";

    /** The mob list's wildcard, every mob no line names. */
    public static final String EVERY_MOB = "*";

    /** How this line was read. */
    public final Kind kind;

    /** The line exactly as it was given, never null. */
    public final String raw;

    /** Whether the line holds nothing but spaces: the game skips it, and the screen says nothing of it. */
    public final boolean blank;

    /**
     * The name the line gives: a block's or an item's registry name without its metadata, a mob's name (or
     * {@link #EVERY_MOB}), an enchantment fragment, a family key, a grade's own name, a dimension's name where the line
     * is not a number, or the free words. Null for an ore group, a fluid, a blank line and a dimension number.
     */
    public final String id;

    /** The metadata written: a number, {@link #WILDCARD}, or {@link #NO_META}. */
    public final int meta;

    /** An {@code ore:} line's group name, or a grade's ore name; otherwise null. */
    public final String ore;

    /** A {@code fluid:} line's fluid name; otherwise null. */
    public final String fluid;

    /** A pair's texture, the part after {@code =}; otherwise null. */
    public final String texture;

    /** A mob line's number exactly as written after the last colon, or null where it wrote none. */
    public final String number;

    /** That number read as the server reads it ({@link Float#parseFloat}), or null where none or unreadable. */
    public final Float weight;

    /** A grade's uses as written, or null. */
    public final String usesText;

    /** A grade's uses as a number, or null where it does not read. */
    public final Integer uses;

    /** A grade's reach as written, or null where the line has three parts. */
    public final String reachText;

    /** A grade's reach as a number, or null where it has none or it does not read. */
    public final Integer reach;

    /** A dimension line's id, or null where the line is a name. */
    public final Integer dimension;

    /**
     * Why the line does not read, as the part that does not (the screen shows it in {@code status.badLine}), or null
     * for a line that reads. A line that does not read is never refused: the game skips or guesses at it, and the
     * screen says so in red.
     */
    public final String error;

    private ListLine(Kind kind, String raw, boolean blank, String id, int meta, String ore, String fluid,
        String texture, String number, Float weight, String usesText, Integer uses, String reachText, Integer reach,
        Integer dimension, String error) {
        this.kind = kind;
        this.raw = raw;
        this.blank = blank;
        this.id = id;
        this.meta = meta;
        this.ore = ore;
        this.fluid = fluid;
        this.texture = texture;
        this.number = number;
        this.weight = weight;
        this.usesText = usesText;
        this.uses = uses;
        this.reachText = reachText;
        this.reach = reach;
        this.dimension = dimension;
        this.error = error;
    }

    /** A line with only a name, the common case. */
    private static ListLine named(Kind kind, String raw, String id, int meta, String error) {
        return new ListLine(
            kind,
            raw,
            false,
            id,
            meta,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            error);
    }

    /**
     * Reads one line as the list of that kind reads it.
     *
     * @param line the line as the file holds it, or as typed; null reads as an empty line
     * @param kind what the list's lines name
     */
    public static ListLine parse(String line, Kind kind) {
        if (kind == null) throw new IllegalArgumentException("a line is read as some kind of list");
        String raw = line == null ? "" : line;
        String t = raw.trim();
        if (t.isEmpty()) {
            return new ListLine(
                kind,
                raw,
                true,
                null,
                NO_META,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
        }
        switch (kind) {
            case BLOCK:
            case BLOCK_NO_META:
                return readBlock(kind, raw, t);
            case BLOCK_OR_ORE:
                if (t.regionMatches(true, 0, ORE_PREFIX, 0, ORE_PREFIX.length())) {
                    // HealingCost: the name runs from after the prefix, untrimmed, and an empty one is skipped.
                    String group = t.substring(ORE_PREFIX.length());
                    return new ListLine(
                        kind,
                        raw,
                        false,
                        null,
                        NO_META,
                        group,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        group.isEmpty() ? t : null);
                }
                return readBlock(kind, raw, t);
            case ITEM_OR_FLUID:
                if (t.regionMatches(true, 0, FLUID_PREFIX, 0, FLUID_PREFIX.length())) {
                    String name = t.substring(FLUID_PREFIX.length());
                    return new ListLine(
                        kind,
                        raw,
                        false,
                        null,
                        NO_META,
                        null,
                        name,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        name.isEmpty() ? t : null);
                }
                return readItem(kind, raw, t);
            case ITEM:
                return readItem(kind, raw, t);
            case MOB:
                return readMob(raw, t);
            case SPRITE_PAIR:
                return readPair(raw);
            case GRADE:
                return readGrade(raw, t);
            case DIMENSION:
                return readDimension(raw, t);
            case ENCHANT:
            case FAMILY:
            case TEXT:
            default:
                return named(kind, raw, t, NO_META, null);
        }
    }

    /**
     * {@code SurfaceRegistry.applyList}'s rule: a metadata suffix follows the last colon, but only where there are two
     * colons, so {@code minecraft:stone} is a name and {@code stone} is too. {@code *} is every metadata. A suffix that
     * is not a number the game treats as every metadata with a warning in its log; here it is a line that does not
     * read, so the screen can say what the log says.
     */
    private static ListLine readBlock(Kind kind, String raw, String t) {
        int last = t.lastIndexOf(':');
        if (last > 0 && t.indexOf(':') != last) {
            String tail = t.substring(last + 1);
            String name = t.substring(0, last);
            if ("*".equals(tail)) return named(kind, raw, name, WILDCARD, null);
            Integer meta = whole(tail);
            if (meta == null || meta.intValue() < 0) return named(kind, raw, name, NO_META, tail);
            return named(kind, raw, name, meta.intValue(), null);
        }
        return named(kind, raw, t, NO_META, null);
    }

    /**
     * {@code ReinforceCost.resolve}'s rule: as a block, except that a suffix which is not a number is part of the name
     * - so {@code modid:name:*} names an item called {@code name:*}, which no game has.
     */
    private static ListLine readItem(Kind kind, String raw, String t) {
        int last = t.lastIndexOf(':');
        if (last > 0 && t.indexOf(':') != last) {
            Integer meta = whole(t.substring(last + 1));
            if (meta != null) {
                if (meta.intValue() < 0) return named(kind, raw, t.substring(0, last), NO_META, t.substring(last + 1));
                return named(kind, raw, t.substring(0, last), meta.intValue(), null);
            }
        }
        return named(kind, raw, t, NO_META, null);
    }

    /**
     * {@code MobEntries.parse}'s rule: the weight follows the last colon, provided something stands before it, and is
     * whatever {@link Float#parseFloat} accepts. A colon at the very start is part of the name. Where the weight does
     * not read the server files the whole line under its own text, a name no mob has, and warns; here it is a line that
     * does not read, and the number field beside it refuses the number and holds Save (M3.3).
     */
    private static ListLine readMob(String raw, String t) {
        int colon = t.lastIndexOf(':');
        if (colon > 0) {
            String name = t.substring(0, colon)
                .trim();
            String written = t.substring(colon + 1);
            Float weight;
            try {
                weight = Float.valueOf(Float.parseFloat(written));
            } catch (NumberFormatException notANumber) {
                weight = null;
            }
            // A registry name on its own - what the ports' magic tamper writes - is a name the reader files whole and
            // does not warn about (TrmtConfig.registryName there), not a name with a number that does not read.
            if (weight == null && registryName(t)) return named(Kind.MOB, raw, t, NO_META, null);
            return new ListLine(
                Kind.MOB,
                raw,
                false,
                name,
                NO_META,
                null,
                null,
                null,
                written,
                weight,
                null,
                null,
                null,
                null,
                null,
                weight == null ? written : null);
        }
        return named(Kind.MOB, raw, t, NO_META, null);
    }

    /**
     * Whether a mob line is a registry name on its own: a namespace and a path as registry names are written, lower
     * case, the path starting with a letter - the ports' {@code TrmtConfig.registryName}, so {@code Villager:1,5} is
     * still a name with a number that does not read.
     */
    static boolean registryName(String line) {
        return line.matches("[a-z0-9_.-]+:[a-z][a-z0-9_./-]*");
    }

    /**
     * {@code InnerLayers.readTable}'s rule: the first {@code =} splits the line, each side trimmed, and a line with an
     * empty side is skipped. The block side is looked up exactly as written - {@code modid:block} or
     * {@code modid:block:meta} - so a {@code :*} there names nothing and does not read.
     */
    private static ListLine readPair(String raw) {
        int split = raw.indexOf('=');
        if (split < 0) return named(Kind.SPRITE_PAIR, raw, raw.trim(), NO_META, raw.trim());
        String head = raw.substring(0, split)
            .trim();
        String texture = raw.substring(split + 1)
            .trim();
        String id = head;
        int meta = NO_META;
        String error = head.isEmpty() || texture.isEmpty() ? raw.trim() : null;
        int last = head.lastIndexOf(':');
        if (last > 0 && head.indexOf(':') != last) {
            Integer written = whole(head.substring(last + 1));
            id = head.substring(0, last);
            if (written == null || written.intValue() < 0) {
                if (error == null) error = head.substring(last + 1);
            } else {
                meta = written.intValue();
            }
        }
        return new ListLine(
            Kind.SPRITE_PAIR,
            raw,
            false,
            id.isEmpty() ? null : id,
            meta,
            null,
            null,
            texture.isEmpty() ? null : texture,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            error);
    }

    /**
     * {@code TamperGrade.resolve}'s rule: split on every colon by {@link String#split}, which drops empty parts at the
     * end, into three or four parts, each trimmed. Uses that do not read lose the grade, so the line does not read;
     * reach that does not read is worked out instead, so the line still reads and only the number field refuses it.
     */
    private static ListLine readGrade(String raw, String t) {
        String[] parts = t.split(":");
        if (parts.length != 3 && parts.length != 4) {
            return named(Kind.GRADE, raw, parts.length > 0 ? parts[0].trim() : null, NO_META, t);
        }
        String usesText = parts[2].trim();
        Integer uses = whole(usesText);
        String reachText = parts.length == 4 ? parts[3].trim() : null;
        Integer reach = reachText == null ? null : whole(reachText);
        return new ListLine(
            Kind.GRADE,
            raw,
            false,
            parts[0].trim(),
            NO_META,
            parts[1].trim(),
            null,
            null,
            null,
            null,
            usesText,
            uses,
            reachText,
            reach,
            null,
            uses == null ? usesText : null);
    }

    /** An id where the line is a whole number; otherwise a name, which {@link NameStatus} resolves where it can. */
    private static ListLine readDimension(String raw, String t) {
        Integer id = whole(t);
        if (id != null) {
            return new ListLine(
                Kind.DIMENSION,
                raw,
                false,
                null,
                NO_META,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                id,
                null);
        }
        return named(Kind.DIMENSION, raw, t, NO_META, null);
    }

    /** {@link Integer#parseInt}'s answer, or null where it refuses. */
    private static Integer whole(String text) {
        try {
            return Integer.valueOf(Integer.parseInt(text));
        } catch (NumberFormatException notWhole) {
            return null;
        }
    }

    /** The line exactly as given: what the file keeps when the line was not edited. */
    public String line() {
        return raw;
    }

    /**
     * The line written from its fields, in the file's own syntax: for every line that reads, the trimmed text it was
     * read from. A line that does not read cannot be written from its parts and comes back trimmed.
     */
    public String canonical() {
        if (blank) return "";
        if (error != null) return raw.trim();
        switch (kind) {
            case BLOCK:
            case BLOCK_NO_META:
                return block(id, meta);
            case BLOCK_OR_ORE:
                return ore != null ? ORE_PREFIX + ore : block(id, meta);
            case ITEM_OR_FLUID:
                return fluid != null ? FLUID_PREFIX + fluid : block(id, meta);
            case ITEM:
                return block(id, meta);
            case MOB:
                return mob(id, number);
            case SPRITE_PAIR:
                return pair(block(id, meta), texture);
            case GRADE:
                return grade(id, ore, usesText, reachText);
            case DIMENSION:
                return dimension != null ? dimension.toString() : id;
            default:
                return id;
        }
    }

    /** Whether the line reads. A blank line reads as nothing. */
    public boolean reads() {
        return error == null;
    }

    // ------------------------------------------------------------------
    // Writing a line from its parts
    // ------------------------------------------------------------------

    /**
     * A block or item line: the name, then {@code :meta}, {@code :*}, or nothing for {@link #NO_META}. Nothing means
     * every metadata to the block lists' readers and metadata 0 to a picture, so a picker that adds one subtype must
     * write its number.
     */
    public static String block(String id, int meta) {
        String name = id == null ? "" : id;
        if (meta == WILDCARD) return name + ":*";
        if (meta >= 0) return name + ":" + meta;
        return name;
    }

    /** A mob line: the name, then {@code :number} where a number is given. */
    public static String mob(String name, String number) {
        String head = name == null ? "" : name;
        return number == null ? head : head + ":" + number;
    }

    /** A block and texture pair. */
    public static String pair(String block, String texture) {
        return (block == null ? "" : block) + "=" + (texture == null ? "" : texture);
    }

    /** A grade line, with the reach only where one is given. */
    public static String grade(String name, String ore, String uses, String reach) {
        StringBuilder out = new StringBuilder();
        out.append(name == null ? "" : name)
            .append(':')
            .append(ore == null ? "" : ore)
            .append(':')
            .append(uses == null ? "" : uses);
        if (reach != null) out.append(':')
            .append(reach);
        return out.toString();
    }

    /** This line with another block (and metadata) in it, its texture or other parts kept. */
    public ListLine withBlock(String blockId, int blockMeta) {
        switch (kind) {
            case SPRITE_PAIR:
                return parse(pair(block(blockId, blockMeta), texture), kind);
            default:
                return parse(block(blockId, blockMeta), kind);
        }
    }

    /** A pair with another texture behind its block. */
    public ListLine withTexture(String newTexture) {
        return parse(pair(head(), newTexture), Kind.SPRITE_PAIR);
    }

    /** A mob line with another number, or none for null. */
    public ListLine withNumber(String newNumber) {
        return parse(mob(id, newNumber == null ? null : newNumber.trim()), Kind.MOB);
    }

    /** A grade with another ore. */
    public ListLine withOre(String newOre) {
        return parse(grade(id, newOre, usesText, reachText), Kind.GRADE);
    }

    /** A grade with another use count. */
    public ListLine withUses(String newUses) {
        return parse(grade(id, ore, newUses, reachText), Kind.GRADE);
    }

    /** A grade with another reach, or none for null or blank. */
    public ListLine withReach(String newReach) {
        String reachPart = newReach == null || newReach.trim()
            .isEmpty() ? null : newReach.trim();
        return parse(grade(id, ore, usesText, reachPart), Kind.GRADE);
    }

    /** A pair's block side as written, trimmed. */
    private String head() {
        int split = raw.indexOf('=');
        return (split < 0 ? raw : raw.substring(0, split)).trim();
    }

    /** The name in lower case, as the game matches it (Locale.ROOT, so a Turkish machine does too). */
    public String lowerId() {
        return id == null ? null : id.toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return raw;
    }
}
