package com.trmtgtnh.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

import com.trmtgtnh.block.GhostFamily;

/**
 * A footstep on a worn square sounds like the family the square is worn as - the 1.7.10 edition's
 * {@code GhostLogic.stepSoundFor} (0.9.222, spec GF10).
 *
 * <p>
 * That edition sets the sound on each stand-in per family; here one ghost stands for every family, and vanilla, which
 * is what Fabric runs, asks a state for its sound with no square in it, so every worn square sounded like gravel. Forge
 * patches this method to ask the block at the square, and the ghost answers there; this is the same question asked at
 * the same place. The square is noted as the step begins, because the sound is asked of a state and the state does
 * not know where it is; the snow-above branch asks the snow, which is not a ghost and keeps its own sound.
 *
 * <p>
 * A muffled step is the same question, and the one a step puts to the ground under a carpet or a snow layer or while
 * wading (0.9.229). {@code Player.playStepSound} overrides this method and goes straight there, so the square is noted
 * one call earlier as well, in the private method every walking step goes through.
 *
 * <p>
 * The other places a block's own sound is played - a horse's step, a landing, a hit, a break and a sniffer's digging -
 * have mixins of their own beside this one, each handing {@link GhostFamily#soundOf} the square.
 * {@code GhostSoundSitesTest} reads the game for the whole list.
 */
@Mixin(Entity.class)
public abstract class MixinGhostStepSound {

    @Unique
    private BlockPos trmt$stepAt;

    @Inject(method = "walkingStepSound", at = @At("HEAD"), require = 0)
    private void trmt$noteTheStep(BlockPos pos, BlockState state, CallbackInfo callback) {
        trmt$stepAt = pos;
    }

    @Inject(method = "playStepSound", at = @At("HEAD"), require = 0)
    private void trmt$noteTheSquare(BlockPos pos, BlockState state, CallbackInfo callback) {
        trmt$stepAt = pos;
    }

    @Redirect(
        require = 0,
        method = { "playStepSound", "playMuffledStepSound" },
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getSoundType()Lnet/minecraft/world/level/block/SoundType;"))
    private SoundType trmt$soundOfTheSquare(BlockState state) {
        return GhostFamily.soundOf(state, ((Entity) (Object) this).level(), trmt$stepAt);
    }
}
