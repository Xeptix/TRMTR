package com.trmtgtnh.mixin;

import net.minecraft.network.NetHandlerPlayServer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.trmtgtnh.erosion.ErosionEngine;

/**
 * Tells the engine the server has just put a player somewhere, so the first square they are seen on is not counted
 * (0.9.228; Xep: "Skip that first square"; see {@link ErosionEngine#arrived}).
 *
 * <p>
 * {@code NetHandlerPlayServer.setPlayerLocation(DDDFF)V} is the server's own "the player is now here", and every
 * vanilla way of moving a player passes through it (read in this version's bytecode): a join
 * ({@code ServerConfigurationManager.initializeConnectionToPlayer}), a respawn ({@code respawnPlayer}), a change of
 * dimension ({@code transferPlayerToDimension}), {@code EntityPlayerMP.setPositionAndUpdate} - the /tp command, ender
 * pearls and most mods' teleports - and {@code mountEntity}. Forge for this version has no event for /tp
 * ({@code EnderTeleportEvent} is pearls and endermen only), so this is the only way to hear it. It also re-sends the
 * position once a second until the client confirms it, and corrects a player who moved wrongly; each of those arms the
 * same one skipped square.
 *
 * <p>
 * Common, because it is server code - a dedicated server's, or the one inside single player.
 */
@Mixin(NetHandlerPlayServer.class)
public abstract class MixinPlayerArrives {

    @Inject(method = "setPlayerLocation(DDDFF)V", at = @At("HEAD"))
    private void trmt$arrives(double x, double y, double z, float yaw, float pitch, CallbackInfo ci) {
        ErosionEngine engine = ErosionEngine.get();
        engine.arrived(((NetHandlerPlayServer) (Object) this).playerEntity);
    }
}
