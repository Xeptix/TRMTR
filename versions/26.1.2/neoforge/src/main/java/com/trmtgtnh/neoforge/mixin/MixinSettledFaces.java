package com.trmtgtnh.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Hands vanilla's face test to {@link com.trmtgtnh.client.render.FaceRule}: a face beside a worn stair tested against
 * the stair at its square, two windows hiding the face between them, and the step two settled blocks share kept where
 * they stand at different heights. {@link com.trmtgtnh.mixin.MixinSettledFacesSodium} is the same for the Sodium
 * family, which asks its own.
 *
 * <p>
 * <strong>In each loader's module, not in common, from 0.9.219.</strong> It names a game method, so the
 * name goes into a refmap, and common's processor writes that refmap in Fabric's intermediary names: a
 * production Forge jar has it remapped on the way out, and a Forge development run reads it as it is and
 * refuses the mixin - which, on a class as early as {@code Block}, is a game that never starts. Every
 * mixin left in common injects with {@code remap = false} for that reason; see
 * {@code CommonMixinsNeedNoRefmapTest}. The Fabric module has the same class.
 */
@Mixin(Block.class)
public abstract class MixinSettledFaces {

    // Optional, as the 1.7.10 edition's cosmetic hooks are: its loss costs a picture, where a required one that a
    // pack's renderer had moved would stop the game from starting (0.9.222, spec SU21).
    // shouldRenderFace is handed the neighbour's position as well from 1.18; the question is asked of the face.
    // From 1.21.2 vanilla's test takes the two states and the face alone; NeoForge keeps one beside it that takes the
    // level and the position too, (level, pos, state, neighbour's state, face), and its ModelBlockRenderer asks that
    // one, the plain form handing on to it with an empty level (26.1.2.122's patched jar). That is the one hooked, by
    // its descriptor, so the rule is still asked with the square's level and position, as 1.21.1's was.
    @Inject(method = "shouldRenderFace(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;"
        + "Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;"
        + "Lnet/minecraft/core/Direction;)Z", at = @At("RETURN"), cancellable = true, require = 0)
    private static void trmt$keepSettledFace(BlockGetter level, BlockPos pos, BlockState state, BlockState neighbour,
        Direction face, CallbackInfoReturnable<Boolean> callback) {
        // The rule every face hook asks - a worn stair beside, two windows, a settled step - is FaceRule's.
        boolean vanilla = callback.getReturnValueZ();
        boolean drawn = com.trmtgtnh.client.render.FaceRule.decide(vanilla, state, level, pos, face);
        if (drawn != vanilla) callback.setReturnValue(Boolean.valueOf(drawn));
    }
}
