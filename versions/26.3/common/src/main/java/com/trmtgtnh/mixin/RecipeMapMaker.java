package com.trmtgtnh.mixin;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import com.google.common.collect.Multimap;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * A {@code RecipeMap} made from recipes in hand, as 26.2's {@code RecipeMap.create(Iterable)} made one.
 *
 * <p>
 * From 26.3 recipes are a reloadable registry and {@code RecipeMap.create} takes that registry's lookup
 * (world/item/crafting/RecipeMap, read from 26.3), so there is no way left to make a map of a list of holders - which is
 * what this mod hands the manager when it adds its own recipes ({@code ExtraRecipes}). The map's own constructor, by
 * type and by key, is still the one {@code create} ends in, and this reaches it. The same class on every loader, so it
 * lives here rather than once per loader.
 */
@Mixin(RecipeMap.class)
public interface RecipeMapMaker {

    @Invoker("<init>")
    static RecipeMap trmt$make(Multimap<RecipeType<?>, RecipeHolder<?>> byType,
        Map<ResourceKey<Recipe<?>>, RecipeHolder<?>> byKey) {
        throw new AssertionError("RecipeMapMaker was not applied");
    }
}
