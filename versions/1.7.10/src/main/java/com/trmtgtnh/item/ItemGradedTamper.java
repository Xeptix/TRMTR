package com.trmtgtnh.item;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.StatCollector;

import com.trmtgtnh.Trmt;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The hand tamper, in whatever the pack is made of.
 *
 * <p>
 * One registration covering every material, for exactly the reason {@link ItemChunkTamper}
 * gives: a registered item per material would be a registered item per material, and GregTech
 * alone offers some nine hundred of them. The grade rides in the stack's own data, where adding
 * or dropping a material never touches a save's id map.
 *
 * <p>
 * This exists because the chunk tamper is built around a tamper of its own metal, and that
 * sentence has no meaning unless a tamper can be made of bronze. Four fixed per-material items
 * came before it and were kept registered for a while so that no save could lose one; they are
 * gone as of 0.9.194, having been uncraftable and unlisted the whole time they lingered, and
 * four ids in every save's map is a great deal to carry for an item nobody could obtain.
 */
public class ItemGradedTamper extends ItemTamper {

    @SideOnly(Side.CLIENT)
    private Map<String, IIcon> icons;

    @SideOnly(Side.CLIENT)
    private IIcon fallbackIcon;

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
        return StatCollector.translateToLocalFormatted(
            "item.trmtgtnh.tamper.graded.name",
            ItemChunkTamper.gradeOf(stack)
                .displayName());
    }

    // ------------------------------------------------------------------
    // Presentation
    // ------------------------------------------------------------------

    @SideOnly(Side.CLIENT)
    @Override
    public void registerIcons(IIconRegister register) {
        icons = new HashMap<String, IIcon>();
        fallbackIcon = register.registerIcon(Trmt.MODID + ":tamper");
        // The drawn set rather than the configured one: this runs while the atlas is stitched,
        // and a name with no file behind it becomes a chequerboard and a log line per sprite.
        for (String key : TamperArt.drawn()) {
            icons.put(key, register.registerIcon(Trmt.MODID + ":" + TamperArt.iconFor("tamper", key)));
        }
        itemIcon = fallbackIcon;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public IIcon getIcon(ItemStack stack, int pass) {
        return iconFor(ItemChunkTamper.gradeOf(stack).key);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public IIcon getIconIndex(ItemStack stack) {
        return iconFor(ItemChunkTamper.gradeOf(stack).key);
    }

    @SideOnly(Side.CLIENT)
    private IIcon iconFor(String key) {
        if (icons == null) return fallbackIcon;
        IIcon found = icons.get(key);
        return found == null ? fallbackIcon : found;
    }

    /** One of each grade the pack can actually supply. */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        for (TamperGrade grade : TamperGrade.available()) {
            ItemStack stack = new ItemStack(item, 1, 0);
            ItemChunkTamper.setGrade(stack, grade);
            list.add(stack);
        }
    }
}
