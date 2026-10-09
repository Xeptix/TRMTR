package com.trmtgtnh.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

import com.trmtgtnh.client.model.GhostWindows;

/**
 * The light read at a window's cell, answered the way the 1.7.10 edition answers it - GhostWindows.lightAt.
 *
 * <p>
 * A worn square over a block with something see-through behind it stops all light, as that block does, and the faces
 * seen through it - the top of the block below, the sides of its neighbours - read their light from its cell, which
 * holds none. That edition's twin says {@code useNeighborBrightness} and its world then answers with the brightest of
 * the cells around; this version has no such flag, and every renderer on it - vanilla's, Indigo, Sodium with Indium, Canvas and OptiFine under OptiFabric - reads a cell's
 * light through this one method (javap, 2026-10-08), so the answer is given here, at its head, and only ever for a
 * window: everything else returns before anything is asked. 0.9.220; Xep's choice of the exact rule over letting light
 * into the cell.
 *
 * <p>
 * In this module rather than common because it names a game method, which common's refmap would name in Fabric's
 * intermediary names - see CommonMixinsNeedNoRefmapTest. The other loader's module has the same class.
 */
@Mixin(LevelRenderer.class)
public abstract class MixinWindowLight {

    @Inject(
        method = "getLightColor(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)I",
        at = @At("HEAD"),
        cancellable = true)
    private static void trmt$windowLight(BlockAndTintGetter level, BlockState state, BlockPos pos,
        CallbackInfoReturnable<Integer> callback) {
        int packed = GhostWindows.lightAt(level, state, pos);
        if (packed >= 0) callback.setReturnValue(Integer.valueOf(packed));
    }
}
