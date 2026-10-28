package com.trmtgtnh.mixin;

import java.util.Set;

import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.play.server.SPacketPlayerPosLook;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.trmtgtnh.erosion.ErosionEngine;

/**
 * Tells the engine the server has just put a player somewhere, so the first square they are seen on is not counted
 * (0.9.228; Xep: "Skip that first square"; see {@link ErosionEngine#arrived}) - the 1.7.10 edition's mixin of this
 * name.
 *
 * <p>
 * {@code NetHandlerPlayServer.setPlayerLocation(DDDFFLjava/util/Set;)V} is the server's own "the player is now here":
 * the five-argument form hands to it, and every vanilla way of moving a player passes through one or the other (read in
 * this version's bytecode) - a join ({@code PlayerList.initializeConnectionToPlayer}), a respawn
 * ({@code recreatePlayerEntity}), a change of dimension ({@code transferPlayerToDimension}),
 * {@code EntityPlayerMP.setPositionAndUpdate} - the /tp command, ender pearls and most mods' teleports - and
 * {@code startRiding}, {@code dismountRidingEntity}, {@code trySleep} and {@code wakeUpPlayer}. Forge for this version
 * has no teleport event ({@code EntityTeleportEvent} is not in its jar), so this is the only way to hear a /tp. It also
 * re-sends the position until the client confirms it, and corrects a player who moved wrongly; each of those arms the
 * same one skipped square.
 *
 * <p>
 * Common, because it is server code - a dedicated server's, or the one inside single player.
 */
@Mixin(NetHandlerPlayServer.class)
public abstract class MixinPlayerArrives {

    @Inject(method = "setPlayerLocation(DDDFFLjava/util/Set;)V", at = @At("HEAD"))
    private void trmt$arrives(double x, double y, double z, float yaw, float pitch,
        Set<SPacketPlayerPosLook.EnumFlags> relative, CallbackInfo ci) {
        ErosionEngine engine = ErosionEngine.get();
        engine.arrived(((NetHandlerPlayServer) (Object) this).player);
    }
}
