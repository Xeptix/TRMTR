package com.trmtgtnh.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RepairItemRecipe;
import net.minecraft.world.level.Level;

import com.trmtgtnh.item.TamperEvents;

/**
 * Keeps a tamper out of the grid repair.
 *
 * <p>
 * <strong>The grid is the one place a per-stack maximum is ignored, and it launders the grade off
 * the stack on its way past.</strong> Two damaged tampers put together make one stack with the
 * declared durability and no grade written on it, which turns a diamond tamper into an ordinary one
 * and loses what it was paid for.
 *
 * <p>
 * Both older editions refuse it on the item, through a Forge switch, and so does the Forge side
 * here. There is no such switch in vanilla - what decides it is this recipe asking whether the item
 * is damageable at all - so on this loader the recipe is asked instead.
 *
 * <p>
 * At the head of matching rather than of assembling, so the recipe never shows a result to take.
 */
@Mixin(RepairItemRecipe.class)
public abstract class MixinRepairItemRecipe {

    @Inject(method = "matches", at = @At("HEAD"), cancellable = true)
    private void trmt$noGridRepairForTampers(CraftingContainer grid, Level level,
        CallbackInfoReturnable<Boolean> callback) {
        for (int slot = 0; slot < grid.getContainerSize(); slot++) {
            ItemStack held = grid.getItem(slot);
            if (TamperEvents.holdsTamper(held)) {
                callback.setReturnValue(Boolean.FALSE);
                return;
            }
        }
    }
}
