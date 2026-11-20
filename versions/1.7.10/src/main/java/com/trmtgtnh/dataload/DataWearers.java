package com.trmtgtnh.dataload;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.trmtgtnh.data.DataKind;
import com.trmtgtnh.data.DataTable;
import com.trmtgtnh.data.PlayerLayer;
import com.trmtgtnh.util.MobWear;

/**
 * What the wearers files say of a mob, for {@code ErosionEngine.multiplierFor} (W3 spec 2.1 and 2.2, task T5): its
 * {@code multiplier} and its {@code group}, laid under the player's mob list by {@link PlayerLayer#multiplier}, so the
 * player's lines still win and a nought there still holds.
 *
 * <p>
 * Game-free: a mob is asked by the name the mobs list knows it by ({@code EntityList.getEntityString} on 1.7.10, which
 * is also the spelling the files use), so the suite holds it without a world. Each answer is kept for the table it
 * came from, since the multiplier is asked on every sampled movement tick and a table is replaced whole on each read.
 */
public final class DataWearers {

    /** One mob's data: a multiplier, a group, either, or neither. */
    public static final class Wearer {

        /** No file names this mob. */
        public static final Wearer NONE = new Wearer(null, null);

        /** The files' multiplier, or null. */
        public final Double multiplier;
        /** The files' group name ({@code hostile}, {@code passive}, {@code villager}), or null. */
        public final String group;

        Wearer(Double multiplier, String group) {
            this.multiplier = multiplier;
            this.group = group;
        }

        /** The group the mob counts in: the file's where it gives one, else its own. */
        public MobWear.Group counted(MobWear.Group own) {
            if (group == null) return own;
            try {
                return MobWear.Group.valueOf(group.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException unknown) {
                return own;
            }
        }

        /** The multiplier with the player's mob list over it: what a mob wears the ground at. */
        public float multiplier(MobWear.Group own, String name, MobWear.Switches switches, Map<String, Float> list) {
            return PlayerLayer.multiplier(own, name, switches, list, multiplier, group);
        }

        /**
         * TRMTR's own figure before the player's list, what a mod's wearer rule is handed (trmtr-api): the file's
         * multiplier where its group is on, else the group's own.
         */
        public float own(MobWear.Group own, MobWear.Switches switches) {
            return PlayerLayer.multiplier(own, null, switches, null, multiplier, group);
        }
    }

    private DataWearers() {}

    /** The answers kept for one table, replaced as one so no answer is ever read against another table. */
    private static final class Kept {

        final DataTable table;
        final Map<String, Wearer> answers = new ConcurrentHashMap<String, Wearer>();

        Kept(DataTable table) {
            this.table = table;
        }
    }

    private static volatile Kept kept = new Kept(null);

    /** What the last data read says of a mob by its name. Any thread. */
    public static Wearer of(String name) {
        return of(DataLoad.table(), name);
    }

    /** What a table says of a mob by its name; {@link Wearer#NONE} where nothing does. Never throws. */
    public static Wearer of(DataTable table, String name) {
        if (table == null || name == null) return Wearer.NONE;
        Kept here = kept;
        if (here.table != table) {
            here = new Kept(table);
            kept = here;
        }
        Map<String, Wearer> answers = here.answers;
        Wearer known = answers.get(name);
        if (known != null) return known;
        Wearer found = read(table, name);
        answers.put(name, found);
        return found;
    }

    private static Wearer read(DataTable table, String name) {
        try {
            if (table.namedIds(DataKind.WEARERS)
                .isEmpty()) return Wearer.NONE;
            DataTable.Facts facts = table.wearer(name, null);
            if (facts.isEmpty()) return Wearer.NONE;
            Object multiplier = facts.value("multiplier");
            Object group = facts.value("group");
            Double asked = multiplier instanceof Number ? Double.valueOf(((Number) multiplier).doubleValue()) : null;
            String grouped = group instanceof String ? (String) group : null;
            if (asked == null && grouped == null) return Wearer.NONE;
            return new Wearer(asked, grouped);
        } catch (RuntimeException unexpected) {
            return Wearer.NONE;
        }
    }
}
