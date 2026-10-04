package com.trmtgtnh.mixin;

import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.S21PacketChunkData;
import net.minecraft.network.play.server.S22PacketMultiBlockChange;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.world.ChunkCoordIntPair;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.trmtgtnh.client.OverlayPainter;

/**
 * Tells the painter when the server has written blocks over ground it may have painted.
 *
 * <p>
 * Worn ground is painted into the client's own copy of the world, and three of the server's own
 * packets write straight over it: a chunk sent again in part, a batch of block changes, and a single
 * one. Nothing announces any of them to a mod - a partial chunk resend does not even count as a chunk
 * loading - so without these the painter went on believing it had painted what the server had just
 * rubbed out. Each hook runs after vanilla has written the blocks, on the client thread that handles
 * the packet, and only ever asks the painter to look again.
 *
 * <p>
 * Not required, like the other drawing corrections: a renderer or network change that moved these
 * methods would cost only the repaint, which is not worth refusing to start a large pack over. The
 * painter says once in the log, the first time one of them runs, that they are in place.
 */
@Mixin(NetHandlerPlayClient.class)
public class MixinBlockArrivals {

    @Inject(method = "handleChunkData", at = @At("TAIL"), require = 0)
    private void trmt$chunkWrittenOver(S21PacketChunkData packet, CallbackInfo ci) {
        OverlayPainter.get()
            .chunkArrived(packet.func_149273_e(), packet.func_149271_f());
    }

    @Inject(method = "handleMultiBlockChange", at = @At("TAIL"), require = 0)
    private void trmt$blocksWrittenOver(S22PacketMultiBlockChange packet, CallbackInfo ci) {
        ChunkCoordIntPair chunk = packet.func_148920_c();
        if (chunk != null) OverlayPainter.get()
            .chunkArrived(chunk.chunkXPos, chunk.chunkZPos);
    }

    @Inject(method = "handleBlockChange", at = @At("TAIL"), require = 0)
    private void trmt$blockWrittenOver(S23PacketBlockChange packet, CallbackInfo ci) {
        OverlayPainter.get()
            .blockArrived(packet.func_148879_d(), packet.func_148878_e(), packet.func_148877_f());
    }
}
