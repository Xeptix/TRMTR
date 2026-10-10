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

    // Optional, as the 1.7.10 edition's cosmetic hooks are: its loss costs a picture, where a required one that a
    // pack's renderer had moved would stop the game from starting (0.9.222, spec SU21).
    @Inject(method = "shouldRenderFace", at = @At("RETURN"), cancellable = true, require = 0)
    private static void trmt$keepSettledFace(BlockState state, BlockGetter level, BlockPos pos, Direction face,
        CallbackInfoReturnable<Boolean> callback) {
        // Two windows side by side hide the face between them, the way two panes of glass do - the 1.7.10 edition's
        // shouldSideBeRendered on its window twins (0.9.220). See GhostWindows.sharesPane.
        if (callback.getReturnValueZ() && com.trmtgtnh.client.model.GhostWindows.sharesPane(state, level, pos, face)) {
            callback.setReturnValue(Boolean.FALSE);
            return;
        }
        if (!callback.getReturnValueZ() && com.trmtgtnh.client.render.Settling.keepsFace(state, level, pos, face)) {
            callback.setReturnValue(Boolean.TRUE);
        }
    }
}
