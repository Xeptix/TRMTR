package com.trmtgtnh.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Where the Wayfarer's Tamper is filed among the world's chests, and whether that place is real.
 *
 * <p>
 * A chest category in this version is only a name a world generator asks Forge's chest hooks for, which
 * is why the setting takes free-form names rather than a fixed list: a pack can point it at whatever its
 * own structures use without this mod having heard of them. Asking for a name nobody has used does not
 * fail, though. Forge makes an empty pool under it and hands that back, so a misspelt name, or the name
 * of a structure mod that is not installed, took the Wayfarer without complaint and filed it where no
 * generator would ever look, and a pack that named only that had no Wayfarer anywhere. Nothing said so.
 *
 * <p>
 * <b>Told apart by what nobody else did to it.</b> Forge's own pools are made with a count of items to
 * put in a chest, and a mod making its own sets one or fills it. A pool only a lookup made has a count
 * of nought to nought and holds nothing but the Wayfarer. That can be read only once every other mod has
 * had its turn, so the caller reads it as a server is about to start and hands the figures here.
 *
 * <p>
 * <b>A guess, and a safe one.</b> A mod that fills its pool later than that reads as unused, and the
 * cost is a warning and the Wayfarer in the library as well, where it would have been with the setting
 * left alone; nothing is ever taken out of a pool. What it cannot see is a real pool a world never draws
 * from, such as the bonus chest in a world made without one.
 *
 * <p>
 * It has nothing from the game in it, so its tests run without Minecraft, and it is on the list
 * CoreStaysPortableTest keeps free of it. Forge's names are handed in rather than written here, for the
 * same reason.
 */
public final class WayfarerCategories {

    /** The most letters added, dropped or changed for a name still to count as a slip for another. */
    public static final int MOST_EDITS = 2;

    private WayfarerCategories() {}

    /**
     * The categories a config list names: trimmed, blanks dropped, in the order written; the fallback
     * alone when that leaves nothing.
     *
     * <p>
     * A name written twice is kept twice. The Wayfarer has one weight for every pool it is filed in, so
     * filing it twice in one is the only way a pack can make one place likelier than another, and it is
     * what a repeated name always did. Names are matched exactly, as Forge matches them.
     */
    public static List<String> named(String[] configured, String fallback) {
        List<String> names = new ArrayList<String>();
        if (configured != null) {
            for (String entry : configured) {
                if (entry == null) continue;
                String name = entry.trim();
                if (!name.isEmpty()) names.add(name);
            }
        }
        if (names.isEmpty()) names.add(fallback);
        return Collections.unmodifiableList(names);
    }

    /** True for a pool only a lookup made: no count, and no entry but the Wayfarer's own. */
    public static boolean unused(int countMin, int countMax, int otherEntries) {
        return countMin == 0 && countMax == 0 && otherEntries == 0;
    }

    /**
     * Whether the Wayfarer has to go into the fallback as well, because every place it was filed is unused.
     *
     * <p>
     * Only when all of them are: one real pool among misspelt ones still puts the Wayfarer somewhere,
     * and the log naming the misspelt ones is the whole answer. Never when the fallback was itself among
     * the names, since a further entry there would add to its odds unasked. Repeats change nothing.
     */
    public static boolean fallsBack(List<String> named, Collection<String> unused, String fallback) {
        if (named == null || named.isEmpty() || unused == null) return false;
        if (named.contains(fallback)) return false;
        return unused.containsAll(named);
    }

    /**
     * The known category this name is most likely a slip for, or null.
     *
     * <p>
     * Case is ignored and up to two letters may differ, which catches the slips a hand-edited list makes
     * - pyramidDesertChest for Forge's pyramidDesertyChest - without offering one real name for another.
     * The nearest wins, the first given on a tie, and a name that is itself known exactly is no slip.
     */
    public static String nearMiss(String name, String[] known) {
        if (name == null || known == null) return null;
        String asked = name.toLowerCase(Locale.ROOT);
        String best = null;
        int bestDistance = MOST_EDITS + 1;
        for (String candidate : known) {
            if (candidate == null) continue;
            if (candidate.equals(name)) return null;
            int distance = distance(asked, candidate.toLowerCase(Locale.ROOT));
            if (distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static int distance(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) previous[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int change = previous[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
                current[j] = Math.min(change, Math.min(previous[j] + 1, current[j - 1] + 1));
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }
}
