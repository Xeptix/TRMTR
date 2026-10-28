package com.trmtgtnh.neoforge;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.RegisterEvent;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.item.ModAchievements;

/**
 * The {@code trmtgtnh:settings} condition on NeoForge (0.9.228, BP30, BP33; Xep: "Match 1.7.10"): an advancement file
 * loads only with every switch it names on, as 1.7.10 registers an achievement only for what the pack can reach.
 * NeoForge's {@code ServerAdvancementManager} reads a file's {@code neoforge:conditions} list and skips the file when one
 * fails ("Skipping loading advancement {} as its conditions were not met"); each is dispatched by its {@code type} to
 * the codec registered in {@code NeoForgeRegistries.CONDITION_SERIALIZERS}. The rule is {@link ModAchievements#allOn},
 * which every loader's condition asks.
 */
public final class NeoForgeConditions {

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
        public boolean test(ICondition.IContext context) {
            return ModAchievements.allOn(settings);
        }

        @Override
        public MapCodec<? extends ICondition> codec() {
            return CODEC;
        }
    }

    private NeoForgeConditions() {}

    /** Put into the condition registry as it is built: see {@code NeoForgeRegistration.conditions}. */
    static void register(RegisterEvent event) {
        event.register(
            NeoForgeRegistries.Keys.CONDITION_CODECS,
            (RegisterEvent.RegisterHelper<MapCodec<? extends ICondition>> helper) -> helper.register(ID, Settings.CODEC));
    }
}
