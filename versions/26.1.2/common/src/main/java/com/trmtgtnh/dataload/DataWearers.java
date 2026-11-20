package com.trmtgtnh.dataload;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.trmtgtnh.data.DataKind;
import com.trmtgtnh.data.DataTable;
import com.trmtgtnh.data.PlayerLayer;
import com.trmtgtnh.util.MobWear;

/**
 * What the wearers files say of a mob, for {@code ErosionEngine.multiplierFor} (W3 spec 2.1 and 2.2, task T5; carried
 * from the 1.16.5 tree's class of the same name in T18): its {@code multiplier} and its {@code group}, laid under the
 * player's mob list by {@link PlayerLayer#multiplier}, so the player's lines still win and a nought there still holds.
 *
 * <p>
 * A mob is asked by its registry id ({@code ns:name}, the spelling the files use) and by the entity type tags it is
 * in ({@code #ns:tag} in a file). The rules are game-free, so the suite holds them without a world; only
 * {@link #of(Object)} reads the game, for the id and the tags. Each answer is kept for the table it came from, since
 * the multiplier is asked on every sampled movement tick and a table is replaced whole on each read - a data-pack
 * reload, which is also what changes the tags, always publishes a new table.
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

    /** How a mob's tags are found, asked only where a wearers file names a tag. */
    public interface Tags {

        /** The entity type tags this mob is in, as {@code ns:tag}; null or empty where none. */
        Collection<String> of(String entityId);
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

    /**
     * What the last data read says of a mob, given its {@code EntityType} (or null). Its id from the game's entity
     * registry, its tags from the game's entity type tags. Any thread; never throws.
     */
    public static Wearer of(Object entityType) {
        if (!(entityType instanceof net.minecraft.world.entity.EntityType)) return Wearer.NONE;
        final net.minecraft.world.entity.EntityType<?> type = (net.minecraft.world.entity.EntityType<?>) entityType;
        String id;
        try {
            net.minecraft.resources.Identifier key = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type);
            id = key == null ? null : key.toString();
        } catch (RuntimeException | LinkageError unnamed) {
            id = null;
        }
        return of(DataLoad.table(), id, unused -> tagsOf(type));
    }

    /**
     * The entity type tags a type is in, as {@code ns:tag}, or null where they cannot be read. Read off the type's own
     * registry holder, which is what a tag bound on either side fills in.
     */
    static Collection<String> tagsOf(net.minecraft.world.entity.EntityType<?> type) {
        try {
            List<String> out = new ArrayList<String>();
            type.builtInRegistryHolder()
                .tags()
                .forEach(
                    tag -> out.add(
                        tag.location()
                            .toString()));
            return out;
        } catch (RuntimeException | LinkageError unread) {
            return null;
        }
    }

    /** What the last data read says of a mob by its id, with no tags. Any thread. */
    public static Wearer of(String name) {
        return of(DataLoad.table(), name, null);
    }

    /** What a table says of a mob by its id; {@link Wearer#NONE} where nothing does. Never throws. */
    public static Wearer of(DataTable table, String name) {
        return of(table, name, null);
    }

    /** What a table says of a mob by its id and, where a file names a tag, its tags. Never throws. */
    public static Wearer of(DataTable table, String name, Tags tags) {
        if (table == null || name == null) return Wearer.NONE;
        Kept here = kept;
        if (here.table != table) {
            here = new Kept(table);
            kept = here;
        }
        Map<String, Wearer> answers = here.answers;
        Wearer known = answers.get(name);
        if (known != null) return known;
        Wearer found = read(table, name, tags);
        answers.put(name, found);
        return found;
    }

    private static Wearer read(DataTable table, String name, Tags tags) {
        try {
            boolean byTag = !table.namedTags(DataKind.WEARERS)
                .isEmpty();
            if (table.namedIds(DataKind.WEARERS)
                .isEmpty() && !byTag) return Wearer.NONE;
            Collection<String> in = byTag && tags != null ? tags.of(name) : null;
            DataTable.Facts facts = table.wearer(name, in);
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
