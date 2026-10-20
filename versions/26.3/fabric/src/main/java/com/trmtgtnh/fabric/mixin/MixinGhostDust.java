package com.trmtgtnh.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import com.trmtgtnh.Client;

/**
 * Holds a worn square's family for its breaking and hitting dust, where Forge would ask the block (0.9.223, spec GF19).
 *
 * <p>
 * Forge asks a block for its own dust first ({@code addDestroyEffects}, {@code addHitEffects}) and the ghost holds its
 * square's family there; Fabric has neither question, so the hold is taken as the game starts spawning either kind of
 * dust, which is the same moment, with the same square. The dust's picture, asked of the ghost's model for each
 * particle in the same call, then answers from what is held - see client.model.GhostDust. Taken for every block: the
 * hold is read only by the ghost's own model, which no other block has. Not required: a game that has moved these
 * methods loses the family's picture, never the game.
 *
 * <p>
 * By 26.1 both kinds of dust are spawned by the client's level ({@code ClientLevel.addDestroyBlockEffect} and
 * {@code addBreakingBlockEffect}, read from 26.2), where 1.21.1's ParticleEngine had {@code destroy} and
 * {@code crack}; the hold is taken at the head of each, the same moment, and the level is the one spawning them.
 * From 26.3 the hitting dust's method is {@code addBreakingBlockEffects(BlockPos, Direction, boolean)}, the last
 * saying whether the breaking sound plays with it (client/multiplayer/ClientLevel, read from 26.3); the same dust.
 */
@Mixin(ClientLevel.class)
public abstract class MixinGhostDust {

    @Inject(method = "addDestroyBlockEffect", at = @At("HEAD"), require = 0)
    private void trmt$holdForBreakingDust(BlockPos pos, BlockState state, CallbackInfo callback) {
        Client.holdDust((ClientLevel) (Object) this, pos);
    }

    @Inject(method = "addBreakingBlockEffects", at = @At("HEAD"), require = 0)
    private void trmt$holdForHittingDust(BlockPos pos, Direction face, boolean playSound, CallbackInfo callback) {
        Client.holdDust((ClientLevel) (Object) this, pos);
    }
}
