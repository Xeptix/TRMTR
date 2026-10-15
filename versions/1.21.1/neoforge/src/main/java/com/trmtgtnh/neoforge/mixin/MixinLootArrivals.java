package com.trmtgtnh.neoforge.mixin;

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
 */
@Mixin(SimpleJsonResourceReloadListener.class)
public abstract class MixinLootArrivals {

    @Inject(method = "scanDirectory", at = @At("TAIL"))
    private static void trmt$addOurFinds(ResourceManager resources, String directory, com.google.gson.Gson gson,
        Map<ResourceLocation, JsonElement> parsed, CallbackInfo callback) {
        if (net.minecraft.core.registries.Registries.elementsDirPath(LootDataType.TABLE.registryKey())
            .equals(directory)) ExtraLoot.offer(parsed);
    }
}
