package com.trmtgtnh.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

import com.trmtgtnh.block.GhostFamily;

/**
 * A worn square breaks with its family's sound (0.9.222, spec GF10).
 *
 * <p>
 * The client's own break is played through the world event a breaking block raises (2001), carrying the state that
 * broke - the ghost, on the client that broke it - and vanilla asks that state for its sound with no square in it; Forge
 * patches this to ask the block at the square. The record is still there to ask: it is the client's cache, filed by the
 * square, not the block. Only the one sound in this method is asked of a block state. See {@link MixinGhostStepSound}.
 */
@Mixin(LevelRenderer.class)
public abstract class MixinGhostBreakSound {

    @Unique
    private BlockPos trmt$brokenAt;

    @Inject(method = "levelEvent", at = @At("HEAD"), require = 0)
    private void trmt$noteTheSquare(Player player, int type, BlockPos pos, int data, CallbackInfo callback) {
        trmt$brokenAt = pos;
    }

    @Redirect(
        require = 0,
        method = "levelEvent",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getSoundType()Lnet/minecraft/world/level/block/SoundType;"))
    private SoundType trmt$soundOfTheSquare(BlockState state) {
        return GhostFamily.soundOf(state, Minecraft.getInstance().level, trmt$brokenAt);
    }
}
