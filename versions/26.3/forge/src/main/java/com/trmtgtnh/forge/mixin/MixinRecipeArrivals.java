package com.trmtgtnh.forge.mixin;

import java.util.Collection;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;

import com.trmtgtnh.item.ExtraRecipes;

/**
 * Puts this mod's recipes in front of the game, once the manager holds the pack's.
 *
 * <p>
 * See {@link ExtraRecipes}, which holds what this does and why it cannot be JSON; this is the one
 * moment the map is reachable. Each loader module has the same hook, in that loader's own copy of the
 * manager, because a mixin that names a method or a field is written per loader - the arrangement
 * {@code MixinBlockArrivals} is already under. The whole of the decision is in {@code ExtraRecipes}.
 *
 * <p>
 * Required rather than optional, unlike the painter's hooks: a game with no recipes for this mod's
 * tools is not a game missing two seconds of polish, and refusing to start says so where a quietly
 * empty crafting book would not.
 *
 * <p>
 * <strong>From 26.3 the manager reads no files.</strong> Recipes are a reloadable registry, loaded with the loot tables,
 * and a manager is made from that registry once per reload: its constructor takes the world's registries and keeps the
 * recipes in a final map, with the learnable ones beside it (world/item/crafting/RecipeManager, read from 26.3), where
 * 26.2's {@code apply} was handed the files read into a map. So the hook is at the tail of that constructor - the same
 * moment, the manager holding the pack's recipes and the book's pages not yet worked out - and the registries it was
 * made with, which 26.2 kept in a field and 26.3 does not, are kept here for the rebuild. Both fields are final, so they
 * are set as {@code @Mutable} shadows, the learnable ones worked out again as the constructor works them out.
 */
@Mixin(RecipeManager.class)
public abstract class MixinRecipeArrivals implements ExtraRecipes.Rebuildable, ExtraRecipes.Replaceable {

    // What the manager holds: one map, made in its constructor from the recipe registry (26.3).
    @Shadow
    @Final
    @Mutable
    private RecipeMap recipes;

    // The recipes a player can be given, worked out from that map in the constructor (26.3).
    @Shadow
    @Final
    @Mutable
    private Collection<RecipeHolder<?>> learnableRecipes;

    // The world's registries the manager was made with: an unlock book's enchantment is looked up in them.
    @Unique
    private HolderLookup.Provider trmt$registries;

    @Inject(method = "<init>(Lnet/minecraft/core/HolderLookup$Provider;)V", at = @At("TAIL"))
    private void trmt$addOurRecipes(HolderLookup.Provider registries, CallbackInfo callback) {
        trmt$registries = registries;
        // From 1.20.2 the manager keeps its recipes in holders; from 1.21.2 they are set all at once through
        // trmt$replaceRecipes below, which stands in for 1.21.1's replaceRecipes.
        ExtraRecipes.added((RecipeManager) (Object) this, registries);
    }

    /**
     * Asked again once the tags are bound, which is after the reload this injection sits in.
     *
     * <p>
     * See {@code ExtraRecipes.Rebuildable} for why: a recipe here is built from what the pack
     * contains, that is read from tags, and at the tail of the constructor there are none.
     */
    @Override
    public void trmt$rebuildTrmtRecipes() {
        ExtraRecipes.again((RecipeManager) (Object) this, trmt$registries);
    }

    /**
     * What 1.21.1's {@code replaceRecipes} did: the manager holds exactly these, found by type and by id.
     *
     * <p>
     * 1.21.2 removed that method and keeps the recipes as one {@code RecipeMap}; from 26.3 that map is made from the
     * registry, so it is made here from the holders as 26.2's {@code RecipeMap.create} made it
     * ({@code ExtraRecipes.mapOf}), and the learnable ones from it as the constructor does. See
     * {@code ExtraRecipes.Replaceable}, which asks for this, and which works the recipe book's pages out again where a
     * change comes after the reload.
     */
    @Override
    public void trmt$replaceRecipes(Collection<RecipeHolder<?>> all) {
        recipes = ExtraRecipes.mapOf(all);
        learnableRecipes = ExtraRecipes.learnable(recipes);
    }
}
