package com.trmtgtnh.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The 1.7.10 names of vanilla's mobs that this version split into kinds of their own, and what each became.
 *
 * <p>
 * A settings file is promised to travel between the editions, and the 1.7.10 edition names a mob as the game named it
 * then. This version kept nearly every one of those names - {@code Villager} is still {@code Villager} - but 1.11 made
 * the wither skeleton, the zombie villager and each kind of horse a mob of its own, so a carried {@code Skeleton:0}
 * stopped skeletons and left wither skeletons wearing, and {@code EntityHorse} named nothing at all (0.9.222, spec WH4).
 * Only those names are here; every other name means here what it meant there. The 1.16.5 edition's copy of this class
 * also carries its blocks, which that version renamed and this one did not.
 */
public final class OldNames {

    /** Each split name, lower case, and the kinds it became besides itself, as this version names them. */
    private static final Map<String, String[]> MOBS = new HashMap<String, String[]>();

    static {
        MOBS.put("skeleton", new String[] { "WitherSkeleton" });
        MOBS.put("zombie", new String[] { "ZombieVillager" });
        MOBS.put("entityhorse", new String[] { "Horse", "Donkey", "Mule", "SkeletonHorse", "ZombieHorse" });
    }

    private OldNames() {}

    /**
     * The other kinds a mob line written as the 1.7.10 edition writes it stands for at this version, or none when its
     * name means the same here.
     */
    public static List<String> mobs(String name) {
        if (name == null) return Collections.emptyList();
        String[] became = MOBS.get(
            name.trim()
                .toLowerCase(Locale.ROOT));
        return became == null ? Collections.<String>emptyList() : Arrays.asList(became);
    }
}
