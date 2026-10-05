package com.trmtgtnh.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.item.ItemStack;

import com.trmtgtnh.item.ItemChunkTamper;

/**
 * How much damage a chunk tamper may take, which depends on the stack and not on the item.
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
    }
}
