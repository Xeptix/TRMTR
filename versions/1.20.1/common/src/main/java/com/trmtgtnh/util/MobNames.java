package com.trmtgtnh.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * What the mobs list's names answer to in this edition (0.9.228): a name no mob in the game answers to is drawn red on
 * the settings screen and said once as a server starts (Xep: "a typed name no mob answers to shown red with a tooltip,
 * and named once in the server log at start"), and the settings screen's Add mob list offers the game's mobs by what is
 * typed in its search.
 *
 * <p>
 * This edition names a mob by its registry name, and reads a line written as the 1.7.10 edition writes it -
 * {@code Villager}, {@code Skeleton} - as the mobs it became ({@link OldNames#mobs}). So a line answers when its name is
 * a registered mob's, or when any mob it stands for is: {@code Villager} answers through {@code minecraft:villager},
 * and {@code Unicorn} answers to nothing. Which names are unknown is {@link MobWear#unknown}'s to say; this hands it
 * every name the edition answers to. Plain Java, so a test can ask it with a stand-in registry; which of the game's
 * entities are mobs is {@code erosion.MobGroups.registered}'s.
 */
public final class MobNames {

    private MobNames() {}

    /**
     * Every name this edition answers to, for these lines: the registered mobs' names, and each line's own name where
     * an old-name reading of it is a registered mob's.
     */
    public static Set<String> answering(String[] lines, Collection<String> registered) {
        Set<String> known = new LinkedHashSet<String>();
        Set<String> keys = new LinkedHashSet<String>();
        if (registered != null) {
            for (String name : registered) {
                if (name == null) continue;
                known.add(name);
                keys.add(name.toLowerCase(Locale.ROOT));
            }
        }
        if (lines == null) return known;
        for (String line : lines) {
            MobEntries.Entry entry = MobEntries.parse(line);
            if (entry == null || entry.badWeight) continue;
            for (String now : OldNames.mobs(entry.name)) {
                if (keys.contains(now.toLowerCase(Locale.ROOT))) {
                    known.add(entry.name);
                    break;
                }
            }
        }
        return known;
    }

    /** The names in these lines no mob answers to, in the list's order, each once - {@link MobWear#unknown}. */
    public static List<String> unknown(String[] lines, Collection<String> registered) {
        return MobWear.unknown(lines, answering(lines, registered));
    }

    /**
     * Whether a name is written as this version's registry writes one: a lower-case namespace, a colon and a lower-case
     * path - {@code minecraft:villager}, {@code twilightforest:deer}. The reader takes the number after a line's last
     * colon, so a registry name with no number of its own reads as a name at one whose number did not read; that is no
     * mistake of the player's, and the settings read does not warn about it.
     */
    public static boolean registryName(String text) {
        return text != null && text.trim()
            .matches("[a-z0-9_.-]+:[a-z0-9_./-]+");
    }

    /**
     * The line said at a server's start for these unknown names, or null for none: the count, then the first
     * {@link MobWear#UNKNOWN_SAID} names, each clipped as every bounded log line here clips one, and the rest counted -
     * the 1.7.10 edition's words.
     */
    public static String unknownLine(List<String> unknown) {
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
     * The mobs the settings screen's Add mob list offers for a search: every mob name holding the words typed, without
     * regard to case, in the order given - all of them for no words - and never the one left out, the Golem of Ways,
     * which never wears the ground by walking, so that a line naming it would do nothing.
     */
    public static List<String> offered(List<String> registered, String search, String leftOut) {
        List<String> out = new ArrayList<String>();
        if (registered == null) return out;
        String words = search == null ? ""
            : search.trim()
                .toLowerCase(Locale.ROOT);
        for (String name : registered) {
            if (name == null || name.equals(leftOut)) continue;
            if (words.isEmpty() || name.toLowerCase(Locale.ROOT)
                .contains(words)) out.add(name);
        }
        return out;
    }
}
