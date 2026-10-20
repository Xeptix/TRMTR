package com.trmtgtnh.forge.mixin;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonElement;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.StrictJsonParser;
import net.minecraft.world.level.storage.loot.LootDataType;

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
 * <strong>From 1.21.2 there is no map of text.</strong> {@code scanDirectory} reads each file and parses it into a table
 * in the same step (server/packs/resources/SimpleJsonResourceReloadListener.java, and Forge 64's patch of it, which
 * reads a file's conditions in between), so the map the 1.21.1 edition was handed no longer exists at any moment. The
 * same moment is made instead: at the head of that method, for the loot tables' folder alone, every table's text is
 * read into a map and offered whole, as 1.21.1 offered it - every table known, the finds added in place, announced
 * once - and each file's text is then handed to the parse from that map in place of a second reading. A file that
 * does not read is left to the game's own loop, which reads it again and says why as it always has.
 */
@Mixin(SimpleJsonResourceReloadListener.class)
public abstract class MixinLootArrivals {

    /** The loot tables' text as offered, by id, while their folder is being parsed on this thread. */
    @Unique
    private static final ThreadLocal<Map<Identifier, JsonElement>> trmt$offered = new ThreadLocal<Map<Identifier, JsonElement>>();

    @Inject(
        method = "scanDirectory(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/resources/FileToIdConverter;"
            + "Lcom/mojang/serialization/DynamicOps;Lcom/mojang/serialization/Codec;Ljava/util/Map;)V",
        at = @At("HEAD"))
    private static void trmt$addOurFinds(ResourceManager resources, FileToIdConverter lister, DynamicOps<JsonElement> ops,
        Codec<?> codec, Map<Identifier, ?> result, CallbackInfo callback) {
        trmt$offered.remove();
        if (lister == null || !net.minecraft.core.registries.Registries.elementsDirPath(LootDataType.TABLE.registryKey())
            .equals(lister.prefix())) return;
        Map<Identifier, JsonElement> parsed = new HashMap<Identifier, JsonElement>();
        for (Map.Entry<Identifier, Resource> entry : lister.listMatchingResources(resources)
            .entrySet()) {
            try (Reader reader = entry.getValue()
                .openAsReader()) {
                parsed.put(lister.fileToId(entry.getKey()), StrictJsonParser.parse(reader));
            } catch (Exception unread) {
                // The game's own loop reads it again and reports it.
            }
        }
        ExtraLoot.offer(parsed);
        trmt$offered.set(parsed);
    }

    /** Each loot table's text as it was offered, in place of reading the file a second time. */
    @WrapOperation(
        method = "scanDirectory(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/resources/FileToIdConverter;"
            + "Lcom/mojang/serialization/DynamicOps;Lcom/mojang/serialization/Codec;Ljava/util/Map;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/StrictJsonParser;parse(Ljava/io/Reader;)Lcom/google/gson/JsonElement;"))
    private static JsonElement trmt$offeredText(Reader reader, Operation<JsonElement> original,
        @Local(ordinal = 1) Identifier id) {
        JsonElement read = original.call(reader);
        Map<Identifier, JsonElement> offered = trmt$offered.get();
        JsonElement ours = offered == null ? null : offered.get(id);
        return ours != null ? ours : read;
    }

    @Inject(
        method = "scanDirectory(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/resources/FileToIdConverter;"
            + "Lcom/mojang/serialization/DynamicOps;Lcom/mojang/serialization/Codec;Ljava/util/Map;)V",
        at = @At("RETURN"))
    private static void trmt$doneWithTheFolder(ResourceManager resources, FileToIdConverter lister,
        DynamicOps<JsonElement> ops, Codec<?> codec, Map<Identifier, ?> result, CallbackInfo callback) {
        trmt$offered.remove();
    }
}
