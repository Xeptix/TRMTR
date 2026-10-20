package com.trmtgtnh.item;

import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;

/**
 * The Wayfarer recipe, which cares which grade of chunk tamper sits in the middle.
 *
 * <p>
 * The Wayfarer is the top of the ladder, so it is built from the top of the ladder: a netherite
 * chunk tamper where the enhancements are on and one can be made, a diamond one where one can, and
 * past both whichever chunk tamper that can be made lasts longest - {@link WayfarerCore} decides,
 * from the chunk tampers that actually have a recipe. A plain shaped recipe
 * compares ingredients with {@code OreDictionary.itemMatches}, which ignores the NBT the grade
 * lives in, so the same override {@link RecipeGradedTamper} needs is needed here - except that
 * the middle is a <em>chunk</em> tamper rather than a plain one, and the Wayfarer itself is a
 * chunk tamper by inheritance and must not be allowed to satisfy its own recipe.
 */
public class RecipeWayfarer extends net.minecraft.world.item.crafting.ShapedRecipe {

    private final String grade;

    /**
     * Laid out rather than patterned, which is this version rather than a redesign.
     *
     * <p>
     * Forge's shaped recipe took the rows and the keys and worked the grid out; vanilla's takes the
     * grid already built, and its id with it, because a recipe is a datapack object here and has to
     * be able to come from a file. {@code OreRecipes.wayfarer} builds both for the callers, so the two
     * places in ModRecipes that make one of these read as they always did.
     */
    public RecipeWayfarer(Identifier id, String group, int wide, int high,
        java.util.List<java.util.Optional<net.minecraft.world.item.crafting.Ingredient>> parts,
        java.util.List<net.minecraft.world.item.crafting.display.SlotDisplay> shown,
        net.minecraft.world.item.ItemStackTemplate result, String grade) {
        // From 1.20.2 a recipe is not told its id, and its grid is a pattern of its own; from 1.21.2 and 26.1 the
        // parts are as RecipeGradedTamper's say.
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

    /** What each square is drawn as in the recipe book: the chunk tamper of this grade in the middle. */
    private final java.util.List<net.minecraft.world.item.crafting.display.SlotDisplay> shown;

    private final net.minecraft.world.item.ItemStackTemplate made;

    /** The recipe as the book draws it, the middle drawn as the chunk tamper of this grade; see RecipeGradedTamper. */
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

    /** Which grade of the chunk tamper in its middle this recipe is about. */
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
            if (held.isEmpty() || !(held.getItem() instanceof ItemChunkTamper)) continue;
            // A Wayfarer is a chunk tamper by inheritance; it is not the ingredient this asks for.
            if (held.getItem() instanceof ItemMagicTamper) continue;
            return grade.equals(ItemChunkTamper.gradeOf(held).key);
        }
        // The shape matched but there is no chunk tamper in it, which this shape does not allow.
        return false;
    }
}
