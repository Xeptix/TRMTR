package com.trmtgtnh.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;

import com.trmtgtnh.client.ClearsInPacketOrder;
import com.trmtgtnh.client.ClientSide;

/**
 * The server told the client to forget a chunk, so its wear and lights go with it (spec PN33) - queued behind this
 * mod's packets that arrived before it, not run on the spot (0.9.222).
 *
 * <p>
 * See {@link ClientSide#forgetChunk} for what goes and {@link ClearsInPacketOrder} for why it waits in this mod's
 * queue. At the tail, because the handler first hands itself to the client thread and returns by throwing: only the
 * run on the client thread reaches here, which is where this mod's packets join its queue on this version, in the
 * order they arrived with vanilla's. Required: there is nothing behind it, and an old record left here paints a chunk
 * the server has healed. The Fabric module has the same hook - a mixin that names a method is written into a refmap in
 * one loader's names and the other refuses it.
 */
@Mixin(ClientPacketListener.class)
public class MixinChunkForgotten {

    @Inject(method = "handleForgetLevelChunk", at = @At("TAIL"))
    private void trmt$chunkForgotten(ClientboundForgetLevelChunkPacket packet, CallbackInfo callback) {
        ClearsInPacketOrder.chunkForgotten(packet.getX(), packet.getZ(), ClientSide::forgetChunk);
    }
}
