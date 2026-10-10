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
 * Only names 1.13 removed are here, so a name that still means something - {@code minecraft:grass} is the plant now,
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
