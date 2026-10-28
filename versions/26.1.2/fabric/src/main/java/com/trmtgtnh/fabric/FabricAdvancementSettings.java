package com.trmtgtnh.fabric;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import net.fabricmc.fabric.api.resource.conditions.v1.ResourceCondition;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditionType;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.item.ModAchievements;

/**
 * {@code trmtgtnh:settings} on Fabric: an advancement file loads only with every switch it names on (0.9.228, spec
 * BP30, BP33; Xep: "Match 1.7.10"). Fabric API's resource conditions read a data file's
 * {@code fabric:load_conditions} in every JSON reload listener - read in fabric-resource-conditions-api-v1 6.1.0 and
 * 6.2.0, whose {@code SimpleJsonResourceReloadListenerMixin} drops a file whose conditions fail - and find a condition
 * by its {@code condition} name among the types registered with {@link ResourceConditions#register}, which
 * {@link #register} does as the mod starts. The rule is the shared {@link ModAchievements#loads}.
 */
public record FabricAdvancementSettings(List<String> settings) implements ResourceCondition {

    public static final MapCodec<FabricAdvancementSettings> CODEC = Codec.STRING.listOf()
        .fieldOf("settings")
        .xmap(FabricAdvancementSettings::new, FabricAdvancementSettings::settings);

    public static final ResourceConditionType<FabricAdvancementSettings> TYPE = ResourceConditionType
        .create(Identifier.fromNamespaceAndPath(Trmt.MODID, ModAchievements.SETTINGS_CONDITION), CODEC);

    /** Registers the condition; once, as the mod starts, before any data is read. */
    public static void register() {
        ResourceConditions.register(TYPE);
    }

    @Override
    public ResourceConditionType<?> getType() {
        return TYPE;
    }

    @Override
    public boolean test(RegistryOps.RegistryInfoLookup registryInfo) {
        return ModAchievements.loads(settings);
    }
}
