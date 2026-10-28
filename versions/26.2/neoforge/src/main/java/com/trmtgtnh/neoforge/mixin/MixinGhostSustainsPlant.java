package com.trmtgtnh.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.TriState;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.extensions.IBlockExtension;

import com.trmtgtnh.block.BlockGhost;
import com.trmtgtnh.block.GhostInherit;

/**
 * Whether a plant may stand on a worn square is the covered block's answer on NeoForge too (0.9.228, spec GF15; Xep:
 * "Pass the question on") - the 1.7.10 edition's {@code GhostLogic.sustainsPlant}, which hands Forge's
 * {@code canSustainPlant} to the covered block, asked with its own metadata.
 *
 * <p>
 * NeoForge asks the soil's {@code canSustainPlant} before a plant's own {@code mayPlaceOn}, and until 0.9.228 the ghost
 * answered it with NeoForge's default, "no opinion": the vanilla plants then fell through to {@code mayPlaceOn}, which
 * the {@code MixinGhostSoil*} mixins already hand to the covered block, but a modded soil whose own rule lives in
 * {@code canSustainPlant} was never asked. So the ghost now answers as the covered block does, through the same view
 * holding it at its square and the same guard against the question coming back round ({@link GhostInherit#askCovered});
 * "no opinion" where nothing is recorded under the square or the question came round. A covered block that itself has
 * no opinion lets the plant fall through to {@code mayPlaceOn}, as before, so vanilla plants answer exactly as they did.
 *
 * <p>
 * An override added to the ghost itself, as {@link MixinGhostFriction} adds NeoForge's friction: the shared module
 * cannot name NeoForge, and a default interface method cannot be injected into at its head the way Forge's class
 * method is. Read from NeoForge 26.1.2.122's, 26.2.0.88's and 26.3.0.94's {@code IBlockExtension}: the same five
 * arguments, returning vanilla's {@code net.minecraft.util.TriState} (1.21.1's was NeoForge's own). Every ghost the
 * module registers gets it: {@link BlockGhost} and its one subclass, the worn stair's {@code BlockGhostStair}.
 */
@Mixin(BlockGhost.class)
public abstract class MixinGhostSustainsPlant implements IBlockExtension {

    @Override
    public TriState canSustainPlant(BlockState state, BlockGetter level, BlockPos soil, Direction facing,
        BlockState plant) {
        TriState covered = GhostInherit.askCovered((BlockGhost) (Object) this, level, soil,
            (under, view) -> under.canSustainPlant(view, soil, facing, plant));
        return covered == null ? TriState.DEFAULT : covered;
    }
}
