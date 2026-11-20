package com.trmtgtnh.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

import com.trmtgtnh.block.GhostFamily;

/**
 * Landing on a worn square sounds like its family, and moving across one slides like its family - worn ice is ice to
 * the client that moves its own player, as the server's real ice is (0.9.222, spec GF10 and GF17).
 *
 * <p>
 * Both are asked of the block with no square in it in vanilla, which is what Fabric runs - {@code getSoundType()} on
 * the state below a landing, {@code getFriction()} on the block below a step - so one ghost for every family sounded
 * like gravel and slid like earth. Until 0.9.222 a player crossing worn ice stopped where the server, sliding them
 * across real ice at 0.98, said they had not, and the server put them back. Forge patches both methods to ask the
 * block at the square, and the ghost answers there; these ask {@link GhostFamily} at the same moments. The square is
 * the one vanilla itself looks the block up at, noted as it does so - the only lookup either method makes.
 *
 * <p>
 * <strong>The friction is taken from the local it is stored in, not redirected</strong> (0.9.229). {@code travel}
 * stores {@code getBlock().getFriction()} straight into a local of its own; this changes that local after the store,
 * so another mod's {@code @Redirect} of the same call - Biome Makeover 1.3.8's {@code LivingEntityMixin} is one -
 * still applies, and this sees what it answered. Until 0.9.228 this was a required {@code @Redirect} too: two
 * redirects of one call cannot both apply, Mixin skipped one, and all of Fabric 3 died before its first screen. Not
 * MixinExtras, which this jar's Fabric Loader floor (0.11.1) does not bring. {@link GhostFamily#frictionOver} keeps the
 * value on every square but a worn one.
 */
@Mixin(LivingEntity.class)
public abstract class MixinGhostLivingEntity {

    @Unique
    private BlockPos trmt$groundAt;

    @ModifyArg(
        method = { "playBlockFallSound", "travel" },
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockPos trmt$noteTheSquare(BlockPos pos) {
        trmt$groundAt = pos;
        return pos;
    }

    @Redirect(
        require = 0,
        method = "playBlockFallSound",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getSoundType()Lnet/minecraft/world/level/block/SoundType;"))
    private SoundType trmt$soundOfTheSquare(BlockState state) {
        return GhostFamily.soundOf(state, ((LivingEntity) (Object) this).level, trmt$groundAt);
    }

    // The first float stored after the friction read: the friction itself, before the game multiplies it by 0.91.
    @ModifyVariable(
        method = "travel",
        at = @At(value = "STORE", ordinal = 0),
        slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;getFriction()F")),
        ordinal = 0)
    private float trmt$frictionOfTheSquare(float friction) {
        return GhostFamily.frictionOver(friction, ((LivingEntity) (Object) this).level, trmt$groundAt);
    }
}
