package com.trmtgtnh.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
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
 * {@code CommonMixinsNeedNoRefmapTest}. The Forge module has the same class.
 *
 * <p>
 * <strong>From 1.21.2 {@code Block.shouldRenderFace} is asked with the two states alone</strong> - no level, no
 * position - so the rule cannot be asked there. Vanilla's renderer asks it from its own private
 * {@code ModelBlockRenderer.shouldRenderFace(level, state, direction, neighbourPos)}, which holds both (read from
 * 26.1.2), and the rule is asked at its return; the square is the neighbour's position stepped back across the face.
 * Under Fabric API the chunk's blocks are drawn by its renderer, Indigo, which asks the same two-state test from its
 * own face cull with the level and the position in its fields; {@link MixinSettledFacesIndigo} asks the rule there.
 * The rule and its order are 1.21.1's.
 */
@Mixin(ModelBlockRenderer.class)
public abstract class MixinSettledFaces {

    // Optional, as the 1.7.10 edition's cosmetic hooks are: its loss costs a picture, where a required one that a
    // pack's renderer had moved would stop the game from starting (0.9.222, spec SU21).
    @Inject(
        method = "shouldRenderFace(Lnet/minecraft/client/renderer/block/BlockAndTintGetter;"
            + "Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;Lnet/minecraft/core/BlockPos;)Z",
        at = @At("RETURN"),
        cancellable = true,
        require = 0)
    private void trmt$keepSettledFace(net.minecraft.client.renderer.block.BlockAndTintGetter level, BlockState state,
        Direction face, BlockPos neighbour, CallbackInfoReturnable<Boolean> callback) {
        BlockPos pos = neighbour.relative(face.getOpposite());
        boolean drawn = decide(callback.getReturnValueZ(), state, level, pos, face);
        if (drawn != callback.getReturnValueZ()) callback.setReturnValue(Boolean.valueOf(drawn));
    }

    /** The rule every face hook asks - a worn stair beside, two windows, a settled step - is FaceRule's. */
    @org.spongepowered.asm.mixin.Unique
    private static boolean decide(boolean vanilla, BlockState state, BlockGetter level, BlockPos pos, Direction face) {
        return com.trmtgtnh.client.render.FaceRule.decide(vanilla, state, level, pos, face);
    }
}
