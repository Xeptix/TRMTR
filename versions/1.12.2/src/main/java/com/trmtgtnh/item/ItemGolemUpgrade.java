package com.trmtgtnh.item;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

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
        setCreativeTab(CreativeTabs.MISC);
    }

    public GolemUpgrade upgrade() {
        return upgrade;
    }

    /** Both endgame ones glint, because each of them is every other upgrade at once. */
    @Override
    @SideOnly(Side.CLIENT)
    public boolean hasEffect(ItemStack stack) {
        return upgrade.carriesTheSet();
    }

    /** See {@link ItemTamper#addInformation} for why this one says which side it is on. */
    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag advanced) {
        // Substituted before anything is written, so every line below - and every line
        // anybody adds later - stops at the edge of a column rather than the screen.
        tooltip = Tooltips.wrapping(tooltip);
        tooltip.add(TextFormatting.GOLD + I18n.translateToLocal("trmtgtnh.golem.upgrade." + upgrade.key + ".desc"));
        tooltip.add(TextFormatting.DARK_GRAY + I18n.translateToLocal("trmtgtnh.golem.upgrade.tip"));
        tooltip.add(
            TextFormatting.GRAY + I18n.translateToLocal("trmtgtnh.golem.upgrade.renames")
                + " "
                + TextFormatting.WHITE
                + I18n.translateToLocal(upgrade.nameKey()));
    }
}
