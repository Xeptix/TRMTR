package com.trmtgtnh.item;

import net.minecraft.world.entity.player.Player;

/**
 * What happens the moment a guide book is actually opened.
 *
 * <p>
 * Its own class rather than a line inside the item, because reading a book is going to be worth
 * something later - an achievement per book is already planned, and a trophy after that - and
 * every one of those wants the same single place to hang from. Today it records the reading and
 * nothing else, which is deliberately a seam rather than a feature.
 *
 * <p>
 * Server side. The screen opens on the client without asking anyone; this is the half that counts.
 */
public final class BookReading {

    private BookReading() {}

    /** Records that this player has read this book. */
    public static void read(Player player, GuideBook book) {
        if (player == null || book == null) return;
        if (player.level == null || player.level.isClientSide()) return;
        ModAchievements.onRead(player, book);
    }
}
