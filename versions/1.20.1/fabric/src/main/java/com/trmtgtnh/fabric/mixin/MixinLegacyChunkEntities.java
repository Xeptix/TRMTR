package com.trmtgtnh.fabric.mixin;

import java.util.Optional;
import java.util.function.Supplier;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.storage.ChunkStorage;
import net.minecraft.world.level.storage.DimensionDataStorage;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.trmtgtnh.erosion.LegacyChunkEntities;

/**
 * A chunk saved before 1.18 keeps its entities through the game's upgrade of it (0.9.227).
 *
 * <p>
 * The upgrade drops a chunk's whole entity list when one entry is a mod's own - a Golem of Ways in a 1.16.5 world took
 * every pig and item frame of its chunk with it, silently. {@link LegacyChunkEntities} has the account. This is every
 * chunk's way through the upgrade: the server's chunk map reads a chunk through it, and so does the world optimiser.
 * The list is copied at the head, before anything is changed, and put back at the return when the upgrade left none.
 * The same mixin is in the Forge module.
 */
@Mixin(ChunkStorage.class)
public abstract class MixinLegacyChunkEntities {

    @Inject(method = "upgradeChunkTag", at = @At("HEAD"))
    private void trmt$keepEntities(ResourceKey<Level> dimension, Supplier<DimensionDataStorage> storage, CompoundTag tag,
        Optional<?> generator, CallbackInfoReturnable<CompoundTag> callback) {
        LegacyChunkEntities.keep(tag);
    }

    @Inject(method = "upgradeChunkTag", at = @At("RETURN"))
    private void trmt$restoreEntities(ResourceKey<Level> dimension, Supplier<DimensionDataStorage> storage,
        CompoundTag tag, Optional<?> generator, CallbackInfoReturnable<CompoundTag> callback) {
        LegacyChunkEntities.restore(callback.getReturnValue());
    }
}
