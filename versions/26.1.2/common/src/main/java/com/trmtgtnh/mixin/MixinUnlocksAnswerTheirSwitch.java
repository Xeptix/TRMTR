package com.trmtgtnh.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

import com.trmtgtnh.item.TamperEnchantment;

/**
 * A switched-off unlock goes onto nothing new: not at an anvil, not by {@code /enchant} (spec RL2).
 *
 * <p>
 * 1.20.1's {@code TamperEnchantment.canEnchant} answered {@code enabled() && a chunk tamper}. The second half is the
 * file's {@code supported_items} now; the first is a feature switch, which a file cannot read, so it is answered here,
 * at the head of both questions the game asks an enchantment about an item: {@code canEnchant}, which vanilla's anvil
 * and {@code /enchant} ask, and {@code isSupportedItem}, which NeoForge's anvil asks instead through
 * {@code IItemExtension.supportsEnchantment}, and which the table's {@code isPrimaryItem} asks too. Only the three are
 * ever refused, and only while their switch is off; every other enchantment goes on as its own file says. Common,
 * because the record is vanilla's and every loader runs it.
 */
@Mixin(Enchantment.class)
public abstract class MixinUnlocksAnswerTheirSwitch {

    @Inject(method = "canEnchant", at = @At("HEAD"), cancellable = true)
    private void trmt$switchedOffGoesOnNothing(ItemStack stack, CallbackInfoReturnable<Boolean> callback) {
        if (!TamperEnchantment.offered((Enchantment) (Object) this)) callback.setReturnValue(false);
    }

    @Inject(method = "isSupportedItem", at = @At("HEAD"), cancellable = true)
    private void trmt$switchedOffSupportsNothing(ItemStack stack, CallbackInfoReturnable<Boolean> callback) {
        if (!TamperEnchantment.offered((Enchantment) (Object) this)) callback.setReturnValue(false);
    }
}
