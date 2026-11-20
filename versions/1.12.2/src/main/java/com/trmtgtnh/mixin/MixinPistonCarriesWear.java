package com.trmtgtnh.mixin;

import net.minecraft.block.BlockPistonBase;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.trmtgtnh.erosion.PistonWear;

/**
 * Hands every move a piston makes to {@link PistonWear}, so the records travel with the blocks (Xep, 2026-11-08: "Wear
 * travels").
 *
 * <p>
 * A push and a sticky piston's pull both go through {@code doMove}. The hook is just before vanilla asks its structure
 * helper whether the move can be made: a retracting piston's head has already been taken away by then, and nothing has
 * moved yet. An inject rather than a redirect, so another mod's hook on the same call still applies, and with only the
 * method's own arguments, so no local is read. Required: without it a pushed worn square loses its wear, which is the
 * server's data and not a drawing correction.
 */
@Mixin(BlockPistonBase.class)
public class MixinPistonCarriesWear {

    @Inject(
        method = "doMove",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/block/state/BlockPistonStructureHelper;canMove()Z"))
    private void trmt$carryWear(World world, BlockPos pos, EnumFacing direction, boolean extending,
        CallbackInfoReturnable<Boolean> cir) {
        PistonWear.moving(world, pos, direction, extending);
    }
}
