package com.trmtgtnh.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

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
 * unworn stair next door; here each read's state is replaced by the covered stair's own wherever a ghost stands over a
 * stair ({@link BlockGhost#asStairsSee}), and every other square's state is left as it was. A ghost exists only in a
 * client's world, so on a server every read passes through as it was. In this module because it names a game method -
 * see CommonMixinsNeedNoRefmapTest; the other loader's module has the same class.
 *
 * <p>
 * <strong>The state is taken after the game stores it, not redirected</strong> (0.9.229). Each read's state is changed
 * in the local the game stores it in - {@code getStairsShape} stores the square behind in local 4 and the square before
 * in local 5, {@code canTakeShape} its one read in local 4 - so another mod's {@code @Redirect} of the same reads still
 * applies and its answer is the one seen, kept on every square but a worn stair's. The square is worked out from the
 * method's own arguments, as vanilla works it out ({@link BlockGhost#stairSquareBehind}, {@link
 * BlockGhost#stairSquareInFront}, and the side {@code canTakeShape} is given), so nothing is held between the read and
 * the answer for a nested call to disturb. Until 0.9.228 this was a required {@code @Redirect}: two redirects of one call
 * cannot both apply, and beside a mod redirecting the same reads the game would have stopped before its first screen, as
 * All of Fabric 3 did over friction. Not MixinExtras, which neither Forge before 1.21.1 nor Fabric Loader before 0.15
 * brings.
 */
@Mixin(StairBlock.class)
public abstract class MixinStairNeighbours {

    @ModifyVariable(method = "getStairsShape", at = @At("STORE"), index = 4)
    private static BlockState trmt$aWornStairIsAStairBehind(BlockState behind, BlockState state, BlockGetter level,
        BlockPos pos) {
        return BlockGhost.asStairsSee(behind, BlockGhost.stairSquareBehind(state, pos));
    }

    @ModifyVariable(method = "getStairsShape", at = @At("STORE"), index = 5)
    private static BlockState trmt$aWornStairIsAStairInFront(BlockState front, BlockState state, BlockGetter level,
        BlockPos pos) {
        return BlockGhost.asStairsSee(front, BlockGhost.stairSquareInFront(state, pos));
    }

    @ModifyVariable(method = "canTakeShape", at = @At("STORE"), index = 4)
    private static BlockState trmt$aWornStairIsAStairBeside(BlockState beside, BlockState state, BlockGetter level,
        BlockPos pos, Direction side) {
        return BlockGhost.asStairsSee(beside, pos.relative(side));
    }
}
