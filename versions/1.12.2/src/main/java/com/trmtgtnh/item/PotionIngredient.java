package com.trmtgtnh.item;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionType;
import net.minecraft.potion.PotionUtils;
import net.minecraftforge.common.crafting.IngredientNBT;

/**
 * One particular brewed potion as a recipe ingredient.
 *
 * <p>
 * The other edition names an awkward potion by its damage value - sixteen, which is what vanilla's
 * bitfield means by one - and a shaped or shapeless recipe compares damage, so that is the whole of
 * it there. 1.12.2 moved what a potion <em>is</em> out of the damage value and into NBT, where an
 * ordinary ingredient does not look: written the same way here, the draught's recipe would have
 * accepted a water bottle, a potion of healing, or anything else in a glass bottle.
 *
 * <p>
 * Forge has exactly the ingredient this needs and keeps its constructor for subclasses, which is
 * what this is. The name is the point of it: a reader of the recipe should see that the bottle is
 * being asked what is in it.
 */
public class PotionIngredient extends IngredientNBT {

    private PotionIngredient(ItemStack stack) {
        super(stack);
    }

    /** A bottle of exactly this brew. */
    public static PotionIngredient of(PotionType type) {
        return new PotionIngredient(PotionUtils.addPotionToItemStack(new ItemStack(Items.POTIONITEM), type));
    }
}
