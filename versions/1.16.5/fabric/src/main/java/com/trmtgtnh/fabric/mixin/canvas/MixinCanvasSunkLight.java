package com.trmtgtnh.fabric.mixin.canvas;

import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
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
 * light each corner by where it is. Canvas works a quad's light out in one public method of its terrain context,
 * {@code computeAo}; at its end the context still holds the block, its position and the region it is drawn from. So a
 * sunk ghost's top has its corners' light and shade replaced there by what {@link SunkLight} answers - Forge's
 * lighter's arithmetic, the same answer the vanilla path of each edition is given (MixinSunkLightVanilla on Forge).
 *
 * <p>
 * Made to be safe when Canvas changes, which is what was asked: {@code @Pseudo}, {@code require = 0}, gated by
 * {@link CanvasGate}, and naming no Canvas type - the quad is taken as the Fabric Renderer API's own
 * {@link MutableQuadView}, which Canvas's quad is, and the context's block, position and region, and the quad's shade,
 * are read by name once and checked. A Canvas that renames any of them, or no longer calls this method, costs the sunk
 * light and nothing else: the hook says so once and stands aside for the session. It touches only a sunk ghost's top
 * face; every other quad Canvas draws passes through untouched.
 */
@Pseudo
@Mixin(targets = "grondag.canvas.apiimpl.rendercontext.TerrainRenderContext", remap = false)
public abstract class MixinCanvasSunkLight {

    /** The context's own fields and the quad's shade, found once; null when Canvas has none of that name. */
    private static java.lang.reflect.Field trmt$state;

    private static java.lang.reflect.Field trmt$pos;

    private static java.lang.reflect.Field trmt$region;

    private static java.lang.reflect.Field trmt$ao;

    private static volatile boolean trmt$looked;

    private static volatile boolean trmt$off;

    @Inject(method = "computeAo", at = @At("TAIL"), require = 0)
    private void trmt$lightSunkTop(@Coerce MutableQuadView quad, CallbackInfo info) {
        if (trmt$off || quad == null || quad.nominalFace() != Direction.UP) return;
        try {
            if (!trmt$looked) trmt$find(this, quad);
            if (trmt$off) return;
            Object stateHeld = trmt$state.get(this);
            if (!(stateHeld instanceof BlockState) || !(((BlockState) stateHeld).getBlock() instanceof BlockGhost)) return;
            BlockState state = (BlockState) stateHeld;
            BlockPos pos = (BlockPos) trmt$pos.get(this);
            Object regionHeld = trmt$region.get(this);
            if (pos == null || !(regionHeld instanceof BlockAndTintGetter)) return;
            BlockAndTintGetter region = (BlockAndTintGetter) regionHeld;
            float height = quad.y(0);
            if (height >= 0.999F || !BlockGhost.litAsSunk(region, pos)) return;
            float[][] where = new float[4][];
            for (int i = 0; i < 4; i++) where[i] = new float[] { quad.x(i), quad.z(i) };
            float[][] corners = SunkLighting.topCorners(region, state, pos, height, where);
            float[] ao = (float[]) trmt$ao.get(quad);
            // No corner darker than the sunk square's own cell, which on this version holds the light let into it -
            // the light Sodium, Rubidium and Embeddium read there (0.9.224): with SunkLight's corners alone the turf
            // part's sunk grass drew up to seven levels darker than Sodium's, more the deeper it sank; with the cell's
            // light under them it is within one at every square. The shade is SunkLight's, as before.
            int cell = net.minecraft.client.renderer.LevelRenderer.getLightColor(region, pos);
            for (int i = 0; i < 4; i++) {
                quad.lightmap(i, SunkLighting.brighterOf(SunkLight.packed(corners[i][0], corners[i][1]), cell));
                if (ao != null && i < ao.length) ao[i] = corners[i][2];
            }
            SunkLighting.noteLit();
        } catch (ReflectiveOperationException | ClassCastException | IllegalArgumentException changed) {
            trmt$standAside(changed.toString());
        }
    }

    /** Finds the fields by name, once; anything missing turns the hook off for the session. */
    private static synchronized void trmt$find(Object context, Object quad) {
        if (trmt$looked) return;
        trmt$state = trmt$field(context.getClass(), "blockState");
        trmt$pos = trmt$field(context.getClass(), "blockPos");
        trmt$region = trmt$field(context.getClass(), "region");
        trmt$ao = trmt$field(quad.getClass(), "ao");
        trmt$looked = true;
        if (trmt$state == null || trmt$pos == null || trmt$region == null || trmt$ao == null) {
            trmt$standAside("Canvas's terrain context or quad no longer names blockState, blockPos, region or ao");
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
                // Up the hierarchy: the context's fields are its abstract parent's.
            }
        }
        return null;
    }

    private static void trmt$standAside(String why) {
        if (trmt$off) return;
        trmt$off = true;
        Trmt.LOG.warn("Not lighting sunk tops by their height under Canvas for this session: {}", why);
    }
}
