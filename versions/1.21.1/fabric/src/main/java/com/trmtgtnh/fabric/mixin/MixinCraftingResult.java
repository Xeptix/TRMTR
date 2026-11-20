package com.trmtgtnh.fabric.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.trmtgtnh.server.ServerEvents;

/**
 * Something was taken out of a crafting result, which is where this mod's tool ladder is awarded.
 *
 * <p>
 * Forge fires {@code PlayerEvent.ItemCraftedEvent} for this and Fabric has no event for it at all, so the call goes
 * where Forge's fires and vanilla hangs its own crafting triggers: anything that counts as having crafted something
 * passes through {@code checkTakeAchievements}.
 *
 * <p>
 * <strong>Not {@code onTake}, where it stood until 0.9.229</strong> (spec BP36, read from each game's
 * {@code CraftingMenu.quickMoveStack}): a shift-click moves the stack first and hands {@code onTake} what is left of it,
 * nothing at all, so a tamper shift-crafted on Fabric awarded nothing. The quick move reaches
 * {@code checkTakeAchievements} through {@code onQuickCraft} with the stack as it was, and a plain click through
 * {@code onTake}; the empty call that follows a shift-click is passed over by {@code onCrafted}, so nothing is told twice.
 *
 * <p>
 * The trigger it reaches was carried into this edition with the advancements and then called by nobody, so crafting a
 * tamper awarded nothing on either loader.
 */
@Mixin(ResultSlot.class)
public abstract class MixinCraftingResult {

    @Shadow
    @Final
    private Player player;

    @Inject(method = "checkTakeAchievements", at = @At("HEAD"))
    private void trmt$crafted(ItemStack taken, CallbackInfo callback) {
        ServerEvents.crafted(player, taken);
    }
}
