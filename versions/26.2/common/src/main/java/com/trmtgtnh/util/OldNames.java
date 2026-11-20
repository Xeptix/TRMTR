package com.trmtgtnh.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The 1.7.10 names of vanilla blocks that 1.13 renamed, and what each became.
 *
 * <p>
 * A settings file is promised to travel between the editions, and the 1.7.10 edition's own settings name blocks the old
 * way - the golem is built from {@code minecraft:stonebrick}, vegetation is {@code minecraft:tallgrass},
 * {@code minecraft:red_flower} and the rest. Read as they stand here they name nothing: until 0.9.222 this edition
 * shipped the golem's body as {@code minecraft:stonebrick}, so no golem could be built without the modded block beside
 * it, and its vegetation list matched no vanilla plant (spec GO2 and SD32).
 *
 * <p>
 * Only names later versions removed are here, so a name that still means something - {@code minecraft:grass} is the plant now,
 * not the block it was - is never second-guessed. An old name with its metadata stands for the one block that variant
 * became; without one, for every block the old name covered, as it did then.
 */
public final class OldNames {

    /** Each old name's variants, by its old metadata. */
    private static final Map<String, String[]> BECAME = new HashMap<String, String[]>();

    static {
        put("stonebrick", "stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks", "chiseled_stone_bricks");
        put("tallgrass", "dead_bush", "grass", "fern");
        put("yellow_flower", "dandelion");
        put("red_flower", "poppy", "blue_orchid", "allium", "azure_bluet", "red_tulip", "orange_tulip", "white_tulip",
            "pink_tulip", "oxeye_daisy");
        put("double_plant", "sunflower", "lilac", "tall_grass", "large_fern", "rose_bush", "peony");
        put("deadbush", "dead_bush");
        put("tilled_field", "farmland");
        put("hardened_clay", "terracotta");
        put("snow_layer", "snow");
        // And 1.17's, which no older edition had to read (0.9.225): the made path every edition's dirt family lists by
        // its 1.9 name, minecraft:grass_path. Read as it stands here it named nothing, and no path wore - the 1.20.1
        // harness's bench laid one and logged it "worn as nothing".
        put("grass_path", "dirt_path");
    }

    private OldNames() {}

    private static void put(String old, String... became) {
        String[] named = new String[became.length];
        for (int at = 0; at < became.length; at++) named[at] = "minecraft:" + became[at];
        BECAME.put("minecraft:" + old, named);
    }

    /**
     * What a settings name stands for now: the blocks an old name became - the one variant its metadata names, or every
     * one where it names none ({@code meta} below zero) - or the name itself, as it is, when it is not an old one.
     */
    public static List<String> orSelf(String name, int meta) {
        String[] became = name == null ? null : BECAME.get(name.trim());
        if (became == null) return Collections.singletonList(name);
        if (meta >= 0) return Collections.singletonList(meta < became.length ? became[meta] : became[0]);
        return java.util.Arrays.asList(became);
    }

    /** The one block an old name stands for when one must be chosen - its own metadata's, or its first. */
    public static String one(String name, int meta) {
        return orSelf(name, meta < 0 ? 0 : meta).get(0);
    }

    /**
     * The blocks this mod's own saved records name the 1.12.2 way, and what each is called now, by old metadata. Unlike
     * {@link #BECAME}, which reads a settings file, where {@code minecraft:grass} may mean the plant, this reads what the
     * mod itself wrote while the name meant the block.
     *
     * <p>
     * <strong>Found reading the wear chain's hop to 1.16.5</strong> (0.9.229, planning E2): the game's upgrade renames a
     * chunk's blocks and knows nothing of a name inside this mod's records. A golem told to leave grass alone holds
     * {@code minecraft:grass#0}, which no block here answers to, so the order was lost without a word, and a chunk tamper
     * that last worked grass offered the plant to the editor. Left out on purpose: 1.12.2's {@code minecraft:snow}, the
     * full block, because here the name is the layer, which wears, and {@code minecraft:stone_stairs}, which 1.14 gave to
     * a new block: either could be a record this edition wrote. Metadata past nought is never written here, so
     * {@code minecraft:stone_slab#3} is 1.12.2's alone.
     */
    private static final Map<String, String[]> SAVED = new HashMap<String, String[]>();

    /** 1.12.2's sixteen colors, by metadata. */
    private static final String[] COLORS = { "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
        "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black" };

