package com.trmtgtnh.mixin;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.passive.EntityVillager;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.trmtgtnh.item.UnlockBooks;

/**
 * Keeps a switched-off unlock off a librarian's new trades.
 *
 * <p>
 * A librarian who unlocks an enchanted-book trade draws the enchantment evenly from the game's book list
 * and asks it nothing - not whether it may go on a book, not whether its feature is on. Each of this mod's
 * unlocks joined that list once, as it registered, and stays in it whatever its switch says, so a
 * switched-off unlock came up as often as Sharpness, for a book nothing could use.
 *
 * <p>
 * The draw is changed as it is stored, and nothing else is. The list is a final field other mods append
 * to, so taking entries out of it means writing a final field by reflection and guessing what others added
 * since. The trades are the villager's own saved data, and a filter once they are made comes too late:
 * every enchanted-book trade matches every other, so a cheaper roll has already overwritten the
 * librarian's one book trade in place, and removing it afterwards would leave the librarian with neither.
 * Changing the enchantment before its level and price are worked out leaves the trade exactly as the game
 * would have made it for what is offered instead.
 *
 * <p>
 * Not the two reads of the list on that line, although that looks simpler: the game reads it once for the
 * array and once for its length, and if another mod's redirect took one of those reads, only the other
 * would be changed, and an array indexed by a length it does not have runs off its end and stops the
 * villager's tick. The stored value is one value.
 *
 * <p>
 * The second pick is asked of the villager's own random source, the one the game drew the first from and
 * goes on to draw the level and the price from, and only when the first pick is switched off - so with
 * every switch on, each villager's trades come out exactly as they always did.
 *
 * <p>
 * Common, because trades are made by the server - a dedicated one, or the one inside single player - and
 * never by a client's copy of the villager. {@code require = 0} for the reason the client mixins give: a
 * pack that has replaced this method gets librarians that now and then sell a switched-off book, as every
 * version before 0.9.213 did, rather than a game that refuses to start. A miss says nothing in the log,
 * which is why 0.9.213 was checked by counting draws on live servers rather than by reading this.
 */
@Mixin(EntityVillager.class)
public abstract class MixinLibrarianBooks {

    // The only Enchantment local in the method, chosen by type rather than by name, because the game's
    // released bytecode carries no local variable names. The development run's recompiled game does carry
    // them, so a run there alone does not show this binds.
    @ModifyVariable(require = 0, method = "addDefaultEquipmentAndRecipies(I)V", at = @At("STORE"), ordinal = 0)
    private Enchantment trmt$onlyOfferedBooks(Enchantment drawn) {
        return UnlockBooks.forLibrarian(drawn, ((EntityVillager) (Object) this).getRNG());
    }
}
