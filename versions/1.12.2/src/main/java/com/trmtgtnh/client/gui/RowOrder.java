package com.trmtgtnh.client.gui;

import java.util.List;

/**
 * The order a Wear Table row cycles its pictures in: the family's road block first (0.9.224; Xep: "Fix in 0.9.224").
 *
 * <p>
 * A row cycles through every block detected for its family, in the order the registry lists them, and is opened on the
 * first. That is grass and sand here, where vanilla's own blocks come first; on 1.16.5 the first block of the grass
 * family is dried kelp and of the sand family black concrete powder, and on 1.12.2 the first of the sand family white
 * concrete powder, so the same row opened on a different block in each edition. Each row now starts on the block the
 * demonstrate yard lays for that family (CommandTrmt.roadBlock) and cycles the rest after it, in every edition - here
 * the sand row now opens on sand. Kept free of the game's types so a test can hold it.
 */
public final class RowOrder {

    private RowOrder() {}

    /** The road block's picture is moved to the front of the list, the rest kept in their order; nothing if absent. */
    public static <T> void leadWith(List<T> pictures, java.util.function.Predicate<T> isRoad) {
        for (int i = 0; i < pictures.size(); i++) {
            if (isRoad.test(pictures.get(i))) {
                if (i > 0) pictures.add(0, pictures.remove(i));
                return;
            }
        }
    }
}
