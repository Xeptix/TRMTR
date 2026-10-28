package com.trmtgtnh.fabric;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions;
import net.minecraft.resources.ResourceLocation;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.item.ModAchievements;

/**
 * The {@code trmtgtnh:settings} condition on Fabric (0.9.228, BP30, BP33; Xep: "Match 1.7.10"): an advancement file
 * loads only with every switch it names on, as 1.7.10 registers an achievement only for what the pack can reach.
 * Fabric API's resource conditions read a file's {@code fabric:load_conditions} array in every JSON data loader -
 * advancements among them, {@code JsonDataLoaderMixin} - and drop the file when one fails; each is found by its
 * {@code condition} among the predicates {@code ResourceConditions.register} holds. The rule is
 * {@link ModAchievements#allOn}, which every loader's condition asks.
 */
public final class FabricConditions {

    static final ResourceLocation ID = new ResourceLocation(Trmt.MODID, ModAchievements.CONDITION);

    private FabricConditions() {}

    public static void register() {
        ResourceConditions.register(ID, FabricConditions::loads);
    }

    static boolean loads(JsonObject condition) {
        List<String> switches = new ArrayList<String>();
        for (JsonElement each : net.minecraft.util.GsonHelper.getAsJsonArray(condition, "settings")) {
            switches.add(each.getAsString());
        }
        return ModAchievements.allOn(switches);
    }
}
