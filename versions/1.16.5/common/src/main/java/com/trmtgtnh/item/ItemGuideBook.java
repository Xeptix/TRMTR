package com.trmtgtnh.item;

import java.util.List;


import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.ChatFormatting;
import net.minecraft.world.level.Level;

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
        super(new Item.Properties().stacksTo(1)
            .tab(CreativeModeTab.TAB_MISC));
        this.book = book;
    }

    public GuideBook book() {
        return book;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (world.isClientSide()) {
            com.trmtgtnh.Client.openGuideScreen(book.ordinal());
        } else {
            // Server side: the moment a book is actually read, which is what gets rewarded.
            BookReading.read(player, book);
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public net.minecraft.network.chat.Component getName(ItemStack stack) {
        return new net.minecraft.network.chat.TextComponent(com.trmtgtnh.util.Translate.get(book.titleKey()));
    }

    /** See {@link ItemTamper#addInformation} for why this one says which side it is on. */
    @Override
    public void appendHoverText(ItemStack stack, Level world,
        List<net.minecraft.network.chat.Component> lines, TooltipFlag advanced) {
        // Named before anything is written, so every line below - and every line anybody adds later
        // - stops at the edge of a column rather than the screen. See Tooltips.
        List<String> tooltip = Tooltips.lines(lines);
        tooltip.add(ChatFormatting.GRAY + com.trmtgtnh.util.Translate.get("trmtgtnh.guide." + book.key + ".blurb"));
        tooltip.add(ChatFormatting.DARK_GRAY + com.trmtgtnh.util.Translate.get("trmtgtnh.guide.tip.open"));
    }
}
