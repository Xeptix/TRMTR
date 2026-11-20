package com.trmtgtnh.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.sniffer.Sniffer;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

import com.trmtgtnh.block.GhostFamily;

/**
 * A sniffer digging at a worn square knocks with the family the square is worn as, not the gravel one ghost answers
 * with no square in hand (0.9.229). The client plays that knock itself, in {@code emitDiggingParticles}; Forge and
 * NeoForge patch the method to ask at the square, and this is the same question asked at the same place. The square is
 * the one the method looks the block up at, noted as it does so; see {@link MixinGhostLivingEntity}.
 */
@Mixin(Sniffer.class)
public abstract class MixinGhostSnifferDig {

    @Unique
    private BlockPos trmt$dugAt;

    @ModifyArg(
        require = 0,
        method = "emitDiggingParticles",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockPos trmt$noteTheSquare(BlockPos pos) {
        trmt$dugAt = pos;
        return pos;
    }

    @Redirect(
        require = 0,
        method = "emitDiggingParticles",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getSoundType()Lnet/minecraft/world/level/block/SoundType;"))
    private SoundType trmt$soundOfTheSquare(BlockState state) {
        return GhostFamily.soundOf(state, ((Entity) (Object) this).level(), trmt$dugAt);
    }
}
