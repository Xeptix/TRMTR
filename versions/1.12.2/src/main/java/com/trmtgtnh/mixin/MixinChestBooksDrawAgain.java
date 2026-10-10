package com.trmtgtnh.mixin;

import java.util.List;
import java.util.Random;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.world.storage.loot.LootContext;
import net.minecraft.world.storage.loot.functions.EnchantRandomly;

import com.trmtgtnh.item.ModEnchantments;

/**
 * Keeps the three unlocks out of vanilla's chest books (0.9.222, spec BP29).
 *
 * <p>
 * See {@link ModEnchantments#withoutUnlocks}, which holds the reason and the redraw. At the return of the random
 * enchant, so the game's own draw stands for every book that did not land on one of the three, and only where the
 * function names no list of its own: a table that names its enchantments names them on purpose.
 *
 * <p>
 * Common, because loot is rolled by the server. {@code require = 0}, as the librarian's is: a pack that has replaced
 * this method gets chests that now and then hold an unlock book, rather than a game that will not start.
 */
@Mixin(EnchantRandomly.class)
public abstract class MixinChestBooksDrawAgain {

    @Shadow
    @Final
    private List<Enchantment> enchantments;

    @Inject(method = "apply", at = @At("RETURN"), cancellable = true, require = 0)
    private void trmt$drawAgainWithoutUnlocks(ItemStack stack, Random rand, LootContext context,
        CallbackInfoReturnable<ItemStack> callback) {
        if (enchantments != null && !enchantments.isEmpty()) return;
        ItemStack drawn = callback.getReturnValue();
        ItemStack again = ModEnchantments.withoutUnlocks(drawn, rand);
        if (again != drawn) callback.setReturnValue(again);
    }
}
