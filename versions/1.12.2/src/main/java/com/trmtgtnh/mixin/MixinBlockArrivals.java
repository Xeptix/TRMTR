package com.trmtgtnh.mixin;

import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.SPacketBlockChange;
import net.minecraft.network.play.server.SPacketChunkData;
import net.minecraft.network.play.server.SPacketMultiBlockChange;
import net.minecraft.util.math.BlockPos;

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
 * <strong>Without these the painter looks for the ghost instead</strong>, every forty ticks, which is
 * what this edition did until now and is written up beside that rescan. It closes the hole but not the
 * flicker: the server writes its own block over the ghost whenever a dig is started or given up, and
 * to the block clicked whenever something is placed against it - so swinging at a worn path made it
 * read as plain ground for up to two seconds. The rescan stays, because it also covers a chunk that
 * was cached out of range and has since come back into it, which no packet announces either.
 *
 * <p>
 * Client-side, because these are the client's own packet handlers, and the painter exists nowhere
 * else.
 *
 * <p>
 * <strong>{@code require = 0}.</strong> A renderer or network change that moved these methods costs
 * the repaint, not the launch. What is lost is two seconds of a worn path reading as plain ground
 * after somebody swings at it, and the rescan behind these hooks covers the same ground more slowly
 * - it is there anyway for a chunk that was cached out of overlay range and has come back into it,
 * which no packet announces either.
 *
 * <p>
 * Refusing to start would be aimed at the wrong person. A player whose pack contains something that
 * moved a packet handler did not choose it and cannot fix it, and would lose the whole game rather
 * than a repaint.
 *
 * <p>
 * The painter still says once in the log, the first time one of these runs, that they are in place.
 *
 * <p>
 * Nothing here catches a missing loader: with no MixinBooter there is nothing reading this config, so
 * {@code require} is never consulted. That is what the {@code required-after:mixinbooter} dependency
 * on {@code Trmt} is for.
 */
@Mixin(NetHandlerPlayClient.class)
public class MixinBlockArrivals {

    @Inject(method = "handleChunkData", at = @At("TAIL"), require = 0)
    private void trmt$chunkWrittenOver(SPacketChunkData packet, CallbackInfo ci) {
        OverlayPainter.get()
            .chunkArrived(packet.getChunkX(), packet.getChunkZ());
    }

    /**
     * A batch of changes, taken as the chunk they are in.
     *
     * <p>
     * The chunk this packet is about is a private field here with nothing to read it by, so it is
     * taken off the first change instead - every change in one of these is in one chunk, which is what
     * the packet is for. Queued as a chunk rather than block by block, as the other edition does it: a
     * burst of changes then costs one pass.
     */
    @Inject(method = "handleMultiBlockChange", at = @At("TAIL"), require = 0)
    private void trmt$blocksWrittenOver(SPacketMultiBlockChange packet, CallbackInfo ci) {
        SPacketMultiBlockChange.BlockUpdateData[] changed = packet.getChangedBlocks();
        if (changed == null || changed.length == 0 || changed[0] == null) return;
        BlockPos first = changed[0].getPos();
        if (first == null) return;
        OverlayPainter.get()
            .chunkArrived(first.getX() >> 4, first.getZ() >> 4);
    }

    @Inject(method = "handleBlockChange", at = @At("TAIL"), require = 0)
    private void trmt$blockWrittenOver(SPacketBlockChange packet, CallbackInfo ci) {
        BlockPos at = packet.getBlockPosition();
        if (at == null) return;
        OverlayPainter.get()
            .blockArrived(at.getX(), at.getY(), at.getZ());
    }
}
