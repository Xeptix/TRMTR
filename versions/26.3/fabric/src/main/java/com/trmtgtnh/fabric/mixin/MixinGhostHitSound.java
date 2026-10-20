package com.trmtgtnh.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

import com.trmtgtnh.block.GhostFamily;

/**
 * The knock of a pick on a worn square is its family's - the 1.7.10 edition's {@code GhostLogic.stepSoundFor}, which
 * that edition's stand-in uses for digging as for stepping (0.9.222, spec GF10). The sound vanilla plays every few ticks
 * while a block is being mined, asked of the state with no square in it; Forge patches this to ask the block at the
 * square. See {@link MixinGhostStepSound}.
 *
 * <p>
 * <strong>From 26.3 the knock is played by the client's level</strong>, {@code ClientLevel.playBreakingSound(pos,
 * state)}, which {@code addBreakingBlockEffects} calls with its sound flag set - as mining starts
 * ({@code MultiPlayerGameMode.startDestroyBlock}) and on each level event the server sends while it goes on
 * ({@code LevelEventHandler}, 2020) - where 26.2's {@code MultiPlayerGameMode.continueDestroyBlock} played it every
 * fourth tick itself (read from 26.3's merged jar). The same question is asked there, with the square the method was
 * handed, so the square no longer has to be noted beforehand.
 */
@Mixin(ClientLevel.class)
public abstract class MixinGhostHitSound {

    @Redirect(
        require = 0,
        method = "playBreakingSound",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getSoundType()Lnet/minecraft/world/level/block/SoundType;"))
    private SoundType trmt$soundOfTheSquare(BlockState state, BlockPos pos, BlockState played) {
        return GhostFamily.soundOf(state, (ClientLevel) (Object) this, pos);
    }
}
