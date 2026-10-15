package com.trmtgtnh.fabric.mixin;

import java.util.Map;

import com.google.gson.JsonElement;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.resources.ResourceLocation;
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
 */
@Mixin(SimpleJsonResourceReloadListener.class)
public abstract class MixinLootArrivals {

    @Inject(method = "scanDirectory", at = @At("TAIL"))
    private static void trmt$addOurFinds(ResourceManager resources, String directory, com.google.gson.Gson gson,
        Map<ResourceLocation, JsonElement> parsed, CallbackInfo callback) {
        // From 1.20.5 loot tables are a reloadable registry, read from the folder the registry's own name gives -
        // loot_table, singular - through this same scan.
        if (net.minecraft.core.registries.Registries.elementsDirPath(LootDataType.TABLE.registryKey())
            .equals(directory)) ExtraLoot.offer(parsed);
    }
}
