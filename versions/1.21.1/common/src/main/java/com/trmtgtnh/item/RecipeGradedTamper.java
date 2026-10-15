package com.trmtgtnh.item;

import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
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
    public RecipeGradedTamper(ResourceLocation id, String group, int wide, int high,
        net.minecraft.core.NonNullList<net.minecraft.world.item.crafting.Ingredient> parts, ItemStack result,
        String grade) {
        // From 1.20.2 a recipe is not told its id - the game keeps it beside the recipe - and its grid is a pattern
        // of its own; the id is still made where it always was, by ModRecipes.
        super(group, net.minecraft.world.item.crafting.CraftingBookCategory.EQUIPMENT, new net.minecraft.world.item.crafting.ShapedRecipePattern(wide, high, parts, java.util.Optional.empty()), result);
        this.grade = grade;
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
