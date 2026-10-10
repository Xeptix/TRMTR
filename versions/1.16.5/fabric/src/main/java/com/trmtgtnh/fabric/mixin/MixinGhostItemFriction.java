package com.trmtgtnh.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Block;

import com.trmtgtnh.block.GhostFamily;

/**
 * An item dropped on worn ice slides as it would on ice (0.9.222, spec GF17).
 *
 * <p>
 * One of the five places vanilla reads a block's friction, all of which Forge patches to ask the block at the square;
 * see {@link MixinGhostLivingEntity} for why the square is noted from vanilla's own lookup. A client moves an item
 * between the server's word on where it is, and on worn ice it stopped short and jumped on.
 */
@Mixin(ItemEntity.class)
public abstract class MixinGhostItemFriction {

    @Unique
    private BlockPos trmt$groundAt;

    @ModifyArg(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockPos trmt$noteTheSquare(BlockPos pos) {
        trmt$groundAt = pos;
        return pos;
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;getFriction()F"))
    private float trmt$frictionOfTheSquare(Block block) {
        return GhostFamily.frictionOf(block, ((Entity) (Object) this).level, trmt$groundAt);
    }
}
