package com.trmtgtnh.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;

import com.trmtgtnh.block.BlockGhost;

/**
 * How slippery a worn square is, asked by the name NeoForge gives the question (spec GF17).
 *
 * <p>
 * The shared ghost answers it as {@code getSlipperiness(state, world, pos, entity)}, Forge's name for the per-square
 * question up to 1.16.5. Forge itself has asked {@code getFriction} since, and the Forge jar has the same bridge as this
 * one from 0.9.227, when it was found missing there. NeoForge asks it as {@code getFriction}, with the same four
 * arguments and the same answer - read from its
 * {@code IBlockExtension} - and asks only that, so on NeoForge the ghost's own answer would never be heard and every
 * worn square would slide as the ghost's built-in 0.6. The shared module cannot name NeoForge, so this adds the
 * NeoForge-named method to the ghost, as an override of NeoForge's default, and hands the question straight to the
 * ghost's own: the answer and the reason for it stay {@link BlockGhost}'s. The worn stair's ghost is a ghost too, and
 * inherits it.
 *
 * <p>
 * Fabric answers the same question with mixins at each place the game reads a block's friction; NeoForge's patches
 * route every one of those reads through this method instead.
 */
@Mixin(BlockGhost.class)
public abstract class MixinGhostFriction {

    // Public and with NeoForge's name and descriptor, so it overrides IBlockExtension.getFriction on the ghost.
    public float getFriction(BlockState state, LevelReader world, BlockPos pos, Entity entity) {
        return ((BlockGhost) (Object) this).getSlipperiness(state, world, pos, entity);
    }
}
