package com.trmtgtnh.item;

import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;

/**
 * A shaped recipe that also cares which material the tamper in the middle is made of.
 *
 * <p>
 * Necessary rather than fussy. Every grade of tamper is one registered item carrying its
 * material in NBT, and a plain shaped recipe compares its ingredients with
 * {@code OreDictionary.itemMatches}, which does not look at NBT at all. So without this, all
 * eleven chunk tamper recipes would match any tamper whatever - drop an iron one into a ring of
 * neutronium plate and the first recipe in the list would answer, which for the player is a
 * silent theft and for the pack is eleven recipes that are really one.
 *
 * <p>
 * Only the centre is checked here. The ring is ore-dictionary plate and has no NBT worth
 * asking about, and the shape itself has already been matched by the time this runs.
 */
public class RecipeGradedTamper extends net.minecraft.world.item.crafting.ShapedRecipe {

    private final String grade;

    /**
     * Laid out rather than patterned, which is this version rather than a redesign.
     *
     * <p>
     * Forge's shaped recipe took the rows and the keys and worked the grid out; vanilla's takes the
     * grid already built, and its id with it, because a recipe is a datapack object here and has to
     * be able to come from a file. {@code OreRecipes.gradedTamper} builds both for the callers, so the two
     * places in ModRecipes that make one of these read as they always did.
     */
    public RecipeGradedTamper(Identifier id, String group, int wide, int high,
        java.util.List<java.util.Optional<net.minecraft.world.item.crafting.Ingredient>> parts,
        java.util.List<net.minecraft.world.item.crafting.display.SlotDisplay> shown,
        net.minecraft.world.item.ItemStackTemplate result, String grade) {
        // From 1.20.2 a recipe is not told its id - the game keeps it beside the recipe - and its grid is a pattern
        // of its own; the id is still made where it always was, by ModRecipes. From 1.21.2 the notification and the
        // book page are parts of their own and an empty square is an absent ingredient; from 26.1 the result is a
        // template. The same recipe, on the same page.
        super(new net.minecraft.world.item.crafting.Recipe.CommonInfo(true),
            new net.minecraft.world.item.crafting.CraftingRecipe.CraftingBookInfo(
                net.minecraft.world.item.crafting.CraftingBookCategory.EQUIPMENT, group),
            new net.minecraft.world.item.crafting.ShapedRecipePattern(wide, high, parts, java.util.Optional.empty()),
            result);
        this.grade = grade;
        this.wide = wide;
        this.high = high;
        this.shown = java.util.List.copyOf(shown);
        this.made = result;
    }

    private final int wide;

    private final int high;

    /** What each square is drawn as in the recipe book: the tamper of this grade in the middle. */
    private final java.util.List<net.minecraft.world.item.crafting.display.SlotDisplay> shown;

    private final net.minecraft.world.item.ItemStackTemplate made;

    /**
     * The recipe as the book draws it, with the middle drawn as the tamper of this grade.
     *
     * <p>
     * From 1.21.2 the book draws a recipe's own display rather than its ingredients, and an ingredient is a set of
     * items that cannot say a grade; 1.21.1's book drew the graded tamper from the ingredient. So the display names it
     * instead, and the bronze recipe shows a bronze tamper as it did.
     */
    @Override
    public java.util.List<net.minecraft.world.item.crafting.display.RecipeDisplay> display() {
        return java.util.List.of(
            new net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay(
                wide,
                high,
                shown,
                new net.minecraft.world.item.crafting.display.SlotDisplay.ItemStackSlotDisplay(made),
                new net.minecraft.world.item.crafting.display.SlotDisplay.ItemSlotDisplay(
                    net.minecraft.world.item.Items.CRAFTING_TABLE)));
    }

    /** Which grade of the tamper in its middle this recipe is about. */
    public String grade() {
        return grade;
    }

    // From 1.21 a recipe is asked about a CraftingInput - the grid's items, trimmed to what is in it - rather than the
    // container itself; every slot is still looked at, which is all this asks.
    @Override
    public boolean matches(net.minecraft.world.item.crafting.CraftingInput grid, Level world) {
        if (!super.matches(grid, world)) return false;

        for (int slot = 0; slot < grid.size(); slot++) {
            ItemStack held = grid.getItem(slot);
            if (held.isEmpty() || !(held.getItem() instanceof ItemGradedTamper)) continue;
            return grade.equals(ItemChunkTamper.gradeOf(held).key);
        }
        // The shape matched but there is no tamper in it, which this recipe's shape does not
        // allow. Refusing is the honest answer.
        return false;
    }
}
