package com.trmtgtnh.client.render;

import net.minecraft.client.renderer.RenderBlocks;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.config.TrmtConfig;

/**
 * Lifts the outer shell of a layered block clear of the layer drawn inside it.
 *
 * <p>
 * All of the thinking that {@link com.trmtgtnh.mixin.MixinLayeredBlockShell} would otherwise have
 * merged into somebody else's renderer, kept here instead so that the compiler, the formatter and
 * anybody reading this mod can see it, and so that the only thing added to a class this mod cannot
 * compile against is one delegating line.
 *
 * <p>
 * The one line in the log is not decoration. The redirect that calls this has to be allowed to find
 * nothing - on a client without Chisel, and on a future Chisel that draws these blocks some other
 * way - and an injector allowed to find nothing says nothing when it finds nothing. Without this
 * line the difference between "the lift is working" and "the lift never applied" is invisible.
 */
public final class LayerLift {

    private static boolean said;

    private LayerLift() {}

    /**
     * Restates the bounds the renderer asked for, lifted outward by the configured hair.
     *
     * <p>
     * Relative to what was asked for rather than a box of its own, so that a later version drawing
     * a shaped shell is lifted correctly rather than stretched back out into a cube.
     */
    public static void lift(RenderBlocks renderer, double minX, double minY, double minZ, double maxX, double maxY,
        double maxZ) {
        double by = TrmtConfig.liftLayeredBlockShell ? TrmtConfig.layeredBlockShellLift : 0.0D;
        if (!said) {
            // A benign race: two mesher threads can both see false and both write. Two identical
            // lines once in a session is a better failure than a lock on this path.
            said = true;
            Trmt.LOG.info("Lifting the shell of a layered block by {} of a block", Double.valueOf(by));
        }
        renderer.setRenderBounds(minX - by, minY - by, minZ - by, maxX + by, maxY + by, maxZ + by);
    }
}
