package com.trmtgtnh.neoforge.mixin;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;

import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
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
 * <strong>From 1.21.2 the scan reads each file straight into a table</strong>, so there is no moment any more when
 * the whole folder is text and no table has been read. On NeoForge 26.1.2.122 the loot tables are read by
 * {@code ReloadableServerRegistries} through {@code scanDirectoryWithOptionalValues}, with each loot type's conditional
 * codec (a table whose NeoForge conditions are not met reads as empty and is skipped). So for the loot tables' registry
 * alone this does that scan in 1.21.1's two steps itself, at the head of the same public, static method: the folder
 * read into text by the game's own scan with the plain JSON codec (NeoForge's patched scan still weighs each file's
 * conditions and drops the ones not met, as it then would have); that text handed to ExtraLoot exactly as before; then
 * each table read from it with the codec the game passed, kept or refused as the game's scan keeps or refuses one.
 * Every other registry's scan, and every other overload, is the game's own. The Fabric module does the same at
 * vanilla's {@code scanDirectory}, which is what Fabric calls there.
 */
@Mixin(SimpleJsonResourceReloadListener.class)
public abstract class MixinLootArrivals {

    @Shadow
    @Final
    private static Logger LOGGER;

    @Inject(
        method = "scanDirectoryWithOptionalValues(Lnet/minecraft/server/packs/resources/ResourceManager;"
            + "Lnet/minecraft/resources/ResourceKey;Lcom/mojang/serialization/DynamicOps;Lcom/mojang/serialization/Codec;"
            + "Ljava/util/Map;)V",
        at = @At("HEAD"),
        cancellable = true)
    private static <T> void trmt$addOurFinds(ResourceManager resources,
        ResourceKey<? extends net.minecraft.core.Registry<T>> registry,
        DynamicOps<JsonElement> ops, Codec<Optional<T>> codec, Map<Identifier, Optional<T>> result,
        CallbackInfo callback) {
        // From 1.20.5 loot tables are a reloadable registry, read from the folder the registry's own name gives -
        // loot_table, singular - which is what 1.21.1's test of the folder's name asked.
        if (!LootDataType.TABLE.registryKey()
            .equals(registry)) return;
        callback.cancel();

        FileToIdConverter files = FileToIdConverter.registry(registry);
        Map<Identifier, JsonElement> parsed = new HashMap<Identifier, JsonElement>();
        SimpleJsonResourceReloadListener.scanDirectory(resources, files, ops, net.minecraft.util.ExtraCodecs.JSON, parsed);

        ExtraLoot.offer(parsed);

        for (Map.Entry<Identifier, JsonElement> each : parsed.entrySet()) {
            Identifier id = each.getKey();
            codec.parse(ops, each.getValue())
                .ifSuccess(table -> result.putIfAbsent(id, table))
                .ifError(
                    error -> LOGGER.error("Couldn't parse data file '{}' from '{}': {}", id, files.idToFile(id), error));
        }
    }
}
