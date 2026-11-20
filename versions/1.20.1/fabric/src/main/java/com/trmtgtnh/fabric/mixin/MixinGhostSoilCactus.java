package com.trmtgtnh.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.state.BlockState;

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
 * <strong>The ground is taken after the game stores it, not redirected</strong> (0.9.229). The state the read answered is
 * changed in the local the game stores it in (local 4), so another mod's {@code @Redirect} of the same read still
 * applies and its answer is the one seen, kept on every square but a worn one; the square is the one below the cactus,
 * worked out from the method's own argument as vanilla works it out ({@link GhostInherit#soilBelow}), so nothing is held
 * between the read and the answer for a nested call to disturb. Until 0.9.228 this was a required {@code @Redirect}: two
 * redirects of one call cannot both apply, and beside a mod redirecting the same read the game would have stopped before
 * its first screen, as All of Fabric 3 did over friction. Not MixinExtras, which this jar's oldest Fabric Loader does not
 * bring.
 */
@Mixin(CactusBlock.class)
public abstract class MixinGhostSoilCactus {

    @ModifyVariable(method = "canSurvive", at = @At("STORE"), index = 4)
    private BlockState trmt$theCoveredBlockBelow(BlockState ground, BlockState cactus, LevelReader world, BlockPos pos) {
        return GhostInherit.soilBelow(ground, pos);
    }
}
