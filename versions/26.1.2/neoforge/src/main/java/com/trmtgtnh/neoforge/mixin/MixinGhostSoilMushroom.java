package com.trmtgtnh.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.state.BlockState;

import com.trmtgtnh.block.GhostInherit;

/**
 * A mushroom in the dark may stand on a worn square wherever it could stand on the covered block (0.9.222, spec
 * GF15).
 *
 * <p>
 * A mushroom has its own {@code canSurvive} rather than a bush's, and it asks the same question a bush does once the
 * light is low enough. Only that question is put to the covered block: the check before it - mycelium and podzol take a
 * mushroom in any light - is asked of the ghost by identity on Forge as well, and in the 1.7.10 edition's
 * {@code BlockMushroom}, and so stays the ghost's here. See {@link MixinGhostSoilBush}.
 *
 * <p>
 * On NeoForge as on Fabric, for the reason {@link MixinGhostSoilBush} gives. NeoForge 21.1.259's patched
 * {@code canSurvive} asks {@code canSustainPlant} of the block it read before its own test and leaves the calls this
 * changes where they were, so they are the same calls as on Fabric; checked against that jar's bytecode.
 */
@Mixin(MushroomBlock.class)
public abstract class MixinGhostSoilMushroom {

    @ModifyArg(
        method = "canSurvive",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/MushroomBlock;mayPlaceOn(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Z"),
        index = 0)
    private BlockState trmt$theCoveredBlock(BlockState ground, BlockGetter world, BlockPos pos) {
        return GhostInherit.soilFor(ground, pos);
    }

    @ModifyArg(
        method = "canSurvive",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/MushroomBlock;mayPlaceOn(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Z"),
        index = 1)
    private BlockGetter trmt$aWorldHoldingIt(BlockState ground, BlockGetter world, BlockPos pos) {
        return GhostInherit.soilView(world, pos);
    }
}
