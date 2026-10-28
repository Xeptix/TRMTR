package com.trmtgtnh.fabric.mixin;

import java.util.Set;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.RelativeMovement;

import com.trmtgtnh.erosion.ErosionEngine;

/**
 * Tells the engine the server has just put a player somewhere, so the first square they are seen on is not counted
 * (0.9.228; Xep: "Skip that first square"; see {@link ErosionEngine#arrived}).
 *
 * <p>
 * {@code ServerGamePacketListenerImpl.teleport(DDDFFLjava/util/Set;)V} is the server's own "the player is now here",
 * and every vanilla way of moving a player passes through it (read in this version's bytecode; the five-argument
 * {@code teleport} hands to it): a join ({@code PlayerList.placeNewPlayer}), a respawn ({@code PlayerList.respawn}),
 * {@code ServerPlayer.teleportTo} - the /tp command, an ender pearl and most mods' teleports - {@code teleportRelative},
 * a change of dimension ({@code changeDimension}), {@code startRiding} and {@code stopSleepInBed}. The
 * listener also re-sends the position once a second until the client confirms it, and corrects a player who moved
 * wrongly; each of those arms the same one skipped square.
 *
 * <p>
 * The 1.7.10 edition's {@code MixinPlayerArrives} on {@code NetHandlerPlayServer.setPlayerLocation}. In this module,
 * and the same in the Forge one, rather than in common: at 1.20.1 a common mixin may name no vanilla member
 * ({@code CommonMixinsNeedNoRefmapTest}), and the 1.21.1 edition keeps the one copy in common.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MixinPlayerArrives {

    @Inject(method = "teleport(DDDFFLjava/util/Set;)V", at = @At("HEAD"))
    private void trmt$arrives(double x, double y, double z, float yaw, float pitch, Set<RelativeMovement> relative,
        CallbackInfo callback) {
        ErosionEngine.get().arrived(((ServerGamePacketListenerImpl) (Object) this).player);
    }
}
