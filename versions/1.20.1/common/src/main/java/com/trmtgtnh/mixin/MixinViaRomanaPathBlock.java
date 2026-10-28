package com.trmtgtnh.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.trmtgtnh.compat.viaromana.PathAnswer;

/**
 * Via Romana's road test asks this mod again where its own answer is no (0.9.228; Xep's decision of 2026-10-20, "Via
 * Romana", part one): a square worn far enough counts as road, and a worn square counts as the block it covers.
 *
 * <p>
 * The target is Via Romana's {@code net.rasanovum.viaromana.util.PathUtils.isBlockValidPath}, static, of a
 * {@code LevelAccessor} and a {@code BlockPos}, answering a boolean - read from all four of its 2.2.3 jars, which spell
 * it alike. Its own name, so nothing here is remapped; its two arguments are the game's, named as each loader's build
 * names the game. Named without a descriptor, not required, and refused by the common config's plugin where Via Romana
 * is not installed, so a game without it never asks for the class, and a Via Romana that changed this method keeps its
 * own answer rather than stopping the game. See {@link PathAnswer} for what is asked.
 */
@Pseudo
@Mixin(targets = "net.rasanovum.viaromana.util.PathUtils", remap = false)
public abstract class MixinViaRomanaPathBlock {

    @Inject(method = "isBlockValidPath", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private static void trmt$wornGroundIsRoad(LevelAccessor world, BlockPos pos, CallbackInfoReturnable<Boolean> answer) {
        if (!answer.getReturnValueZ() && PathAnswer.isRoad(world, pos)) answer.setReturnValue(Boolean.TRUE);
    }
}
