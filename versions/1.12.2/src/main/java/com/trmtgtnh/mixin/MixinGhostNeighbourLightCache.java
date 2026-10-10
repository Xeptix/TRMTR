package com.trmtgtnh.mixin;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ChunkCache;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.IBlockAccess;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.trmtgtnh.block.BlockGhost;

/**
 * The mesher's light read at a ghost's square, asked per square whether it takes its neighbours' light - see
 * {@link BlockGhost#litFromNeighboursAt} (0.9.222, spec GT27, GF8).
 *
 * <p>
 * This version asks {@code useNeighborBrightness} of the state alone, and one ghost stands in for every square, so the
 * flag the block sets answers for worn clear ice too, which the 1.7.10 edition lights from its own cell. The mesher's view
 * reads the flag here and nowhere else - vanilla's chunk build, Forge's light pipeline, and OptiFine's ChunkCacheOF, which
 * takes its light from this class (javap of OptiFine 1.12.2 HD U G5, 2026-10-09) - so the read is answered here, by
 * position, the state's own answer chained so another mod wrapping the same call still has its say. Wrapped rather than
 * redirected for the same reason; a version whose method does not hold the call loses the fix, never the game.
 */
@Mixin(ChunkCache.class)
public abstract class MixinGhostNeighbourLightCache {

    @WrapOperation(
        require = 0,
        method = "getLightForExt",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/block/state/IBlockState;useNeighborBrightness()Z"))
    private boolean trmt$neighbourLight(IBlockState state, Operation<Boolean> original, EnumSkyBlock type,
        BlockPos pos) {
        boolean flagged = original.call(state).booleanValue();
        if (!flagged || !(state.getBlock() instanceof BlockGhost)) return flagged;
        return BlockGhost.litFromNeighboursAt((IBlockAccess) (Object) this, pos);
    }
}
