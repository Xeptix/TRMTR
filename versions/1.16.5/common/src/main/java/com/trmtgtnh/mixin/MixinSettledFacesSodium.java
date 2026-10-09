package com.trmtgtnh.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Sodium family's face test, kept open for a settled step the way {@link MixinSettledFaces} keeps
 * vanilla's. Rubidium, Embeddium and Sodium mesh chunks themselves and ask {@code shouldDrawSide} rather
 * than vanilla's test - the 1.7.10 edition answers Angelica's better face culling the same way.
 *
 * <p>
 * Named by string and gated by {@link OculusGate}, so a client without one of them never sees it; both
 * descriptors are named, Forge's for Rubidium and Embeddium and Fabric's intermediary one for Sodium,
 * because the target is never remapped.
 */
@Pseudo
@Mixin(targets = OculusGate.SODIUM_FACES, remap = false)
public abstract class MixinSettledFacesSodium {

    @Inject(
        method = {
            "shouldDrawSide(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/IBlockReader;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/Direction;)Z",
            "shouldDrawSide(Lnet/minecraft/class_2680;Lnet/minecraft/class_1922;Lnet/minecraft/class_2338;Lnet/minecraft/class_2350;)Z" },
        at = @At("RETURN"),
        cancellable = true,
        require = 0,
        remap = false)
    private void trmt$keepSettledFace(BlockState state, BlockGetter level, BlockPos pos, Direction face,
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
