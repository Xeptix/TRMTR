package com.trmtgtnh.fabric.mixin.canvas;

import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.block.BlockGhost;
import com.trmtgtnh.client.render.SunkLighting;

/**
 * A ghost whose cell holds no light is no dark corner under Canvas (0.9.228; Xep, 2026-10-26, of the full pass's blast
 * photographs: "1.16.5 Fabric / Canvas Pack, 1.20.1 Fabric / Canvas Pack ... The craters look darker (more shadows)
 * than the other versions").
 *
 * <p>
 * Canvas works a face's corner light out from the cells around it, and leaves out only the cells its region calls
 * closed - those whose state is a solid cube. Every other cell's light goes into the average as it is. A ghost says it
 * occludes nothing (a square with a dip in it must not have its neighbours' faces culled), so Canvas never calls it
 * closed; and a ghost standing in for dirt stops light as the dirt did, so its cell holds none. Each crater wall and
 * each floor beside one therefore averaged a nought into its corners: the walls drew at under half the luma, and the
 * floors at three quarters, of the same crater under Indigo, Sodium or vanilla, which take a neighbour holding no
 * light as missing rather than as dark. Measured in the blast photographs (tools/blast_squares.py, the crater's walls
 * against a plain pit of the game's own blocks in the same picture), with the plain pit drawn as everywhere else.
 *
 * <p>
 * So the cell Canvas asks about is called closed for its light when it is a ghost and holds no light - which is what
 * it would have been called as the block it stands in for, and what vanilla's blend makes of it. Asked in the one
 * place Canvas asks it for its light, the ambient-occlusion calculator its terrain context makes ({@code
 * TerrainRenderContext$1.isOpaque}), so culling and Canvas's own occlusion of whole regions are untouched; a ghost
 * that lets light in - a sunk one - or glows is left open, as before. Made to be safe when Canvas changes, as
 * MixinCanvasSunkLight is: {@code @Pseudo}, {@code require = 0}, gated by {@link CanvasGate}, naming no Canvas type,
 * and standing aside for the session when a field it reads is gone.
 */
@Pseudo
@Mixin(targets = "grondag.canvas.apiimpl.rendercontext.TerrainRenderContext$1", remap = false)
public abstract class MixinCanvasGhostClosed {

    /** The calculator's context, the context's region, the region's states and its light, found once. */
    private static java.lang.reflect.Field trmt$context;

    private static java.lang.reflect.Field trmt$region;

    private static java.lang.reflect.Field trmt$states;

    private static java.lang.reflect.Method trmt$light;

    private static volatile boolean trmt$looked;

    private static volatile boolean trmt$off;

    @Inject(method = "isOpaque(I)Z", at = @At("RETURN"), cancellable = true, require = 0)
    private void trmt$closeDarkGhost(int cacheIndex, CallbackInfoReturnable<Boolean> info) {
        if (trmt$off || info.getReturnValueZ()) return;
        try {
            if (!trmt$looked) trmt$find(this);
            if (trmt$off) return;
            Object region = trmt$region.get(trmt$context.get(this));
            Object[] states = (Object[]) trmt$states.get(region);
            if (cacheIndex < 0 || cacheIndex >= states.length) return;
            Object state = states[cacheIndex];
            boolean ghost = state instanceof BlockState && ((BlockState) state).getBlock() instanceof BlockGhost;
            if (!ghost) return;
            int light = ((Integer) trmt$light.invoke(region, Integer.valueOf(cacheIndex))).intValue();
            if (SunkLighting.closedToLight(ghost, light)) info.setReturnValue(Boolean.TRUE);
        } catch (ReflectiveOperationException | ClassCastException | IllegalArgumentException | NullPointerException changed) {
            trmt$standAside(changed.toString());
        }
    }

    /** Finds the fields and the method by name, once; anything missing turns the hook off for the session. */
    private static synchronized void trmt$find(Object calculator) {
        if (trmt$looked) return;
        try {
            trmt$context = trmt$field(calculator.getClass(), "this$0");
            Object context = trmt$context == null ? null : trmt$context.get(calculator);
            trmt$region = context == null ? null : trmt$field(context.getClass(), "region");
            Object region = trmt$region == null ? null : trmt$region.get(context);
            trmt$states = region == null ? null : trmt$field(region.getClass(), "states");
            trmt$light = region == null ? null : region.getClass()
                .getMethod("cachedBrightness", int.class);
        } catch (ReflectiveOperationException | RuntimeException missing) {
            trmt$states = null;
        }
        trmt$looked = true;
        if (trmt$context == null || trmt$region == null || trmt$states == null || trmt$light == null) {
            trmt$standAside("Canvas's light calculator no longer reaches a region's states and cachedBrightness");
        } else {
            Trmt.LOG.info("Taking a ghost that holds no light as closed under Canvas, as the other renderers take it");
        }
    }

    private static java.lang.reflect.Field trmt$field(Class<?> from, String name) {
        for (Class<?> at = from; at != null; at = at.getSuperclass()) {
            try {
                java.lang.reflect.Field found = at.getDeclaredField(name);
                found.setAccessible(true);
                return found;
            } catch (NoSuchFieldException | RuntimeException notHere) {
                // Up the hierarchy: the context's region is its abstract parent's.
            }
        }
        return null;
    }

    private static void trmt$standAside(String why) {
        if (trmt$off) return;
        trmt$off = true;
        Trmt.LOG.warn("Not taking dark ghosts as closed under Canvas for this session: {}", why);
    }
}
