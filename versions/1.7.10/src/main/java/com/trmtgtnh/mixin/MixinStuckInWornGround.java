package com.trmtgtnh.mixin;

import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.trmtgtnh.erosion.PhysicalDecay;

/**
 * Stops the game shoving dropped items out of ruts they are not in.
 *
 * <p>
 * The game asks two different questions about the shape of a block, and until this was here the
 * mod only answered one of them. {@code addCollisionBoxesToList} is hooked, so everything that
 * moves collides with the hollow a worn road really has. {@code getCollisionBoundingBoxFromPool}
 * is not, because a worn position still holds ordinary grass and that grass answers for itself -
 * which is right everywhere except in the one vanilla method that asks it whether a position is a
 * full cube.
 *
 * <p>
 * That method is this one, and what it feeds is the escape an entity makes when it finds itself
 * inside a block: pick the nearest open side and fling the entity out of it, disabling collision
 * for the tick. It fires when the entity overlaps a collision box <em>or</em> when the position it
 * is standing in reads as a full cube. Worn ground answered the second, so an item lying in a rut
 * was declared stuck in ground it was resting neatly on top of, and thrown upward - vanilla's
 * choice, since the downward case in that method is unreachable. Next tick it landed, and was
 * thrown again. That is the item that hops and skitters on a worn road.
 *
 * <p>
 * Items and experience orbs only, which is the tell: they are the two entities that ask the
 * question about the middle of their own box, and at a quarter of a block tall an item's middle is
 * still inside the cell of any ground worn more than two pixels down. A player's middle is well
 * clear of it, so nothing ever happened to players and the fault stayed in the drops.
 *
 * <p>
 * The answer is the average edge of the shape this mod actually gives the position, so ground worn
 * by any amount stops reading as a full cube. Nothing that genuinely is inside the remaining ground
 * loses its way out: an entity overlapping the worn box satisfies the other half of that test and
 * escapes exactly as before.
 */
@Mixin(World.class)
public abstract class MixinStuckInWornGround {

    @Inject(method = "func_147469_q(III)Z", at = @At("HEAD"), cancellable = true)
    private void trmt$wornGroundIsNotAFullCube(int x, int y, int z, CallbackInfoReturnable<Boolean> cir) {
        AxisAlignedBB shape = PhysicalDecay.shapeAt((World) (Object) this, x, y, z);
        if (shape == null) return;
        cir.setReturnValue(Boolean.valueOf(shape.getAverageEdgeLength() >= 1.0D));
    }
}
