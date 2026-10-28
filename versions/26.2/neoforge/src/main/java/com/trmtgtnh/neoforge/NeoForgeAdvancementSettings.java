package com.trmtgtnh.neoforge;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import net.neoforged.neoforge.common.conditions.ICondition;

import com.trmtgtnh.item.ModAchievements;

/**
 * {@code trmtgtnh:settings} on NeoForge: an advancement file loads only with every switch it names on (0.9.228, spec
 * BP30, BP33; Xep: "Match 1.7.10"). NeoForge reads a data file's {@code neoforge:conditions} in the reload listener
 * every advancement is loaded through - read in NeoForge 26.1.2.122's, 26.2.0.88's and 26.3.0.94's
 * {@code SimpleJsonResourceReloadListener}, which skips a file "as its conditions were not met" - and finds a condition
 * by its {@code type} in {@code NeoForgeRegistries.CONDITION_SERIALIZERS}, where {@link NeoForgeRegistration} puts
 * this one. The rule is the shared {@link ModAchievements#loads}.
 */
public record NeoForgeAdvancementSettings(List<String> settings) implements ICondition {

    public static final MapCodec<NeoForgeAdvancementSettings> CODEC = Codec.STRING.listOf()
        .fieldOf("settings")
        .xmap(NeoForgeAdvancementSettings::new, NeoForgeAdvancementSettings::settings);

    @Override
    public boolean test(ICondition.IContext context) {
        return ModAchievements.loads(settings);
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }
}
