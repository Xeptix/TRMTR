package com.trmtgtnh.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import com.trmtgtnh.block.BlockGhost;

/**
 * Holds the ghost's tint for its breaking and hitting dust, where Forge would ask the block (0.9.222, spec GF19).
 *
 * <p>
 * Forge asks a block for its own dust first ({@code addDestroyEffects}, {@code addHitEffects}), and the ghost answers
 * there by holding its tint and letting the game spawn the dust - the 1.7.10 edition's {@code holdShadeForDust}. Fabric
 * has neither question, so the hold is taken as the game starts spawning either kind of dust, which is the same moment.
 * Taken for every block, because it reaches only the ghost's own color handler, which no other block has; see
 * {@link BlockGhost#holdTintForDust} for what the handler does with it, and why a quad drawn in the meantime cannot
 * notice.
 */
@Mixin(ParticleEngine.class)
public abstract class MixinGhostDust {

    @Inject(method = "destroy", at = @At("HEAD"), require = 0)
    private void trmt$holdForBreakingDust(BlockPos pos, BlockState state, CallbackInfo callback) {
        BlockGhost.holdTintForDust();
    }

    @Inject(method = "crack", at = @At("HEAD"), require = 0)
    private void trmt$holdForHittingDust(BlockPos pos, Direction face, CallbackInfo callback) {
        BlockGhost.holdTintForDust();
    }
}
