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
        setMaxStackSize(1);
        setCreativeTab(CreativeTabs.tabMisc);
        setTextureName(Trmt.MODID + ":golem_egg");
    }

    /** It glints. It is the rarest thing here. */
    @Override
    public boolean hasEffect(ItemStack stack, int pass) {
        return true;
    }

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        if (world.isRemote) return true;
        if (!TrmtConfig.golemEnabled) {
            player.addChatMessage(
                new net.minecraft.util.ChatComponentText(
                    EnumChatFormatting.RED + StatCollector.translateToLocal("trmtgtnh.golem.disabled")));
            return true;
        }

        EntityGolemOfWays golem = new EntityGolemOfWays(world);
        golem.setLocationAndAngles(x + 0.5D, y + 1.0D, z + 0.5D, world.rand.nextFloat() * 360f, 0f);
        golem.setAnchor(x, y + 1, z);
        golem.setSummonedBy(player.getCommandSenderName());
        ModAchievements.onGolemBuilt(player);
        world.spawnEntityInWorld(golem);

        if (!player.capabilities.isCreativeMode) stack.stackSize--;
        return true;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean advanced) {
        // Substituted before anything is written, so every line below - and every line
        // anybody adds later - stops at the edge of a column rather than the screen.
        tooltip = Tooltips.wrapping(tooltip);
        tooltip.add(EnumChatFormatting.GOLD + StatCollector.translateToLocal("trmtgtnh.golem.egg.desc"));
        tooltip.add(EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal("trmtgtnh.golem.egg.tip"));
    }
}
