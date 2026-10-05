package com.trmtgtnh.fabric.mixin;

import java.util.Map;

import com.google.gson.JsonElement;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.storage.loot.LootTables;

import com.trmtgtnh.item.ExtraLoot;

/**
 * Adds this mod's finds to every loot table, while each is still the text a pack wrote it as.
 *
 * <p>
 * See {@link ExtraLoot} for what this does and why neither loader's own loot hook was used; the
 * Forge module has the same injection and its copy carries the two details worth stating - the head,
 * because the body of this method is what reads the map, and the full descriptor, because a bridge
 * of the same name sits beside the real method.
 *
 * <p>
 * Required rather than optional. A chest that never holds one of this mod's tools is not a missing
 * flourish - it is the whole of how a player who has not read a wiki finds out the mod is installed.
 */
@Mixin(LootTables.class)
public abstract class MixinLootArrivals {

    @Inject(
        method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;"
            + "Lnet/minecraft/util/profiling/ProfilerFiller;)V",
        at = @At("HEAD"))
    private void trmt$addOurFinds(Map<ResourceLocation, JsonElement> parsed, ResourceManager resources,
        ProfilerFiller profiler, CallbackInfo callback) {
        ExtraLoot.offer(parsed);
    }
}
