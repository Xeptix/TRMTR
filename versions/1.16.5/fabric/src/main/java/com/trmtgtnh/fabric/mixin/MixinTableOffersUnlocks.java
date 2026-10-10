package com.trmtgtnh.fabric.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

import com.trmtgtnh.item.ModEnchantments;

/**
 * An enchanting table offers the three unlocks on a chunk tamper, and on no other tool (spec RL2).
 *
 * <p>
 * See {@link ModEnchantments#offerAtTable}, which holds the questions. This loader's table asks an enchantment's
 * category and nothing else, and the unlocks' category takes every digging tool and no chunk tamper, so without this a
 * table offered them on a pickaxe and never on the tool they are for. At the return, onto the list the table has just
 * built, so every other enchantment is offered exactly as before. Forge asks the enchantment itself and needs no hook.
 */
@Mixin(EnchantmentHelper.class)
public class MixinTableOffersUnlocks {

    @Inject(method = "getAvailableEnchantmentResults", at = @At("RETURN"))
    private static void trmt$offerUnlocks(int power, ItemStack stack, boolean treasure,
        CallbackInfoReturnable<List<EnchantmentInstance>> callback) {
        ModEnchantments.offerAtTable(power, stack, treasure, callback.getReturnValue());
    }
}
