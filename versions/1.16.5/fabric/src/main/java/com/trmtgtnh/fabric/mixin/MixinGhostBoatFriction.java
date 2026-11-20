package com.trmtgtnh.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Slice;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import com.trmtgtnh.block.GhostFamily;

/**
 * A boat on worn ice runs as fast as it does on ice (0.9.222, spec GF17).
 *
 * <p>
 * A boat the player steers is moved by the player's own client, and its ground friction is the average of the blocks
 * under its hull - which on worn ice was earth's, so the boat crawled where the server's real ice would carry it. One of
 * the five places vanilla reads a block's friction, all of which Forge patches to ask the block at the square. The
 * squares are visited in a loop with one position object moved along it; each is noted as vanilla looks it up and read
 * straight after, before the loop moves it on. See {@link MixinGhostLivingEntity}.
 *
 * <p>
 * <strong>Composed, not redirected</strong> (0.9.229; see {@link MixinGhostLivingEntity} for the crash). Here the
 * friction is added straight into the running sum, {@code f += getBlock().getFriction()}, with no local of its own, so
 * the sum is read as the game loads it for the addition and changed after the game stores it back: on a worn square
 * the sum before it plus the ghost's friction, exactly what the game would have added; on any other square the sum the
 * game - or another mod's redirect of the read - made, untouched.
 */
@Mixin(Boat.class)
public abstract class MixinGhostBoatFriction {

    @Unique
    private BlockPos trmt$groundAt;

    @Unique
    private float trmt$sumBefore;

    @ModifyArg(
        method = "getGroundFriction",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockPos trmt$noteTheSquare(BlockPos pos) {
        trmt$groundAt = pos;
        return pos;
    }

    // The sum's one load before the friction read: the left side of the addition.
    @ModifyVariable(
        method = "getGroundFriction",
        at = @At(value = "LOAD", ordinal = 0),
        slice = @Slice(to = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;getFriction()F")),
        ordinal = 0)
    private float trmt$noteTheSum(float sum) {
        trmt$sumBefore = sum;
        return sum;
    }

    // The sum stored back after the addition.
    @ModifyVariable(
        method = "getGroundFriction",
        at = @At(value = "STORE", ordinal = 0),
        slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;getFriction()F")),
        ordinal = 0)
    private float trmt$frictionOfTheSquare(float sum) {
        Level level = ((Entity) (Object) this).level;
        Block ghost = GhostFamily.ghostAt(level, trmt$groundAt);
        return ghost == null ? sum : trmt$sumBefore + GhostFamily.frictionOf(ghost, level, trmt$groundAt);
    }
}
