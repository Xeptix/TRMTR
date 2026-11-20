package com.trmtgtnh.neoforge.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.trmtgtnh.erosion.PhysicalDecay;

/**
 * Lets snow lie on worn ground, as the 1.7.10 edition does.
 *
 * <p>
 * A snow layer here survives only where the top of the block below is whole, read from its collision shape - and
 * {@link MixinWornCollision} hands that read the worn shape, so a sunk square could hold no snow, snow never fell on
 * a worn road, and snow already there went at the next update beside it. The one read is wrapped, and
 * {@link PhysicalDecay#restingShape} gives it back the block's own shape where the ground is worn; everything else
 * the method asks, and whatever the read answers anywhere else, is vanilla's. The Fabric module carries the same
 * mixin.
 *
 * <p>
 * Common rather than client: survival is the server's to decide.
 *
 * <p>
 * <strong>The read is wrapped, not redirected</strong> (0.9.229). MixinExtras' {@code @WrapOperation} hands this the
 * read as an operation and calls it, so another mod's {@code @Redirect} of the same read is what the operation runs and
 * its answer is the one seen, kept on every square but a worn one. Until 0.9.228 this was a required {@code @Redirect}:
 * two redirects of one call cannot both apply, and beside a snow mod redirecting the same read the game would have
 * stopped before its first screen, as All of Fabric 3 did over friction.
 */
@Mixin(SnowLayerBlock.class)
public abstract class MixinSnowRestsOnWornGround {

    @WrapOperation(method = "canSurvive(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/shapes/VoxelShape;"))
    private VoxelShape trmt$restsOnItsOwnShape(BlockState below, BlockGetter access, BlockPos pos, Operation<VoxelShape> read) {
        return PhysicalDecay.restingShape(below, access, pos, read.call(below, access, pos));
    }
}
