package com.trmtgtnh.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.state.BlockState;

import com.trmtgtnh.block.GhostInherit;

/**
 * A reed may stand on worn grass, earth or sand beside water (0.9.222, spec GF15).
 *
 * <p>
 * A reed is no bush: it asks the block below it whether it is grass, dirt, sand or another reed, by identity, and only
 * then looks for water beside that block. Forge puts the question to the block below ({@code canSustainPlant}), whose
 * answer for a reed is the same blocks and the same water; here the block below is read as the covered block instead.
 * Only that read - the first this method makes; the water is looked for in the world, as Forge looks for it. See
 * {@link MixinGhostSoilBush}.
 */
@Mixin(SugarCaneBlock.class)
public abstract class MixinGhostSoilSugarCane {

    @Redirect(
        method = "canSurvive",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/LevelReader;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;",
            ordinal = 0))
    private BlockState trmt$theCoveredBlockBelow(LevelReader world, BlockPos below) {
        return GhostInherit.soilFor(world.getBlockState(below), below);
    }
}
