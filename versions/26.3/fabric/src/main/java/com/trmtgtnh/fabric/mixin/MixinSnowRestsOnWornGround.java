package com.trmtgtnh.fabric.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.trmtgtnh.erosion.PhysicalDecay;

/**
 * Lets snow lie on worn ground, as the 1.7.10 edition does.
 *
 * <p>
 * A snow layer here survives only where the top of the block below is whole, read from its collision shape - and
 * {@link MixinWornCollision} hands that read the worn shape, so a sunk square could hold no snow, snow never fell on
 * a worn road, and snow already there went at the next update beside it. The one read is redirected, and
 * {@link PhysicalDecay#restingShape} gives it back the block's own shape where the ground is worn; everything else
 * the method asks, and whatever the read answers anywhere else, is vanilla's. The Forge module carries the same
 * mixin.
 *
 * <p>
 * Common rather than client: survival is the server's to decide.
 */
@Mixin(SnowLayerBlock.class)
public abstract class MixinSnowRestsOnWornGround {

    @Redirect(method = "canSurvive(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/shapes/VoxelShape;"),
        require = 1)
    private VoxelShape trmt$restsOnItsOwnShape(BlockState below, BlockGetter access, BlockPos pos) {
        return PhysicalDecay.restingShape(below, access, pos, below.getCollisionShape(access, pos));
    }
}
