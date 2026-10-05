package com.trmtgtnh.item;

import java.util.List;


import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.ChatFormatting;
import net.minecraft.world.level.Level;

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
        super(new Item.Properties().stacksTo(1)
            .tab(CreativeModeTab.TAB_MISC));
        this.upgrade = upgrade;
    }

    public GolemUpgrade upgrade() {
        return upgrade;
    }

    /** Both endgame ones glint, because each of them is every other upgrade at once. */
    @Override
    public boolean isFoil(ItemStack stack) {
        return upgrade.carriesTheSet();
    }

    /** See {@link ItemTamper#addInformation} for why this one says which side it is on. */
    @Override
    public void appendHoverText(ItemStack stack, Level world,
        List<net.minecraft.network.chat.Component> lines, TooltipFlag advanced) {
        // Named before anything is written, so every line below - and every line anybody adds later
        // - stops at the edge of a column rather than the screen. See Tooltips.
        List<String> tooltip = Tooltips.lines(lines);
        tooltip.add(ChatFormatting.GOLD + com.trmtgtnh.util.Translate.get("trmtgtnh.golem.upgrade." + upgrade.key + ".desc"));
        tooltip.add(ChatFormatting.DARK_GRAY + com.trmtgtnh.util.Translate.get("trmtgtnh.golem.upgrade.tip"));
        tooltip.add(
            ChatFormatting.GRAY + com.trmtgtnh.util.Translate.get("trmtgtnh.golem.upgrade.renames")
                + " "
                + ChatFormatting.WHITE
                + com.trmtgtnh.util.Translate.get(upgrade.nameKey()));
    }
}
