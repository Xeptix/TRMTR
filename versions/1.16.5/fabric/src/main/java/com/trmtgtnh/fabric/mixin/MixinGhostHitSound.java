package com.trmtgtnh.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

import com.trmtgtnh.block.GhostFamily;

/**
 * The knock of a pick on a worn square is its family's - the 1.7.10 edition's {@code GhostLogic.stepSoundFor}, which
 * that edition's stand-in uses for digging as for stepping (0.9.222, spec GF10). The sound vanilla plays every few ticks
 * while a block is being mined, asked of the state with no square in it; Forge patches this to ask the block at the
 * square. See {@link MixinGhostStepSound}.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MixinGhostHitSound {

    @Unique
    private BlockPos trmt$minedAt;

    @Inject(method = "continueDestroyBlock", at = @At("HEAD"), require = 0)
    private void trmt$noteTheSquare(BlockPos pos, Direction face, CallbackInfoReturnable<Boolean> callback) {
        trmt$minedAt = pos;
    }

    @Redirect(
        require = 0,
        method = "continueDestroyBlock",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getSoundType()Lnet/minecraft/world/level/block/SoundType;"))
    private SoundType trmt$soundOfTheSquare(BlockState state) {
        return GhostFamily.soundOf(state, Minecraft.getInstance().level, trmt$minedAt);
    }
}
