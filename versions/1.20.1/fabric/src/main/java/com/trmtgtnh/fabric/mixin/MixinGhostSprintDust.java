package com.trmtgtnh.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import com.trmtgtnh.Client;

/**
 * Holds a worn square's family for the dust a runner kicks up from it, where Forge would ask the block (0.9.223, spec
 * GF19).
 *
 * <p>
 * Forge asks the block under a sprinting entity for its own running dust ({@code addRunningEffects}) and the ghost
 * holds its square's family there; Fabric has no such question, so the hold is taken at the one read of that block in
 * {@code spawnSprintParticle}, with the square it reads, before the dust is spawned from what it read. The dust's
 * picture then answers from what is held - see client.model.GhostDust. Only on a client, where ghosts are; the read
 * itself is unchanged. Not required: a game that has moved this read loses the family's picture, never the game.
 */
@Mixin(Entity.class)
public abstract class MixinGhostSprintDust {

    @Redirect(
        method = "spawnSprintParticle",
        require = 0,
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState trmt$holdForRunningDust(Level level, BlockPos pos) {
        if (level.isClientSide()) Client.holdDust(level, pos);
        return level.getBlockState(pos);
    }
}
