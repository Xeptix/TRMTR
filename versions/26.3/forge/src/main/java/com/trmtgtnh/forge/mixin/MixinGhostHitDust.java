package com.trmtgtnh.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import com.trmtgtnh.block.BlockGhost;

/**
 * The ghost's hitting dust, held for the square's family on Forge from 26.3 (spec GF19).
 *
 * <p>
 * To 26.2 Forge asked a block's client extensions for its hitting dust with the hit in hand
 * ({@code IClientBlockExtensions.addHitEffects(state, level, HitResult, engine)}), and {@code GhostDustForge} handed it
 * to the ghost, which held the square the hit named. Forge 66 asks it with the face alone - {@code addHitEffects(state,
 * level, Direction, engine)}, called from {@code ClientLevel.addBreakingBlockEffects} (read from Forge 26.3-66.0.13's
 * patched jar) - so the extension can no longer say which square it is. This holds at the head of that method instead,
 * where the square is in hand, the moment Forge asks its extension, before the game spawns the dust: for a ghost only,
 * as the extension was asked for a ghost only. Not required, as Fabric's copy of this moment is not: a game that has
 * moved the method loses the family's picture, never the game.
 */
@Mixin(ClientLevel.class)
public abstract class MixinGhostHitDust {

    @Inject(method = "addBreakingBlockEffects", at = @At("HEAD"), require = 0)
    private void trmt$holdForHittingDust(BlockPos pos, Direction face, boolean playSound, CallbackInfo callback) {
        ClientLevel level = (ClientLevel) (Object) this;
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof BlockGhost) {
            ((BlockGhost) state.getBlock()).addHitEffects(state, level, pos,
                net.minecraft.client.Minecraft.getInstance().particleEngine);
        }
    }
}
