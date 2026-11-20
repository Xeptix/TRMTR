package com.trmtgtnh.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.trmtgtnh.block.BlockGhost;

/**
 * A worn stair is a stair to the stair rules - the 1.7.10 edition's {@code MixinStairMetadata}, carried as this version
 * reads a neighbour (0.9.223, spec GF5).
 *
 * <p>
 * Vanilla decides a stair's corner from its neighbours: {@code getStairsShape} reads the square behind it and the square
 * before it, and {@code canTakeShape} the two beside those, each asking whether the state there is a stair facing the
 * same way in the same half. The server holds the real stairs and sends shapes worked out against them, but the client
 * works a shape out itself whenever it updates one - as it predicts a stair being placed, and on every shape update a
 * placed block sends its neighbours - and there a worn stair's square holds the ghost, no stair and no facing, so an
 * unworn stair beside it was put back straight until the chunk was sent again.
 *
 * <p>
 * The 1.7.10 edition answers at the point of reading, because the block that needs the right answer is as often the
 * unworn stair next door; here the read hands back the covered stair's own state wherever a ghost stands over a stair
 * ({@link BlockGhost#asStairsSee}), and every other square's state untouched. A ghost exists only in a client's world,
 * so on a server every read passes through as it was. In this module because it names a game method - see
 * CommonMixinsNeedNoRefmapTest; the other loader's module has the same class.
 *
 * <p>
 * <strong>The reads are wrapped, not redirected</strong> (0.9.229). MixinExtras' {@code @WrapOperation} hands this each
 * read as an operation and calls it, so another mod's {@code @Redirect} of the same reads is what the operation runs and
 * its answer is the one seen, kept on every square but a worn stair's. Until 0.9.228 this was a required
 * {@code @Redirect}: two redirects of one call cannot both apply, and beside a mod redirecting the same reads the game
 * would have stopped before its first screen, as All of Fabric 3 did over friction.
 */
@Mixin(StairBlock.class)
public abstract class MixinStairNeighbours {

    @WrapOperation(
        method = { "getStairsShape", "canTakeShape" },
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/BlockGetter;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private static BlockState trmt$aWornStairIsAStair(BlockGetter level, BlockPos pos, Operation<BlockState> read) {
        return BlockGhost.asStairsSee(read.call(level, pos), pos);
    }
}
