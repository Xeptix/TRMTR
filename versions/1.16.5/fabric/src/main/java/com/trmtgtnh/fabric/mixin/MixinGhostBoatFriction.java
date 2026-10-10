package com.trmtgtnh.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.Boat;
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
 */
@Mixin(Boat.class)
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

    @Redirect(
        method = "getGroundFriction",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;getFriction()F"))
    private float trmt$frictionOfTheSquare(Block block) {
        return GhostFamily.frictionOf(block, ((Entity) (Object) this).level, trmt$groundAt);
    }
}
