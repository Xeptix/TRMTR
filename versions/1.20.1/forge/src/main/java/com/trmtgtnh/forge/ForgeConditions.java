package com.trmtgtnh.forge;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.common.crafting.conditions.IConditionSerializer;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.item.ModAchievements;

/**
 * The {@code trmtgtnh:settings} condition on Forge (0.9.228, BP30, BP33; Xep: "Match 1.7.10"): an advancement file
 * loads only with every switch it names on, as 1.7.10 registers an achievement only for what the pack can reach. Forge
 * 47's {@code ServerAdvancementManager} reads a file's {@code conditions} array through
 * {@code ConditionalAdvancement.processConditional} and skips the file when one fails ("Skipping loading advancement {}
 * as its conditions were not met", read in its bytecode); each condition is found by its {@code type} among the
 * serializers {@code CraftingHelper.register} holds. The rule is {@link ModAchievements#allOn}, which every loader's
 * condition asks.
 */
public final class ForgeConditions {

    static final ResourceLocation ID = new ResourceLocation(Trmt.MODID, ModAchievements.CONDITION);

    private ForgeConditions() {}

    /** Registered as the mod is constructed, before any data is read. */
    public static void register() {
        CraftingHelper.register(Serializer.INSTANCE);
    }

    /** The switches a file names, all of which must be on. */
    static final class Settings implements ICondition {

        final List<String> switches;

        Settings(List<String> switches) {
            this.switches = switches;
        }

        @Override
        public ResourceLocation getID() {
            return ID;
        }

        @Override
        public boolean test(ICondition.IContext context) {
            return ModAchievements.allOn(switches);
        }
    }

    static final class Serializer implements IConditionSerializer<Settings> {

        static final Serializer INSTANCE = new Serializer();

        @Override
        public void write(JsonObject json, Settings value) {
            JsonArray list = new JsonArray();
            for (String each : value.switches) list.add(each);
            json.add("settings", list);
        }

        @Override
        public Settings read(JsonObject json) {
            List<String> switches = new ArrayList<String>();
            JsonArray list = net.minecraft.util.GsonHelper.getAsJsonArray(json, "settings");
            for (JsonElement each : list) switches.add(each.getAsString());
            return new Settings(switches);
        }

        @Override
        public ResourceLocation getID() {
            return ID;
        }
    }
}
