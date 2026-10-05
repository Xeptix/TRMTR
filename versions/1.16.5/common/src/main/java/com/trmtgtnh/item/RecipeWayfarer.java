package com.trmtgtnh.item;

import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
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
    public RecipeWayfarer(ResourceLocation id, String group, int wide, int high,
        net.minecraft.core.NonNullList<net.minecraft.world.item.crafting.Ingredient> parts, ItemStack result,
        String grade) {
        super(id, group, wide, high, parts, result);
        this.grade = grade;
    }

    /** Which grade of the chunk tamper in its middle this recipe is about. */
    public String grade() {
        return grade;
    }

    @Override
    public boolean matches(CraftingContainer grid, Level world) {
        if (!super.matches(grid, world)) return false;

        for (int slot = 0; slot < grid.getContainerSize(); slot++) {
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
