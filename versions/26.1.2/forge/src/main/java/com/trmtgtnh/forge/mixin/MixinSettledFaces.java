package com.trmtgtnh.forge.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Hands the renderer's face test to {@link com.trmtgtnh.client.render.FaceRule}: a face beside a worn stair tested
 * against the stair at its square, two windows hiding the face between them, and the step two settled blocks share
 * kept where they stand at different heights. {@link com.trmtgtnh.mixin.MixinSettledFacesSodium} is the same for the
 * Sodium family, which asks its own.
 *
 * <p>
 * <strong>In each loader's module, not in common, from 0.9.219.</strong> It names a game method, so the
 * name goes into a refmap, and common's processor writes that refmap in Fabric's intermediary names: a
 * production Forge jar has it remapped on the way out, and a Forge development run reads it as it is and
 * refuses the mixin - which, on a class as early as {@code Block}, is a game that never starts. Every
 * mixin left in common injects with {@code remap = false} for that reason; see
 * {@code CommonMixinsNeedNoRefmapTest}. The Fabric module has the same class.
 *
 * <p>
 * <strong>From 1.21.2 {@code Block.shouldRenderFace} is handed no view and no position</strong> (the two states and the
 * face), so it cannot be asked about a square. The renderer's own test, {@code ModelBlockRenderer.shouldRenderFace}
 * (private, client/renderer/block/ModelBlockRenderer.java), holds the chunk's view, the state and the neighbour's
 * position, and decides there; so the same two rules are asked at its return, of the square the neighbour faces, and
 * only when the renderer culls at all - 1.21.1's block test was only asked then. Forge 64's five-argument
 * {@code Block.shouldRenderFace} is not called by this renderer with a real view, so it is no door either.
 */
@Mixin(ModelBlockRenderer.class)
public abstract class MixinSettledFaces {

    @Shadow
    @Final
    private boolean cull;

    // Optional, as the 1.7.10 edition's cosmetic hooks are: its loss costs a picture, where a required one that a
    // pack's renderer had moved would stop the game from starting (0.9.222, spec SU21).
    @Inject(method = "shouldRenderFace", at = @At("RETURN"), cancellable = true, require = 0)
    private void trmt$keepSettledFace(BlockAndTintGetter level, BlockState state, Direction face, BlockPos neighbour,
        CallbackInfoReturnable<Boolean> callback) {
        if (!this.cull || level == null || face == null || neighbour == null) return;
        BlockPos pos = neighbour.relative(face.getOpposite());
        // The rule every face hook asks - a worn stair beside, two windows, a settled step - is FaceRule's.
        boolean vanilla = callback.getReturnValueZ();
        boolean drawn = com.trmtgtnh.client.render.FaceRule.decide(vanilla, state, level, pos, face);
        if (drawn != vanilla) callback.setReturnValue(Boolean.valueOf(drawn));
    }
}
