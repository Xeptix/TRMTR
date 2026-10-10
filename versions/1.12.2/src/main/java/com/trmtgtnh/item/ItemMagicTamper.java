package com.trmtgtnh.item;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.config.TrmtConfig;

/**
 * The end of the tamper line: no material, no wear, and a reach the others cannot buy.
 *
 * <p>
 * A subclass rather than a third implementation, because every gesture it has is a gesture the
 * chunk tamper already performs and a copy of them would be a copy to keep in step. What differs
 * is two answers - it costs nothing and it never wears - and both are questions the parent asks
 * rather than assumptions the parent makes.
 *
 * <p>
 * It never takes damage rather than declaring itself unbreakable. Declaring a maximum of zero
 * would have been the obvious way and is a trap: {@code Item.isDamageable} asks the item, and the
 * packet writer consults it to decide whether a stack's own data is sent to the client at all -
 * so an item claiming no durability would arrive with its settings stripped and no clue why.
 */
public class ItemMagicTamper extends ItemChunkTamper {

    public ItemMagicTamper() {
        super();
        setCreativeTab(CreativeTabs.TOOLS);
        // Glints the way an enchanted thing does, without an enchantment on it. The tool is the
        // end of a ladder and should read as one on the hotbar before its tooltip is opened.
        setMaxStackSize(1);
    }

    /**
     * One picture, not a set of them.
     *
     * <p>
     * The grade was always a statement about material, and this tool is past caring about
     * material - so there is nothing for a per-material picture to say. It keeps the animated
     * arcane rammer, which is the same silhouette as the chunk tamper on purpose: it is the end
     * of that ladder rather than something unrelated.
     */
    @Override
    public boolean gradedIcons() {
        return false;
    }

    /** Costs nothing. Its whole point is that the material stopped being the constraint. */
    @Override
    public boolean isFree(ItemStack stack) {
        return true;
    }

    /**
     * Ctrl + right-click on an entity remembers what kind it was, and opens the screen on that
     * entity, so an operator can say from there whether that kind wears the ground.
     *
     * <p>
     * Only the modifier gesture is taken; a plain right-click is handed back untouched so the
     * Wayfarer never stops you mounting a horse or feeding a cow. Fires on both sides - the
     * client opens the screen, the server writes the authoritative stack - because the entity
     * name is the same on both and the screen needs it the instant it opens.
     *
     * <p>
     * Named the way the engine names a mob when it asks what that mob does to the ground, so the
     * name this writes is the name those settings are keyed by. 1.12.2 has a second name for an
     * entity - its registry one - and using it here would have written {@code minecraft:zombie}
     * into a stack whose settings are listed under {@code Zombie}.
     */
    @Override
    public boolean itemInteractionForEntity(ItemStack stack, EntityPlayer player, EntityLivingBase entity,
        EnumHand hand) {
        if (entity == null || player == null) return false;
        if (!TamperModifiers.held(player)) return false;
        String name = EntityList.getEntityString(entity);
        if (name == null || name.isEmpty()) return true; // consumed the gesture, nothing to name
        setEntity(stack, name);
        if (player.world != null && player.world.isRemote) {
            Trmt.proxy.openTamperScreen(stack);
        }
        return true;
    }

    /** Never wears. Not unbreakable-by-declaration - see the class note for why that matters. */
    @Override
    public boolean wearsOut(ItemStack stack) {
        return false;
    }

    @Override
    public boolean hasEffect(ItemStack stack) {
        return true;
    }

    /** Takes to enchantment better than anything below it, which is the whole idea of it. */
    @Override
    public int getItemEnchantability() {
        return 22;
    }

    /** See {@link ItemTamper#addInformation} for why this one says which side it is on. */
    @SideOnly(Side.CLIENT)
    @SuppressWarnings("unchecked")
    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag advanced) {
        // Substituted before anything is written, so every line below - and every line
        // anybody adds later - stops at the edge of a column rather than the screen.
        tooltip = Tooltips.wrapping(tooltip);
        int edge = reachOf(stack) * 2 + 1;
        tooltip.add(TextFormatting.LIGHT_PURPLE + I18n.translateToLocal("trmtgtnh.magictamper.blurb"));
        tooltip.add(
            TextFormatting.GRAY + I18n.translateToLocal(
                "trmtgtnh.chunktamper.area") + " " + TextFormatting.WHITE + edge + "x" + edge + "x" + edge);
        tooltip.add(
            TextFormatting.GRAY + I18n.translateToLocal("trmtgtnh.chunktamper.perUse")
                + " "
                + TextFormatting.WHITE
                + stepsOf(stack));
        // The mode it is set to, said as the chunk tamper says it: this tool takes the three unlocks best of all and
        // its screen offers every mode, and until 0.9.222 nothing on it said which one was on (spec TA72).
        addModeLines(stack, tooltip);
        // The two gesture lines that stood here are added again inside the expanded block below,
        // so holding Shift printed each of them twice. Dropped rather than removed from the
        // expansion, because the chunk tamper this inherits from shows no gestures at all while
        // collapsed, and the two tools' tooltips should read the same way.
        tooltip.add(TextFormatting.DARK_GRAY + I18n.translateToLocal("trmtgtnh.chunktamper.tip.settings"));
        if (TamperTooltip.expanded()) {
            tooltip.add(TextFormatting.GRAY + I18n.translateToLocal("trmtgtnh.chunktamper.tip.left"));
            tooltip.add(TextFormatting.GRAY + I18n.translateToLocal("trmtgtnh.chunktamper.tip.sneakLeft"));
            tooltip.add(TextFormatting.GRAY + I18n.translateToLocal("trmtgtnh.chunktamper.tip.right"));
            tooltip.add(TextFormatting.GRAY + I18n.translateToLocal("trmtgtnh.chunktamper.tip.sneakRight"));
            tooltip.add(TextFormatting.GRAY + I18n.translateToLocal("trmtgtnh.chunktamper.tip.air"));
        } else {
            TamperTooltip.shiftHint(tooltip);
        }
    }

    /**
     * One, at its full reach.
     *
     * <p>
     * No grades: the grade was always a statement about material, and this one is past caring
     * about material.
     */
    @Override
    public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> list) {
        if (!isInCreativeTab(tab)) return;
        ItemStack stack = new ItemStack(this, 1, 0);
        setReach(stack, TrmtConfig.chunkTamperMaxReach);
        list.add(stack);
    }
}
