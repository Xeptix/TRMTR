package com.trmtgtnh.item;

import net.minecraft.core.NonNullList;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.alchemy.PotionContents;
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

    private final Holder<Potion> brew;

    public RecipeDraught(Identifier id, String group, net.minecraft.world.item.ItemStackTemplate result,
        java.util.List<Ingredient> parts, java.util.List<net.minecraft.world.item.crafting.display.SlotDisplay> shown,
        Holder<Potion> brew) {
        // From 1.20.2 a recipe is not told its id; see RecipeGradedTamper. From 1.21.2 the notification and the book
        // page are parts of their own, and from 26.1 the result is a template. The same recipe, on the same page.
        super(new net.minecraft.world.item.crafting.Recipe.CommonInfo(true),
            new net.minecraft.world.item.crafting.CraftingRecipe.CraftingBookInfo(
                net.minecraft.world.item.crafting.CraftingBookCategory.MISC, group),
            result, parts);
        this.brew = brew;
        this.shown = java.util.List.copyOf(shown);
        this.made = result;
    }

    /** What each part is drawn as in the recipe book: the awkward potion as itself. */
    private final java.util.List<net.minecraft.world.item.crafting.display.SlotDisplay> shown;

    private final net.minecraft.world.item.ItemStackTemplate made;

    /**
     * The recipe as the book draws it, with the bottle drawn as the brew it has to hold.
     *
     * <p>
     * From 1.21.2 the book draws a recipe's own display, and the bottle's ingredient is the plain potion item, which
     * says no brew; 1.21.1's book drew the awkward potion the ingredient was made from. So the display names it.
     */
    @Override
    public java.util.List<net.minecraft.world.item.crafting.display.RecipeDisplay> display() {
        return java.util.List.of(
            new net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay(
                shown,
                new net.minecraft.world.item.crafting.display.SlotDisplay.ItemStackSlotDisplay(made),
                new net.minecraft.world.item.crafting.display.SlotDisplay.ItemSlotDisplay(
                    net.minecraft.world.item.Items.CRAFTING_TABLE)));
    }

    /** Which brew the bottle in this recipe has to hold. */
    public Holder<Potion> brew() {
        return brew;
    }

    // From 1.21 a recipe is asked about a CraftingInput - the grid's items, trimmed to what is in it - rather than the
    // container itself; every slot is still looked at, which is all this asks.
    @Override
    public boolean matches(net.minecraft.world.item.crafting.CraftingInput grid, Level world) {
        if (!super.matches(grid, world)) return false;
        if (brew == null) return true;

        // The first bottle in the grid, which the shapeless match above has already proved is there.
        // Asked of the stack rather than of the ingredient, because the ingredient cannot be asked:
        // see the class note.
        for (int slot = 0; slot < grid.size(); slot++) {
            ItemStack held = grid.getItem(slot);
            if (held.isEmpty() || !(held.getItem() instanceof net.minecraft.world.item.PotionItem)) continue;
            // 1.20.5 keeps a bottle's brew in its potion contents, naming the potion by its registry entry.
            return held.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY)
                .is(brew);
        }
        // The ingredients matched and there is no bottle among them, which this recipe's own list
        // does not allow. Refusing is the honest answer, as it is in the two recipes above.
        return false;
    }
}
