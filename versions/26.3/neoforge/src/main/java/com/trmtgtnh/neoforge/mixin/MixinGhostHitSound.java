package com.trmtgtnh.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The knock of a pick on a worn square is its family's, on NeoForge as on the other loaders (0.9.222, spec GF10).
 *
 * <p>
 * To 26.2 NeoForge's patch of {@code MultiPlayerGameMode.continueDestroyBlock} asked the state at its square for the
 * sound ({@code getSoundType(level, pos, player)}, NeoForge's own question, which the ghost answers with its family's -
 * {@code BlockGhost.getSoundType}). From 26.3 the knock is played by {@code ClientLevel.playBreakingSound(pos, state)},
 * and NeoForge 26.3.0.94's copy of it asks the plain {@code getSoundType()}, with no square (read from that version's
 * patched jar, where its {@code LevelEventHandler} still asks with the square for the breaking sound). So this asks
 * NeoForge's question there again, as 26.2's NeoForge asked it: the client's level, the square, the client's player.
 * Not required: a game that has moved the method plays the ghost's own knock, never stops.
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
        return state.getSoundType((ClientLevel) (Object) this, pos, net.minecraft.client.Minecraft.getInstance().player);
    }
}
