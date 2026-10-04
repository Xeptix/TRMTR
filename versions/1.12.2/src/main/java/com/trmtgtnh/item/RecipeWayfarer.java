package com.trmtgtnh.item;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.oredict.ShapedOreRecipe;

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
public class RecipeWayfarer extends ShapedOreRecipe {

    private final String grade;

    public RecipeWayfarer(ResourceLocation group, ItemStack result, String grade, Object... recipe) {
        super(group, result, recipe);
        this.grade = grade;
    }

    /** Which grade of the chunk tamper in its middle this recipe is about. */
    public String grade() {
        return grade;
    }

    @Override
    public boolean matches(InventoryCrafting grid, World world) {
        if (!super.matches(grid, world)) return false;

        for (int slot = 0; slot < grid.getSizeInventory(); slot++) {
            ItemStack held = grid.getStackInSlot(slot);
            if (held.isEmpty() || !(held.getItem() instanceof ItemChunkTamper)) continue;
            // A Wayfarer is a chunk tamper by inheritance; it is not the ingredient this asks for.
            if (held.getItem() instanceof ItemMagicTamper) continue;
            return grade.equals(ItemChunkTamper.gradeOf(held).key);
        }
        // The shape matched but there is no chunk tamper in it, which this shape does not allow.
        return false;
    }
}
