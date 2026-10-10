package com.trmtgtnh.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

import com.trmtgtnh.block.GhostFamily;

/**
 * A horse's step on a worn square, which is not {@code Entity.playStepSound} - a horse plays its own, and a horse the
 * player rides is moved, and heard, on the player's own client. The same family's sound as a footstep, for the same
 * reason; see {@link MixinGhostStepSound} (0.9.222, spec GF10). Forge patches this method too.
 */
@Mixin(AbstractHorse.class)
public abstract class MixinGhostHorseStepSound {

    @Unique
    private BlockPos trmt$stepAt;

    @Inject(method = "playStepSound", at = @At("HEAD"), require = 0)
    private void trmt$noteTheSquare(BlockPos pos, BlockState state, CallbackInfo callback) {
        trmt$stepAt = pos;
    }

    @Redirect(
        require = 0,
        method = "playStepSound",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getSoundType()Lnet/minecraft/world/level/block/SoundType;"))
    private SoundType trmt$soundOfTheSquare(BlockState state) {
        return GhostFamily.soundOf(state, ((Entity) (Object) this).level, trmt$stepAt);
    }
}
