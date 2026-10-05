package com.trmtgtnh.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;

import com.trmtgtnh.client.OverlayArrivals;

/**
 * Tells the painter when the server has written blocks over ground it may have painted.
 *
 * <p>
 * See {@link OverlayArrivals}, which holds what these do and why; this is the three places the
 * client hears about it. The Fabric module has the same three and reaches them the same way - a
 * mixin that names a method is written into a refmap in one loader's names and the other refuses it,
 * so the hooks cannot be shared even though their bodies are.
 *
 * <p>
 * Each is optional. A pack whose renderer has moved one of these methods gets a game that still
 * starts, and the painter's own rescan closes the hole a couple of seconds later; what the hooks buy
 * is those two seconds.
 */
@Mixin(ClientPacketListener.class)
public class MixinBlockArrivals {

    @Inject(method = "handleLevelChunk", at = @At("TAIL"), require = 0)
    private void trmt$chunkWrittenOver(ClientboundLevelChunkPacket packet, CallbackInfo callback) {
        OverlayArrivals.chunk(packet.getX(), packet.getZ());
    }

    /**
     * A batch of changes, taken as the chunk they are in.
     *
     * <p>
     * The chunk this packet is about is private with nothing to read it by, so it is taken off the
     * first change instead - every change in one of these is in one chunk, which is what the packet
     * is for. Walked through the packet's own iteration because that is the only way in, and the
     * walk stops caring after the first.
     */
    @Inject(method = "handleChunkBlocksUpdate", at = @At("TAIL"), require = 0)
    private void trmt$blocksWrittenOver(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo callback) {
        final BlockPos[] first = new BlockPos[1];
        packet.runUpdates((at, state) -> {
            if (first[0] == null) first[0] = at.immutable();
        });
        OverlayArrivals.batch(first[0]);
    }

    @Inject(method = "handleBlockUpdate", at = @At("TAIL"), require = 0)
    private void trmt$blockWrittenOver(ClientboundBlockUpdatePacket packet, CallbackInfo callback) {
        OverlayArrivals.block(packet.getPos());
    }
}
