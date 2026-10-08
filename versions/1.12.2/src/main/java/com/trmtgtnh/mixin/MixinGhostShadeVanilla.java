package com.trmtgtnh.mixin;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.trmtgtnh.block.BlockGhost;

/**
 * Vanilla's smooth lighting, which draws when Forge's light pipeline is off - and OptiFine switches it off and
 * draws through this, its own copy of it: a whole ghost shades the corners beside it, as the block it replaced
 * did - see {@link BlockGhost#wholeAt}.
 *
 * <p>
 * Each corner's shade is read as {@code world.getBlockState(pos).getAmbientOcclusionLightValue()}, the state
 * asked straight after it is fetched. So the fetch is remembered, and the reading answered from it where the
 * state is that same ghost - by identity, so a reading that does not follow its own fetch is left alone.
 * Wrapped rather than redirected, so another mod wrapping the same calls is chained rather than refused.
 */
@Mixin(targets = "net.minecraft.client.renderer.BlockModelRenderer$AmbientOcclusionFace")
public abstract class MixinGhostShadeVanilla {

    @Unique
    private IBlockAccess trmt$world;

    @Unique
    private IBlockState trmt$fetched;

    @Unique
    private int trmt$x;

    @Unique
    private int trmt$y;

    @Unique
    private int trmt$z;

    @WrapOperation(
        method = "updateVertexBrightness",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/IBlockAccess;getBlockState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/state/IBlockState;"))
    private IBlockState trmt$remember(IBlockAccess world, BlockPos pos, Operation<IBlockState> original) {
        IBlockState state = original.call(world, pos);
        trmt$world = world;
        trmt$fetched = state;
        trmt$x = pos.getX();
        trmt$y = pos.getY();
        trmt$z = pos.getZ();
        return state;
    }

    @WrapOperation(
        method = "updateVertexBrightness",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/block/state/IBlockState;getAmbientOcclusionLightValue()F"))
    private float trmt$shade(IBlockState state, Operation<Float> original) {
        if (state == trmt$fetched && state.getBlock() instanceof BlockGhost
            && BlockGhost.wholeAt(trmt$world, new BlockPos(trmt$x, trmt$y, trmt$z))) {
            BlockGhost.shadeSeen("vanilla's smooth lighting");
            return BlockGhost.WHOLE_SHADE;
        }
        return original.call(state);
    }
}
