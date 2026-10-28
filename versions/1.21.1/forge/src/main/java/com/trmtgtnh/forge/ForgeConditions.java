package com.trmtgtnh.forge;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.item.ModAchievements;

/**
 * The {@code trmtgtnh:settings} condition on Forge (0.9.228, BP30, BP33; Xep: "Match 1.7.10"): an advancement file
 * loads only with every switch it names on, as 1.7.10 registers an achievement only for what the pack can reach. Forge
 * 52's {@code ServerAdvancementManager} asks {@code ForgeHooks.readConditionalAdvancement}, which reads a file's single
 * {@code forge:condition} object and skips the file when it fails ("Skipping loading advancement {} as its conditions
 * were not met", read in its bytecode); the condition is dispatched by its {@code type} to the codec registered in
 * {@code ForgeRegistries.CONDITION_SERIALIZERS}. The rule is {@link ModAchievements#allOn}, which every loader's
 * condition asks.
 */
public final class ForgeConditions {

    static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Trmt.MODID, ModAchievements.CONDITION);

    /** The switches a file names, all of which must be on. */
    record Settings(List<String> settings) implements ICondition {

        static final MapCodec<Settings> CODEC = RecordCodecBuilder.mapCodec(
            in -> in.group(
                Codec.STRING.listOf()
                    .fieldOf("settings")
                    .forGetter(Settings::settings))
                .apply(in, Settings::new));

        @Override
        public boolean test(ICondition.IContext context, DynamicOps<?> ops) {
            return ModAchievements.allOn(settings);
        }

        @Override
        public MapCodec<? extends ICondition> codec() {
            return CODEC;
        }
    }

    private ForgeConditions() {}

    /** Put into the condition registry as it is built: see {@code ForgeRegistration.conditions}. */
    static void register(RegisterEvent event) {
        event.register(
            ForgeRegistries.Keys.CONDITION_SERIALIZERS,
            (RegisterEvent.RegisterHelper<MapCodec<? extends ICondition>> helper) -> helper.register(ID, Settings.CODEC));
    }
}
