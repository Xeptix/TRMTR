package com.trmtgtnh.forge.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.trmtgtnh.block.GhostFamily;

/**
 * Presence Footsteps hears a worn square as the ground it stands for (0.9.229 W6 T9; docs/goals/0.9.229-w6-spec.md 3.3
 * in the 1.7.10 tree).
 *
 * <p>
 * The mod picks a step's sound from the block state under the foot, by its own block map, and falls to the state's
 * sound where the map has no row. Its builds from 1.10.1 ask the state's camouflage hook first, which the ghost answers
 * on the editions that have the hook; this edition has none, and the mod's build here, {@code r29} (Fabric and the Forge
 * port alike, read with javap), reads the state with {@code Level.getBlockState} four times in its solver's
 * {@code findAssociation} methods and nothing else. So a worn square was the ghost, in no block map, and every family
 * was heard as the ghost's gravel. Each of those reads is handed {@link GhostFamily#groundFor}: the covered block while
 * the square wears as its family, so a worn lawn is the lawn the player's own map knows, and the family's stock block
 * past it; anything that is not a ghost as it was.
 *
 * <p>
 * {@code @Pseudo}, because the mod is not on this build's classpath, and {@code require = 0}, because a build that has
 * changed its solver should keep its own answer rather than stop the game. Its licence (PolyForm Shield) allows a change
 * for a permitted purpose, and nothing of it is copied: this names one of its methods and a vanilla call in it. The
 * method is the mod's own name and is never remapped; the call is the game's and is, through this module's refmap.
 */
@Pseudo
@Mixin(targets = "eu.ha3.presencefootsteps.world.PFSolver", remap = false)
public abstract class MixinPresenceFootstepsGround {

    @Redirect(
        method = "findAssociation*",
        require = 0,
        remap = false,
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;",
            remap = true))
    private BlockState trmt$theGroundItStandsFor(Level world, BlockPos pos) {
        return GhostFamily.groundFor(world.getBlockState(pos), world, pos);
    }
}
