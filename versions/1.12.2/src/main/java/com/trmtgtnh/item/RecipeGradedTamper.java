package com.trmtgtnh.item;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.oredict.ShapedOreRecipe;

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
public class RecipeGradedTamper extends ShapedOreRecipe {

    private final String grade;

    public RecipeGradedTamper(ResourceLocation group, ItemStack result, String grade, Object... recipe) {
        super(group, result, recipe);
        this.grade = grade;
    }

    /** Which grade of the tamper in its middle this recipe is about. */
    public String grade() {
        return grade;
    }

    @Override
    public boolean matches(InventoryCrafting grid, World world) {
        if (!super.matches(grid, world)) return false;

        for (int slot = 0; slot < grid.getSizeInventory(); slot++) {
            ItemStack held = grid.getStackInSlot(slot);
            if (held.isEmpty() || !(held.getItem() instanceof ItemGradedTamper)) continue;
            return grade.equals(ItemChunkTamper.gradeOf(held).key);
        }
        // The shape matched but there is no tamper in it, which this recipe's shape does not
        // allow. Refusing is the honest answer.
        return false;
    }
}
