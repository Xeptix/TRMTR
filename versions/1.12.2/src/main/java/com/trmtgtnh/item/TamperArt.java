package com.trmtgtnh.item;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Which grades have an icon of their own, and what it is called.
 *
 * <p>
 * A fixed list rather than a sweep of the configured grades, and the reason is timing. Icons are
 * registered while the texture atlas is stitched, and a name registered there with no file
 * behind it does not fail - it silently becomes the missing-texture chequerboard and writes a
 * line into the log for every sprite. The configured set can be anything a pack author types;
 * this set is exactly what {@code tools/make_tamper_icons.py} draws, so the two can only agree.
 *
 * <p>
 * A grade outside this list is not a problem and not an error: it gets the plain metal icon and
 * its own name, which is what a material nobody has drawn should look like.
 */
public final class TamperArt {

    /** Grade keys with drawn icons. Keep in step with {@code MATERIALS} in the icon script. */
    private static final Set<String> DRAWN = Collections.unmodifiableSet(
        new HashSet<String>(
            Arrays.asList(
                "iron",
                "gold",
                "diamond",
                "netherite",
                "bronze",
                "steel",
                "aluminium",
                "stainlesssteel",
                "titanium",
                "tungstensteel",
                "neutronium",
                "copper",
                "tin",
                "lead",
                "nickel",
                "zinc",
                "silver",
                "electrum",
                "invar",
                "cupronickel",
                "brass",
                "wroughtiron",
                "tungsten",
                "cobalt",
                "chrome",
                "nichrome",
                "kanthal",
                "tungstencarbide",
                "hssg",
                "hsse",
                "hsss",
                "damascussteel",
                "osmium",
                "iridium",
                "platinum",
                "palladium",
                "manganese",
                "tantalum",
                "molybdenum",
                "vanadiumsteel",
                "darksteel",
                "redsteel",
                "bluesteel")));

    private TamperArt() {}

    public static Set<String> drawn() {
        return DRAWN;
    }

    /**
     * The sprite for one grade of a tool, or the tool's undecorated sprite when nobody drew it.
     *
     * @param base "tamper" or "chunk_tamper"
     */
    public static String iconFor(String base, String gradeKey) {
        if (gradeKey != null && DRAWN.contains(gradeKey.toLowerCase(Locale.ROOT))) {
            return base + "_" + gradeKey.toLowerCase(Locale.ROOT);
        }
        return base;
    }
}
