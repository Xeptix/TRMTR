package com.trmtgtnh.mixin;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.trmtgtnh.erosion.PistonWear;

/**
 * Hands every moving block a piston sets down to {@link PistonWear}, so its record travels with it (Xep, 2026-11-08:
 * "Wear travels").
 *
 * <p>
 * The piston itself is not hooked. Every move it makes, a push of up to twelve blocks, a sticky piston's pull and its
 * head, ends in {@code World.setTileEntity} with a {@code TileEntityPiston} that knows the block and its way, so the
 * world's own method is the one place that sees them all, after vanilla has decided the move and in the order it
 * makes it. A mod that rewrites the piston's loop or raises its limit still sets its blocks down here. At the head, so
 * the records move before the world takes the moving block; everything that is not a piston's moving block, and every
 * client, leaves at the first test, which is one instanceof on a method that is not on any hot path.
 *
 * <p>
 * Required, like the collision hook: without it a pushed worn square loses its wear, which is the server's data and
 * not a drawing correction.
 */
@Mixin(World.class)
public class MixinPistonCarriesWear {

    @Inject(method = "setTileEntity", at = @At("HEAD"))
    private void trmt$carryWear(int x, int y, int z, TileEntity tileEntity, CallbackInfo ci) {
        PistonWear.moved((World) (Object) this, x, y, z, tileEntity);
    }
}
