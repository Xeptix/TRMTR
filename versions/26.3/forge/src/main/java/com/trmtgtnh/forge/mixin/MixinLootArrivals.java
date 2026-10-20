package com.trmtgtnh.forge.mixin;

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
 * <strong>Why here rather than at Forge's own loot event is {@link ExtraLoot}'s note, and the short
 * of it is the other loader:</strong> a table's pools and their entries are private and final in
 * vanilla, and the methods that edit them are Forge's patches, so the event has no counterpart to
 * carry to. A moment earlier, the table is a {@code JsonObject} and anything a loot table can say
 * can be said in it.
 *
 * <p>
 * From 1.20 the tables are read by {@code LootDataManager}, which reads each kind of loot data's folder into a map of
 * text with {@code SimpleJsonResourceReloadListener.scanDirectory} and then reads each entry of the map into a table, in
 * a lambda of its own. So this waits at the end of that public, static and stable method - every loader and every
 * mapping names it the same, where a lambda's name is the compiler's - and acts only on the loot tables' own folder:
 * the map is then the pack's own text, complete, and not yet read into a single table, which is the moment the 1.16.5
 * edition's head of {@code LootTables.apply} was. Nothing is cancelled and nothing is replaced - what is handed on is
 * the pack's own text with entries appended to a pool it already had, which is why a datapack that rewrites one of
 * these tables keeps its own version and simply gets the finds added to that.
 *
 * <p>
 * <strong>From 1.21.2 there is no map of text.</strong> {@code scanDirectory} read each file and parsed it into a table in
 * the same step, so the same moment was made at the head of that method, for the loot tables' folder alone: every
 * table's text read into a map and offered whole, as 1.21.1 offered it, and each file's text handed to the parse from
 * that map in place of a second reading.
 *
 * <p>
 * <strong>From 26.3 the loot tables are read by the game's registry loader</strong>, as a reloadable registry beside
 * the recipes, and {@code scanDirectory} is gone. {@code ResourceManagerRegistryLoadTask.load} lists a registry's folder
 * into a map of files and then reads each file into an entry on its own, in parallel; on Forge 66 both steps are handed
 * the decoder Forge's conditions wrap the element's codec in, and each file's parse
 * ({@code RegistryLoadTask.PendingRegistration.loadFromResource}) reads it through that decoder (read from Forge
 * 26.3-66.0.13's patched jar). So the moment is the head of the step that is handed the listed files: the whole folder
 * known and no table read. There, for the loot tables' folder alone, every table's text is read and offered whole, as
 * 26.2 offered it - every table known, the finds added in place, announced once - and each file's parse is then handed
 * that file's offered text in place of the file. A file that does not read is left to the game's own parse, which
 * reads it again and says why as it always has. Both steps are lambdas, named by their descriptors - the method's own
 * shape - rather than by the numbers the compiler gave them.
 */
@Mixin(ResourceManagerRegistryLoadTask.class)
public abstract class MixinLootArrivals {

    /** The loot tables' text as offered, by id, once this task has been handed its folder; null for any other. */
    @Unique
    private volatile Map<Identifier, JsonElement> trmt$offered;

    @Inject(
        method = "*(Lnet/minecraft/resources/RegistryOps$RegistryInfoLookup;Lnet/minecraft/resources/FileToIdConverter;"
            + "Lcom/mojang/serialization/Decoder;Ljava/util/concurrent/Executor;Ljava/util/Map;)"
            + "Ljava/util/concurrent/CompletionStage;",
        at = @At("HEAD"))
    private void trmt$addOurFinds(RegistryOps.RegistryInfoLookup context, FileToIdConverter lister, Decoder<?> decoder,
        java.util.concurrent.Executor executor, Map<Identifier, Resource> files,
        CallbackInfoReturnable<java.util.concurrent.CompletionStage<?>> callback) {
        if (!ExtraLoot.isLootTables(lister)) return;
        trmt$offered = ExtraLoot.offerFolder(lister, files);
    }

    /** Each loot table's text as it was offered, in place of reading the file a second time. */
    @WrapOperation(
        method = "*(Lnet/minecraft/resources/FileToIdConverter;Lcom/mojang/serialization/Decoder;"
            + "Lnet/minecraft/resources/RegistryOps;Lnet/minecraft/resources/Identifier;"
            + "Lnet/minecraft/server/packs/resources/Resource;)Lnet/minecraft/resources/RegistryLoadTask$PendingRegistration;",
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
