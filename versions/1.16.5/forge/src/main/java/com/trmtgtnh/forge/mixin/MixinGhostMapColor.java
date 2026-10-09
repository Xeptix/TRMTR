package com.trmtgtnh.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MaterialColor;

import com.trmtgtnh.block.BlockGhost;

/**
 * Makes a worn square paint on a map as the ground it stands in for.
 *
 * <p>
 * Everything that draws a map reads this - the vanilla map item, every minimap, and anything
 * rendering the world at a distance - so one answer here is the answer for all of them. Without it a
 * path reads as an unknown block, which is what the 1.7.10 edition had to correct inside JourneyMap
 * by reflection, having no way to know which square was being asked about.
 *
 * <p>
 * <strong>A mixin because the question is asked of the state, not of the block.</strong> 1.12.2
 * overrides {@code Block.getMapColor(state, world, pos)}; vanilla has no such method at this version
 * - a block's map color is a value fixed when it is built, and the per-position question belongs to
 * {@code BlockStateBase}. Forge adds a block-level hook back and Fabric does not, so the one place
 * both loaders have is this one, and the decision itself lives in
 * {@link BlockGhost#mapColorAt} where it can be read beside the rest of what a ghost owes the block
 * it covers.
 *
 * <p>
 * Guarded on the block first, so every other block in the world pays one {@code instanceof} and
 * nothing else. A null answer means the square has no opinion - no record, or a covered block that
 * would not say - and the color the state was built with stands.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class MixinGhostMapColor {

    @Inject(
        method = "getMapColor(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)"
            + "Lnet/minecraft/world/level/material/MaterialColor;",
        at = @At("HEAD"),
        cancellable = true)
    private void trmt$paintWornGroundAsItsOwn(BlockGetter level, BlockPos pos,
        CallbackInfoReturnable<MaterialColor> callback) {
        BlockState state = (BlockState) (Object) this;
        if (!(state.getBlock() instanceof BlockGhost)) return;
        MaterialColor own = BlockGhost.mapColorAt(state, level, pos);
        if (own != null) callback.setReturnValue(own);
    }
}
