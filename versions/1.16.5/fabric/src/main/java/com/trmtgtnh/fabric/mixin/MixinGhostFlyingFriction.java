package com.trmtgtnh.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.level.block.Block;

import com.trmtgtnh.block.GhostFamily;

/**
 * A flying mob's movement over worn ice reads ice's friction, as {@code LivingEntity.travel} does for everything that
 * walks (0.9.222, spec GF17) - one of the five places vanilla reads a block's friction, all of which Forge patches to
 * ask the block at the square. Two lookups here, each followed by its own read. See {@link MixinGhostLivingEntity}.
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

    @Redirect(method = "travel", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;getFriction()F"))
    private float trmt$frictionOfTheSquare(Block block) {
        return GhostFamily.frictionOf(block, ((Entity) (Object) this).level, trmt$groundAt);
    }
}
