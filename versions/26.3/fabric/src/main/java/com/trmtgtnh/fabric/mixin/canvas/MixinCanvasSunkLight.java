package com.trmtgtnh.fabric.mixin.canvas;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
// The client's own tint reader by 26.1, where 1.21.1 had it in world.level.
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.block.BlockGhost;
import com.trmtgtnh.client.render.SunkLighting;
import com.trmtgtnh.client.texture.SunkLight;

/**
 * A sunk top lit by its height under Canvas, as every other renderer here lights it (0.9.224; Xep: "Try a Canvas hook
 * for sunk light").
 *
 * <p>
 * Canvas lights a face below its block's top the way vanilla does - from inside the block, every corner alike - so a
 * worn row of grass drew darker the deeper it sank, where Forge's pipeline, Rubidium, Embeddium, Sodium and Indigo
 * light each corner by where it is. So a sunk ghost's top has its corners' light and shade replaced, once Canvas has
 * worked them out, by what {@link SunkLight} answers - Forge's lighter's arithmetic, the same answer the vanilla path of
 * each edition is given (MixinSunkLightVanilla on Forge).
 *
 * <p>
 * <strong>Where, on the Canvas 1.20.1 has (0.9.225).</strong> Canvas for 1.20.1 is built on FREX: 1.16.5's
 * {@code TerrainRenderContext.computeAo(quad)} is {@code CanvasTerrainRenderContext.shadeQuad()}, which takes no quad -
 * the quad is the context's {@code emitter}, and the block and its position are its {@code inputContext}'s - and FREX
 * keeps a quad's shade as a whole number out of 255 where Canvas 1.0 kept a fraction. Read from the jar the 1.20.1
 * Canvas instance carries (fetch_1201_mods.py); until it was, the hook named the old class, never ran, and the
 * instance's log never said the line below.
 *
 * <p>
 * Made to be safe when Canvas changes, which is what was asked: {@code @Pseudo}, {@code require = 0}, gated by
 * {@link CanvasGate}, and naming no Canvas or FREX type - every field and method is found by name once and checked. A
 * Canvas that renames any of them costs the sunk light and nothing else: the hook says so once and stands aside for the
 * session. It looks no further than the block of any quad that is not a ghost's.
 */
@Pseudo
@Mixin(targets = "grondag.canvas.apiimpl.rendercontext.CanvasTerrainRenderContext", remap = false)
public abstract class MixinCanvasSunkLight {

    /** The context's own fields, its quad's methods and shade, found once; null when Canvas has none of that name. */
    private static java.lang.reflect.Field trmt$emitter;

    private static java.lang.reflect.Field trmt$input;

    private static java.lang.reflect.Field trmt$state;

    private static java.lang.reflect.Field trmt$pos;

    private static java.lang.reflect.Field trmt$region;

    private static java.lang.reflect.Field trmt$ao;

    private static MethodHandle trmt$face;

    private static MethodHandle trmt$x;

    private static MethodHandle trmt$y;

    private static MethodHandle trmt$z;

    private static MethodHandle trmt$lightmap;

    private static volatile boolean trmt$looked;

    private static volatile boolean trmt$off;

