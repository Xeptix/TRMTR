package com.trmtgtnh.original;

import java.util.Locale;

/**
 * The blocks this mod stands aside from: every block of another wear mod that owns its own worn blocks (D60;
 * docs/goals/0.9.229-w5-spec.md, 2.11).
 *
 * <p>
 * The original TRMT replaces a square it has worn four to eight times with a block of its own, in the {@code trmt}
 * namespace, and those blocks copy the grass, dirt and sand settings. Left to itself, this mod's detection would find
 * them, call them grass, dirt and sand, and draw its own wear over the other mod's. So no block of a namespace here is
 * ever put into a family - by detection, by the player's lists or by a data file - whether or not that mod is loaded,
 * and a record already on such a square is dropped by the ordinary rule for a block that no longer matches its record.
 * Both mods still wear the same plain grass, dirt and sand until the other one takes a square over.
 *
 * <p>
 * The whole namespace, not a list of its block names, so a block a later release of that mod adds is refused too.
 * Worn Path is not here: it writes vanilla blocks and owns none (spec 2.11), so it has nothing to stand aside on.
 *
 * <p>
 * Pure, over the registry name as text, so it is the same file in all eight editions.
 */
public final class OriginalBlocks {

    /** The namespaces whose blocks are never one of this mod's surfaces, lower case. */
    private static final String[] NAMESPACES = { "trmt" };

    private OriginalBlocks() {}

    /** The namespaces refused, a copy. */
    public static String[] namespaces() {
        return NAMESPACES.clone();
    }

    /**
     * Whether a block, by its registry name ({@code namespace:path}, any case), belongs to another wear mod and so is
     * never one of this mod's surfaces. A name with no namespace is the game's own and is never refused here.
     */
    public static boolean standsAside(String blockName) {
        if (blockName == null) return false;
        String name = blockName.trim();
        int colon = name.indexOf(':');
        if (colon <= 0) return false;
        String namespace = name.substring(0, colon)
            .toLowerCase(Locale.ROOT);
        for (String each : NAMESPACES) {
            if (each.equals(namespace)) return true;
        }
        return false;
    }
}
