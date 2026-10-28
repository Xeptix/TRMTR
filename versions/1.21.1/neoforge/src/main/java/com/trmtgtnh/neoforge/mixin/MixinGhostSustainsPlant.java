package com.trmtgtnh.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.extensions.IBlockExtension;
import net.neoforged.neoforge.common.util.TriState;

import com.trmtgtnh.block.BlockGhost;
import com.trmtgtnh.block.GhostInherit;

/**
 * A worn square hands NeoForge's {@code canSustainPlant} to the block it covers (0.9.228, GF15; Xep: "Pass the question
 * on") - the 1.7.10 edition's {@code GhostLogic.sustainsPlant}, which asks the covered block with its own metadata,
 * because the game asks a soil by identity and a ghost is never the grass it is drawn as.
 *
 * <p>
 * NeoForge asks the soil's {@code IBlockExtension.canSustainPlant(BlockState, BlockGetter, BlockPos, Direction,
 * BlockState plant)} first, answered as {@code net.neoforged.neoforge.common.util.TriState} (read in 21.1's jar), and
 * the vanilla plants' {@code mayPlaceOn} only when it answers {@code DEFAULT}. The ghost answered {@code DEFAULT}, so a
 * modded soil whose own rule lives in that method was never asked; the soil mixins hand {@code mayPlaceOn} to the
 * covered block, which keeps every vanilla plant right. Now the covered block is asked first, through the same view and
 * the same guard as Forge's {@code MixinGhostSustainsPlant}, and its three-way answer is passed on; {@code DEFAULT}
 * where nothing is recorded under the square or the question came back round.
 *
 * <p>
 * Onto {@link BlockGhost} itself, as an override of the interface's default: a default interface method cannot be
 * injected into at its head the way Forge's class method is. {@code BlockGhostStair}, the one other ghost the NeoForge
 * module registers, is a {@code BlockGhost} and inherits it.
 */
@Mixin(BlockGhost.class)
public abstract class MixinGhostSustainsPlant implements IBlockExtension {

    @Override
    public TriState canSustainPlant(BlockState state, BlockGetter level, BlockPos soil, Direction facing,
        BlockState plant) {
        TriState covered = GhostInherit.askCovered(
            (Block) (Object) this,
            level,
            soil,
            (under, view) -> under.canSustainPlant(view, soil, facing, plant));
        return covered == null ? TriState.DEFAULT : covered;
    }
}
