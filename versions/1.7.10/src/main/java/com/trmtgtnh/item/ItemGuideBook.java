package com.trmtgtnh.item;

import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.trmtgtnh.Trmt;

/**
 * One of the guide books, in the hand.
 *
 * <p>
 * Right-click opens it. The screen is the client's business and opens without asking the server -
 * every word it shows is in the language file the client already has - while the server branch is
 * where reading is recorded, which is what an achievement can later be hung on. Both sides see the
 * same right-click, so neither needs a packet.
 */
public class ItemGuideBook extends Item {

    private final GuideBook book;

    public ItemGuideBook(GuideBook book) {
        this.book = book;
        setMaxStackSize(1);
        setCreativeTab(CreativeTabs.tabMisc);
        setTextureName(Trmt.MODID + ":" + book.itemName());
    }

    public GuideBook book() {
        return book;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (world.isRemote) {
            Trmt.proxy.openGuideScreen(book.ordinal());
            return stack;
        }
        // Server side: the moment a book is actually read, which is what gets rewarded.
        BookReading.read(player, book);
        return stack;
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        return StatCollector.translateToLocal(book.titleKey());
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean advanced) {
        // Substituted before anything is written, so every line below - and every line
        // anybody adds later - stops at the edge of a column rather than the screen.
        tooltip = Tooltips.wrapping(tooltip);
        tooltip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("trmtgtnh.guide." + book.key + ".blurb"));
        tooltip.add(EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal("trmtgtnh.guide.tip.open"));
    }
}
