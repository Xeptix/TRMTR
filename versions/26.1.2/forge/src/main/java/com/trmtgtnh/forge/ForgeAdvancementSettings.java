package com.trmtgtnh.forge;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;

import net.minecraftforge.common.crafting.conditions.ICondition;

import com.trmtgtnh.item.ModAchievements;

/**
 * {@code trmtgtnh:settings} on Forge: an advancement file loads only with every switch it names on (0.9.228, spec BP30,
 * BP33; Xep: "Match 1.7.10"). Forge reads one condition from a data file's {@code forge:condition} - read in Forge
 * 64.1.5's, 65.1.5's and 66.0.13's {@code ForgeHooks.readConditional}, which every advancement passes through in
 * {@code SimpleJsonResourceReloadListener} and which skips a file "as its conditions were not met" - and finds it by
 * its {@code type} in {@code ForgeRegistries.CONDITION_SERIALIZERS}, where {@link ForgeRegistration} puts this one.
 * The rule is the shared {@link ModAchievements#loads}.
 */
public record ForgeAdvancementSettings(List<String> settings) implements ICondition {

    public static final MapCodec<ForgeAdvancementSettings> CODEC = Codec.STRING.listOf()
        .fieldOf("settings")
        .xmap(ForgeAdvancementSettings::new, ForgeAdvancementSettings::settings);

    @Override
    public boolean test(ICondition.IContext context, DynamicOps<?> ops) {
        return ModAchievements.loads(settings);
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }
}
