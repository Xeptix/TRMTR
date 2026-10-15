package com.trmtgtnh.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;

import com.trmtgtnh.block.GhostInherit;

/**
 * A sapling, a flower, grass, a crop - anything built on a bush - may stand on a worn square wherever it could stand on
 * the block the square covers (0.9.222, spec GF15).
 *
 * <p>
 * A bush asks whether it may stand on the block below by identity - grass, dirt, farmland - and a ghost is none of
 * those whatever it is drawn as. The client asks before it sends a placement and sends nothing on a no, so nothing
 * could be planted on worn ground although the server, which holds the real ground, would have taken it; and a plant
 * already there asks again when a neighbour changes, and the client took it away. Forge puts this question to the
 * block ({@code canSustainPlant}), which the 1.7.10 edition's ghost answers for the covered block; Fabric's bushes
 * decide it themselves, so the bush's own question is put to the covered block here: the block it is shown, and a
 * world that holds that block at the square (see {@link GhostInherit#soilFor} and {@link GhostInherit#soilView}).
 *
 * <p>
 * Each argument is decided from the square rather than from the other, so the two hold whichever order they are
 * applied in.
 */
@Mixin(BushBlock.class)
public abstract class MixinGhostSoilBush {

    @ModifyArg(
        method = "canSurvive",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/BushBlock;mayPlaceOn(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Z"),
        index = 0)
    private BlockState trmt$theCoveredBlock(BlockState ground, BlockGetter world, BlockPos pos) {
        return GhostInherit.soilFor(ground, pos);
    }

    @ModifyArg(
        method = "canSurvive",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/BushBlock;mayPlaceOn(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Z"),
        index = 1)
    private BlockGetter trmt$aWorldHoldingIt(BlockState ground, BlockGetter world, BlockPos pos) {
        return GhostInherit.soilView(world, pos);
    }
}
