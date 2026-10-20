package com.trmtgtnh.neoforge.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.trmtgtnh.client.render.Settling;

/**
 * The crosshair finds snow and carpet settled onto worn ground where they are drawn - the 1.7.10 edition's
 * MixinSettledHit (0.9.223).
 *
 * <p>
 * The crosshair clips each block's outline shape, which a clip asks of its context; the shape a block that settles
 * hands back is lowered by the rut's drop in {@link Settling#hitShape}. Only for a clip of outlines - a clip of
 * collision shapes is the collision hook's, which already holds settled ground where it stands. Its outline comes
 * down by the same test in {@link MixinSettledOutline}, and only there: Xep, 2026-10-10, "lower it as long as the
 * actual hitbox lowers with it". Not required: a mod that rewrites the clip leaves the hit where it was, and the
 * outline, which comes down only once a hit has, with it. A level with no ghosts in it - the integrated server's -
 * finds no drop.
 */
@Mixin(ClipContext.class)
public abstract class MixinSettledHit {

    @Shadow
    @Final
    private ClipContext.Block block;

    @Inject(method = "getBlockShape", at = @At("RETURN"), cancellable = true, require = 0)
    private void trmt$hitWhereItIsDrawn(BlockState state, BlockGetter level, BlockPos pos,
        CallbackInfoReturnable<VoxelShape> callback) {
        if (this.block != ClipContext.Block.OUTLINE) return;
        VoxelShape shape = callback.getReturnValue();
        VoxelShape lowered = Settling.hitShape(state, level, pos, shape);
        if (lowered != shape) callback.setReturnValue(lowered);
    }
}
