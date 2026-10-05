package com.trmtgtnh.item;

import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.Level;

/**
 * A shapeless recipe that also cares what is in the bottle.
 *
 * <p>
 * <strong>This is what {@code PotionIngredient} became, and it is a replacement rather than a
 * carry.</strong> The 1.7.10 edition names an awkward potion by its damage value, which every
 * recipe compares, so there is nothing to say there. 1.12.2 moved what a potion <em>is</em> into
 * NBT, where an ordinary ingredient does not look - so that edition subclasses Forge's
 * {@code IngredientNBT}, and the draught's recipe stops accepting a water bottle.
 *
 * <p>
 * That door is shut here. Forge still has the ingredient, as {@code NBTIngredient}; Fabric has no
 * counterpart, and {@code Ingredient}'s constructor is private, so there is nothing to subclass on
 * the other side. A seam for one ingredient would be a seam carrying a single line.
 *
 * <p>
 * So the question moves up a level and is asked by the recipe instead - which is exactly what
 * {@link RecipeGradedTamper} and {@link RecipeWayfarer} already do for the tool in their middle,
 * and needs nothing either loader has to supply. The bottle is an ordinary ingredient, matched by
 * item as any other is, and this adds the one question that ingredient could not: is the thing in
 * it the brew this recipe asked for.
 */
public class RecipeDraught extends ShapelessRecipe {

    private final Potion brew;

    public RecipeDraught(ResourceLocation id, String group, ItemStack result, NonNullList<Ingredient> parts,
        Potion brew) {
        super(id, group, result, parts);
        this.brew = brew;
    }

    /** Which brew the bottle in this recipe has to hold. */
    public Potion brew() {
        return brew;
    }

    @Override
    public boolean matches(CraftingContainer grid, Level world) {
        if (!super.matches(grid, world)) return false;
        if (brew == null) return true;

        // The first bottle in the grid, which the shapeless match above has already proved is there.
        // Asked of the stack rather than of the ingredient, because the ingredient cannot be asked:
        // see the class note.
        for (int slot = 0; slot < grid.getContainerSize(); slot++) {
            ItemStack held = grid.getItem(slot);
            if (held.isEmpty() || !(held.getItem() instanceof net.minecraft.world.item.PotionItem)) continue;
            return PotionUtils.getPotion(held) == brew;
        }
        // The ingredients matched and there is no bottle among them, which this recipe's own list
        // does not allow. Refusing is the honest answer, as it is in the two recipes above.
        return false;
    }
}
