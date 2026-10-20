package com.trmtgtnh.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.item.ItemStack;

import com.trmtgtnh.item.ItemChunkTamper;

/**
 * How much damage a tamper may take - the chunk tamper, the Wayfarer, the hand tamper - which depends on the stack and
 * not on the item.
 *
 * <p>
 * <strong>Without this every grade of tamper lasts exactly as long as every other.</strong> A grade
 * is kept in the stack's own data rather than being an item of its own - see {@code ItemChunkTamper}
 * for why that is the whole design - so a diamond one and an iron one are the same item with
 * different numbers written on them. Forge has a method for asking an item about a particular stack
 * and vanilla has none: it asks the item, gets the declared figure, and every grade is a thousand
 * and twenty-four uses.
 *
 * <p>
 * The number is not decided here. It comes from {@code ItemChunkTamper.maxDamageOf}, which is the
 * same method the Forge side's subclass hands back, so the two loaders cannot answer differently.
 *
 * <p>
 * Everything that is not one of this mod's tampers falls straight through, which is every stack in
 * the game but a handful.
 */
@Mixin(ItemStack.class)
public abstract class MixinItemStack {

    @Inject(method = "getMaxDamage", at = @At("HEAD"), cancellable = true)
    private void trmt$gradeDecidesDurability(CallbackInfoReturnable<Integer> callback) {
        ItemStack self = (ItemStack) (Object) this;
        if (self.getItem() instanceof ItemChunkTamper) {
            callback.setReturnValue(Integer.valueOf(((ItemChunkTamper) self.getItem()).maxDamageOf(self)));
        }
        // The hand tamper too, which keeps its grade the same way: until 0.9.222 only the chunk tamper was asked, and on
        // this loader every grade of hand tamper lasted its declared figure (spec TA13). Forge's ForgeTampers answers
        // all three.
        if (self.getItem() instanceof com.trmtgtnh.item.ItemGradedTamper) {
            callback.setReturnValue(
                Integer.valueOf(((com.trmtgtnh.item.ItemGradedTamper) self.getItem()).maxDamageOf(self)));
        }
    }

    /**
     * What an anvil may mend a tamper with, which also depends on the grade in the stack.
     *
     * <p>
     * 1.21.1 asked the item ({@code Item.isValidRepairItem(tool, material)}), which each tamper answered. From 1.21.2
     * an anvil asks the stack, and the stack asks its repairable component (ItemStack.isValidRepairItem, read from
     * 26.1.2's AnvilMenu), which cannot carry a per-grade answer. So the tampers' own 1.21.1 answers are asked here,
     * at the head of the stack's question, and every other stack is the game's.
     */
    @Inject(method = "isValidRepairItem", at = @At("HEAD"), cancellable = true)
    private void trmt$gradeDecidesRepair(ItemStack material, CallbackInfoReturnable<Boolean> callback) {
        ItemStack self = (ItemStack) (Object) this;
        if (self.getItem() instanceof ItemChunkTamper) {
            callback.setReturnValue(
                Boolean.valueOf(((ItemChunkTamper) self.getItem()).isValidRepairItem(self, material)));
        } else if (self.getItem() instanceof com.trmtgtnh.item.ItemTamper) {
            // ItemGradedTamper extends ItemTamper and answers through the same call.
            callback.setReturnValue(
                Boolean.valueOf(((com.trmtgtnh.item.ItemTamper) self.getItem()).isValidRepairItem(self, material)));
        }
    }
}
