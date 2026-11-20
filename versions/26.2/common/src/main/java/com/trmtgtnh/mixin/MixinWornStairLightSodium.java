package com.trmtgtnh.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import com.trmtgtnh.block.BlockGhostStair;

/**
 * Sodium's light cache asks a worn stair whether it is a solid cube at its square, as Sodium 0.6 asked every block on
 * 1.21.1 (1.0.0).
 *
 * <p>
 * <strong>Why this exists.</strong> Sodium reads each cell's light once into its light cache
 * ({@code LightDataAccess.compute}), and a cell whose state says {@code isSolidRender()} - with no light of its own - is
 * given no light at all, block or sky. From 1.21.2 a state answers that alone, kept from its occlusion shape at no
 * square, and the worn stair's ghost keeps the whole cell there ({@link BlockGhostStair}), so Sodium read every worn
 * stair's cell as dark. A face inside a block - a stair's riser and its lower tread - takes its light from that block's
 * own cell: Sodium's flat path entirely ({@code FlatLightPipeline.calculate}, for a quad with no cull face that is
 * neither on the block's edge nor in a full cube), its smooth path in part. With smooth lighting off for a worn stair on
 * Fabric (0.9.228), the 1.0.0 final pass photographed every riser and lower tread of the through yard's worn stairs on
 * 26.1.2 Fabric Sodium as pure black bands, about two fifths of the frame, where Indigo and vanilla, which read the
 * cell's light whatever the state says, drew them lit; NeoForge Sodium, smooth, drew them darker than vanilla. So the
 * one call is answered for a worn stair by {@link BlockGhostStair#solidRenderAt}: never solid, as no stair is, and the
 * cell's light - the stair's, let in through its open half - is read as for any other block. Everything that is not a
 * worn stair is the game's own answer, untouched.
 *
 * <p>
 * The square is the cache's own {@code pos}, which it has just set to the cell it read the state from, and the getter
 * its {@code level}, the chunk copy being meshed. Named by string and gated by {@link OculusGate} on this class being
 * there, so a client without Sodium never sees it, and {@code require = 0}, so a Sodium that has moved the call cannot
 * stop the game: the harness logs how many worn stairs were read here, nought where it did not bind. The same
 * {@code compute(III)I} and the same call are in Sodium 0.9.2 for 26.1.2, 26.2 and 26.3, Fabric and NeoForge alike
 * (javap, 2026-11-19).
 */
@Pseudo
@Mixin(targets = OculusGate.SODIUM_LIGHT, remap = false)
public abstract class MixinWornStairLightSodium {

    @Shadow(remap = false)
    @Final
    private BlockPos.MutableBlockPos pos;

    @Shadow(remap = false)
    protected BlockAndTintGetter level;

    @WrapOperation(
        method = "compute(III)I",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;isSolidRender()Z",
            remap = false),
        require = 0,
        remap = false)
    private boolean trmt$wornStairAtItsSquare(BlockState state, Operation<Boolean> original) {
        if (!(state.getBlock() instanceof BlockGhostStair)) return original.call(state);
        BlockGhostStair.noteReadAtSquare();
        return BlockGhostStair.solidRenderAt(state, level, pos);
    }
}
