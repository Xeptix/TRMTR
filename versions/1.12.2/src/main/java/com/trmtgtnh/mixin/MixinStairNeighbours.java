package com.trmtgtnh.mixin;

import net.minecraft.block.BlockStairs;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.trmtgtnh.block.BlockGhost;

/**
 * A worn stair is a stair to the stair rules - the 1.7.10 edition's {@code MixinStairMetadata}, carried as this
 * version reads a neighbour (0.9.223, spec GF5).
 *
 * <p>
 * Vanilla decides a stair's corner from its neighbours: {@code getStairsShape} looks at the square behind it and the
 * square before it, and {@code isDifferentStairs} at the two beside those, each by reading the state there and asking
 * whether it is a stair facing the same way in the same half. A worn stair's square holds the ghost, which is no stair
 * and carries no facing, so an unworn stair beside a worn one never turned its corner and two worn stairs never joined.
 *
 * <p>
 * The 1.7.10 edition answers at the point of reading, and for the same reason: the block that needs the right answer is
 * as often the unworn stair next door, which asks about its neighbours through its own copy of the world and cannot be
 * reached from the ghost at all. There its stand-in is a stair class and the mixin puts back the stair's own facing
 * and half; here the ghost is no stair class, so the read hands back the covered stair's own state - facing, half and
 * all - wherever a ghost stands over a stair, and every other square's state untouched. The worn stair's own shape
 * comes through the same two methods, since the ghost draws and collides as the covered stair's actual state at its
 * square ({@link BlockGhost#stairCodeAt}).
 *
 * <p>
 * Client only, where ghosts exist. Wrapped rather than redirected so another mod's hook on the same read still runs.
 */
@Mixin(BlockStairs.class)
public abstract class MixinStairNeighbours {

    @WrapOperation(
        method = { "getStairsShape", "isDifferentStairs" },
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/IBlockAccess;getBlockState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/state/IBlockState;"))
    private static IBlockState trmt$aWornStairIsAStair(IBlockAccess world, BlockPos pos,
        Operation<IBlockState> original) {
        return BlockGhost.asStairsSee(original.call(world, pos), pos);
    }
}
