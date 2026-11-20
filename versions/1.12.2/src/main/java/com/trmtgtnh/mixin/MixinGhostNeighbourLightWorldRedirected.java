package com.trmtgtnh.mixin;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.trmtgtnh.block.BlockGhost;

/**
 * {@link MixinGhostNeighbourLightWorld} in a game without MixinExtras (G17 R5, 2026-11-11): the same reads in the same
 * methods, answered the same way, redirected rather than wrapped because MixinExtras comes with MixinBooter and a pack
 * that boots its own Mixin, such as RLCraft, has neither. {@link OculusGate} applies this or that, never both. The one
 * difference is a redirect's: another mod redirecting the same call is refused by Mixin rather than chained.
 */
@Mixin(value = World.class, priority = OculusGate.ABOVE_OVERWRITES)
public abstract class MixinGhostNeighbourLightWorldRedirected {

    @Redirect(
        require = 0,
        method = "getLightFromNeighborsFor",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/block/state/IBlockState;useNeighborBrightness()Z"))
    private boolean trmt$neighbourLightFor(IBlockState state, EnumSkyBlock type, BlockPos pos) {
        boolean flagged = state.useNeighborBrightness();
        if (!flagged || !(state.getBlock() instanceof BlockGhost)) return flagged;
        return BlockGhost.litFromNeighboursAt((IBlockAccess) (Object) this, pos);
    }

    @Redirect(
        require = 0,
        method = "getLight(Lnet/minecraft/util/math/BlockPos;Z)I",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/block/state/IBlockState;useNeighborBrightness()Z"))
    private boolean trmt$neighbourLight(IBlockState state, BlockPos pos, boolean checkNeighbors) {
        boolean flagged = state.useNeighborBrightness();
        if (!flagged || !(state.getBlock() instanceof BlockGhost)) return flagged;
        return BlockGhost.litFromNeighboursAt((IBlockAccess) (Object) this, pos);
    }
}
