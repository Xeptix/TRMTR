package com.trmtgtnh.data;

import java.util.Locale;
import java.util.Map;

import com.trmtgtnh.util.MobWear;

/**
 * Layer 4, the player's own settings, over what the data says (W3 spec 2.2: "data beats detection; the player beats
 * data").
 *
 * <p>
 * Two rules, written once here so every tree's wiring asks the same question the same way. A block's family: an
 * exclusion, or a family switched off, keeps it from wearing whatever any file says; a block on the player's own list
 * for a family wears as that family; otherwise a file's family (or {@code none}, which stands it aside) beats what
 * detection found. A mob's multiplier: a line in the player's mob list still wins and a nought there still holds, a
 * group the player switched off stays off, and otherwise a file's multiplier replaces the group's.
 *
 * <p>
 * The player's lists are only read here. Data is never written into them ({@code recordDetectedSurfaces} is handed
 * detection's findings alone), so removing a mod leaves nothing of its data in anybody's config.
 *
 * <p>
 * Portable: the same file in all eight trees.
 */
public final class PlayerLayer {

    private PlayerLayer() {}

    /** The family that stands a block aside in a data file. */
    public static final String NONE = "none";

    /**
     * The family a block wears as, or null when it does not wear.
     *
     * @param detected     what detection found (layer 0), or null
     * @param fromData     the merged data's {@code family} (layers 1 to 3), {@code none}, or null when no file names it
     * @param playerListed the family whose player list names the block (layer 4), or null
     * @param excluded     whether the player excluded the block
     * @param familyOn     whether the player has the answer's family switched on (asked of the answer)
     */
    public static String family(String detected, String fromData, String playerListed, boolean excluded,
        FamilySwitch familyOn) {
        if (excluded) return null;
        String answer;
        if (playerListed != null) answer = playerListed;
        else if (fromData != null) answer = NONE.equals(fromData) ? null : fromData;
        else answer = detected;
        if (answer == null) return null;
        return familyOn == null || familyOn.on(answer) ? answer : null;
    }

    /** Whether the player has a family switched on. */
    public interface FamilySwitch {

        boolean on(String family);
    }

    /**
     * How hard a mob wears the ground, data included: {@link MobWear#multiplier}'s rules with the file's group and
     * multiplier between the built-in groups and the player's list.
     *
     * @param group          the mob's built-in group, or null
     * @param name           the mob's name as its edition names it
     * @param switches       the player's four switches
     * @param list           the player's mob list, as {@code MobEntries.table} reads it (read only)
     * @param dataMultiplier the merged data's {@code multiplier}, or null
     * @param dataGroup      the merged data's {@code group}, or null
     */
    public static float multiplier(MobWear.Group group, String name, MobWear.Switches switches, Map<String, Float> list,
        Double dataMultiplier, String dataGroup) {
        MobWear.Group counted = group;
        if (dataGroup != null) {
            try {
                counted = MobWear.Group.valueOf(dataGroup.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException unknown) {
                counted = group;
            }
        }
        float player = MobWear.multiplier(counted, name, switches, list);
        Float named = null;
        if (list != null && name != null) named = list.get(
            name.trim()
                .toLowerCase(Locale.ROOT));
        // The player's line: a nought always holds, and a number wins while the list is on.
        if (named != null && (named.floatValue() <= 0f || switches.custom)) return player;
        if (dataMultiplier == null) return player;
        // A group the player switched off stays off, whatever the file says.
        if (counted != null && !groupOn(counted, switches)) return player;
        return dataMultiplier.floatValue();
    }

    private static boolean groupOn(MobWear.Group group, MobWear.Switches switches) {
        switch (group) {
            case HOSTILE:
                return switches.hostile;
            case PASSIVE:
                return switches.passive;
            default:
                return switches.villagers;
        }
    }
}
