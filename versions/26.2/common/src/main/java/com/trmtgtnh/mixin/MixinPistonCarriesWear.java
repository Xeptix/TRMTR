package com.trmtgtnh.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonBaseBlock;

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
 * A push and a sticky piston's pull both go through {@code moveBlocks}. The hook is just before vanilla asks its
 * structure resolver whether the move can be made: a retracting piston's head has already been taken away by then, and
 * nothing has moved yet. An inject rather than a redirect, so another mod's hook on the same call still applies, and
 * with only the method's own arguments, so no local is read. Required: without it a pushed worn square loses its
 * wear, which is the server's data and not a drawing correction. Common, one copy for every loader, because every
 * loader here runs Mojang's names (CommonMixinsNeedNoRefmapTest); the 1.20.1 edition keeps one in each loader module.
 */
@Mixin(PistonBaseBlock.class)
public abstract class MixinPistonCarriesWear {

    @Inject(method = "moveBlocks(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Z)Z",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/piston/PistonStructureResolver;resolve()Z"),
        require = 1)
    private void trmt$carryWear(Level level, BlockPos pos, Direction direction, boolean extending,
        CallbackInfoReturnable<Boolean> cir) {
        PistonWear.moving(level, pos, direction, extending);
    }
}
