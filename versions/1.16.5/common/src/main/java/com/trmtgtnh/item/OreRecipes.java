package com.trmtgtnh.item;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;

/**
 * Builds this mod's recipes the way it has always described them, out of what this version has.
 *
 * <p>
 * Both older editions write their recipes with Forge's {@code ShapedOreRecipe} and
 * {@code ShapelessOreRecipe}, which take a mixture of stacks and ore-dictionary names and sort out
 * which is which. There is no ore dictionary here and no Forge recipe class either - recipes are
 * vanilla's, and what was an ore name is a tag. So this is the shim that lets {@code ModRecipes}
 * carry across almost unchanged: the same mixed arguments go in, and a vanilla recipe comes out.
 *
 * <h2>Why a function rather than a recipe</h2>
 *
 * <p>
 * A recipe carries its own id at this version, and the id is final. The older editions build the
 * recipe first and name it afterwards - {@code add(recipe, name)} - which cannot be done here.
 * Rather than restructure every one of the twenty call sites, each builder answers a
 * {@code Function} from id to recipe, and {@code add} is the thing that knows the name. The call
 * sites read exactly as they did.
 *
 * <h2>What an ore name becomes</h2>
 *
 * <p>
 * A tag, through {@link OreNames}, which holds the whole of that translation and the folder table
 * it needs. {@code ItemTags.bind} rather than a lookup, because these are built while the game is
 * still starting and the tag it names may not be populated yet; binding is a promise to ask later,
 * which is what a recipe wants anyway - a pack that adds something to a tag should not have to
 * restart for the recipe to accept it.
 */
public final class OreRecipes {

    private OreRecipes() {}

    /**
     * A shaped recipe, from the same arguments Forge's own takes: rows, then key-and-what pairs.
     *
     * <p>
     * The width and height are read off the rows rather than passed, which is also what Forge's
     * does, and the key characters are matched against the rows exactly as the JSON loader would.
     */
    /**
     * What a shaped recipe is finally made by, once its grid has been laid out.
     *
     * <p>
     * Here because two of this mod's recipes are not plain shaped recipes: the chunk tamper's and
     * the Wayfarer's each care which grade of tool is in the middle, which a shape cannot express.
     * Both older editions say that by subclassing Forge's recipe; the subclasses carry, and this is
     * how they are reached without every caller having to lay the grid out itself.
     */
    public interface Shape {

        Recipe<?> make(ResourceLocation id, String group, int wide, int high, NonNullList<Ingredient> parts,
            ItemStack result);
    }

    /** A chunk tamper's recipe, which also asks what the tamper in its middle is made of. */
    public static Function<ResourceLocation, Recipe<?>> gradedTamper(String group, ItemStack result,
        final String grade, Object... patternAndKeys) {
        return shaped(
            group,
            result,
            (id, band, wide, high, parts, made) -> new RecipeGradedTamper(id, band, wide, high, parts, made, grade),
            patternAndKeys);
    }

    /** The Wayfarer's, which asks the same of the chunk tamper in its middle. */
    public static Function<ResourceLocation, Recipe<?>> wayfarer(String group, ItemStack result,
        final String grade, Object... patternAndKeys) {
        return shaped(
            group,
            result,
            (id, band, wide, high, parts, made) -> new RecipeWayfarer(id, band, wide, high, parts, made, grade),
            patternAndKeys);
    }

    public static Function<ResourceLocation, Recipe<?>> shaped(String group, ItemStack result,
        Object... patternAndKeys) {
        return shaped(group, result, ShapedRecipe::new, patternAndKeys);
    }

