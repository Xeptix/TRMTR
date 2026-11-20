package com.trmtgtnh.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;

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
 * From 1.21.2 every boat and raft is an AbstractBoat (vehicle.boat), which holds getGroundFriction; 1.21.1's Boat is one
 * kind of it.
 *
 * <p>
 * <strong>Changed after the read, not redirected</strong> (0.9.229; see {@link MixinGhostLivingEntity} for the crash):
 * each square's read is taken as it is made, before the game adds it to the sum, so a worn square adds the ghost's
 * friction and any other adds what the game - or another mod's redirect of the read - gave.
 */
@Mixin(AbstractBoat.class)
public abstract class MixinGhostBoatFriction {

    @Unique
    private BlockPos trmt$groundAt;

    @ModifyArg(
        method = "getGroundFriction",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockPos trmt$noteTheSquare(BlockPos pos) {
        trmt$groundAt = pos;
        return pos;
    }

    @ModifyExpressionValue(
        method = "getGroundFriction",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;getFriction()F"))
    private float trmt$frictionOfTheSquare(float friction) {
        return GhostFamily.frictionOver(friction, ((Entity) (Object) this).level(), trmt$groundAt);
    }
}
