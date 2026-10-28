package com.trmtgtnh.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Which loose mobs wear the ground, and how hard: the rules of 0.9.228's four switches (Xep, 2026-10-20, every detail
 * in
 * docs/goals/0.9.228-decisions.md, "Which mobs wear the ground").
 *
 * <p>
 * A mob is in one of three groups - hostile (every monster, bosses too), villager (villagers, and wandering traders
 * where
 * the game has them) and passive (every other mob: animals, golems, water and ambient mobs) - and each group has a
 * switch
 * and a multiplier of a player's step: {@code fromHostileMobs} and {@code fromPassiveMobs} at 0.25,
 * {@code fromVillagers}
 * at 1.0. The fourth switch, {@code fromCustom}, is the curated list, {@code multipliers.mobs}, read as
 * {@link MobEntries}
 * reads it. Two rules decide between them: a line naming the mob replaces its group's multiplier ("list number wins"),
 * and a nought on a line always keeps the mob out, with {@code fromCustom} off as well ("zeros always hold"), so the
 * magic
 * tamper's stop button, which writes Name:0, always works. A '*' line counts for a mob no line names and whose group is
 * off. Players, and anything on a lead, are weighed before any of this, as they always were; the Golem of Ways never
 * is.
 *
 * <p>
 * An existing config is read into the switches so that it wears exactly as it did ({@link #upgrade}), and a crowd can
 * be
 * held to so many movers of a kind per chunk ({@link Caps}). Which group a mob is in, and what a version calls its
 * villagers, is each edition's own; everything here is plain Java, the same in every edition, and on the list
 * CoreStaysPortableTest keeps free of the game.
 */
public final class MobWear {

    /** The groups a loose mob can be in. */
    public enum Group {
        HOSTILE,
        PASSIVE,
        VILLAGER
    }

    /** The multipliers a new config gives each group, of a player's step. */
    public static final float HOSTILE_DEFAULT = 0.25f;

    public static final float PASSIVE_DEFAULT = 0.25f;

    public static final float VILLAGER_DEFAULT = 1.0f;

    /** How many movers of a kind a chunk lets wear the ground at once, once its cap is switched on. */
    public static final int CAP_DEFAULT = 8;

    /** The kinds a cap counts, one per switch: players, the three groups, and the mobs the curated list names. */
    public static final int PLAYERS = 0;

    public static final int HOSTILE = 1;

    public static final int PASSIVE = 2;

    public static final int VILLAGERS = 3;

    public static final int CUSTOM = 4;

    static final int KINDS = 5;

    /** At most so many unknown names are said at a server's start, so a long list cannot fill the log. */
    public static final int UNKNOWN_SAID = 20;

    private static final String EVERY_MOB = "*";

    private MobWear() {}

    /** The four switches and the three groups' multipliers, as read from the file. */
    public static final class Switches {

        public final boolean hostile;

        public final boolean passive;

        public final boolean villagers;

        public final boolean custom;

        public final float hostileMultiplier;

        public final float passiveMultiplier;

        public final float villagerMultiplier;

        public Switches(boolean hostile, boolean passive, boolean villagers, boolean custom, float hostileMultiplier,
            float passiveMultiplier, float villagerMultiplier) {
            this.hostile = hostile;
            this.passive = passive;
            this.villagers = villagers;
            this.custom = custom;
            this.hostileMultiplier = hostileMultiplier;
            this.passiveMultiplier = passiveMultiplier;
            this.villagerMultiplier = villagerMultiplier;
        }

        /** A new config's, or one reset to its defaults: all four on, each group at its default. */
        public static Switches fresh() {
            return new Switches(true, true, true, true, HOSTILE_DEFAULT, PASSIVE_DEFAULT, VILLAGER_DEFAULT);
        }
    }

    /**
     * How hard a loose mob of this group and name wears the ground, or nought when it is not tracked.
     *
     * @param group the mob's group, or null for one in none - which only the list can then count
     * @param name  the mob's name as its edition names it
     * @param list  the curated list as {@link MobEntries#table} reads it
     */
    public static float multiplier(Group group, String name, Switches switches, Map<String, Float> list) {
        Float named = named(list, name);
        if (named != null) {
            // A nought always holds, whatever the switches say.
            if (named.floatValue() <= 0f) return 0f;
            // The list's number wins over the group's - while the list is on.
            if (switches.custom) return named.floatValue();
        }
        float grouped = groupMultiplier(group, switches);
        if (grouped > 0f) return grouped;
        if (switches.custom && named == null && list != null) {
            Float every = list.get(EVERY_MOB);
            if (every != null) return every.floatValue();
        }
        return 0f;
    }

    /**
     * The kind a cap counts this mob as: the curated list's where a line names it and the list is on, else its group.
     */
    public static int kindOf(Group group, String name, Switches switches, Map<String, Float> list) {
        Float named = named(list, name);
        if (switches.custom && named != null && named.floatValue() > 0f) return CUSTOM;
        if (group == Group.HOSTILE) return HOSTILE;
        if (group == Group.VILLAGER) return VILLAGERS;
        if (group == Group.PASSIVE) return PASSIVE;
        return CUSTOM;
    }

    private static float groupMultiplier(Group group, Switches switches) {
        if (group == Group.HOSTILE) return switches.hostile ? switches.hostileMultiplier : 0f;
        if (group == Group.PASSIVE) return switches.passive ? switches.passiveMultiplier : 0f;
        if (group == Group.VILLAGER) return switches.villagers ? switches.villagerMultiplier : 0f;
        return 0f;
    }

    /** The weight of the line naming this mob, nought included, or null where no line names it. */
    private static Float named(Map<String, Float> list, String name) {
        if (list == null || name == null) return null;
        String key = name.trim()
            .toLowerCase(Locale.ROOT);
        if (key.isEmpty() || EVERY_MOB.equals(key)) return null;
        return list.get(key);
    }

    /**
     * What is said when a settings file was set aside as broken (0.9.228; Xep: "Say so in the log line"): a fresh
     * file starts with an empty mob list and every switch on, so the player is told where their own list still is.
     */
    public static String keptIn(String asideName) {
        return "Your mob list (multipliers.mobs) and every other setting you had are kept in " + asideName
            + ": copy back the lines you want.";
    }

    /** A config read from before 0.9.228, as the four switches and the list that wear exactly as it did. */
    public static final class Upgraded {

        public final Switches switches;

        public final String[] list;

        Upgraded(Switches switches, String[] list) {
            this.switches = switches;
            this.list = list;
        }
    }

    /**
     * Reads a list from before 0.9.228 into the switches and the list that wear exactly as it did (Xep: "Villagers
     * moved
     * over", and a '*' line "Groups on at its number"). Hostile and passive off, the curated list on with the player's
     * own lines; a line naming a villager taken out, and villagers on at its number (one where none was given) - no
     * such
     * line, villagers off; a '*' line taken out, and all three groups on at its number, a villager line's number still
     * the villagers'. Where the villager group holds a mob no old list named - the wandering trader - and the old list
     * would have weighed it differently from the group now, a line for it at its old weight keeps it as it was, a
     * nought
     * included. A line that does not read is left where it stands, counting for what it always did: nothing.
     *
     * @param old            the list as the file holds it; null is read as empty
     * @param villagerNames  every name this edition reads as a villager: the line the default list always held
     * @param otherVillagers the other mobs this edition puts in the villager group, by name
     */
    public static Upgraded upgrade(String[] old, Collection<String> villagerNames, Collection<String> otherVillagers) {
        String[] lines = old == null ? new String[0] : old;
        Map<String, Float> before = MobEntries.table(lines);
        Set<String> villagerKeys = keys(villagerNames);
        Float villagerWeight = null;
        Float every = null;
        List<String> kept = new ArrayList<String>(lines.length);
        for (String line : lines) {
            MobEntries.Entry entry = MobEntries.parse(line);
            if (entry == null) continue;
            String key = entry.name.toLowerCase(Locale.ROOT);
            if (entry.badWeight) {
                // A line the reader files under its whole text at a weight of one: a registry name such as
                // minecraft:villager reads so, its colon taken for a weight that is no number. Where that text names a
                // villager it moves as any villager line does, at the one the reader gives it; anything else stays.
                if (villagerKeys.contains(key)) villagerWeight = Float.valueOf(entry.weight);
                else kept.add(line);
                continue;
            }
            if (entry.weight < 0f || Float.isNaN(entry.weight)) {
                kept.add(line);
                continue;
            }
            if (EVERY_MOB.equals(key)) {
                every = Float.valueOf(entry.weight);
            } else if (villagerKeys.contains(key)) {
                // The later line wins, as the reader has it.
                villagerWeight = Float.valueOf(entry.weight);
            } else {
                kept.add(line);
            }
        }
        boolean groups = every != null;
        float all = groups ? every.floatValue() : 0f;
        boolean villagers = groups || villagerWeight != null;
        float villagerMultiplier = villagerWeight != null ? villagerWeight.floatValue()
            : groups ? all : VILLAGER_DEFAULT;
        Switches switches = new Switches(
            groups,
            groups,
            villagers,
            true,
            groups ? all : HOSTILE_DEFAULT,
            groups ? all : PASSIVE_DEFAULT,
            villagerMultiplier);
        if (villagers && otherVillagers != null) {
            for (String other : otherVillagers) {
                if (other == null || other.trim()
                    .isEmpty()
                    || before.containsKey(
                        other.trim()
                            .toLowerCase(Locale.ROOT)))
                    continue;
                float was = MobEntries.lookup(before, other.trim());
                if (was != villagerMultiplier) kept.add(other.trim() + ":" + plain(was));
            }
        }
        return new Upgraded(switches, kept.toArray(new String[kept.size()]));
    }

    /** A weight written back as a line writes it: 1.0 as "1", 0.5 as "0.5". */
    static String plain(float weight) {
        String written = Float.toString(weight);
        return written.endsWith(".0") ? written.substring(0, written.length() - 2) : written;
    }

    private static Set<String> keys(Collection<String> names) {
        if (names == null) return Collections.emptySet();
        Set<String> out = new LinkedHashSet<String>();
        for (String name : names) {
            if (name != null && !name.trim()
                .isEmpty()) out.add(
                    name.trim()
                        .toLowerCase(Locale.ROOT));
        }
        return out;
    }

    /**
     * The names in the list no mob of the game answers to, in the list's order, each once - to be drawn red on the
     * settings screen and said at a server's start, where at most {@link #UNKNOWN_SAID} are named. A '*' line is left
     * out: it names no mob. A line whose weight does not read is asked by the whole text the reader files it under - a
     * registry name such as minecraft:pig reads so, and a typo in one, minecraft:creper, is the name most worth
     * catching.
     *
     * @param registered every name the game answers to, compared without regard to case
     */
    public static List<String> unknown(String[] list, Collection<String> registered) {
        if (list == null) return Collections.emptyList();
        Set<String> known = keys(registered);
        Set<String> said = new LinkedHashSet<String>();
        List<String> out = new ArrayList<String>();
        for (String line : list) {
            MobEntries.Entry entry = MobEntries.parse(line);
            if (entry == null) continue;
            String key = entry.name.toLowerCase(Locale.ROOT);
            if (EVERY_MOB.equals(key) || known.contains(key)) continue;
            // Once, however the lines spell it: names are matched without regard to case.
            if (said.add(key)) out.add(entry.name);
        }
        return out;
    }

    /**
     * The per-chunk caps (Xep: "Movers per chunk"): at most so many movers of a kind wear the ground in one chunk at a
     * time, the rest walking without wearing. "At a time" is one round of the sampled movement - every mover is asked
     * on
     * the same tick - so the count starts again with each round. A cap of nought or less is no cap. One per server,
     * used
     * on its own thread.
     */
    public static final class Caps {

        private long round = Long.MIN_VALUE;

        private final Map<Long, int[]> counts = new HashMap<Long, int[]>();

        /** Whether one more mover of this kind may wear the ground in this chunk in this round, counting it if so. */
        public boolean admit(long round, long chunk, int kind, int cap) {
            if (cap <= 0) return true;
            if (round != this.round) {
                counts.clear();
                this.round = round;
            }
            Long key = Long.valueOf(chunk);
            int[] count = counts.get(key);
            if (count == null) {
                count = new int[KINDS];
                counts.put(key, count);
            }
            if (count[kind] >= cap) return false;
            count[kind]++;
            return true;
        }

        /** One chunk of one world as a key: the world's own number, and the chunk's coordinates. */
        public static long chunk(int world, int chunkX, int chunkZ) {
            return ((long) world << 44) ^ (((long) chunkX & 0x3FFFFFL) << 22) ^ ((long) chunkZ & 0x3FFFFFL);
        }
    }
}
