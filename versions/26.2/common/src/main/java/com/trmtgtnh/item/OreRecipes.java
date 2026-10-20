package com.trmtgtnh.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.display.SlotDisplay;

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
 * it needs. The registry's own named set for the tag rather than a copy of what is in it, because a
 * named set is rebound in place when the tags are read again; that is what a recipe wants - a pack
 * that adds something to a tag should not have to restart for the recipe to accept it.
 *
 * <h2>What 1.21.2 to 26.1 changed underneath</h2>
 *
 * <p>
 * A recipe is three parts now: whether it announces itself (always, as every 1.21.1 recipe of this mod did), its
 * book page and group, and what it is made of. An ingredient is a set of items and can be neither empty nor a
 * stack, so a grid's empty square is an absent ingredient rather than an empty one, and a stack handed in as an
 * ingredient is matched by its item - which is all 1.21.1's ingredient ever compared. A recipe's result is a
 * template from 26.1, made from the stack {@code ModRecipes} builds, components and all. And what the recipe book
 * draws is the recipe's own display from 1.21.2: a stack handed in that carries more than its item - a tamper of one
 * grade, an awkward potion - is drawn as that stack, where 1.21.1's book drew it from the ingredient.
 */
public final class OreRecipes {

    private OreRecipes() {}

    /**
     * What a shaped recipe is finally made by, once its grid has been laid out.
     *
     * <p>
     * Here because two of this mod's recipes are not plain shaped recipes: the chunk tamper's and
     * the Wayfarer's each care which grade of tool is in the middle, which a shape cannot express.
     * Both older editions say that by subclassing Forge's recipe; the subclasses carry, and this is
     * how they are reached without every caller having to lay the grid out itself.
     *
     * <p>
     * From 1.21.2 the grid is a list of optional ingredients, an absent one an empty square, and what each square is
     * drawn as in the recipe book comes beside it; from 26.1 the result is a template rather than a stack.
     */
    public interface Shape {

        Recipe<?> make(Identifier id, String group, int wide, int high, List<Optional<Ingredient>> parts,
            List<SlotDisplay> shown, ItemStackTemplate result);
    }

    /** A chunk tamper's recipe, which also asks what the tamper in its middle is made of. */
    public static Function<Identifier, Recipe<?>> gradedTamper(String group, ItemStack result,
        final String grade, Object... patternAndKeys) {
        return shaped(
            group,
            result,
            (id, band, wide, high, parts, shown, made) -> new RecipeGradedTamper(id, band, wide, high, parts, shown,
                made, grade),
            patternAndKeys);
    }

    /** The Wayfarer's, which asks the same of the chunk tamper in its middle. */
    public static Function<Identifier, Recipe<?>> wayfarer(String group, ItemStack result,
        final String grade, Object... patternAndKeys) {
        return shaped(
            group,
            result,
            (id, band, wide, high, parts, shown, made) -> new RecipeWayfarer(id, band, wide, high, parts, shown, made,
                grade),
            patternAndKeys);
    }

    public static Function<Identifier, Recipe<?>> shaped(String group, ItemStack result,
        Object... patternAndKeys) {
        // From 1.19.3 a recipe says which page of the recipe book it is on.
        // From 1.20.2 a recipe is not told its id - the game keeps it beside the recipe - and the grid is a pattern.
        // From 1.21.2 the page and group are one part and the notification another; a plain recipe draws its own
        // ingredients, so what each square shows is not needed here.
        return shaped(group, result, (id, band, wide, high, parts, shown, made) -> new ShapedRecipe(
            new Recipe.CommonInfo(true), new CraftingRecipe.CraftingBookInfo(CraftingBookCategory.MISC, band),
            new ShapedRecipePattern(wide, high, parts, Optional.empty()), made), patternAndKeys);
    }

    public static Function<Identifier, Recipe<?>> shaped(String group, ItemStack result, final Shape make,
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
        final ItemStackTemplate made = template(result);
        final String band = group == null ? "" : group;
        return id -> {
            List<Optional<Ingredient>> parts = new ArrayList<Optional<Ingredient>>(wide * high);
            List<SlotDisplay> shown = new ArrayList<SlotDisplay>(wide * high);
            for (int row = 0; row < high; row++) {
                String line = laidOut.get(row);
                for (int column = 0; column < wide; column++) {
                    char key = column < line.length() ? line.charAt(column) : ' ';
                    if (key == ' ') {
                        parts.add(Optional.<Ingredient>empty());
                        shown.add(SlotDisplay.Empty.INSTANCE);
                        continue;
                    }
                    Object what = keys.get(Character.valueOf(key));
                    // A key that names nothing the pack has is an empty square, as 1.21.1's empty ingredient was in a
                    // grid; the sweep in ModRecipes registers no recipe whose materials are missing, so none reaches
                    // a player.
                    Optional<Ingredient> part = ingredient(what);
                    parts.add(part);
                    shown.add(display(what, part));
                }
            }
            return make.make(id, band, wide, high, parts, shown, made);
        };
    }

