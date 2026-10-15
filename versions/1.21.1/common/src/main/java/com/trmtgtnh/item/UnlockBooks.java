package com.trmtgtnh.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.enchantment.Enchantment;

import com.trmtgtnh.util.OfferedDraw;

/**
 * Which of the unlock books may still be handed out new.
 *
 * <p>
 * Turning a feature off should stop its book being made, not only stop it working, and the book is made in four
 * places that each ask differently. The recipe and the chest finds read the switch once, as they are registered at
 * startup, and keep what they read until a restart. An enchanting table and an anvil ask the enchantment, and here that
 * is {@code MixinUnlocksAnswerTheirSwitch} answering from the switch. A librarian asks nothing at all: it draws from
 * {@code #minecraft:tradeable}, a tag read once as the world loads, which the three are in. So
 * {@code MixinLibrarianDrawsAgain} hands the librarian's draw here, and a switched-off unlock is drawn again from what
 * is on offer - as {@code MixinLibrarianBooks} does in 1.7.10 and 1.12.2. 1.16.5 and 1.20.1 needed none, because their
 * librarian asked each enchantment {@code isTradeable} before it drew; 1.21 took that question away with the class.
 *
 * <p>
 * Drawn again from the tag the librarian drew from, so a redraw can land on anything the first draw could have, less
 * the switched-off unlocks - every edition redraws from exactly what it replaced.
 *
 * <p>
 * Only this mod's three are ever held back: another mod's enchantment keeps whatever that mod decided about librarians.
 * And only the librarian's own draw is covered - a mod that picks from the tag for itself, for its own villagers or its
 * own loot, is not.
 *
 * <p>
 * Neither reaches back. A trade a librarian has already rolled is saved with the villager and stays, and a book already
 * made stays a book, which will not go onto a tool while its switch is off except in the hands of a creative player.
 */
public final class UnlockBooks {

    private UnlockBooks() {}

    /** Whether this is one of the three unlocks, whatever its switch says. */
    public static boolean isUnlock(Holder<Enchantment> enchantment) {
        return TamperEnchantment.of(enchantment) != null;
    }

    /** False only for one of the three unlocks whose feature is switched off. Read live. */
    public static boolean offered(Holder<Enchantment> enchantment) {
        return TamperEnchantment.offered(enchantment);
    }

    /**
     * A librarian's pick, drawn again from the tag it was drawn from where it is a switched-off unlock.
     *
     * @param random the villager's own, which the game drew the pick from and goes on to draw the level and the price
     *               from. Asked only when the pick is not on offer, so a draw that stands leaves every librarian's level
     *               and price to come out of the sequence the game would have used.
     */
    public static Optional<Holder<Enchantment>> forLibrarian(Optional<Holder<Enchantment>> drawn,
        Registry<Enchantment> registry, TagKey<Enchantment> tradeable, RandomSource random) {
        if (drawn == null || drawn.isEmpty()) return drawn;
        // The rule is OfferedDraw's, as in 1.20.1 - a pick on offer back untouched without asking the random, an even
        // draw from what is offered, the pick itself where nothing is - in the portable core where its tests hold it.
        List<Holder<Enchantment>> listed = new ArrayList<Holder<Enchantment>>();
        Optional<HolderSet.Named<Enchantment>> tag = registry.getTag(tradeable);
        if (tag.isPresent()) {
            for (Holder<Enchantment> each : tag.get()) listed.add(each);
        }
        @SuppressWarnings("unchecked")
        Holder<Enchantment>[] from = listed.toArray(new Holder[listed.size()]);
        return Optional.of(OfferedDraw.redraw(drawn.get(), from, UnlockBooks::offered, villagers(random)));
    }

    /** The villager's own random, as the java.util.Random the portable core draws with: asked for bounded ints alone. */
    private static java.util.Random villagers(RandomSource random) {
        return new java.util.Random() {

            private static final long serialVersionUID = 1L;

            @Override
            public int nextInt(int bound) {
                return random.nextInt(bound);
            }
        };
    }
}