    static {
        saved("grass", "grass_block");
        saved("dirt", "dirt", "coarse_dirt", "podzol");
        saved("sand", "sand", "red_sand");
        saved("stone", "stone", "granite", "polished_granite", "diorite", "polished_diorite", "andesite",
            "polished_andesite");
        saved("stonebrick", "stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks", "chiseled_stone_bricks");
        saved("sandstone", "sandstone", "chiseled_sandstone", "cut_sandstone");
        saved("red_sandstone", "red_sandstone", "chiseled_red_sandstone", "cut_red_sandstone");
        saved("stone_slab", "stone_slab", "sandstone_slab", "petrified_oak_slab", "cobblestone_slab", "brick_slab",
            "stone_brick_slab", "nether_brick_slab", "quartz_slab");
        saved("stone_slab2", "red_sandstone_slab");
        saved("nether_brick", "nether_bricks");
        saved("red_nether_brick", "red_nether_bricks");
        saved("end_bricks", "end_stone_bricks");
        saved("brick_block", "bricks");
        saved("magma", "magma_block");
        saved("quartz_block", "quartz_block", "chiseled_quartz_block", "quartz_pillar", "quartz_pillar", "quartz_pillar");
        saved("prismarine", "prismarine", "prismarine_bricks", "dark_prismarine");
        String[] clay = new String[COLORS.length];
        String[] concrete = new String[COLORS.length];
        String[] powder = new String[COLORS.length];
        for (int at = 0; at < COLORS.length; at++) {
            clay[at] = COLORS[at] + "_terracotta";
            concrete[at] = COLORS[at] + "_concrete";
            powder[at] = COLORS[at] + "_concrete_powder";
        }
        saved("stained_hardened_clay", clay);
        saved("concrete", concrete);
        saved("concrete_powder", powder);
    }

    private static void saved(String old, String... became) {
        String[] named = new String[became.length];
        for (int at = 0; at < became.length; at++) named[at] = "minecraft:" + became[at];
        SAVED.put("minecraft:" + old, named);
    }

    private static String savedBlock(String name, int meta) {
        String[] became = name == null ? null : SAVED.get(name);
        return became != null ? became[meta >= 0 && meta < became.length ? meta : 0] : one(name, meta);
    }

    /**
     * A block's name as this mod saved it, with no metadata beside it - a chunk tamper's last target - as it reads now.
     * A name that was never 1.12.2's, or the empty name of a tamper that has worked nothing, comes back as it is, so a
     * record this edition wrote reads the same every time. On 1.16.5 and 1.20.1 a tamper that last touched the plant
     * reads as grass too: the two cannot be told apart, the plant is no ground to edit, and the next click writes over it.
     */
    public static String savedBlock(String name) {
        return savedBlock(name, 0);
    }

    /**
     * A golem's block key, {@code name#meta} as {@code EntityGolemOfWays.blockKey} writes it, as it reads now: under
     * this version's name, at the metadata nought every key here has. A key that was never 1.12.2's comes back as it is.
     */
    public static String savedKey(String key) {
        int hash = key == null ? -1 : key.lastIndexOf('#');
        if (hash < 0) return key;
        int meta;
        try {
            meta = Integer.parseInt(key.substring(hash + 1));
        } catch (NumberFormatException notOne) {
            return key;
        }
        String name = key.substring(0, hash);
        String now = savedBlock(name, meta);
        return now.equals(name) ? key : now + "#0";
    }

    /** The 1.7.10 names of vanilla's mobs that later versions renamed or split, lower case, and what each became. */
    private static final Map<String, String[]> MOBS = new HashMap<String, String[]>();

    static {
        mob("skeleton", "skeleton", "wither_skeleton");
        mob("zombie", "zombie", "zombie_villager");
        mob("pigzombie", "zombified_piglin");
        mob("cavespider", "cave_spider");
        mob("lavaslime", "magma_cube");
        mob("enderdragon", "ender_dragon");
        mob("witherboss", "wither");
        mob("mushroomcow", "mooshroom");
        mob("snowman", "snow_golem");
        mob("ozelot", "ocelot", "cat");
        mob("villagergolem", "iron_golem");
        mob("entityhorse", "horse", "donkey", "mule", "skeleton_horse", "zombie_horse");
    }

    private static void mob(String old, String... became) {
        String[] named = new String[became.length];
        for (int at = 0; at < became.length; at++) named[at] = "minecraft:" + became[at];
        MOBS.put(old, named);
    }

    /**
     * The registry names a mob line written without a namespace stands for: what a 1.7.10 name became - every kind of
     * it where later versions split one into several, as {@code Skeleton} was the wither skeleton too - or vanilla's mob
     * of that name, as {@code Villager} is {@code minecraft:villager}. Empty for a line that names its namespace, or the
     * wildcard, which are read as they stand.
     *
     * <p>
     * The 1.7.10 edition names a mob as the game named it then, and ships {@code multipliers.mobs} as {@code Villager};
     * this version names a mob by its registry name alone, so until 0.9.222 that line matched nothing and no loose mob
     * wore the ground here, villagers included (spec WH4).
     */
    public static List<String> mobs(String name) {
        if (name == null) return Collections.emptyList();
        String old = name.trim()
            .toLowerCase(java.util.Locale.ROOT);
        if (old.isEmpty() || old.indexOf(':') >= 0 || "*".equals(old)) return Collections.emptyList();
        String[] became = MOBS.get(old);
        return became != null ? java.util.Arrays.asList(became) : Collections.singletonList("minecraft:" + old);
    }
}
