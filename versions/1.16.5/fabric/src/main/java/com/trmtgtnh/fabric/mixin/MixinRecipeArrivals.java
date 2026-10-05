package com.trmtgtnh.fabric.mixin;

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
 * See {@link ExtraRecipes}, which holds what this does and why it cannot be JSON; this is the one
 * moment the map is reachable. The Forge module has the same hook and its copy carries the two
 * details that are easy to get wrong - the descriptor, because a bridge of the same name sits beside
 * the real method, and the tail, because the field is assigned last.
 *
 * <p>
 * Required rather than optional, unlike the painter's hooks: a game with no recipes for this mod's
 * tools is not a game missing two seconds of polish, and refusing to start says so where a quietly
 * empty crafting book would not.
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
