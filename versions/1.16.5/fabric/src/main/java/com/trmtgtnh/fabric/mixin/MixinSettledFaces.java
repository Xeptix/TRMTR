package com.trmtgtnh.fabric.mixin;

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
 * Keeps the step two settled blocks share when they stand at different heights, in vanilla's face test -
 * see {@code client.render.Settling.keepsFace}. Only ever turns a hidden face back on, so vanilla's answer
 * stands wherever the two still meet level. {@link com.trmtgtnh.mixin.MixinSettledFacesSodium} is the same for the Sodium
 * family, which asks its own.
 *
 * <p>
 * <strong>In each loader's module, not in common, from 0.9.219.</strong> It names a game method, so the
 * name goes into a refmap, and common's processor writes that refmap in Fabric's intermediary names: a
 * production Forge jar has it remapped on the way out, and a Forge development run reads it as it is and
 * refuses the mixin - which, on a class as early as {@code Block}, is a game that never starts. Every
 * mixin left in common injects with {@code remap = false} for that reason; see
 * {@code CommonMixinsNeedNoRefmapTest}. The Forge module has the same class.
 */
@Mixin(Block.class)
public abstract class MixinSettledFaces {

    @Inject(method = "shouldRenderFace", at = @At("RETURN"), cancellable = true)
    private static void trmt$keepSettledFace(BlockState state, BlockGetter level, BlockPos pos, Direction face,
        CallbackInfoReturnable<Boolean> callback) {
        if (!callback.getReturnValueZ() && com.trmtgtnh.client.render.Settling.keepsFace(state, level, pos, face)) {
            callback.setReturnValue(Boolean.TRUE);
        }
    }
}
