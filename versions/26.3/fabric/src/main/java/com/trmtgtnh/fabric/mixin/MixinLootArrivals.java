package com.trmtgtnh.fabric.mixin;

import java.util.Map;

import com.google.gson.JsonElement;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Decoder;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceManagerRegistryLoadTask;
import net.minecraft.server.packs.resources.Resource;

import com.trmtgtnh.item.ExtraLoot;

/**
 * Adds this mod's finds to every loot table, while each is still the text a pack wrote it as.
 *
 * <p>
 * See {@link ExtraLoot} for what this does and why neither loader's own loot hook was used; the
 * Forge module has the same injection and its copy carries the detail worth stating - why from 1.20 it waits at the
 * end of the method that reads the loot tables' folder into text, rather than at the head of the method that read
 * that text into tables, as the 1.16.5 edition did.
 *
 * <p>
 * Required rather than optional. A chest that never holds one of this mod's tools is not a missing
 * flourish - it is the whole of how a player who has not read a wiki finds out the mod is installed.
 *
 * <p>
 * <strong>From 26.3 the loot tables are read by the game's registry loader</strong>, as a reloadable registry beside
 * the recipes, and {@code SimpleJsonResourceReloadListener.scanDirectory}, which 1.21.2 to 26.2 read them through, is
 * gone. {@code ResourceManagerRegistryLoadTask.load} lists a registry's folder into a map of files and then reads each
 * file into an entry on its own, in parallel (resources/ResourceManagerRegistryLoadTask, read from 26.3). So the moment
 * 1.21.1 waited for is the head of the step that is handed the listed files: the whole folder known and no table yet
 * read. There, for the loot tables' folder alone, every table's text is read and offered whole, as before; then each
 * file's own parse, the game's, is handed that file's offered text in place of reading the file, and keeps or refuses
 * the table as it always has. Every other registry is read as the game reads it. Both steps are lambdas, named by their
 * descriptors - the method's own shape - rather than by the numbers the compiler gave them.
 */
@Mixin(ResourceManagerRegistryLoadTask.class)
public abstract class MixinLootArrivals {

    /** The loot tables' text as offered, by id, once this task has been handed its folder; null for any other. */
    @Unique
    private volatile Map<Identifier, JsonElement> trmt$offered;

    @Inject(
        method = "*(Lnet/minecraft/resources/RegistryOps$RegistryInfoLookup;Lnet/minecraft/resources/FileToIdConverter;"
            + "Ljava/util/concurrent/Executor;Ljava/util/Map;)Ljava/util/concurrent/CompletionStage;",
        at = @At("HEAD"))
    private void trmt$addOurFinds(RegistryOps.RegistryInfoLookup context, FileToIdConverter lister,
        java.util.concurrent.Executor executor, Map<Identifier, Resource> files,
        CallbackInfoReturnable<java.util.concurrent.CompletionStage<?>> callback) {
        if (!ExtraLoot.isLootTables(lister)) return;
        trmt$offered = ExtraLoot.offerFolder(lister, files);
    }

    /** Each loot table's text as it was offered, in place of reading the file a second time. */
    @WrapOperation(
        method = "*(Lnet/minecraft/resources/FileToIdConverter;Lnet/minecraft/resources/RegistryOps;"
            + "Lnet/minecraft/resources/Identifier;Lnet/minecraft/server/packs/resources/Resource;)"
            + "Lnet/minecraft/resources/RegistryLoadTask$PendingRegistration;",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/resources/RegistryLoadTask$PendingRegistration;loadFromResource("
                + "Lcom/mojang/serialization/Decoder;Lnet/minecraft/resources/RegistryOps;Lnet/minecraft/resources/ResourceKey;"
                + "Lnet/minecraft/server/packs/resources/Resource;)Lcom/mojang/datafixers/util/Either;"))
    private Either<?, Exception> trmt$offeredText(Decoder<?> decoder, RegistryOps<JsonElement> ops, ResourceKey<?> key,
        Resource file, Operation<Either<?, Exception>> original) {
        Map<Identifier, JsonElement> offered = trmt$offered;
        JsonElement ours = offered == null || key == null ? null : offered.get(key.identifier());
        return original.call(decoder, ops, key, ours == null ? file : ExtraLoot.asResource(file, ours));
    }
}
