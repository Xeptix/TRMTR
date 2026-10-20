package com.trmtgtnh.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;

/**
 * A draw from a list, made again when what came up is not on offer.
 *
 * <p>
 * Written for a librarian's enchanted book. The game picks one enchantment from its book list, evenly,
 * and asks nothing about this mod's feature switches, so a switched-off unlock comes up as often as any
 * other. The list cannot simply lose it - it is a final field other mods add to - so the pick is looked at
 * once it is made, and one that is not on offer is replaced by an even draw from the entries that are.
 * Every offered entry is then exactly as likely as every other, as though the switched-off ones had never
 * been listed, and an entry the list holds twice counts twice, as in the game's draw.
 *
 * <p>
 * Three promises the caller relies on. A pick that is on offer comes back untouched and without asking
 * the random source, so with every switch on the game's draws go exactly as they always did. A list with
 * nothing on offer gives the pick back as it was, because an empty draw is an exception in the middle of a
 * villager's tick. And each entry is asked once, so the pool that is counted and the entry handed out rest
 * on the same answers.
 *
 * <p>
 * Nothing from the game in it, so it is on the list CoreStaysPortableTest keeps free of Minecraft.
 */
public final class OfferedDraw {

    private OfferedDraw() {}

    /**
     * @param drawn   what the first draw produced; returned as it is when offered, or when null
     * @param from    the list it was drawn from, holes allowed
     * @param offered whether an entry may be handed out
     * @param random  asked once, and only when the pick is not on offer
     */
    public static <T> T redraw(T drawn, T[] from, Predicate<? super T> offered, Random random) {
        if (drawn == null || offered.test(drawn)) return drawn;
        List<T> kept = new ArrayList<T>(from.length);
        for (T entry : from) {
            if (entry != null && offered.test(entry)) kept.add(entry);
        }
        return kept.isEmpty() ? drawn : kept.get(random.nextInt(kept.size()));
    }
}
