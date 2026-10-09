package com.trmtgtnh.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MaterialColor;

import com.trmtgtnh.block.BlockGhost;

/**
 * Makes a worn square paint on a map as the ground it stands in for.
 *
 * <p>
 * See {@link BlockGhost#mapColorAt}, which holds the decision, and the Forge module's copy of this
 * injection, which carries the argument for why a mixin is the only place both loaders have. The
 * guard is on the block, so every other block in the world pays one {@code instanceof}.
 *
 * <p>
 * Required rather than optional: a path that reads as an unknown block on every map and minimap in
 * the pack is the kind of wrongness a player reports as a bug in the minimap.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class MixinGhostMapColor {

    @Inject(
        method = "getMapColor(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)"
            + "Lnet/minecraft/world/level/material/MaterialColor;",
        at = @At("HEAD"),
        cancellable = true)
    private void trmt$paintWornGroundAsItsOwn(BlockGetter level, BlockPos pos,
        CallbackInfoReturnable<MaterialColor> callback) {
        BlockState state = (BlockState) (Object) this;
        if (!(state.getBlock() instanceof BlockGhost)) return;
        MaterialColor own = BlockGhost.mapColorAt(state, level, pos);
        if (own != null) callback.setReturnValue(own);
    }
}
