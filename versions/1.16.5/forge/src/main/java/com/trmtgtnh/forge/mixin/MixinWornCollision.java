package com.trmtgtnh.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.trmtgtnh.erosion.PhysicalDecay;

/**
 * Gives the server's real blocks the shape the client draws over them.
 *
 * <p>
 * Where the 1.12.2 edition has a Forge event and the 1.7.10 edition a mixin on the block, this
 * version has neither: collision became a shape asked of the state, and that is where this sits. See
 * {@link PhysicalDecay}, which holds every decision and is what the Fabric module's identical mixin
 * calls too.
 *
 * <p>
 * <strong>Both overloads, because the game uses both.</strong> The three-argument one is what
 * movement asks; the two-argument one is what the push-out and several of the game's own checks ask,
 * and the 1.7.10 edition's hook sat on one path and not the other - an item thrown into a rut was
 * flung out of it again every tick until 0.9.216 added a second mixin. Sitting on both is the whole
 * point of having moved here.
 *
 * <p>
 * At the return rather than the head, so the block has already said what it thinks and this is
 * handed it: the settling half moves that shape rather than replacing it, and a block that answered
 * with nothing at all is left alone.
 *
 * <p>
 * <strong>Required, unlike the painter's hooks.</strong> Those are optional because the painter has
 * a slow rescan behind them and a pack that moved one of those methods still gets worn ground a
 * couple of seconds later. There is nothing behind this one: an injection that does not attach
 * leaves every rut in the world a picture, with no error anywhere. It was written optional first and
 * spent a run looking exactly like a bug in the depth arithmetic.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class MixinWornCollision {

    @Inject(method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
        at = @At("RETURN"), cancellable = true, require = 1)
    private void trmt$wornShape(BlockGetter access, BlockPos pos, CallbackInfoReturnable<VoxelShape> callback) {
        VoxelShape worn = PhysicalDecay
            .shapeAt(access, pos, (BlockState) (Object) this, callback.getReturnValue());
        if (worn != null) callback.setReturnValue(worn);
    }

    @Inject(method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
        at = @At("RETURN"), cancellable = true, require = 1)
    private void trmt$wornShapeInContext(BlockGetter access, BlockPos pos, CollisionContext context,
        CallbackInfoReturnable<VoxelShape> callback) {
        VoxelShape worn = PhysicalDecay
            .shapeAt(access, pos, (BlockState) (Object) this, callback.getReturnValue());
        if (worn != null) callback.setReturnValue(worn);
    }
}
