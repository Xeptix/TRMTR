package com.trmtgtnh.item;

import java.util.List;


import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionResult;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.world.level.Level;

import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.entity.EntityGolemOfWays;

/**
 * A Golem of Ways in the hand, waiting to be put down.
 *
 * <p>
 * The mod's own item rather than a vanilla spawn egg, because a vanilla egg needs a global entity
 * id and those are worth more than this. It also means the rarest find in the mod gets to look like
 * the thing it makes.
 *
 * <p>
 * Right-click a block to set one on top of it. It remembers who put it there, the same as one that
 * was built.
 */
public class ItemGolemEgg extends Item {

    public ItemGolemEgg() {
        super(new Item.Properties().stacksTo(1)
            .tab(CreativeModeTab.TAB_MISC));
    }

    /** It glints. It is the rarest thing here. */
    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
        Player player = context.getPlayer();
        Level world = context.getLevel();
        if (player == null || world == null) return InteractionResult.PASS;
        BlockPos pos = context.getClickedPos();
        ItemStack stack = player.getItemInHand(context.getHand());
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        if (world.isClientSide()) return InteractionResult.SUCCESS;
        if (!TrmtConfig.golemEnabled) {
            player.sendMessage(
                new TextComponent(ChatFormatting.RED + com.trmtgtnh.util.Translate.get("trmtgtnh.golem.disabled")),
                net.minecraft.Util.NIL_UUID);
            return InteractionResult.SUCCESS;
        }

        EntityGolemOfWays golem = com.trmtgtnh.entity.ModEntities.newGolem(world);
        if (golem == null) return InteractionResult.SUCCESS;
        golem.moveTo(x + 0.5D, y + 1.0D, z + 0.5D, world.random.nextFloat() * 360f, 0f);
        golem.setAnchor(x, y + 1, z);
        golem.setSummonedBy(
            player.getGameProfile()
                .getName());
        ModAchievements.onGolemBuilt(player);
        world.addFreshEntity(golem);

        if (!player.isCreative()) stack.shrink(1);
        return InteractionResult.SUCCESS;
    }

    /** See {@link ItemTamper#addInformation} for why this one says which side it is on. */
    @Override
    public void appendHoverText(ItemStack stack, Level world,
        List<net.minecraft.network.chat.Component> lines, TooltipFlag advanced) {
        // Named before anything is written, so every line below - and every line anybody adds later
        // - stops at the edge of a column rather than the screen. See Tooltips.
        List<String> tooltip = Tooltips.lines(lines);
        tooltip.add(ChatFormatting.GOLD + com.trmtgtnh.util.Translate.get("trmtgtnh.golem.egg.desc"));
        tooltip.add(ChatFormatting.DARK_GRAY + com.trmtgtnh.util.Translate.get("trmtgtnh.golem.egg.tip"));
    }
}
