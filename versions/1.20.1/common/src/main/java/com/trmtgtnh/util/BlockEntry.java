package com.trmtgtnh.util;

/**
 * How a block is spelled in config: {@code mod:block}, or {@code mod:block:meta}.
 *
 * <p>
 * Two places in the mod already read this spelling and a third has just started: the golem's build
 * blocks, the surface lists, and the container the demonstrate command fills. Each had its own copy
 * of the parse, which is two copies too many for a rule with a genuine trap in it - a registry name
 * already contains a colon, so the metadata cannot simply be whatever follows the first one, and a
 * parser that assumes it can turns {@code IronChest:BlockIronChest} into a mod called IronChest and
 * a metadata called BlockIronChest.
 *
 * <p>
 * The rule is that a trailing colon-separated number is a metadata and anything else is part of the
 * name. Anything that does not parse as a number is left in the name rather than refused, because a
 * block genuinely called {@code mod:thing:special} is likelier than a typo, and a name that does not
 * resolve is a visible failure where a silently dropped one is not.
 */
public final class BlockEntry {

    private BlockEntry() {}

    /** The name half, which is the whole of an entry that names no metadata. */
    public static String nameOf(String entry) {
        if (entry == null) return "";
        String trimmed = entry.trim();
        return hasMeta(trimmed) ? trimmed.substring(0, trimmed.lastIndexOf(':')) : trimmed;
    }

    /**
     * The metadata half.
     *
     * @param fallback what an entry that names no metadata means, which is a wildcard where the
     *                 caller is matching against a block already in the world and zero where it is
     *                 about to place one
     */
    public static int metaOf(String entry, int fallback) {
        if (entry == null) return fallback;
        String trimmed = entry.trim();
        if (!hasMeta(trimmed)) return fallback;
        return Integer.parseInt(trimmed.substring(trimmed.lastIndexOf(':') + 1));
    }

    /** Whether the tail past the last colon is a number, and there is a colon before it to keep. */
    private static boolean hasMeta(String trimmed) {
        int last = trimmed.lastIndexOf(':');
        if (last <= 0 || trimmed.indexOf(':') == last) return false;
        try {
            Integer.parseInt(trimmed.substring(last + 1));
            return true;
        } catch (NumberFormatException notAMeta) {
            return false;
        }
    }
}
