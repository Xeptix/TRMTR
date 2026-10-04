package com.trmtgtnh.item;

import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.entity.GolemUpgrade;

/**
 * One upgrade for a Golem of Ways.
 *
 * <p>
 * An item per upgrade rather than one item with a metadata, for the same reason the guide books are
 * separate: each is meant to look like itself on the shelf, in the golem's slot, and on the golem.
 * The item carries which upgrade it is; everything the upgrade actually <em>does</em> is asked of
 * {@link GolemUpgrade} by whatever needs to know.
 */
public class ItemGolemUpgrade extends Item {

    private final GolemUpgrade upgrade;

    public ItemGolemUpgrade(GolemUpgrade upgrade) {
        this.upgrade = upgrade;
        setMaxStackSize(1);
        setCreativeTab(CreativeTabs.tabMisc);
        setTextureName(Trmt.MODID + ":" + upgrade.itemName());
    }

    public GolemUpgrade upgrade() {
        return upgrade;
    }

    /** Both endgame ones glint, because each of them is every other upgrade at once. */
    @Override
    public boolean hasEffect(ItemStack stack, int pass) {
        return upgrade.carriesTheSet();
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean advanced) {
        // Substituted before anything is written, so every line below - and every line
        // anybody adds later - stops at the edge of a column rather than the screen.
        tooltip = Tooltips.wrapping(tooltip);
        tooltip.add(
            EnumChatFormatting.GOLD
                + StatCollector.translateToLocal("trmtgtnh.golem.upgrade." + upgrade.key + ".desc"));
        tooltip.add(EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal("trmtgtnh.golem.upgrade.tip"));
        tooltip.add(
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("trmtgtnh.golem.upgrade.renames")
                + " "
                + EnumChatFormatting.WHITE
                + StatCollector.translateToLocal(upgrade.nameKey()));
    }
}
