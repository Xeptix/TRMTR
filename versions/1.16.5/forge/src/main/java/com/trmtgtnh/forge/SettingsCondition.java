package com.trmtgtnh.forge;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.common.crafting.conditions.IConditionSerializer;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.item.ModAchievements;

/**
 * {@code trmtgtnh:settings}: a load condition true when every switch it names is on (0.9.228, BP30, BP33; Xep: "Match
 * 1.7.10"). Each of this mod's advancement files names its own in {@code conditions}, which Forge reads before it
 * loads the file - {@code ConditionalAdvancement.processConditional}, called from {@code Advancement.Builder.fromJson},
 * read in this version's jar - and skips a file whose conditions fail ("Skipping loading advancement {} as it's
 * conditions were not met"). So with {@code general.achievements} off there is no tab, and with it on nothing shows
 * that the pack cannot reach, as in the 1.7.10 edition. The rule is {@link ModAchievements#allOn}, the one Fabric's
 * filter asks through {@link ModAchievements#loads}.
 *
 * <p>
 * Registered with {@code CraftingHelper.register} in the recipe serializers' registry event (ForgeRegistration), where
 * Forge registers its own.
 */
public final class SettingsCondition implements ICondition {

    public static final ResourceLocation ID = new ResourceLocation(Trmt.MODID, ModAchievements.CONDITION);

    private final String[] switches;

    SettingsCondition(String[] switches) {
        this.switches = switches;
    }

    @Override
    public ResourceLocation getID() {
        return ID;
    }

    @Override
    public boolean test() {
        return ModAchievements.allOn(switches);
    }

    /** Reads and writes the condition as the files carry it: {@code {"type": ..., "settings": ["achievements", ...]}}. */
    public static final class Serializer implements IConditionSerializer<SettingsCondition> {

        public static final Serializer INSTANCE = new Serializer();

        @Override
        public void write(JsonObject json, SettingsCondition value) {
            JsonArray named = new JsonArray();
            for (String each : value.switches) named.add(each);
            json.add(ModAchievements.CONDITION, named);
        }

        @Override
        public SettingsCondition read(JsonObject json) {
            JsonArray named = json.has(ModAchievements.CONDITION) && json.get(ModAchievements.CONDITION)
                .isJsonArray() ? json.getAsJsonArray(ModAchievements.CONDITION) : new JsonArray();
            String[] switches = new String[named.size()];
            int at = 0;
            for (JsonElement each : named) switches[at++] = each.getAsString();
            return new SettingsCondition(switches);
        }

        @Override
        public ResourceLocation getID() {
            return ID;
        }
    }
}
