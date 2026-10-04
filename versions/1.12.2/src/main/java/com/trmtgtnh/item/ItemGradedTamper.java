package com.trmtgtnh.item;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.text.translation.I18n;

/**
 * The hand tamper, in whatever the pack is made of.
 *
 * <p>
 * One registration covering every material, for exactly the reason the chunk tamper gives: a
 * registered item per material would be a registered item per material, and GregTech alone offers
 * some nine hundred of them. The grade rides in the stack's own data, where adding or dropping a
 * material never touches a save's id map - and on 1.12.2 there is no id map to touch, only a set of
 * registry names, which the same argument covers for the same reason.
 *
 * <p>
 * This exists because the chunk tamper is built around a tamper of its own metal, and that
 * sentence has no meaning unless a tamper can be made of bronze. Four fixed per-material items
 * came before it and were kept registered for a while so that no save could lose one; they are
 * gone as of 0.9.194, having been uncraftable and unlisted the whole time they lingered, and
 * four ids in every save's map is a great deal to carry for an item nobody could obtain.
 */
public class ItemGradedTamper extends ItemTamper {

    public ItemGradedTamper() {
        super();
        setNoRepair();
    }

    // ------------------------------------------------------------------
    // Everything a tier used to decide
    // ------------------------------------------------------------------

    @Override
    public int reachOf(ItemStack stack) {
        return ItemChunkTamper.gradeOf(stack)
            .reach();
    }

    @Override
    public String repairOre(ItemStack stack) {
        return ItemChunkTamper.gradeOf(stack).ore;
    }

    @Override
    public int getMaxDamage(ItemStack stack) {
        return ItemChunkTamper.gradeOf(stack)
            .uses();
    }

    /**
     * Anvil repair, in the grade's own material.
     *
     * <p>
     * The two-by-two grid repair is shut off in the constructor instead, and for the reason the
     * chunk tamper shuts it off: that one path asks the <em>item</em> for its maximum and builds
     * its result without copying NBT, so two of these in a crafting square would have come out
     * as one with no grade at all.
     */
    @Override
    public boolean getIsRepairable(ItemStack tool, ItemStack material) {
        return ItemChunkTamper.gradeOf(tool)
            .matches(material);
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        return I18n.translateToLocalFormatted(
            "item.trmtgtnh.tamper.graded.name",
            ItemChunkTamper.gradeOf(stack)
                .displayName());
    }

    // ------------------------------------------------------------------
    // Presentation
    // ------------------------------------------------------------------

    /**
     * One of each grade the pack can actually supply.
     *
     * <p>
     * Which picture each is drawn with is not decided here, as it is in the other edition, but in
     * {@code client.model.TamperModels} - see that class for why the client side owns it now.
     */
    @Override
    public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> list) {
        if (!isInCreativeTab(tab)) return;
        for (TamperGrade grade : TamperGrade.available()) {
            ItemStack stack = new ItemStack(this, 1, 0);
            ItemChunkTamper.setGrade(stack, grade);
            list.add(stack);
        }
    }
}
