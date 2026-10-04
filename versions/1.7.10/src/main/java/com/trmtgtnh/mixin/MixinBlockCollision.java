package com.trmtgtnh.mixin;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.trmtgtnh.erosion.ISettlingBlock;
import com.trmtgtnh.erosion.ISinkableBlock;
import com.trmtgtnh.erosion.PhysicalDecay;

/**
 * Makes a worn rut something you can walk down into.
 *
 * <p>
 * This was the mod's first bytecode injection, and it exists for one reason: with
 * {@code physicalDecay = real} the server has to answer for the shape of a block it never
 * changed. The block at a worn position is still ordinary grass, so without this the server
 * would insist the ground is full height and shove any player standing in a rut back out of it
 * every tick.
 *
 * <p>
 * It sits on the busiest method in the game — collision is tested for every moving entity,
 * every tick, over every block its box sweeps — so the cost of <em>not</em> being worn ground
 * is what matters. That is a static boolean and a field on the block instance, both stamped
 * ahead of time. Only a block from a family that can sink ever reaches a position lookup.
 */
@Mixin(Block.class)
public abstract class MixinBlockCollision implements ISinkableBlock, ISettlingBlock {

    @Unique
    private boolean trmt$sinkable;

    @Override
    public boolean trmt$isSinkable() {
        return trmt$sinkable;
    }

    @Override
    public void trmt$setSinkable(boolean sinkable) {
        this.trmt$sinkable = sinkable;
    }

    /**
     * Whether this block rests on the ground and should be drawn down with it.
     *
     * <p>
     * Carried on the same mixin as the sink flag because they are the same kind of fact about the
     * same object, and because this class is already applied to Block - which is what makes the
     * instanceof in the render guard free rather than a class-hierarchy walk.
     */
    @Unique
    private boolean trmt$settling;

    @Override
    public boolean trmt$isSettling() {
        return trmt$settling;
    }

    @Override
    public void trmt$setSettling(boolean settling) {
        this.trmt$settling = settling;
    }

    @Inject(
        method = "addCollisionBoxesToList(Lnet/minecraft/world/World;IIILnet/minecraft/util/AxisAlignedBB;Ljava/util/List;Lnet/minecraft/entity/Entity;)V",
        at = @At("HEAD"),
        cancellable = true)
    private void trmt$sinkWornGround(World world, int x, int y, int z, AxisAlignedBB mask, List list, Entity entity,
        CallbackInfo ci) {
        if (!PhysicalDecay.isActive()) return;

        if (trmt$settling) {
            // Something lying on worn ground, drawn down into the rut. Its footing has to come down
            // with the picture, or the player stands at the height the ground had before it wore
            // and looks down at snow half a block below their feet.
            AxisAlignedBB settled = PhysicalDecay.settledBoxAt(world, x, y, z);
            if (settled == null) return;
            if (mask.intersectsWith(settled)) list.add(settled);
            ci.cancel();
            return;
        }
        if (!trmt$sinkable) return;

        AxisAlignedBB box = PhysicalDecay.boxAt(world, x, y, z);
        if (box == null) return;

        if (mask.intersectsWith(box)) {
            list.add(box);
        }
        ci.cancel();
    }
}
