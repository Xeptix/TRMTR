package com.trmtgtnh.forge.mixin;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;

import com.trmtgtnh.item.ExtraRecipes;

/**
 * Puts this mod's recipes in front of the game, once the manager has read the files.
 *
 * <p>
 * <strong>Why a mixin at all is {@code ExtraRecipes}' own note:</strong> this mod's recipes are
 * decided by what the pack contains, so they cannot be shipped as JSON, and there is no registry to
 * push them into at this version and no event for adding one. The manager parses a datapack into a
 * map and keeps it; this is the moment after that, and it hands the map back with ours in it.
 *
 * <p>
 * The body is the same on both loaders and lives twice because a mixin that names a method or a
 * field is written into a refmap in one loader's names, which the other refuses - the arrangement
 * {@code MixinBlockArrivals} is already under. The whole of the decision is in {@code ExtraRecipes};
 * each copy is three lines.
 *
 * <h2>The two things that are stated rather than guessed</h2>
 *
 * <p>
 * The method is named with its full descriptor rather than by name alone, because this one is
 * generic in its supertype and the compiler leaves a bridge of the same name beside it. By name
 * alone the injection lands in both, and the recipes would be added twice - the second pass harmless
 * only because a claimed id is left alone, which is luck rather than design.
 *
 * <p>
 * At the tail rather than the return of the loop, so the field this reads has already been assigned
 * and the count the manager logs is its own. Ours is logged separately, by {@code ExtraRecipes}, so
 * the two numbers in the log can be told apart.
 */
@Mixin(RecipeManager.class)
public abstract class MixinRecipeArrivals implements ExtraRecipes.Rebuildable {

    @Shadow
    private Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> recipes;

    @Inject(
        method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;"
            + "Lnet/minecraft/util/profiling/ProfilerFiller;)V",
        at = @At("TAIL"))
    private void trmt$addOurRecipes(CallbackInfo callback) {
        this.recipes = ExtraRecipes.added(this.recipes);
    }

    /**
     * Asked again once the tags are bound, which is after the reload this injection sits in.
     *
     * <p>
     * See {@code ExtraRecipes.Rebuildable} for why: a recipe here is built from what the pack
     * contains, that is read from tags, and at the tail of apply there are none.
     */
    @Override
    public void trmt$rebuildTrmtRecipes() {
        this.recipes = ExtraRecipes.again(this.recipes);
    }
}
