package com.trmtgtnh.mixin;

import java.util.Random;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.IMerchant;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.village.MerchantRecipeList;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.trmtgtnh.item.UnlockBooks;

/**
 * Keeps a switched-off unlock off a librarian's new trades.
 *
 * <p>
 * A librarian who unlocks an enchanted-book trade draws the enchantment evenly from the game's
 * enchantments and asks it nothing - not whether it may go on a book, not whether its feature is on.
 * Each of this mod's unlocks is in that draw as soon as it registers, and stays in it whatever its
 * switch says, so a switched-off unlock came up as often as Sharpness, for a book nothing could use.
 *
 * <p>
 * The draw is changed as it is stored, and nothing else is. A filter once the trades are made comes too
 * late: every enchanted-book trade matches every other, so a cheaper roll has already overwritten the
 * librarian's one book trade in place, and removing it afterwards would leave the librarian with
 * neither. Changing the enchantment before its level and price are worked out leaves the trade exactly
 * as the game would have made it for what is offered instead.
 *
 * <p>
 * <strong>This edition's first mixin, and a smaller one than the other edition's.</strong> There the
 * draw happens inside the villager's own {@code addDefaultEquipmentAndRecipies}, among its armour and
 * its other trades, and the hook has to pick an {@code Enchantment} local out of a long method by type
 * because the released game carries no local variable names. 1.12.2 moved every trade into a small
 * class of its own, so the draw being modified is three lines from the top of a method that does
 * nothing else - and the random the redraw needs is a parameter rather than something to fetch off the
 * villager.
 *
 * <p>
 * Common, because trades are made by the server - a dedicated one, or the one inside single player -
 * and never by a client's copy of the villager.
 *
 * <p>
 * <strong>{@code require = 1}, which is where this edition parts company with the other one.</strong>
 * There the reasoning is that a pack which has replaced this method should get librarians that now and
 * then sell a switched-off book rather than a game that refuses to start. The trouble with it is that
 * a miss leaves <em>nothing</em> in the log: the behaviour quietly reverts and the only way to know is
 * to count a thousand draws. So this one insists on binding, and a pack that has replaced this method
 * is told at startup instead of shipping with a bug nobody can see.
 *
 * <p>
 * Nothing about this catches a missing loader, which is a different failure with a different answer:
 * with no MixinBooter there is nothing reading this config at all, so {@code require} is never
 * consulted. That is what the {@code required-after:mixinbooter} dependency on {@code Trmt} is for.
 */
@Mixin(EntityVillager.ListEnchantedBookForEmeralds.class)
public abstract class MixinLibrarianBooks {

    // The only Enchantment local in the method, and the first thing it does.
    @ModifyVariable(require = 1, method = "addMerchantRecipe", at = @At("STORE"), ordinal = 0)
    private Enchantment trmt$onlyOfferedBooks(Enchantment drawn, IMerchant merchant, MerchantRecipeList recipes,
        Random random) {
        return UnlockBooks.forLibrarian(drawn, random);
    }
}
