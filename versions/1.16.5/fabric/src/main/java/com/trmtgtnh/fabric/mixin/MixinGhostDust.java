package com.trmtgtnh.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
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
 */
@Mixin(ParticleEngine.class)
public abstract class MixinGhostDust {

    @Shadow
    protected ClientLevel level;

    @Inject(method = "destroy", at = @At("HEAD"), require = 0)
    private void trmt$holdForBreakingDust(BlockPos pos, BlockState state, CallbackInfo callback) {
        Client.holdDust(level, pos);
    }

    @Inject(method = "crack", at = @At("HEAD"), require = 0)
    private void trmt$holdForHittingDust(BlockPos pos, Direction face, CallbackInfo callback) {
        Client.holdDust(level, pos);
    }
}
