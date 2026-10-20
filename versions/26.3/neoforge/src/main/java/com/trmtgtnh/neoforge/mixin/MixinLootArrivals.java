package com.trmtgtnh.neoforge.mixin;

import java.util.Map;
import java.util.Optional;

import com.google.gson.JsonElement;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
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
 * From 1.20 to 1.21.1 the tables were read by {@code LootDataManager} and then the reloadable registries, which read
 * each kind of loot data's folder into a map of text with {@code SimpleJsonResourceReloadListener.scanDirectory} and
 * then read each entry of the map into a table. So 1.21.1 waited at the end of that public, static and stable method
 * and acted only on the loot tables' own folder: the map was then the pack's own text, complete, and not yet read into
 * a single table, which is the moment the 1.16.5 edition's head of {@code LootTables.apply} was. Nothing is cancelled
 * and nothing is replaced in what a table says - what is handed on is the pack's own text with entries appended to a
 * pool it already had, which is why a datapack that rewrites one of these tables keeps its own version and simply gets
 * the finds added to that.
 *
 * <p>
 * <strong>From 26.3 the loot tables are read by the game's registry loader</strong>, as a reloadable registry beside
 * the recipes; {@code scanDirectoryWithOptionalValues}, which NeoForge 26.2 read them through, is gone with vanilla's
 * scan. {@code ResourceManagerRegistryLoadTask.load} lists a registry's folder into a map of files and then reads each
 * file into an entry on its own, in parallel; on NeoForge 26.3.0.94 that step is handed a {@code ConditionalOps}, and
 * each file's parse ({@code RegistryLoadTask.PendingRegistration.loadFromResource}) reads it with NeoForge's
 * conditional codec, a table whose conditions are not met skipped (read from that version's patched jar). So the
 * moment 1.21.1 waited for is where that step has its ops and has not yet begun the parse: the whole folder known and
 * no table read. There, for the loot tables' folder alone, every table's text is read and weighed against its
 * conditions as 26.2's scan weighed it - one not met is neither offered nor known - and the rest offered whole, as
 * before; then each file's own parse, the game's, is handed that file's offered text in place of reading the file, and
 * keeps, skips or refuses the table as it always has. Every other registry is read as the game reads it. Both steps
 * are lambdas, named by their descriptors - the method's own shape - rather than by the numbers the compiler gave
 * them. The Fabric module does the same in vanilla's copy of the class.
 */
@Mixin(ResourceManagerRegistryLoadTask.class)
public abstract class MixinLootArrivals {

    /** The loot tables' text as offered, by id, once this task has been handed its folder; null for any other. */
    @Unique
    private volatile Map<Identifier, JsonElement> trmt$offered;

    @Inject(
        method = "*(Lnet/minecraft/resources/RegistryOps$RegistryInfoLookup;Lnet/minecraft/resources/FileToIdConverter;"
            + "Ljava/util/concurrent/Executor;Ljava/util/Map;)Ljava/util/concurrent/CompletionStage;",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/thread/ParallelMapTransform;schedule("
                + "Ljava/util/Map;Ljava/util/function/BiFunction;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
    private void trmt$addOurFinds(RegistryOps.RegistryInfoLookup context, FileToIdConverter lister,
        java.util.concurrent.Executor executor, Map<Identifier, Resource> files,
        CallbackInfoReturnable<java.util.concurrent.CompletionStage<?>> callback, @Local RegistryOps<JsonElement> ops) {
        if (!ExtraLoot.isLootTables(lister)) return;
        // A table read as NeoForge's conditional codec reads it, as 26.2's scan did: what it answers is offered, and one
        // whose conditions are not met (empty) or that does not read (an error) is left to the game's own parse.
        final Codec<Optional<JsonElement>> conditional = net.neoforged.neoforge.common.conditions.ConditionalOps
            .createConditionalCodec(net.minecraft.util.ExtraCodecs.JSON);
        trmt$offered = ExtraLoot.offerFolder(lister, files, text -> conditional.parse(ops, text)
            .result()
            .flatMap(kept -> kept)
            .orElse(null));
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
