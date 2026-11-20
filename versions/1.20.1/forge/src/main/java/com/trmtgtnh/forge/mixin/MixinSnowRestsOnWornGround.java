package com.trmtgtnh.forge.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.trmtgtnh.erosion.PhysicalDecay;

/**
 * Lets snow lie on worn ground, as the 1.7.10 edition does.
 *
 * <p>
 * A snow layer here survives only where the top of the block below is whole, read from its collision shape - and
 * {@link MixinWornCollision} hands that read the worn shape, so a sunk square could hold no snow, snow never fell on
 * a worn road, and snow already there went at the next update beside it. {@link PhysicalDecay#restingShape} gives the
 * block's own shape back where the ground is worn; everything else the method asks, and whatever the read answers
 * anywhere else, is vanilla's. The Fabric module carries the same mixin.
 *
 * <p>
 * <strong>The shape is taken as the game hands it on, not redirected</strong> (0.9.229). Straight after the read, the
 * square below is noted from the method's own arguments, and the shape the read answered is changed as the game passes
 * it to {@code Block.isFaceFull}, so a snow mod's {@code @Redirect} of the same read still applies and its answer is the
 * one seen, kept on every square but a worn one. Nothing of the game's runs between the note and the answer, and the
 * notes are a stack ({@link PhysicalDecay#noteRestingRead}), so a check run again inside another one - by another mod's
 * code at the same place - answers its own square and leaves the outer one's. Until 0.9.228 this was a required
 * {@code @Redirect}: two redirects of one call cannot both apply, and beside a snow mod redirecting the same read the
 * game would have stopped before its first screen, as All of Fabric 3 did over friction. Not MixinExtras, which neither
 * Forge before 1.21.1 nor this jar's oldest Fabric Loader brings.
 *
 * <p>
 * Common rather than client: survival is the server's to decide.
 */
@Mixin(SnowLayerBlock.class)
public abstract class MixinSnowRestsOnWornGround {

    @Inject(method = "canSurvive(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
            shift = At.Shift.AFTER))
    private void trmt$noteTheGround(BlockState snow, LevelReader level, BlockPos pos, CallbackInfoReturnable<Boolean> survives) {
        PhysicalDecay.noteRestingRead(level, pos.below());
    }

    @ModifyArg(method = "canSurvive(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/Block;isFaceFull(Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/core/Direction;)Z"),
        index = 0)
    private VoxelShape trmt$restsOnItsOwnShape(VoxelShape shape) {
        return PhysicalDecay.restingShapeOfNoted(shape);
    }
}
