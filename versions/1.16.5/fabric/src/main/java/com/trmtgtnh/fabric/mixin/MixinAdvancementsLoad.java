package com.trmtgtnh.fabric.mixin;

import java.util.Iterator;
import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.google.gson.JsonElement;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.item.ModAchievements;

/**
 * This mod's advancements load only when the switches each one names are on (0.9.228, BP30, BP33; Xep: "Match
 * 1.7.10"; see {@link ModAchievements#loads}).
 *
 * <p>
 * Forge reads each file's {@code conditions}; Fabric API at this version has no load conditions at all (no
 * resource-conditions module on this edition's classpath), so here the files are taken out of what the advancement
 * manager was handed, at the head of {@code ServerAdvancementManager.apply(Map, ResourceManager, ProfilerFiller)},
 * before it builds a single one - on a server's start and on every {@code /reload}. The map is the one the reload
 * listener's preparation built for this load, a fresh one each time. Only this mod's own files are touched.
 */
@Mixin(ServerAdvancementManager.class)
public abstract class MixinAdvancementsLoad {

    @Inject(
        method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
        at = @At("HEAD"))
    private void trmt$leaveOutWhatIsSwitchedOff(Map<ResourceLocation, JsonElement> files, ResourceManager resources,
        ProfilerFiller profiler, CallbackInfo ci) {
        if (files == null) return;
        int left = 0;
        for (Iterator<Map.Entry<ResourceLocation, JsonElement>> each = files.entrySet()
            .iterator(); each.hasNext();) {
            ResourceLocation id = each.next()
                .getKey();
            if (id == null || !Trmt.MODID.equals(id.getNamespace())) continue;
            if (ModAchievements.loads(id.getPath())) continue;
            each.remove();
            left++;
        }
        if (left > 0) Trmt.LOG.info("Left out {} of this mod's advancements the settings switch off", Integer.valueOf(left));
    }
}
