package com.trmtgtnh.item;

import java.util.Map;

/**
 * Which grade of chunk tamper the Wayfarer's tamper is built around.
 *
 * <p>
 * The rule used to be netherite where the enhancements are on and the pack has it, and diamond
 * otherwise - and "otherwise" was never checked. A pack whose grade list had lost its diamond line,
 * pointed it at an ore nothing registers, or was left empty got a recipe asking for a chunk tamper
 * nobody could make, drawn in NEI around whichever grade the list happened to begin with. It could
 * not be completed, and the achievement, quest and trophy for it were written all the same.
 *
 * <p>
 * So the choice is made from the grades whose chunk tamper actually has a recipe, and it lands on
 * one of them whenever there are any. Netherite first where the enhancements are on and diamond
 * next, exactly as before, because a fix for packs without diamond must not move the price of the
 * last tool on the packs that have it. Past both, the grade whose entry declares the most uses: the
 * Wayfarer is the top of the ladder, and durability is the one measure every grade states. Of equals
 * the first in list order wins, so the answer depends on nothing but the list.
 *
 * <p>
 * Durability as written in the entry, not as tamperDurabilityScale and the short ceiling leave it.
 * The recipe is registered once and the scale is read live, and rounding a small scale or capping a
 * large figure turns different grades into equal ones.
 *
 * <p>
 * Null only when no chunk tamper can be made at all, which the caller writes to the log. Nothing
 * from the game is in it, so a test can pin the order without a pack, and it is on the list
 * CoreStaysPortableTest keeps free of Minecraft.
 */
public final class WayfarerCore {

    /** Preferred where the enhancements are on and a chunk tamper of it can be made. */
    public static final String ENHANCED = "netherite";

    /** Preferred everywhere else, and the answer on plain vanilla. */
    public static final String USUAL = "diamond";

    private WayfarerCore() {}

    /**
     * @param enhanced  whether integration.gtnhEnhanced is on
     * @param craftable every grade whose chunk tamper has a recipe, by key, with the uses its entry
     *                  declares, in the order the grades were resolved
     * @return the grade to build from, or null where there is none
     */
    public static String gradeFor(boolean enhanced, Map<String, Integer> craftable) {
        if (craftable == null || craftable.isEmpty()) return null;
        if (enhanced && craftable.containsKey(ENHANCED)) return ENHANCED;
        if (craftable.containsKey(USUAL)) return USUAL;

        String longest = null;
        int most = 0;
        for (Map.Entry<String, Integer> grade : craftable.entrySet()) {
            int uses = grade.getValue() == null ? 0
                : grade.getValue()
                    .intValue();
            // Strictly more, so the first of equals keeps its place.
            if (longest == null || uses > most) {
                longest = grade.getKey();
                most = uses;
            }
        }
        return longest;
    }
}
