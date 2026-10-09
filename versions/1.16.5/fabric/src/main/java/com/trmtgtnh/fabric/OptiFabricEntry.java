package com.trmtgtnh.fabric;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.client.renderer.RenderType;

import com.trmtgtnh.Trmt;

/**
 * The buffer Indigo is about to write a FRAPI model's quads into, for the one arrangement that needs to be told:
 * OptiFabric.
 *
 * <p>
 * OptiFine stamps every vertex with the shader entry on top of a small stack its buffer carries, and pushes a
 * block's entry from its own renderModel. Under OptiFabric a FRAPI model never gets there: Indigo's redirect inside
 * OptiFine's chunk rebuild hands it to TerrainRenderContext.tesselateBlock, which calls the model's emitBlockQuads
 * straight - found on 2026-10-08 with a stack trace from inside the model, after a hook on OptiFine's renderModel
 * had bound and never once seen a ghost. So worn ground took the pack's default material there. The model pushes
 * the covered block's entry itself, onto the buffer this finds, before it emits, and takes it off after.
 *
 * <p>
 * The buffer is the one Indigo's own emitter writes a quad of that pass into: the terrain context's ChunkRenderInfo,
 * asked by pass - read with javap from Indigo 0.4.5 in fabric-api 0.42.0. Indigo is Fabric API's implementation and
 * named nowhere at compile time; the first lookup that fails gives up for the session, as every other seam into
 * another mod's insides here does. Any other context - Indium's, Canvas's, Indigo drawing a block outside a chunk -
 * gets null, and so does every context when OptiFine is not there to push onto.
 */
public final class OptiFabricEntry {

    private static final String TERRAIN_CONTEXT = "net.fabricmc.fabric.impl.client.indigo.renderer.render.TerrainRenderContext";

    private static Field chunkInfo;

    private static Method bufferFor;

    private static volatile boolean looked;

    private static volatile boolean gaveUp;

    private OptiFabricEntry() {}

    private static synchronized void look(Class<?> context) {
        if (looked) return;
        looked = true;
        try {
            chunkInfo = context.getDeclaredField("chunkInfo");
            chunkInfo.setAccessible(true);
            bufferFor = chunkInfo.getType()
                .getMethod("getInitializedBuffer", RenderType.class);
        } catch (Throwable moved) {
            chunkInfo = null;
            bufferFor = null;
            gaveUp = true;
            Trmt.LOG.warn("Indigo's terrain buffers are not where 0.4.5 keeps them, so worn ground under OptiFabric takes the shader pack's default material: {}", moved.toString());
        }
    }

    /** The buffer a quad drawn in this pass goes into, or null wherever there is nothing to push onto. */
    public static VertexConsumer bufferOf(RenderContext context, RenderType pass) {
        if (gaveUp || context == null || !TERRAIN_CONTEXT.equals(context.getClass()
            .getName())) return null;
        if (!looked) look(context.getClass());
        if (chunkInfo == null || bufferFor == null) return null;
        try {
            return (VertexConsumer) bufferFor.invoke(chunkInfo.get(context), pass);
        } catch (Throwable awkward) {
            chunkInfo = null;
            bufferFor = null;
            gaveUp = true;
            Trmt.LOG.warn("Giving up on Indigo's terrain buffers under OptiFabric: {}", awkward.toString());
            return null;
        }
    }
}
