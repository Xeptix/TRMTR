package com.trmtgtnh.mixin;

import net.minecraft.block.BlockSnow;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.trmtgtnh.client.render.Settling;

/**
 * Keeps the side two snow layers share when they have settled to different heights - see
 * {@link Settling#keepsFace}. Only ever turns a hidden face back on, at the return, so vanilla's answer
 * stands wherever the two still meet level.
 */
@Mixin(BlockSnow.class)
public abstract class MixinSettledSnowFaces {

    @Inject(
        method = "shouldSideBeRendered(Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/EnumFacing;)Z",
        at = @At("RETURN"),
        cancellable = true)
    private void trmt$keepSettledFace(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side,
        CallbackInfoReturnable<Boolean> callback) {
        if (Boolean.TRUE.equals(callback.getReturnValue())) return;
        if (Settling.keepsFace(state, world, pos, side)) callback.setReturnValue(Boolean.TRUE);
    }
}
