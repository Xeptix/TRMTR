package com.trmtgtnh.mixin;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.trmtgtnh.client.render.Settling;

/**
 * Draws snow and carpet down with the ground worn away underneath them, through the offset every block
 * renderer adds to a block's picture - see {@link Settling}, which says why that is the seam at this
 * version and what it leaves alone.
 *
 * <p>
 * On {@code Block} because neither snow nor carpet answers the offset itself, and at the return so
 * whatever the block or another mod worked out is kept and only lowered. Client side only: the picture
 * is all this moves, and the footing comes down by the same figure in the collision hook.
 */
@Mixin(Block.class)
public abstract class MixinSettleOnWornGround {

    @Inject(
        method = "getOffset(Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/util/math/Vec3d;",
        at = @At("RETURN"),
        cancellable = true)
    private void trmt$settleOnWornGround(IBlockState state, IBlockAccess world, BlockPos pos,
        CallbackInfoReturnable<Vec3d> callback) {
        Vec3d settled = Settling.offset(state, world, pos, callback.getReturnValue());
        if (settled != null) callback.setReturnValue(settled);
    }
}
