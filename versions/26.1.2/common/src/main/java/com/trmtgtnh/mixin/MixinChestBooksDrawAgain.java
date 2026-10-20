package com.trmtgtnh.mixin;

import java.util.Optional;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.EnchantRandomlyFunction;

import com.trmtgtnh.item.ModEnchantments;

/**
 * Keeps the three unlocks out of chest books drawn from every enchantment there is (0.9.222, spec BP29).
 *
 * <p>
 * See {@link ModEnchantments#withoutUnlocks}, which holds the reason and the redraw. At the return of the random
 * enchant, so the game's own draw stands for every book that did not land on one of the three, and only where the
 * function names no list of its own: a table that names its enchantments names them on purpose - vanilla's own name
 * {@code #minecraft:on_random_loot}, which the three are not in. {@code require = 0}: a pack that has replaced this
 * method gets chests that now and then hold an unlock book, rather than a game that will not start. Common at this
 * version: loot is rolled by the server, and every loader names the method alike.
 */
@Mixin(EnchantRandomlyFunction.class)
public abstract class MixinChestBooksDrawAgain {

    @Shadow
    @Final
    private Optional<HolderSet<Enchantment>> options;

    @Inject(method = "run", at = @At("RETURN"), cancellable = true, require = 0)
    private void trmt$drawAgainWithoutUnlocks(ItemStack stack, LootContext context,
        CallbackInfoReturnable<ItemStack> callback) {
        if (options != null && options.isPresent()) return;
        ItemStack drawn = callback.getReturnValue();
        Iterable<Holder<Enchantment>> pool = context.getLevel()
            .registryAccess()
            .lookupOrThrow(Registries.ENCHANTMENT)
            .listElements()
            .map(each -> (Holder<Enchantment>) each)
            .toList();
        ItemStack again = ModEnchantments.withoutUnlocks(drawn, context.getRandom(), pool);
        if (again != drawn) callback.setReturnValue(again);
    }
}