    @Inject(method = "shadeQuad", at = @At("TAIL"), require = 0)
    private void trmt$lightSunkTop(CallbackInfo info) {
        // Minecraft.useAmbientOcclusion() is gone by 26.1; the option it read is asked directly.
        if (trmt$off || !net.minecraft.client.Minecraft.getInstance().options.ambientOcclusion()
            .get()
            .booleanValue()) return;
        try {
            if (!trmt$looked) trmt$find(this);
            if (trmt$off) return;
            Object input = trmt$input.get(this);
            Object stateHeld = input == null ? null : trmt$state.get(input);
            if (!(stateHeld instanceof BlockState) || !(((BlockState) stateHeld).getBlock() instanceof BlockGhost)) return;
            Object quad = trmt$emitter.get(this);
            if (quad == null || (Direction) trmt$face.invoke(quad) != Direction.UP) return;
            BlockState state = (BlockState) stateHeld;
            BlockPos pos = (BlockPos) trmt$pos.get(input);
            Object regionHeld = trmt$region.get(this);
            if (pos == null || !(regionHeld instanceof BlockAndTintGetter)) return;
            BlockAndTintGetter region = (BlockAndTintGetter) regionHeld;
            float height = (float) trmt$y.invoke(quad, 0);
            if (height >= 0.999F || !BlockGhost.litAsSunk(region, pos)) return;
            float[][] where = new float[4][];
            for (int i = 0; i < 4; i++) where[i] = new float[] { (float) trmt$x.invoke(quad, i), (float) trmt$z.invoke(quad, i) };
            float[][] corners = SunkLighting.topCorners(region, state, pos, height, where);
            int[] ao = (int[]) trmt$ao.get(quad);
            // No corner darker than the sunk square's own cell, which on this version holds the light let into it -
            // the light Sodium, Rubidium and Embeddium read there (0.9.224). The shade is SunkLight's, as before, in
            // FREX's own scale.
            // getLightColor is getLightCoords by 26.1, the same packed light of the cell, on LightCoordsUtil from 26.2.
            int cell = net.minecraft.util.LightCoordsUtil.getLightCoords(region, pos);
            for (int i = 0; i < 4; i++) {
                trmt$lightmap.invoke(quad, i, SunkLighting.brighterOf(SunkLight.packed(corners[i][0], corners[i][1]), cell));
                if (ao != null && i < ao.length) ao[i] = Math.round(corners[i][2] * 255.0F);
            }
            SunkLighting.noteLit();
        } catch (Throwable changed) {
            trmt$standAside(changed.toString());
        }
    }

    /** Finds the fields and methods by name, once; anything missing turns the hook off for the session. */
    private static synchronized void trmt$find(Object context) throws IllegalAccessException {
        if (trmt$looked) return;
        trmt$emitter = trmt$field(context.getClass(), "emitter");
        trmt$input = trmt$field(context.getClass(), "inputContext");
        trmt$region = trmt$field(context.getClass(), "region");
        Object input = trmt$input == null ? null : trmt$input.get(context);
        Object quad = trmt$emitter == null ? null : trmt$emitter.get(context);
        if (input != null) {
            trmt$state = trmt$field(input.getClass(), "blockState");
            trmt$pos = trmt$field(input.getClass(), "blockPos");
        }
        if (quad != null) {
            trmt$ao = trmt$field(quad.getClass(), "ao");
            trmt$face = trmt$method(quad.getClass(), "nominalFace");
            trmt$x = trmt$method(quad.getClass(), "x", int.class);
            trmt$y = trmt$method(quad.getClass(), "y", int.class);
            trmt$z = trmt$method(quad.getClass(), "z", int.class);
            trmt$lightmap = trmt$method(quad.getClass(), "lightmap", int.class, int.class);
        }
        trmt$looked = true;
        if (trmt$state == null || trmt$pos == null || trmt$region == null || trmt$ao == null || trmt$face == null
            || trmt$x == null || trmt$y == null || trmt$z == null || trmt$lightmap == null) {
            trmt$standAside("Canvas's terrain context or quad no longer names what this reads");
        } else {
            Trmt.LOG.info("Lighting sunk tops by their height under Canvas, as the other renderers light them");
        }
    }

    private static java.lang.reflect.Field trmt$field(Class<?> from, String name) {
        for (Class<?> at = from; at != null; at = at.getSuperclass()) {
            try {
                java.lang.reflect.Field found = at.getDeclaredField(name);
                found.setAccessible(true);
                return found;
            } catch (NoSuchFieldException | RuntimeException notHere) {
                // Up the hierarchy: the context's fields are its parents'.
            }
        }
        return null;
    }

    private static MethodHandle trmt$method(Class<?> from, String name, Class<?>... args) {
        try {
            java.lang.reflect.Method found = from.getMethod(name, args);
            found.setAccessible(true);
            return MethodHandles.lookup()
                .unreflect(found);
        } catch (NoSuchMethodException | IllegalAccessException | RuntimeException notThere) {
            return null;
        }
    }

    private static void trmt$standAside(String why) {
        if (trmt$off) return;
        trmt$off = true;
        Trmt.LOG.warn("Not lighting sunk tops by their height under Canvas for this session: {}", why);
    }
}
