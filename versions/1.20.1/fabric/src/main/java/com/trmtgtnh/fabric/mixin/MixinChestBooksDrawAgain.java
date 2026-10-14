package com.trmtgtnh.fabric.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.EnchantRandomlyFunction;

import com.trmtgtnh.item.ModEnchantments;

/**
 * Keeps the three unlocks out of vanilla's chest books (0.9.222, spec BP29).
 *
 * <p>
 * See {@link ModEnchantments#withoutUnlocks}, which holds the reason and the redraw. At the return of the random
 * enchant, so the game's own draw stands for every book that did not land on one of the three, and only where the
 * function names no list of its own: a table that names its enchantments names them on purpose. Common, because loot
 * is rolled by the server. {@code require = 0}: a pack that has replaced this method gets chests that now and then
 * hold an unlock book, rather than a game that will not start. The Forge module has the same hook - a mixin that
 * names a method is written into a refmap in one loader's names and the other refuses it.
 */
@Mixin(EnchantRandomlyFunction.class)
public abstract class MixinChestBooksDrawAgain {

    @Shadow
    @Final
    private List<Enchantment> enchantments;

    @Inject(method = "run", at = @At("RETURN"), cancellable = true, require = 0)
    private void trmt$drawAgainWithoutUnlocks(ItemStack stack, LootContext context,
        CallbackInfoReturnable<ItemStack> callback) {
        if (enchantments != null && !enchantments.isEmpty()) return;
        ItemStack drawn = callback.getReturnValue();
        ItemStack again = ModEnchantments.withoutUnlocks(drawn, context.getRandom());
        if (again != drawn) callback.setReturnValue(again);
    }
}
