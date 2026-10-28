package com.trmtgtnh.forge.mixin;

import java.util.Set;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import com.trmtgtnh.erosion.ErosionEngine;

/**
 * Tells the engine the server has just put a player somewhere, so the first square they are seen on is not counted
 * (0.9.228; Xep: "Skip that first square"; see {@link ErosionEngine#arrived}).
 *
 * <p>
 * {@code ServerGamePacketListenerImpl.teleport(DDDFFLjava/util/Set;)V} is the server's own "the player is now here" -
 * the five-argument one hands to it - and every vanilla way of moving a player passes through it at this version
 * (read in this loader's jar): a join ({@code PlayerList.placeNewPlayer}), a respawn ({@code PlayerList.respawn}),
 * {@code ServerPlayer.teleportTo} - the /tp command, an ender pearl, most mods' teleports - a ride and a bed. The one
 * way it misses is a change of dimension, which {@code ServerPlayer.changeDimension} does without it here; that is
 * heard from the loader's own event instead. The 1.7.10 edition's seam is {@code
 * NetHandlerPlayServer.setPlayerLocation}, the same method under its older name.
 *
 * <p>
 * In this loader's module rather than common's, because it names a game method, which a common mixin may not (see
 * CommonMixinsNeedNoRefmapTest); the same mixin stands in the other loader's module. Server code, in both sides' list.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MixinPlayerArrives {

    @Inject(method = "teleport(DDDFFLjava/util/Set;)V", at = @At("HEAD"))
    private void trmt$arrives(double x, double y, double z, float yaw, float pitch,
        Set<ClientboundPlayerPositionPacket.RelativeArgument> relative, CallbackInfo ci) {
        ErosionEngine.get().arrived(((ServerGamePacketListenerImpl) (Object) this).player);
    }
}
