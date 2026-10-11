package com.trmtgtnh.forge.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BambooBlock;
import net.minecraft.world.level.block.BambooSaplingBlock;
import net.minecraft.world.level.block.ChorusFlowerBlock;
import net.minecraft.world.level.block.ChorusPlantBlock;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.trmtgtnh.block.GhostInherit;

/**
 * Bamboo and chorus stand on worn ground wherever the covered block would take them (0.9.223; Xep, 2026-10-10).
 *
 * <p>
 * These four ask the ground by tag or by identity - bamboo whether it is among the blocks bamboo is planted on, chorus
 * whether it is end stone or more chorus - and a ghost is none of those whatever it is drawn as, so the client refused
 * bamboo on worn grass and took chorus away from worn end stone, where the server's real ground holds both. Neither
 * loader puts these through a plant hook, so both take this: every block each one reads while deciding whether it
 * may stand is read as the covered block where a ghost stands in for it, through {@link GhostInherit#soilFor}, the
 * Fabric soil hooks' own answer. A ghost only ever stands on the ground, so reading every square that way changes
 * nothing but the ground. There is no 1.7.10 counterpart - that version has neither plant.
 *
 * <p>
 * Not required: a loader that rewrote one of these questions keeps its own answer rather than refusing to start, and
 * {@link GhostInherit#soilFor} says once in the log when a plant here has been handed a covered block.
 */
@Mixin({ BambooBlock.class, BambooSaplingBlock.class, ChorusFlowerBlock.class, ChorusPlantBlock.class })
public abstract class MixinGhostSoilTagged {

    @Redirect(
        method = "canSurvive",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/LevelReader;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"),
        require = 0)
    private BlockState trmt$theCoveredGround(LevelReader level, BlockPos pos) {
        return GhostInherit.taggedSoilFor(level.getBlockState(pos), pos);
    }
}
