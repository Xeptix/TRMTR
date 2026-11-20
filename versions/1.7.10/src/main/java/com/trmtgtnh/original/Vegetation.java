package com.trmtgtnh.original;

import java.util.Locale;

/**
 * The original mod's eighteen plants, in each edition's own names.
 *
 * <p>
 * 0.5.1 tramples the plants listed in its {@code erodableVegetation} (tools/original_trmt_051.json), named as the game
 * named them from 1.20.3 on. Faithful Mode and "Use your TRMT settings" need the same plants in every edition, and the
 * names moved twice: before 1.13 a flower was a block and a metadata value ({@code minecraft:red_flower:1} is the blue
 * orchid), and short grass was {@code minecraft:grass} until 1.20.3. Cornflower and lily of the valley came in 1.14, so
 * 1.7.10 and 1.12.2 have no such plant, and a list that names one says so rather than quietly losing it.
 *
 * <p>
 * Each row was read from that version's own game jar on 2026-10-30: 1.7.10 and 1.12.2 from the patched class folders
 * ({@code BlockFlower}, {@code BlockDoublePlant} and {@code BlockTallGrass}, their metadata in the order their names
 * are declared), the rest from {@code Blocks} or {@code BlockItemIds}. A port carrying this file reads its row again
 * from its own jar with {@code tools/mcsource.py} rather than trusting the row.
 *
 * <p>
 * The same file in all eight editions, which is why every row is here and each tree asks for its own.
 */
public final class Vegetation {

    /** The editions, in the order of {@link #ROWS}. */
    public static final String[] EDITIONS = { "1.7.10", "1.12.2", "1.16.5", "1.20.1", "1.21.1", "26.1.2", "26.2",
        "26.3" };

    /** 0.5.1's eighteen, as it writes them in a fresh {@code trmt.json} from 1.20.3 on. */
    private static final String[] ORIGINAL = { "minecraft:short_grass", "minecraft:tall_grass", "minecraft:dandelion",
        "minecraft:poppy", "minecraft:blue_orchid", "minecraft:allium", "minecraft:azure_bluet", "minecraft:red_tulip",
        "minecraft:orange_tulip", "minecraft:white_tulip", "minecraft:pink_tulip", "minecraft:oxeye_daisy",
        "minecraft:cornflower", "minecraft:lily_of_the_valley", "minecraft:sunflower", "minecraft:lilac",
        "minecraft:rose_bush", "minecraft:peony" };

    /** Before 1.13: block and metadata, the form this mod's surface lists read ("modid:block:meta"). */
    private static final String[] BLOCK_AND_METADATA = { "minecraft:tallgrass:1", "minecraft:double_plant:2",
        "minecraft:yellow_flower:0", "minecraft:red_flower:0", "minecraft:red_flower:1", "minecraft:red_flower:2",
        "minecraft:red_flower:3", "minecraft:red_flower:4", "minecraft:red_flower:5", "minecraft:red_flower:6",
        "minecraft:red_flower:7", "minecraft:red_flower:8", null, null, "minecraft:double_plant:0",
        "minecraft:double_plant:1", "minecraft:double_plant:4", "minecraft:double_plant:5" };

    /** 1.14 to 1.20.2: every plant by its own name, short grass still called grass. */
    private static final String[] GRASS_NAMED_GRASS = { "minecraft:grass", "minecraft:tall_grass",
        "minecraft:dandelion", "minecraft:poppy", "minecraft:blue_orchid", "minecraft:allium", "minecraft:azure_bluet",
        "minecraft:red_tulip", "minecraft:orange_tulip", "minecraft:white_tulip", "minecraft:pink_tulip",
        "minecraft:oxeye_daisy", "minecraft:cornflower", "minecraft:lily_of_the_valley", "minecraft:sunflower",
        "minecraft:lilac", "minecraft:rose_bush", "minecraft:peony" };

    /** One row per edition, in the order of {@link #EDITIONS}; null where that edition has no such plant. */
    private static final String[][] ROWS = { BLOCK_AND_METADATA, BLOCK_AND_METADATA, GRASS_NAMED_GRASS,
        GRASS_NAMED_GRASS, ORIGINAL, ORIGINAL, ORIGINAL, ORIGINAL };

    /** The name 1.20.1 and older write for short grass, which a file carried from 1.20.1 may hold. */
    private static final String OLD_SHORT_GRASS = "minecraft:grass";

    private Vegetation() {}

    /** The eighteen as 0.5.1 names them: the default list when a file has none. */
    public static String[] original() {
        return ORIGINAL.clone();
    }

    /** This edition's names for the eighteen, in the original's order, null where the edition has no such plant. */
    public static String[] row(String edition) {
        return ROWS[editionIndex(edition)].clone();
    }

    /** Whether this edition names a plant without metadata: 1.13 and later. */
    public static boolean flattened(String edition) {
        return editionIndex(edition) >= 2;
    }

    /**
     * This edition's name for a plant named as the original's file names it, or null when this edition has no such
     * plant.
     *
     * <p>
     * A name is read as the original read it: without a namespace it is a {@code minecraft} one, and case does not
     * matter. {@code minecraft:grass} is short grass, as a file written on 1.20.1 calls it. A name outside the eighteen
     * is passed through unchanged in an edition that names plants the same way, where this mod's own list reads it and
     * skips what nothing provides; before 1.13 a {@code minecraft} name outside the eighteen has no metadata to go on,
     * so it is not matched, and another mod's name is passed through.
     */
    public static String inEdition(String edition, String id) {
        String[] row = ROWS[editionIndex(edition)];
        String name = normalise(id);
        if (name == null) return null;
        if (OLD_SHORT_GRASS.equals(name)) return row[0];
        for (int i = 0; i < ORIGINAL.length; i++) {
            if (ORIGINAL[i].equals(name)) return row[i];
        }
        if (flattened(edition) || !name.startsWith("minecraft:")) return name;
        return null;
    }

    /** A name as the original reads it, or null for one that is empty. */
    static String normalise(String id) {
        if (id == null) return null;
        String name = id.trim()
            .toLowerCase(Locale.ROOT);
        if (name.isEmpty()) return null;
        return name.indexOf(':') < 0 ? "minecraft:" + name : name;
    }

    private static int editionIndex(String edition) {
        for (int i = 0; i < EDITIONS.length; i++) {
            if (EDITIONS[i].equals(edition)) return i;
        }
        throw new IllegalArgumentException("No such edition: " + edition);
    }
}
