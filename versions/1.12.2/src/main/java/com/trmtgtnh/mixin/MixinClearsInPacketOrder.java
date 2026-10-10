package com.trmtgtnh.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.SPacketJoinGame;
import net.minecraft.network.play.server.SPacketRespawn;
import net.minecraft.network.play.server.SPacketUnloadChunk;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.trmtgtnh.client.ClearsInPacketOrder;
import com.trmtgtnh.client.ClientProxy;

/**
 * The client's clears of its wear and lights, queued as their packets arrive so they keep their place among this mod's
 * own packets (spec PN33, PN34; 0.9.222).
 *
 * <p>
 * See {@link ClearsInPacketOrder}, which holds what this does and why. At the head, so the run on the network thread
 * is seen - the one that arrives in order with this mod's packets - before vanilla hands the packet on to the client
 * thread and returns by throwing; the second run, on the client thread, is told so and queues nothing.
 *
 * <p>
 * <strong>{@code require = 1}.</strong> Nothing stands behind these: Forge's unload events no longer clear anything,
 * because a clear from there lands out of order with the wear. Without a hook the client keeps a healed chunk's old
 * record and paints it again when the chunk comes back, and paints one dimension's paths onto the next one's chunks
 * at the same coordinates, until the player leaves the server - wrong ground the player cannot correct, which is worse
 * than being told at once. The head of a vanilla packet handler is missing only if the handler itself is, which is a
 * different game from the one this was built for.
 */
@Mixin(NetHandlerPlayClient.class)
public class MixinClearsInPacketOrder {

    @Inject(method = "handleJoinGame", at = @At("HEAD"), require = 1)
    private void trmt$joined(SPacketJoinGame packet, CallbackInfo ci) {
        ClearsInPacketOrder.joined(
            Minecraft.getMinecraft()
                .isCallingFromMinecraftThread(),
            packet.getDimension(),
            ClientProxy::forgetWorld);
    }

    @Inject(method = "handleRespawn", at = @At("HEAD"), require = 1)
    private void trmt$respawned(SPacketRespawn packet, CallbackInfo ci) {
        ClearsInPacketOrder.respawned(
            Minecraft.getMinecraft()
                .isCallingFromMinecraftThread(),
            packet.getDimensionID(),
            ClientProxy::forgetWorld);
    }

    @Inject(method = "processChunkUnload", at = @At("HEAD"), require = 1)
    private void trmt$chunkUnloaded(SPacketUnloadChunk packet, CallbackInfo ci) {
        ClearsInPacketOrder.chunkUnloaded(
            Minecraft.getMinecraft()
                .isCallingFromMinecraftThread(),
            packet.getX(),
            packet.getZ(),
            ClientProxy::forgetChunk);
    }
}
