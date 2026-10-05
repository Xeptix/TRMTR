package com.trmtgtnh.item;

import java.util.Random;
import java.util.function.Predicate;

import net.minecraft.world.item.enchantment.Enchantment;

import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.util.OfferedDraw;

/**
 * Which of the unlock books may still be handed out new.
 *
 * <p>
 * Turning a feature off should stop its book being made, not only stop it working, and the book is made
 * in four places that each ask differently. The recipe and the chest finds read the switch once, as they
 * are registered at startup, and keep what they read until a restart. An enchanting table asks the
 * enchantment, and each of the three answers from its switch whether the table is asking about a tool
 * (canApplyAtEnchantingTable) or a plain book (isAllowedOnBooks). A librarian asks nothing at all: it draws
 * from the game's book list, which an enchantment joins once, for good, as it registers. So
 * MixinLibrarianBooks hands the librarian's draw here, and a switched-off unlock is drawn again from what is
 * on offer.
 *
 * <p>
 * Drawn again from the whole enchantment registry, because that is what the game itself draws a
 * librarian's book from here - {@code Enchantment.REGISTRY.getRandomObject}, which asks nothing about
 * books either. The other edition redraws from the game's book list, which is the list its own
 * librarian draws from; both editions therefore redraw from exactly what they replaced, and a redraw
 * can land on anything the first draw could have.
 *
 * <p>
 * What is on offer is asked of the enchantment itself, so a table and a librarian cannot come to different
 * answers. Only this mod's three are ever held back: another mod's enchantment that declines table books
 * keeps whatever that mod decided about librarians. And only the librarian's own draw is covered - a mod
 * that picks from the book list for itself, for its own villagers or its own loot, is not.
 *
 * <p>
 * Neither reaches back. A trade a librarian has already rolled is saved with the villager and stays, and a
 * book already made stays a book, which will not go onto a tool while its switch is off except in the
 * hands of a creative player.
 */
public final class UnlockBooks {

    /** {@link #offered}, in the shape the draw asks for it. */
    private static final Predicate<Enchantment> OFFERED = new Predicate<Enchantment>() {

        @Override
        public boolean test(Enchantment enchantment) {
            return offered(enchantment);
        }
    };

    private UnlockBooks() {}

    /** Whether this is one of the three unlocks, whatever its switch says. */
    public static boolean isUnlock(Enchantment enchantment) {
        return enchantment != null && (enchantment == EnchReinforce.INSTANCE || enchantment == EnchWard.INSTANCE
            || enchantment == EnchLight.INSTANCE);
    }

    /** False only for one of the three unlocks whose feature is switched off. Read live. */
    public static boolean offered(Enchantment enchantment) {
        if (!isUnlock(enchantment)) return true;
        // Both older editions ask the enchantment whether it is allowed on a book, which is Forge's
        // question and which this edition's TamperEnchantment cannot answer - vanilla asks the
        // enchantment's category instead, and a category knows nothing about a feature switch. What
        // that method returns there is the switch, so the switch is asked here directly.
        if (enchantment == EnchReinforce.INSTANCE) return TrmtConfig.reinforceEnabled;
        if (enchantment == EnchWard.INSTANCE) return TrmtConfig.wardEnabled;
        return TrmtConfig.lightEnabled;
    }

    /**
     * A librarian's pick, drawn again from the book list where it is a switched-off unlock.
     *
     * @param random the villager's own, which the game drew the pick from and goes on to draw the level and
     *               the price from
     */
    public static Enchantment forLibrarian(Enchantment drawn, Random random) {
        // Before the list is built, and not to save the building: redraw asks the random only when the
        // pick is not on offer, so a draw that stands must not reach it at all or every librarian's
        // level and price would come out of a different sequence than the game would have used.
        if (offered(drawn)) return drawn;
        java.util.List<Enchantment> known = new java.util.ArrayList<Enchantment>();
        for (Enchantment each : net.minecraft.core.Registry.ENCHANTMENT) known.add(each);
        return OfferedDraw.redraw(drawn, known.toArray(new Enchantment[known.size()]), OFFERED, random);
    }

}
