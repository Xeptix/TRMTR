package com.trmtgtnh.apihost;

import java.util.Map;

import com.trmtgtnh.util.MobWear;

/**
 * Where a mod's {@code WearerRule} sits among the mob multiplier's rules (W3 spec 2.5; api/API.md, "WearerRule"):
 * after TRMTR has worked out the mob's own multiplier from its group, and before the player's mob list is applied, so
 * the player's list still wins.
 *
 * <p>
 * {@link MobWear#multiplier} holds the list's rules - a line's number replaces the group's while the list is on, a
 * nought on a line always holds, a '*' line counts where the group gives nothing and no line names the mob - and they
 * are not written twice. The rule's answer is handed to it as the group's own figure: the mob is weighed as a hostile
 * mob whose group is switched on at the rule's number (off at nought), with the list and its switch as the player set
 * them. Only the group's figure is read from the group, so which group carries it changes nothing else.
 *
 * <p>
 * Portable: the same file in all eight trees, as {@link MobWear} is.
 */
public final class RuledWearers {

    private RuledWearers() {}

    /** The mob's own multiplier, from its group alone, before the list: what a wearer rule is given. */
    public static float own(MobWear.Group group, MobWear.Switches switches) {
        return MobWear.multiplier(group, null, switches, null);
    }

    /** The player's list applied over a multiplier a wearer rule answered. */
    public static float overList(float ruled, String name, MobWear.Switches switches, Map<String, Float> list) {
        float carried = ruled > 0f ? ruled : 0f;
        MobWear.Switches carrying = new MobWear.Switches(carried > 0f, false, false, switches.custom, carried, 0f, 0f);
        return MobWear.multiplier(MobWear.Group.HOSTILE, name, carrying, list);
    }
}
