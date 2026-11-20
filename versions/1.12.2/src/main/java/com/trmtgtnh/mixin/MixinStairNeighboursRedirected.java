package com.trmtgtnh.mixin;

import net.minecraft.block.BlockStairs;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.trmtgtnh.block.BlockGhost;

/**
 * {@link MixinStairNeighbours} in a game without MixinExtras (G17 R5, 2026-11-11): the same read in the same two
 * methods, answered the same way, redirected rather than wrapped because MixinExtras comes with MixinBooter and a pack
 * that boots its own Mixin, such as RLCraft, has neither. {@link OculusGate} applies this or that, never both. The one
 * difference is a redirect's: another mod redirecting the same read is refused by Mixin rather than chained.
 */
@Mixin(BlockStairs.class)
public abstract class MixinStairNeighboursRedirected {

    @Redirect(
        method = { "getStairsShape", "isDifferentStairs" },
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/IBlockAccess;getBlockState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/state/IBlockState;"))
    private static IBlockState trmt$aWornStairIsAStair(IBlockAccess world, BlockPos pos) {
        return BlockGhost.asStairsSee(world.getBlockState(pos), pos);
    }
}
