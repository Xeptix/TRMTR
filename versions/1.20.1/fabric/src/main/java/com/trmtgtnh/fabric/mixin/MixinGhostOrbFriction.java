package com.trmtgtnh.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Slice;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import com.trmtgtnh.block.GhostFamily;

/**
 * An experience orb on worn ice slides as it would on ice (0.9.222, spec GF17) - one of the five places vanilla reads
 * a block's friction, all of which Forge patches to ask the block at the square. See {@link MixinGhostLivingEntity}.
 *
 * <p>
 * <strong>Composed, not redirected</strong> (0.9.229), as {@link MixinGhostItemFriction} is: the game stores
 * {@code getFriction() * 0.98F}, and on a worn square this stores the ghost's friction times the same 0.98 over it.
 */
@Mixin(ExperienceOrb.class)
public abstract class MixinGhostOrbFriction {

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

    // The first float stored after the friction read: the friction times vanilla's 0.98 (ExperienceOrb.tick).
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
