package com.trmtgtnh.fabric.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.trmtgtnh.server.ServerEvents;

/**
 * Something was taken out of a crafting result, which is where this mod's tool ladder is awarded.
 *
 * <p>
 * Forge fires {@code PlayerEvent.ItemCraftedEvent} for this and Fabric has no event for it at all,
 * so the call goes where the game already does the thing. {@code ResultSlot.onTake} is the same place
 * vanilla hangs its own crafting triggers, which is the argument for it being the right one: anything
 * that counts as having crafted something passes through here.
 *
 * <p>
 * The trigger it reaches was carried into this edition with the advancements and then called by
 * nobody, so crafting a tamper awarded nothing on either loader.
 */
@Mixin(ResultSlot.class)
public abstract class MixinCraftingResult {

    @Inject(method = "onTake", at = @At("HEAD"))
    private void trmt$crafted(Player player, ItemStack taken, CallbackInfoReturnable<ItemStack> callback) {
        ServerEvents.crafted(player, taken);
    }
}
