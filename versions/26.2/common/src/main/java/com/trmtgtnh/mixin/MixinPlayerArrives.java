package com.trmtgtnh.mixin;

import java.util.Set;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3;

import com.trmtgtnh.erosion.ClientAnswered;

/**
 * The first square a player is seen on after joining or a teleport is not counted (0.9.228; Xep: "Skip that first
 * square"): the server's own "the player is now here" seam tells the engine the player has arrived.
 *
 * <p>
 * {@code ServerGamePacketListenerImpl.teleport(PositionMoveRotation, Set<Relative>)} is where every vanilla way of
 * moving a player ends - read in 26.1.2's, 26.2's and 26.3's jars on each loader: {@code PlayerList.placeNewPlayer}
 * (joining), {@code PlayerList.respawn}, {@code ServerPlayer.teleport(TeleportTransition)} (dimension changes
 * included), {@code teleportTo}, {@code teleportRelative}, {@code startRiding}, {@code startSleeping} and
 * {@code stopSleepInBed}; the five-number teleport builds a {@code PositionMoveRotation} and hands to this one. Fabric
 * has no teleport event at any version, and NeoForge's and Forge's {@code EntityTeleportEvent} miss the join and the
 * respawn, so the seam is the one place every loader's jar hears them all; one mixin in the shared config serves
 * NeoForge, Forge and Fabric. The 1.7.10 edition's seam is {@code NetHandlerPlayServer.setPlayerLocation}.
 *
 * <p>
 * At the head, before the position moves: the engine only notes the player and forgets their last square, and the
 * square is decided on their next grounded tick. The body does nothing but call out, as every mixin here.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MixinPlayerArrives implements ClientAnswered {

    @Inject(
        method = "teleport(Lnet/minecraft/world/entity/PositionMoveRotation;Ljava/util/Set;)V",
        at = @At("HEAD"))
    private void trmt$arrived(PositionMoveRotation destination, Set<Relative> relatives, CallbackInfo callback) {
        com.trmtgtnh.erosion.ErosionEngine.get().arrived(((ServerGamePacketListenerImpl) (Object) this).player);
    }

    /** The position the server waits for the client to confirm, null once it has (0.9.229). */
    @Shadow
    private Vec3 awaitingPositionFromClient;

    /**
     * Whether the client has answered the last place it was put (0.9.229; see ClientAnswered): the confirmation the
     * server waits for, and the client's word that its world has loaded, before which this version's server drops the
     * client's movement (handleMovePlayer returns at hasClientLoaded). Until it has, the server's "on the ground" for
     * the player is the saved or the old one, and an arrival waits.
     */
    @Override
    public boolean trmtAnswered() {
        return awaitingPositionFromClient == null && ((ServerGamePacketListenerImpl) (Object) this).hasClientLoaded();
    }
}
