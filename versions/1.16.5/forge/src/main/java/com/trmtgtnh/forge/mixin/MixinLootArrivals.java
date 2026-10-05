package com.trmtgtnh.forge.mixin;

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
 * <strong>Why here rather than at Forge's own loot event is {@link ExtraLoot}'s note, and the short
 * of it is the other loader:</strong> a table's pools and their entries are private and final in
 * vanilla, and the methods that edit them are Forge's patches, so the event has no counterpart to
 * carry to. A moment earlier, the table is a {@code JsonObject} and anything a loot table can say
 * can be said in it.
 *
 * <p>
 * At the head, because the map is read into tables by the body of this method and an edit made
 * afterwards would be an edit to nothing. Nothing is cancelled and nothing is replaced - what is
 * handed over is the pack's own text with entries appended to a pool it already had, which is why a
 * datapack that rewrites one of these tables keeps its own version and simply gets the finds added
 * to that.
 *
 * <p>
 * The descriptor is spelled out for the reason {@code MixinRecipeArrivals} spells its own out: this
 * method is generic in its supertype, so the compiler leaves a bridge of the same name beside it,
 * and by name alone the injection lands in both and the finds are added twice.
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
