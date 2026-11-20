package com.trmtgtnh.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import com.trmtgtnh.block.BlockGhost;
import com.trmtgtnh.block.GhostFamily;

/**
 * What a worn square looks like, asked by the name and the argument Forge gives the question from 26.1 (W6 S1).
 *
 * <p>
 * <strong>Found reading each loader's jar for W6.</strong> Up to 1.21.1 every loader spelt {@code getAppearance} the same
 * way, so the ghost's one plain method overrides all of them; 26.1 moved {@code BlockAndTintGetter} to the client package
 * and the loaders parted. NeoForge and Fabric API took {@code BlockAndLightGetter}, which the ghost declares, and Forge
 * took the client's {@code BlockAndTintGetter}, which it cannot: that type is not on a dedicated server, and the ghost is.
 * So the override is here, a client mixin onto the ghost, with the answer still {@link GhostFamily#appearanceAt}'s.
 * {@code tools/appearance_sites.py} reads the hook from each jar and says when this stops being true.
 */
@Mixin(BlockGhost.class)
public abstract class MixinGhostAppearance {

    // Public and with Forge's name and descriptor, so it overrides IForgeBlock.getAppearance on the ghost.
    public BlockState getAppearance(BlockState state, BlockAndTintGetter level, BlockPos pos, Direction side,
        BlockState queryState, BlockPos queryPos) {
        return GhostFamily.appearanceAt(state, level, pos);
    }
}
