package com.trmtgtnh.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BrushItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import com.trmtgtnh.server.FaithfulBrush;

/**
 * The brush on Faithful Mode's worn sand (0.9.229 W5 T17): every use tick of a brush is handed to
 * {@link FaithfulBrush#used}, which strokes, counts and gives a stage back as the original's {@code BrushItemMixin} did.
 * At the head of {@code BrushItem.onUseTick}, read in 1.20.1's own jar, and vanilla's tick goes on after it either way.
 * In this module, and the same in the Forge one, rather than in common: at 1.20.1 a common mixin may name no vanilla
 * member ({@code CommonMixinsNeedNoRefmapTest}).
 */
@Mixin(BrushItem.class)
public abstract class MixinFaithfulBrush {

    @Inject(method = "onUseTick", at = @At("HEAD"))
    private void trmt$brushes(Level level, LivingEntity user, ItemStack brush, int remaining, CallbackInfo callback) {
        FaithfulBrush.used(level, user, brush, ((BrushItem) (Object) this).getUseDuration(brush), remaining);
    }
}
