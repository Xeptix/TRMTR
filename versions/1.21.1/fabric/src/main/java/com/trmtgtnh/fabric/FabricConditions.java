package com.trmtgtnh.fabric;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.fabricmc.fabric.api.resource.conditions.v1.ResourceCondition;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditionType;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.item.ModAchievements;

/**
 * The {@code trmtgtnh:settings} condition on Fabric (0.9.228, BP30, BP33; Xep: "Match 1.7.10"): an advancement file
 * loads only with every switch it names on, as 1.7.10 registers an achievement only for what the pack can reach.
 * Fabric API's resource conditions read a file's {@code fabric:load_conditions} array - for advancements in
 * {@code ServerAdvancementLoaderMixin} at this version - and drop the file when one fails; each is dispatched by its
 * {@code condition} to the {@link ResourceConditionType} registered under that id. The rule is
 * {@link ModAchievements#allOn}, which every loader's condition asks.
 */
public final class FabricConditions {

    static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Trmt.MODID, ModAchievements.CONDITION);

    /** The switches a file names, all of which must be on. */
    record Settings(List<String> settings) implements ResourceCondition {

        static final MapCodec<Settings> CODEC = RecordCodecBuilder.mapCodec(
            in -> in.group(
                Codec.STRING.listOf()
                    .fieldOf("settings")
                    .forGetter(Settings::settings))
                .apply(in, Settings::new));

        static final ResourceConditionType<Settings> TYPE = ResourceConditionType.create(ID, CODEC);

        @Override
        public ResourceConditionType<?> getType() {
            return TYPE;
        }

        @Override
        public boolean test(HolderLookup.Provider registries) {
            return ModAchievements.allOn(settings);
        }
    }

    private FabricConditions() {}

    public static void register() {
        ResourceConditions.register(Settings.TYPE);
    }
}
