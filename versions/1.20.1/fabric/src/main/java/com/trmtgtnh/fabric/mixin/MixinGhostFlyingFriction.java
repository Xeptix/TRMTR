package com.trmtgtnh.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Slice;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import com.trmtgtnh.block.GhostFamily;

/**
 * A flying mob's movement over worn ice reads ice's friction, as {@code LivingEntity.travel} does for everything that
 * walks (0.9.222, spec GF17) - one of the five places vanilla reads a block's friction, all of which Forge patches to
 * ask the block at the square. Two lookups here, each followed by its own read. See {@link MixinGhostLivingEntity}.
 *
 * <p>
 * <strong>Composed, not redirected</strong> (0.9.229), as {@link MixinGhostItemFriction} is: after each read the game
 * stores {@code getFriction() * 0.91F}, and on a worn square this stores the ghost's friction times the same 0.91 over
 * it. One handler per read, each sliced from its own read, because between the two the game stores a plain 0.91 into
 * the same local, which is not a read and must stay as it is.
 */
@Mixin(FlyingMob.class)
public abstract class MixinGhostFlyingFriction {

    @Unique
    private BlockPos trmt$groundAt;

    @ModifyArg(
        method = "travel",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockPos trmt$noteTheSquare(BlockPos pos) {
        trmt$groundAt = pos;
        return pos;
    }

    // The first float stored after the first read: the friction times vanilla's 0.91 (FlyingMob.travel).
    @ModifyVariable(
        method = "travel",
        at = @At(value = "STORE", ordinal = 0),
        slice = @Slice(
            from = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;getFriction()F", ordinal = 0)),
        ordinal = 0)
    private float trmt$frictionOfTheSquare(float slide) {
        return trmt$slideOver(slide);
    }

    // And after the second read, the same.
    @ModifyVariable(
        method = "travel",
        at = @At(value = "STORE", ordinal = 0),
        slice = @Slice(
            from = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;getFriction()F", ordinal = 1)),
        ordinal = 0)
    private float trmt$frictionOfTheSquareAgain(float slide) {
        return trmt$slideOver(slide);
    }

    @Unique
    private float trmt$slideOver(float slide) {
        Level level = ((Entity) (Object) this).level();
        Block ghost = GhostFamily.ghostAt(level, trmt$groundAt);
        return ghost == null ? slide : GhostFamily.frictionOf(ghost, level, trmt$groundAt) * 0.91F;
    }
}
