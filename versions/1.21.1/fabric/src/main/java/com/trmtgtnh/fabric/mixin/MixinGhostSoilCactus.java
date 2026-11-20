package com.trmtgtnh.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.state.BlockState;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.trmtgtnh.block.GhostInherit;

/**
 * A cactus may stand on worn sand (0.9.222, spec GF15).
 *
 * <p>
 * A cactus is no bush: it asks the block below it whether it is sand, red sand or another cactus, by identity, and a
 * ghost is none of those. Forge puts that question to the block below ({@code canSustainPlant}), whose answer for a
 * cactus is the same three blocks; here the block below is read as the covered block instead. Only that read - the
 * second of the three this method makes, after the four sides and before the one above, which are asked of the world as
 * Forge asks them. See {@link MixinGhostSoilBush}.
 *
 * <p>
 * <strong>The read is wrapped, not redirected</strong> (0.9.229). MixinExtras' {@code @WrapOperation} hands this the
 * read as an operation and calls it, so another mod's {@code @Redirect} of the same read is what the operation runs and
 * its answer is the one seen, kept on every square but a worn one. Until 0.9.228 this was a required {@code @Redirect}:
 * two redirects of one call cannot both apply, and beside a mod redirecting the same read the game would have stopped
 * before its first screen, as All of Fabric 3 did over friction.
 */
@Mixin(CactusBlock.class)
public abstract class MixinGhostSoilCactus {

    @WrapOperation(
        method = "canSurvive",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/LevelReader;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;",
            ordinal = 1))
    private BlockState trmt$theCoveredBlockBelow(LevelReader world, BlockPos below, Operation<BlockState> read) {
        return GhostInherit.soilFor(read.call(world, below), below);
    }
}