    /**
     * A draught's recipe: shapeless, and the bottle in it has to hold the right brew.
     *
     * <p>
     * The brew cannot be said by the ingredient at this version; see {@link RecipeDraught} for why
     * it is the recipe that asks.
     */
    public static Function<Identifier, Recipe<?>> draught(String group, ItemStack result,
        final net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion> brew, Object... what) {
        final Object[] parts = what.clone();
        final ItemStackTemplate made = template(result);
        // Never null, as shapeless() makes it: a recipe goes to every joining client, and a null group fails as
        // the packet is written - the first fix here passed the group as given and a dedicated server kicked
        // the first player to join, which single player, writing no packets, cannot show.
        final String band = group == null ? "" : group;
        return id -> {
            List<Ingredient> ingredients = ingredients(parts);
            if (ingredients == null) return null;
            // The group as given, which Fabric kept on the client only in 1.21.1 - and this runs as the server
            // builds its recipes. Found by tools/server_safe.py on 2026-10-07. Built from the parts here rather than
            // read back off a shapeless recipe, whose result and ingredients are private from 1.21.2.
            return new RecipeDraught(id, band, made, ingredients, displays(parts), brew);
        };
    }

    /** A shapeless recipe: everything that goes in, in no particular order. */
    public static Function<Identifier, Recipe<?>> shapeless(String group, ItemStack result,
        Object... what) {
        final Object[] parts = what.clone();
        final ItemStackTemplate made = template(result);
        final String band = group == null ? "" : group;
        return id -> {
            List<Ingredient> ingredients = ingredients(parts);
            // No recipe where a part names nothing the pack has. 1.21.1's empty ingredient left such a recipe
            // impossible to complete; from 1.21.2 an ingredient cannot be empty, and the game drops a recipe that
            // cannot be placed anyway. The sweep in ModRecipes registers none of these, so it is never reached.
            if (ingredients == null) return null;
            return new ShapelessRecipe(new Recipe.CommonInfo(true),
                new CraftingRecipe.CraftingBookInfo(CraftingBookCategory.MISC, band), made, ingredients);
        };
    }

    /** Every part as an ingredient, or null when any part names nothing. */
    private static List<Ingredient> ingredients(Object[] parts) {
        List<Ingredient> ingredients = new ArrayList<Ingredient>(parts.length);
        for (Object one : parts) {
            Optional<Ingredient> part = ingredient(one);
            if (part.isEmpty()) return null;
            ingredients.add(part.get());
        }
        return ingredients;
    }

    /** What the recipe book draws for each part, in order. */
    private static List<SlotDisplay> displays(Object[] parts) {
        List<SlotDisplay> shown = new ArrayList<SlotDisplay>(parts.length);
        for (Object one : parts) {
            shown.add(display(one, ingredient(one)));
        }
        return shown;
    }

    /**
     * What one square is drawn as: a stack that carries more than its item is drawn as itself, as 1.21.1's ingredient
     * drew it, and anything else as its ingredient.
     */
    private static SlotDisplay display(Object one, Optional<Ingredient> part) {
        if (one instanceof ItemStack && !((ItemStack) one).isEmpty()
            && !((ItemStack) one).getComponentsPatch().isEmpty()) {
            return new SlotDisplay.ItemStackSlotDisplay(ItemStackTemplate.fromNonEmptyStack((ItemStack) one));
        }
        return Ingredient.optionalIngredientToDisplay(part);
    }

    /** A recipe's result as the template 26.1 keeps, components and all. */
    private static ItemStackTemplate template(ItemStack result) {
        return ItemStackTemplate.fromNonEmptyStack(result);
    }

    /**
     * One ingredient, from whichever of the three things the older editions pass.
     *
     * <p>
     * A {@code String} is an ore name and becomes a tag; a stack or an item is itself. An argument
     * that is none of those is no ingredient rather than a thrown exception, because a recipe
     * that quietly cannot be made is better than a mod that will not start - and the recipe sweep
     * already refuses to register anything whose materials the pack has not got.
     */
    private static Optional<Ingredient> ingredient(Object one) {
        if (one instanceof String) {
            Identifier named = com.trmtgtnh.util.OreNames.tagFor((String) one);
            if (named == null) return Optional.empty();
            // Nothing under it: no ingredient rather than an ingredient over an empty tag.
            // The difference is not cosmetic - Forge answers an empty tag ingredient by putting a
            // barrier in its place and asking the serialiser for the tag's name to label it, which
            // throws for any tag the serialiser does not hold, and that killed the whole build part
            // way through. The sweep above refuses to register anything whose materials are missing,
            // so what reaches here with nothing under it is a name no recipe will use anyway.
            if (!com.trmtgtnh.util.OreNames.present((String) one)) return Optional.empty();
            // The registry's own named set rather than a copy of the tag of the moment: an ingredient outlives the
            // moment it was built, and the named set is rebound in place. From 1.21.2 an ingredient is made from a set
            // of items rather than from the tag's key. See OreNames.LazyTags.
            Optional<HolderSet.Named<Item>> tag = BuiltInRegistries.ITEM.get(com.trmtgtnh.util.OreNames.tagOf((String) one));
            return tag.map(Ingredient::of);
        }
        // The stack's item, which is all 1.21.1's ingredient compared; an ingredient cannot hold a stack from 1.21.2.
        // The Stream overload, which Fabric's dedicated server has, as it was here before.
        if (one instanceof ItemStack) {
            ItemStack stack = (ItemStack) one;
            if (stack.isEmpty()) return Optional.empty();
            return Optional.of(Ingredient.of(java.util.stream.Stream.of(stack.getItem())));
        }
        if (one instanceof Item) return Optional.of(Ingredient.of((Item) one));
        if (one instanceof net.minecraft.world.level.ItemLike) {
            return Optional.of(Ingredient.of((net.minecraft.world.level.ItemLike) one));
        }
        return Optional.empty();
    }
}
