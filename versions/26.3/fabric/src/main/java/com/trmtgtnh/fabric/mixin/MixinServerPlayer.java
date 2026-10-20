package com.trmtgtnh.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerPlayer;

import com.trmtgtnh.server.ServerEvents;

/**
 * A player has finished a tick on the server, which is where almost all wear comes from.
 *
 * <p>
 * Forge fires {@code PlayerTickEvent} for this. Fabric has a server tick, and walking every player
 * from there once a tick would have worked - but it would read a player who moved at the start of the
 * tick and one who moved at the end at the same moment, and the engine credits wear by the block a
 * mover has just entered. Here, as in the other two editions, the question is asked inside the
 * player's own tick, straight after it has moved.
 *
 * <p>
 * The server's copy of a player, not the client's: this mixin names the server class, so a client's
 * own player is not touched by it at all and there is no side check to forget.
 */
@Mixin(ServerPlayer.class)
public abstract class MixinServerPlayer {

    @Inject(method = "tick", at = @At("TAIL"))
    private void trmt$ticked(CallbackInfo callback) {
        ServerEvents.playerTicked((ServerPlayer) (Object) this);
    }
}
