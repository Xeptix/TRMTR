package com.trmtgtnh.mixin;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.trmtgtnh.block.BlockGhost;

/**
 * The world's own two light reads at a ghost's square, asked per square whether it takes its neighbours' light - see
 * {@link BlockGhost#litFromNeighboursAt} and {@code MixinGhostNeighbourLightCache}, which answers the mesher (0.9.222,
 * spec GT27, GF8).
 *
 * <p>
 * These are what everything drawn outside a chunk build reads - an entity, a particle, a block entity standing in the
 * square ({@code getCombinedLight} through {@code getLightFromNeighborsFor}) - and what the game asks of a square's light
 * level ({@code getLight}). A ghost exists only in a client's world, so on any other world the block is never a ghost
 * and the state's own answer stands.
 */
@Mixin(World.class)
public abstract class MixinGhostNeighbourLightWorld {

    @WrapOperation(
        require = 0,
        method = "getLightFromNeighborsFor",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/block/state/IBlockState;useNeighborBrightness()Z"))
    private boolean trmt$neighbourLightFor(IBlockState state, Operation<Boolean> original, EnumSkyBlock type,
        BlockPos pos) {
        boolean flagged = original.call(state).booleanValue();
        if (!flagged || !(state.getBlock() instanceof BlockGhost)) return flagged;
        return BlockGhost.litFromNeighboursAt((IBlockAccess) (Object) this, pos);
    }

    @WrapOperation(
        require = 0,
        method = "getLight(Lnet/minecraft/util/math/BlockPos;Z)I",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/block/state/IBlockState;useNeighborBrightness()Z"))
    private boolean trmt$neighbourLight(IBlockState state, Operation<Boolean> original, BlockPos pos,
        boolean checkNeighbors) {
        boolean flagged = original.call(state).booleanValue();
        if (!flagged || !(state.getBlock() instanceof BlockGhost)) return flagged;
        return BlockGhost.litFromNeighboursAt((IBlockAccess) (Object) this, pos);
    }
}
