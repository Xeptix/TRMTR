package com.trmtgtnh.mixin;

import java.util.List;
import java.util.stream.Stream;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

import com.trmtgtnh.item.ModEnchantments;

/**
 * An enchanting table offers a switched-off unlock on nothing (spec RL2).
 *
 * <p>
 * See {@link ModEnchantments#withdrawSwitchedOff}. The table offers the three on a chunk tamper and a magic tamper and
 * nothing else by itself here - they are in {@code #minecraft:in_enchanting_table} and their files name those two -
 * which is what 1.20.1's Fabric module needed this hook for, since its table asked a category that took every digging
 * tool and no chunk tamper. What a tag cannot do is follow the feature switch, so this takes a switched-off one back out
 * of the list the table has just built, at the return, leaving every other enchantment as offered. Common: every
 * loader's table builds its offer here.
 */
@Mixin(EnchantmentHelper.class)
public abstract class MixinTableOffersUnlocks {

    @Inject(method = "getAvailableEnchantmentResults", at = @At("RETURN"))
    private static void trmt$withdrawSwitchedOff(int power, ItemStack stack, Stream<Holder<Enchantment>> possible,
        CallbackInfoReturnable<List<EnchantmentInstance>> callback) {
        ModEnchantments.withdrawSwitchedOff(callback.getReturnValue());
    }
}