    public static Function<ResourceLocation, Recipe<?>> shaped(String group, ItemStack result, final Shape make,
        Object... patternAndKeys) {
        // The rows run until the first key, and a key is the only Character in the list. An ore name
        // is a String too, but it can only ever appear *after* a key, so nothing ambiguous reaches
        // this loop.
        List<String> rows = new ArrayList<String>();
        int at = 0;
        while (at < patternAndKeys.length && !(patternAndKeys[at] instanceof Character)) {
            rows.add(String.valueOf(patternAndKeys[at]));
            at++;
        }

        int width = 0;
        for (String row : rows) {
            width = Math.max(width, row.length());
        }
        final int wide = width;
        final int high = rows.size();

        // The keys, as they are written: 'S', HANDLE, 'M', sole.
        final java.util.Map<Character, Object> keys = new java.util.HashMap<Character, Object>();
        for (int k = at; k + 1 < patternAndKeys.length; k += 2) {
            if (!(patternAndKeys[k] instanceof Character)) continue;
            keys.put((Character) patternAndKeys[k], patternAndKeys[k + 1]);
        }

        final List<String> laidOut = rows;
        final ItemStack made = result;
        final String band = group == null ? "" : group;
        return id -> {
            NonNullList<Ingredient> parts = NonNullList.withSize(wide * high, Ingredient.EMPTY);
            for (int row = 0; row < high; row++) {
                String line = laidOut.get(row);
                for (int column = 0; column < wide; column++) {
                    char key = column < line.length() ? line.charAt(column) : ' ';
                    if (key == ' ') continue;
                    parts.set(row * wide + column, ingredient(keys.get(Character.valueOf(key))));
                }
            }
            return make.make(id, band, wide, high, parts, made);
        };
    }

    /**
     * A draught's recipe: shapeless, and the bottle in it has to hold the right brew.
     *
     * <p>
     * The brew cannot be said by the ingredient at this version; see {@link RecipeDraught} for why
     * it is the recipe that asks.
     */
    public static Function<ResourceLocation, Recipe<?>> draught(String group, ItemStack result,
        final net.minecraft.world.item.alchemy.Potion brew, Object... what) {
        final Function<ResourceLocation, Recipe<?>> plain = shapeless(group, result, what);
        return id -> {
            Recipe<?> made = plain.apply(id);
            ShapelessRecipe loose = (ShapelessRecipe) made;
            return new RecipeDraught(id, loose.getGroup(), loose.getResultItem(), loose.getIngredients(), brew);
        };
    }

    /** A shapeless recipe: everything that goes in, in no particular order. */
    public static Function<ResourceLocation, Recipe<?>> shapeless(String group, ItemStack result,
        Object... what) {
        final Object[] parts = what.clone();
        final ItemStack made = result;
        final String band = group == null ? "" : group;
        return id -> {
            NonNullList<Ingredient> ingredients = NonNullList.create();
            for (Object one : parts) {
                ingredients.add(ingredient(one));
            }
            return new ShapelessRecipe(id, band, made, ingredients);
        };
    }

    /**
     * One ingredient, from whichever of the three things the older editions pass.
     *
     * <p>
     * A {@code String} is an ore name and becomes a tag; a stack or an item is itself. An argument
     * that is none of those is an empty ingredient rather than a thrown exception, because a recipe
     * that quietly cannot be made is better than a mod that will not start - and the recipe sweep
     * already refuses to register anything whose materials the pack has not got.
     */
    private static Ingredient ingredient(Object one) {
        if (one instanceof String) {
            ResourceLocation named = com.trmtgtnh.util.OreNames.tagFor((String) one);
            if (named == null) return Ingredient.EMPTY;
            // Nothing under it: an empty ingredient rather than an ingredient over an empty tag.
            // The difference is not cosmetic - Forge answers an empty tag ingredient by putting a
            // barrier in its place and asking the serialiser for the tag's name to label it, which
            // throws for any tag the serialiser does not hold, and that killed the whole build part
            // way through. The sweep above refuses to register anything whose materials are missing,
            // so what reaches here with nothing under it is a name no recipe will use anyway.
            if (!com.trmtgtnh.util.OreNames.present((String) one)) return Ingredient.EMPTY;
            // The loader's own named handle rather than a tag fetched from the collection of the
            // moment: an ingredient outlives the moment it was built, and a fetched tag does not.
            // See OreNames.LazyTags.
            return Ingredient.of(com.trmtgtnh.util.OreNames.tagOf((String) one));
        }
        if (one instanceof ItemStack) return Ingredient.of((ItemStack) one);
        if (one instanceof Item) return Ingredient.of((Item) one);
        if (one instanceof net.minecraft.world.level.ItemLike) {
            return Ingredient.of((net.minecraft.world.level.ItemLike) one);
        }
        return Ingredient.EMPTY;
    }
}
