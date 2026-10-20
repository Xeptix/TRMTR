package com.trmtgtnh.fabric.mixin;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;

import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
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
 * <strong>From 1.21.2 the scan reads each file straight into a table</strong>: {@code scanDirectory} takes the
 * registry's codec and parses every file as it reads it, so there is no moment any more when the whole folder is text
 * and no table has been read - the moment 1.21.1 waited for at the scan's end. So for the loot tables' registry alone
 * this does the scan in 1.21.1's two steps itself, at the head of the same public, static method the game calls for
 * it (ReloadableServerRegistries): the folder read into text by the game's own scan, with the plain JSON codec; that
 * text handed to ExtraLoot exactly as before; then each table read from it with the codec the game passed, kept or
 * refused as the game's scan keeps or refuses one. Every other registry's scan, and every other overload, is the
 * game's own.
 */
@Mixin(SimpleJsonResourceReloadListener.class)
public abstract class MixinLootArrivals {

    @Shadow
    @Final
    private static Logger LOGGER;

    /** Set while this scan reads the folder as text, so that inner call is the game's own. */
    @Unique
    private static final ThreadLocal<Boolean> trmt$asText = new ThreadLocal<Boolean>();

    @Inject(
        method = "scanDirectory(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/resources/ResourceKey;"
            + "Lcom/mojang/serialization/DynamicOps;Lcom/mojang/serialization/Codec;Ljava/util/Map;)V",
        at = @At("HEAD"),
        cancellable = true)
    private static <T> void trmt$addOurFinds(ResourceManager resources,
        ResourceKey<? extends net.minecraft.core.Registry<T>> registry,
        DynamicOps<JsonElement> ops, Codec<T> codec, Map<Identifier, T> result, CallbackInfo callback) {
        // From 1.20.5 loot tables are a reloadable registry, read from the folder the registry's own name gives -
        // loot_table, singular - through this same scan.
        if (!LootDataType.TABLE.registryKey()
            .equals(registry) || trmt$asText.get() != null) return;
        callback.cancel();

        Map<Identifier, JsonElement> parsed = new HashMap<Identifier, JsonElement>();
        trmt$asText.set(Boolean.TRUE);
        try {
            @SuppressWarnings({ "unchecked", "rawtypes" })
            ResourceKey<? extends net.minecraft.core.Registry<JsonElement>> asText = (ResourceKey) registry;
            SimpleJsonResourceReloadListener
                .scanDirectory(resources, asText, ops, net.minecraft.util.ExtraCodecs.JSON, parsed);
        } finally {
            trmt$asText.remove();
        }

        ExtraLoot.offer(parsed);

        FileToIdConverter files = FileToIdConverter.registry(registry);
        for (Map.Entry<Identifier, JsonElement> each : parsed.entrySet()) {
            Identifier id = each.getKey();
            codec.parse(ops, each.getValue())
                .ifSuccess(table -> result.putIfAbsent(id, table))
                .ifError(
                    error -> LOGGER.error("Couldn't parse data file '{}' from '{}': {}", id, files.idToFile(id), error));
        }
    }
}
