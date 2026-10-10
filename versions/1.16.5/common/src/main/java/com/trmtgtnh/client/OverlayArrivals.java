package com.trmtgtnh.client;

import net.minecraft.core.BlockPos;

/**
 * What the painter is told when the server writes blocks over ground it may have painted.
 *
 * <p>
 * Worn ground is painted into the client's own copy of the world, and three of the server's own
 * packets write straight over it: a chunk sent again in part, a batch of block changes, and a single
 * one. Nothing announces any of them to a mod - a partial chunk resend does not even count as a
 * chunk loading - so without these the painter goes on believing it has painted what the server has
 * just rubbed out.
 *
 * <p>
 * <strong>This exists so the two loaders' hooks have one body between them.</strong> The hooks
 * themselves are mixins on the client's packet handler and have to live in each loader's own module,
 * because a mixin that names a method is written into a refmap in that loader's names and the other
 * refuses it. What they do is the same on both, so it is here, and each hook is three lines of
 * unpacking.
 */
public final class OverlayArrivals {

    private OverlayArrivals() {}

    /** A chunk, or part of one, has just been written over. */
    public static void chunk(int chunkX, int chunkZ) {
        OverlayPainter.get()
            .chunkArrived(chunkX, chunkZ);
    }

    /** A batch of changes, taken as the chunk they are in: every change in one of these shares it. */
    public static void batch(BlockPos first) {
        if (first == null) return;
        chunk(first.getX() >> 4, first.getZ() >> 4);
    }

    /** One block written over. */
    public static void block(BlockPos at) {
        if (at == null) return;
        OverlayPainter.get()
            .blockArrived(at.getX(), at.getY(), at.getZ());
    }
}
