package com.trmtgtnh.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;

/**
 * The server's light for one chunk column has just been queued over this client's own, so the squares
 * painted there are lit again once it lands - see {@code ClientOverlay.serverLightArrived}, which says
 * why the server's light is wrong for them and why it cannot simply be left to the next frame.
 *
 * <p>
 * {@code handleLightUpdatePacked} is the official name at this version, typing slip and all - read
 * from the mapped game jar rather than guessed, because the obvious spelling is not it.
 *
 * <p>
 * At the tail, so it runs only on the game thread: the handler's first line hands a packet that
 * arrived anywhere else to that thread and leaves by an exception, which never reaches the tail. And
 * the body only calls out, for the reason {@link com.trmtgtnh.mixin.MixinPackRepository} gives.
 *
 * <p>
 * <strong>In each loader's module, not in common, from 0.9.219.</strong> It names a game method, so the
 * name goes into a refmap, and common's processor writes that refmap in Fabric's intermediary names: a
 * production Forge jar has it remapped on the way out, and a Forge development run reads it as it is and
 * refuses the mixin - which, on a class as early as {@code Block}, is a game that never starts. Every
 * mixin left in common injects with {@code remap = false} for that reason; see
 * {@code CommonMixinsNeedNoRefmapTest}. The Fabric module has the same class.
 */
@Mixin(ClientPacketListener.class)
public abstract class MixinServerLightArrives {

    @Inject(method = "handleLightUpdatePacked", at = @At("TAIL"))
    private void trmt$relightPainted(ClientboundLightUpdatePacket packet, CallbackInfo callback) {
        com.trmtgtnh.client.ClientOverlay.serverLightArrived(packet.getX(), packet.getZ());
    }
}
