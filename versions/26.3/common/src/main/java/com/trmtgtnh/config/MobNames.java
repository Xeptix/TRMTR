package com.trmtgtnh.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.util.LogSample;
import com.trmtgtnh.util.MobEntries;
import com.trmtgtnh.util.MobWear;
import com.trmtgtnh.util.OldNames;

/**
 * The names this game answers to for its mobs (0.9.228), asked by the settings screen - to draw a name no mob answers
 * to in red, and to offer every mob in its picker - and at a server's start, to say once in the log which names in
 * {@code multipliers.mobs} answer to none (Xep, 2026-10-20: "nor does it seem to check if the name you put in is
 * correct or not"). At a server's start rather than when the settings are read, because mods register their mobs
 * after the settings are first read. The 1.7.10 edition's {@code MobNames}, with this game's names.
 *
 * <p>
 * Here a mob's name is its registry name - {@code minecraft:villager}, {@code twilightforest:deer} - which is the name
 * the engine looks a mob up in the list by; and a line written as the 1.7.10 edition writes it - {@code Villager},
 * {@code Skeleton} - answers too wherever a mob it is read as is registered ({@link OldNames#mobs}), since the list
 * reads it as that mob ({@link TrmtConfig#named}).
 *
 * <p>
 * <strong>Which registered entities are mobs</strong> is told without making one: an entity type is no class here,
 * and the registry says nothing of what its factory makes. Every type whose spawn category is not {@code MISC} is a
 * mob - those categories hold nothing else - and of the {@code MISC} types, which are also the arrows, the boats, the
 * items and the armour stand, a mob is one with a spawn egg: the villager, the iron and snow golems. A modded mob filed
 * as {@code MISC} with no egg of its own is not offered; typed by hand it is drawn red and named at a server's start,
 * and still counts as the list says.
 */
public final class MobNames {

    private MobNames() {}

    /** Every mob name the game's registry holds, sorted without regard to case. */
    public static List<String> registered() {
        Set<EntityType<?>> egged = new HashSet<EntityType<?>>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (!(item instanceof SpawnEggItem)) continue;
            try {
                EntityType<?> laid = SpawnEggItem.getType(item.getDefaultInstance());
                if (laid != null) egged.add(laid);
            } catch (RuntimeException unbound) {
                // An egg whose components are not bound yet says nothing, which leaves its mob to its category.
            }
        }
        Map<String, Boolean> mapping = new LinkedHashMap<String, Boolean>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (key != null) mapping.put(key.toString(), Boolean.valueOf(isMob(type.getCategory(), egged.contains(type))));
        }
        return mobsIn(mapping);
    }

    /** Whether a registered entity is a mob, by its spawn category and whether it has an egg (see the class). */
    static boolean isMob(MobCategory category, boolean hasEgg) {
        return hasEgg || (category != null && category != MobCategory.MISC);
    }

    /** The mob names of a registry, each with whether it is a mob, sorted without regard to case: a stand-in's in a test. */
    static List<String> mobsIn(Map<String, Boolean> mapping) {
        List<String> out = new ArrayList<String>();
        if (mapping == null) return out;
        for (Map.Entry<String, Boolean> each : mapping.entrySet()) {
            if (each.getKey() != null && Boolean.TRUE.equals(each.getValue())) out.add(each.getKey());
        }
        Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    /**
     * Every name the game answers to for these lines: the registry's mobs, and each line's own name where it is the
     * 1.7.10 edition's spelling of one - {@code Villager} for {@code minecraft:villager}, {@code Skeleton} for the
     * skeleton - as the list reads it.
     */
    public static List<String> answering(String[] lines, Collection<String> registered) {
        List<String> out = new ArrayList<String>(registered == null ? Collections.<String>emptyList() : registered);
        if (lines == null || registered == null) return out;
        Set<String> known = new HashSet<String>();
        for (String name : registered) {
            if (name != null) known.add(name.toLowerCase(Locale.ROOT));
        }
        for (String line : lines) {
            MobEntries.Entry entry = MobEntries.parse(line);
            if (entry == null || entry.badWeight) continue;
            for (String became : OldNames.mobs(entry.name)) {
                if (known.contains(became)) {
                    out.add(entry.name.trim());
                    break;
                }
            }
        }
        return out;
    }

    /**
     * The names in these lines no mob of the game answers to ({@link MobWear#unknown}), against the registry's mobs and
     * the 1.7.10 spellings that stand for them ({@link #answering}).
     */
    public static List<String> unknown(String[] lines, Collection<String> registered) {
        return MobWear.unknown(lines, answering(lines, registered));
    }

    /**
     * Said once as a server starts, where the mobs list names any mob no mob in this game answers to. Silent where
     * every name answers.
     */
    public static void sayUnknown() {
        String line = unknownLine(unknown(currentList(), registered()));
        if (line != null) Trmt.LOG.warn(line);
    }

    /** The list as the settings hold it now. */
    private static String[] currentList() {
        ConfigFile config = TrmtConfig.raw();
        if (config == null || !config.hasCategory(TrmtConfig.CATEGORY_MULTIPLIERS)) return new String[0];
        ConfigFile.Setting mobs = config.getCategory(TrmtConfig.CATEGORY_MULTIPLIERS)
            .get("mobs");
        return mobs == null || !mobs.isList() ? new String[0] : mobs.getStringList();
    }

    /**
     * The line said at a server's start for these unknown names, or null for none: the count, then the first
     * {@link MobWear#UNKNOWN_SAID} names, each clipped as every bounded log line here clips one, and the rest counted.
     */
    static String unknownLine(List<String> unknown) {
        if (unknown == null || unknown.isEmpty()) return null;
        int count = unknown.size();
        StringBuilder out = new StringBuilder("The mobs list names ").append(count)
            .append(count == 1 ? " mob" : " mobs")
            .append(" no mob in this game answers to: ");
        int said = Math.min(count, MobWear.UNKNOWN_SAID);
        for (int at = 0; at < said; at++) {
            if (at > 0) out.append(", ");
            String name = String.valueOf(unknown.get(at));
            out.append(name.length() <= LogSample.LONGEST ? name : name.substring(0, LogSample.LONGEST) + "...");
        }
        if (count > said) out.append(" (and ")
            .append(count - said)
            .append(" more)");
        return out.toString();
    }

    /**
     * The mobs the settings screen's picker offers for a search: every mob name holding the words typed, without
     * regard to case, in the order {@link #mobsIn} gives - all of them for no words. The Golem of Ways is left out,
     * because it never wears the ground by walking and a line naming it would do nothing.
     */
    public static List<String> offered(List<String> registered, String search) {
        return offered(registered, search, com.trmtgtnh.entity.ModEntities.GOLEM_OF_WAYS.toString());
    }

    /** {@link #offered(List, String)}, with the name left out given: the Golem of Ways', or a stand-in's in a test. */
    static List<String> offered(List<String> registered, String search, String leftOut) {
        List<String> out = new ArrayList<String>();
        String words = search == null ? ""
            : search.trim()
                .toLowerCase(Locale.ROOT);
        for (String name : registered) {
            if (name.equals(leftOut)) continue;
            if (words.isEmpty() || name.toLowerCase(Locale.ROOT)
                .contains(words)) out.add(name);
        }
        return out;
    }
}
