package com.trmtgtnh.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Slice;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import com.trmtgtnh.block.GhostFamily;

/**
 * An item dropped on worn ice slides as it would on ice (0.9.222, spec GF17).
 *
 * <p>
 * One of the five places vanilla reads a block's friction, all of which Forge patches to ask the block at the square;
 * see {@link MixinGhostLivingEntity} for why the square is noted from vanilla's own lookup. A client moves an item
 * between the server's word on where it is, and on worn ice it stopped short and jumped on.
 *
 * <p>
 * <strong>Composed, not redirected</strong> (0.9.229; see {@link MixinGhostLivingEntity} for the crash). The game
 * stores {@code getFriction() * 0.98F} into its slide local; this changes that local after the store, on a worn square
 * to the ghost's friction times the same 0.98, exactly what the game would have stored, and on any other square not at
 * all, so another mod's redirect of the read still decides it there.
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

    // The first float stored after the friction read: the friction times vanilla's 0.98 (ItemEntity.tick).
    @ModifyVariable(
        method = "tick",
        at = @At(value = "STORE", ordinal = 0),
        slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;getFriction()F")),
        ordinal = 0)
    private float trmt$frictionOfTheSquare(float slide) {
        Level level = ((Entity) (Object) this).level();
        Block ghost = GhostFamily.ghostAt(level, trmt$groundAt);
        return ghost == null ? slide : GhostFamily.frictionOf(ghost, level, trmt$groundAt) * 0.98F;
    }
}
