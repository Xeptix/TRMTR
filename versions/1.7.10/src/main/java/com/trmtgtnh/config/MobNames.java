package com.trmtgtnh.config;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.util.LogSample;
import com.trmtgtnh.util.MobWear;

/**
 * The names this game answers to for its mobs (0.9.228), asked by the settings screen - to draw a name no mob answers
 * to in red, and to offer every mob in its picker - and at a server's start, to say once in the log which names in
 * {@code multipliers.mobs} answer to none (Xep, 2026-10-20: "nor does it seem to check if the name you put in is
 * correct or not"). At a server's start rather than when the settings are read, because mods register their mobs
 * after the settings are first read.
 *
 * <p>
 * On 1.7.10 a mob's name is the one its class has in {@code EntityList} - "Villager", "Zombie", and "modid.Name" for a
 * mod's - which is the name {@code EntityList.getEntityString} gives the engine when it looks a mob up in the list. A
 * mob is a class the registry holds that is an {@code EntityLiving} and can be made: the registry's own abstract
 * entries, "Mob" and "Monster", name no mob that ever walks, and nor does an arrow, an item or a painting. This
 * edition maps no old names to new ones, so there are no aliases to answer to.
 */
public final class MobNames {

    private MobNames() {}

    /** Every mob name the game's registry holds, sorted without regard to case. */
    public static List<String> registered() {
        return mobsIn(EntityList.stringToClassMapping);
    }

    /** The mob names of a registry, sorted without regard to case: a stand-in registry's in a test. */
    static List<String> mobsIn(Map<String, ? extends Class<?>> mapping) {
        List<String> out = new ArrayList<String>();
        if (mapping == null) return out;
        for (Map.Entry<String, ? extends Class<?>> each : new ArrayList<Map.Entry<String, ? extends Class<?>>>(
            mapping.entrySet())) {
            if (each.getKey() != null && isMob(each.getValue())) out.add(each.getKey());
        }
        Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    /** Whether a registered class is a mob that can walk: a living thing of the game's own kind, not abstract. */
    static boolean isMob(Class<?> type) {
        return type != null && EntityLiving.class.isAssignableFrom(type) && !Modifier.isAbstract(type.getModifiers());
    }

    /**
     * Said once as a server starts, where the mobs list names any mob no mob in this game answers to. Silent where
     * every name answers.
     */
    public static void sayUnknown() {
        String line = unknownLine(MobWear.unknown(currentList(), registered()));
        if (line != null) Trmt.LOG.warn(line);
    }

    /** The list as the settings hold it now. */
    private static String[] currentList() {
        net.minecraftforge.common.config.Configuration config = TrmtConfig.raw();
        if (config == null || !config.hasCategory(TrmtConfig.CATEGORY_MULTIPLIERS)) return new String[0];
        net.minecraftforge.common.config.Property mobs = config.getCategory(TrmtConfig.CATEGORY_MULTIPLIERS)
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
        return offered(registered, search, golemName());
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

    /** The Golem of Ways' registered name, or null before it is registered. */
    private static String golemName() {
        try {
            return EntityList.classToStringMapping.get(com.trmtgtnh.entity.EntityGolemOfWays.class);
        } catch (Throwable unregistered) {
            return null;
        }
    }
}
