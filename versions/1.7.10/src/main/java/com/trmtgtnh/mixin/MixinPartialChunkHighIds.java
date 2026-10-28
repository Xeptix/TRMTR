package com.trmtgtnh.mixin;

import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Clears the high half of the block ids in each section a partial chunk packet sends without one, before the packet is
 * read (0.9.228; Xep, 2026-10-27, of the 1.7.10 Chisel copy's crater rim drawn as Chisel's valentines block: "Fix
 * before release").
 *
 * <p>
 * A block id above 255 keeps its high four bits in a section's second array, and vanilla clears that array only when a
 * whole chunk arrives: a chunk sent again in part - which the server does after sixty-four changes in one, as a blast
 * makes - writes the low eight bits and leaves the high four as they were. Vanilla's own world never notices, because
 * the client's blocks are the server's. Worn ground's are not: every ghost is a block of this mod's, painted into the
 * client's copy alone with an id above 255, so a section resent in part kept each ghost's high bits under whatever the
 * server sent. The rim of a blast crater is grass, id 2, under ghosts whose high bits were 2; read back it was 0x202,
 * which is
 * whatever block a pack numbers 514 - Chisel's valentines in that copy.
 *
 * <p>
 * A section the packet sends with no high half is one the server holds no id above 255 in, so clearing the client's is
 * what the server says; the painter then paints the ground again from what was really sent (MixinBlockArrivals). A
 * section the packet does send a high half for is read whole by vanilla, and a whole chunk is cleared by vanilla.
 *
 * <p>
 * Not required, like the other drawing corrections: a pack whose chunk reader is replaced reads its own way.
 */
@Mixin(Chunk.class)
public abstract class MixinPartialChunkHighIds {

    @Shadow
    private ExtendedBlockStorage[] storageArrays;

    @Inject(method = "fillChunk", at = @At("HEAD"), require = 0)
    private void trmt$clearStaleHighIds(byte[] data, int sent, int sentHigh, boolean whole, CallbackInfo ci) {
        if (whole) return;
        for (int section = 0; section < storageArrays.length; section++) {
            if (stale(sent, sentHigh, section) && storageArrays[section] != null
                && storageArrays[section].getBlockMSBArray() != null) {
                storageArrays[section].clearMSBArray();
            }
        }
    }

    /** Whether a section is sent with its low ids and without its high half, so the client's high half is stale. */
    private static boolean stale(int sent, int sentHigh, int section) {
        return (sent & 1 << section) != 0 && (sentHigh & 1 << section) == 0;
    }
}
