package com.trmtgtnh.item;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

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
        setCreativeTab(CreativeTabs.MISC);
    }

    public GuideBook book() {
        return book;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (world.isRemote) {
            Trmt.proxy.openGuideScreen(book.ordinal());
        } else {
            // Server side: the moment a book is actually read, which is what gets rewarded.
            BookReading.read(player, book);
        }
        return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, stack);
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        return I18n.translateToLocal(book.titleKey());
    }

    /** See {@link ItemTamper#addInformation} for why this one says which side it is on. */
    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag advanced) {
        // Substituted before anything is written, so every line below - and every line
        // anybody adds later - stops at the edge of a column rather than the screen.
        tooltip = Tooltips.wrapping(tooltip);
        tooltip.add(TextFormatting.GRAY + I18n.translateToLocal("trmtgtnh.guide." + book.key + ".blurb"));
        tooltip.add(TextFormatting.DARK_GRAY + I18n.translateToLocal("trmtgtnh.guide.tip.open"));
    }
}
