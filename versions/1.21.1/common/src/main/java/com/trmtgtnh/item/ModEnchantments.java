package com.trmtgtnh.item;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

/**
 * Where the three tamper enchantments meet the places the game hands enchantments out.
 *
 * <p>
 * The older editions register the three here. This version does not: they are data (see {@link TamperEnchantment}),
 * and the game makes them from their files as a world loads, on both sides alike. What is left is the feature switch,
 * which a file cannot read, asked at each place the game decides - the table and the chest book here, by the mixins
 * named on each method, and the librarian in {@link UnlockBooks#forLibrarian}. The anvil asks the enchantment, and
 * {@code MixinUnlocksAnswerTheirSwitch} answers it.
 */
public final class ModEnchantments {

    private ModEnchantments() {}

    /**
     * Takes a switched-off unlock out of what an enchanting table has just offered (spec RL2), as 1.20.1's
     * {@code isDiscoverable} kept it out before the offer was built. The table draws only from
     * {@code #minecraft:in_enchanting_table}, which holds the three, and offers each only on the items its file names,
     * so a switched-on unlock is offered on a chunk tamper, a magic tamper or a plain book and on nothing else, with no
     * help from here. Called by {@code MixinUnlocksAnswerTheirSwitch} with the list the game built, so every other
     * enchantment is offered exactly as before.
     */
    public static void withdrawSwitchedOff(List<EnchantmentInstance> offered) {
        if (offered == null) return;
        Iterator<EnchantmentInstance> each = offered.iterator();
        while (each.hasNext()) {
            if (!TamperEnchantment.offered(each.next().enchantment)) each.remove();
        }
    }

    /**
     * A chest book drawn again if the game's draw landed on one of the three (0.9.222, spec BP29).
     *
     * <p>
     * The 1.7.10 edition's chests never hand out an unlock book: their enchanted book is rolled at level thirty, past
     * anything the three can reach, so this mod's own loot entry is the only chest that gives one. Vanilla's chests
     * here draw from {@code #minecraft:on_random_loot}, which the three are not in, so they never reach this. A table
     * that names no list of its own - another mod's, as a rule - draws from every enchantment there is, and those are
     * the books drawn again: evenly, at any level the enchantment has, from what the game would have drawn from, less
     * the three, so every other book's odds stand as they were. Called by {@code MixinChestBooksDrawAgain} with what the
     * draw made and everything it could have drawn.
     */
    public static ItemStack withoutUnlocks(ItemStack book, RandomSource rand, Iterable<Holder<Enchantment>> pool) {
        if (book == null || book.getItem() != Items.ENCHANTED_BOOK || rand == null || pool == null) return book;
        boolean unlock = false;
        for (Holder<Enchantment> each : EnchantmentHelper.getEnchantmentsForCrafting(book)
            .keySet()) {
            if (TamperEnchantment.of(each) != null) unlock = true;
        }
        if (!unlock) return book;
        List<Holder<Enchantment>> others = new ArrayList<Holder<Enchantment>>();
        for (Holder<Enchantment> each : pool) {
            if (TamperEnchantment.of(each) == null) others.add(each);
        }
        if (others.isEmpty()) return new ItemStack(Items.BOOK);
        Holder<Enchantment> drawn = others.get(rand.nextInt(others.size()));
        return EnchantedBookItem.createForEnchantment(
            new EnchantmentInstance(
                drawn,
                Mth.nextInt(
                    rand,
                    drawn.value()
                        .getMinLevel(),
                    drawn.value()
                        .getMaxLevel())));
    }
}
