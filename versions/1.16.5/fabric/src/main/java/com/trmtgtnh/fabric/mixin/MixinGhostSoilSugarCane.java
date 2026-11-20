package com.trmtgtnh.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

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
 *
 * <p>
 * <strong>The ground is taken after the game stores it, not redirected</strong> (0.9.229), as {@link
 * MixinGhostSoilCactus} takes it: the state the read answered changed in the local the game stores it in (local 4), at
 * the square below the reed worked out from the method's own argument, so another mod's {@code @Redirect} of the same
 * read still applies, its answer is kept on every square but a worn one, and nothing is held between the read and the
 * answer.
 */
@Mixin(SugarCaneBlock.class)
public abstract class MixinGhostSoilSugarCane {

    @ModifyVariable(method = "canSurvive", at = @At("STORE"), index = 4)
    private BlockState trmt$theCoveredBlockBelow(BlockState ground, BlockState reed, LevelReader world, BlockPos pos) {
        return GhostInherit.soilBelow(ground, pos);
    }
}
