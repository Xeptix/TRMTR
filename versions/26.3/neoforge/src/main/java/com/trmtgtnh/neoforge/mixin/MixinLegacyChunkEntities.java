package com.trmtgtnh.neoforge.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;

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
 * chunk's way through the upgrade: the server's chunk map reads a chunk through it, and so do the world upgrader and
 * the file fixes that run as an old world opens. The list is copied at the head, before anything is changed, and put
 * back at the return when the upgrade left none. The same mixin is in the Forge and Fabric modules.
 *
 * <p>
 * 1.21.1 hooked {@code ChunkStorage.upgradeChunkTag}; after 1.21.1 that upgrade is {@code SimpleRegionStorage}'s, with
 * the data-fix context and the target version as arguments, and two shorter overloads that call this one. So the
 * method is named by its whole descriptor, and the hook is on it alone, which each of them reaches once.
 */
@Mixin(SimpleRegionStorage.class)
public abstract class MixinLegacyChunkEntities {

    @Inject(method = "upgradeChunkTag(Lnet/minecraft/nbt/CompoundTag;ILnet/minecraft/nbt/CompoundTag;I)Lnet/minecraft/nbt/CompoundTag;",
        at = @At("HEAD"))
    private void trmt$keepEntities(CompoundTag tag, int defaultVersion, CompoundTag context, int targetVersion,
        CallbackInfoReturnable<CompoundTag> callback) {
        LegacyChunkEntities.keep(tag);
    }

    @Inject(method = "upgradeChunkTag(Lnet/minecraft/nbt/CompoundTag;ILnet/minecraft/nbt/CompoundTag;I)Lnet/minecraft/nbt/CompoundTag;",
        at = @At("RETURN"))
    private void trmt$restoreEntities(CompoundTag tag, int defaultVersion, CompoundTag context, int targetVersion,
        CallbackInfoReturnable<CompoundTag> callback) {
        LegacyChunkEntities.restore(callback.getReturnValue());
    }
}
