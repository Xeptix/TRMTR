package com.trmtgtnh.mixin;

import net.minecraft.block.BlockStairs;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.trmtgtnh.block.GhostStairs;

/**
 * Stops the stair rules reading a wear gradation as a compass bearing.
 *
 * <p>
 * Everything that gives a stair its shape reads metadata, and a worn stair's metadata is its wear
 * gradation rather than its facing. The ghost could answer for itself - and used to, by handing its
 * own three shape methods a wrapped view of the world - but that only ever covered half of it. The
 * other half is the stair <em>next door</em>: an ordinary unworn stair joins into a corner when a
 * neighbour is a stair facing the same way in the same half, and it asks that question itself,
 * through its own copy of the world, about a block it has no reason to think is unusual. A ghost
 * passes the "is it a stair" half, so the comparison came down to a bearing against a gradation and
 * agreed once every four steps of wear. The neighbour rebuilt itself as a corner, lost the quad on
 * the face they share, and got it back four gradations later.
 *
 * <p>
 * Which is why this is a mixin on vanilla's class rather than an override on ours. The block that
 * needs the right answer is not always the block that has been replaced, and nothing on a
 * replacement can reach into a neighbour's reasoning about it.
 *
 * <p>
 * Required, unlike the two optional renderer hooks. A stair drawn and clicked with the wrong
 * geometry is not a blemish, and {@code BlockStairs} is a block class rather than a renderer, so
 * there is no version of this game where the methods have quietly moved.
 *
 * <p>
 * The cost outside this mod is a block lookup and a failed type check per metadata read while a
 * stair is being shaped, which happens on the meshing thread during a chunk build. Every stair in
 * the world pays it; none of them notices.
 */
@Mixin(BlockStairs.class)
public class MixinStairMetadata {

    /**
     * The four that decide a stair's shape, all reading through the access they were handed.
     *
     * <p>
     * {@code func_150147_e} sets the base box, {@code func_150145_f} and {@code func_150144_g}
     * decide whether there is a second and a third, and {@code func_150146_f} is the neighbour
     * comparison the other two lean on. Between them they read metadata at the position and at all
     * four horizontal neighbours.
     */
    @Redirect(
        method = { "func_150147_e", "func_150145_f", "func_150144_g", "func_150146_f" },
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/IBlockAccess;getBlockMetadata(III)I"))
    private int trmt$shapeMetadata(IBlockAccess world, int x, int y, int z) {
        return GhostStairs.metadataAt(world, x, y, z);
    }

    /**
     * And the one that decides what the crosshair is pointing at.
     *
     * <p>
     * Separate because it is handed a {@code World} rather than an {@code IBlockAccess}, which is a
     * different call for the injector even though it is the same method underneath. Left out, a
     * worn stair would be shaped correctly and clicked as though it faced somewhere else: you could
     * break through where there was nothing and miss where there was.
     */
    @Redirect(
        method = "collisionRayTrace",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getBlockMetadata(III)I"))
    private int trmt$rayTraceMetadata(World world, int x, int y, int z) {
        return GhostStairs.metadataAt(world, x, y, z);
    }
}
